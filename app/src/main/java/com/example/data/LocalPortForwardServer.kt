package com.example.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Locale
import java.util.concurrent.TimeUnit

class LocalPortForwardServer(
    private val loadSettings: suspend () -> AppSettings,
    private val onStatusChanged: (String) -> Unit = {}
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    @Volatile
    var listeningPort: Int? = null
        private set

    val isRunning: Boolean
        get() = acceptJob?.isActive == true && serverSocket?.isClosed == false

    fun start(scope: CoroutineScope, port: Int): Result<Unit> {
        if (port !in 1..65535) {
            return Result.failure(IllegalArgumentException("Port must be between 1 and 65535"))
        }

        stop()

        return try {
            val socket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress("0.0.0.0", port))
            }
            serverSocket = socket
            listeningPort = port

            acceptJob = scope.launch(Dispatchers.IO) {
                onStatusChanged("端口转发已启动，监听 0.0.0.0:$port")
                while (isActive) {
                    val client = try {
                        socket.accept()
                    } catch (e: SocketException) {
                        if (!isActive || socket.isClosed) break
                        onStatusChanged("端口转发监听异常: ${e.localizedMessage ?: e.message}")
                        continue
                    } catch (e: IOException) {
                        if (!isActive) break
                        onStatusChanged("端口转发监听异常: ${e.localizedMessage ?: e.message}")
                        continue
                    }

                    launch(Dispatchers.IO) {
                        handleClient(client)
                    }
                }
                onStatusChanged("端口转发已停止")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            onStatusChanged("端口转发启动失败: ${e.localizedMessage ?: e.message}")
            Result.failure(e)
        }
    }

    fun stop() {
        acceptJob?.cancel(CancellationException("Stopped by user/config"))
        acceptJob = null

        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }

        serverSocket = null
        listeningPort = null
    }

    private suspend fun handleClient(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 20_000

            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            try {
                val requestLine = readHttpLine(input) ?: return
                if (requestLine.isBlank()) {
                    writeHttpResponse(output, 400, "text/plain; charset=utf-8", "Bad Request")
                    return
                }

                val parts = requestLine.split(" ")
                if (parts.size < 2) {
                    writeHttpResponse(output, 400, "text/plain; charset=utf-8", "Bad Request")
                    return
                }

                val method = parts[0].uppercase(Locale.ROOT)
                val path = parts[1]
                if (method != "POST" && method != "GET") {
                    writeHttpResponse(output, 405, "application/json; charset=utf-8", "{\"error\":\"Only GET and POST are supported\"}")
                    return
                }

                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readHttpLine(input) ?: break
                    if (line.isBlank()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) {
                        val key = line.substring(0, idx).trim().lowercase(Locale.ROOT)
                        val value = line.substring(idx + 1).trim()
                        headers[key] = value
                    }
                }

                val settings = loadSettings()
                if (method == "GET") {
                    if (path == "/health") {
                        writeHttpResponse(
                            output,
                            200,
                            "application/json; charset=utf-8",
                            "{\"ok\":true,\"running\":true,\"message\":\"Local forward server is running\"}"
                        )
                        return
                    }

                    val targetUrl = buildGetTargetUrl(settings.baseUrl, path)
                    val requestBuilder = Request.Builder().url(targetUrl).get()
                    if (settings.apiKey.isNotBlank()) {
                        requestBuilder.addHeader("Authorization", "Bearer ${settings.apiKey}")
                    }

                    val upstreamResponse = httpClient.newCall(requestBuilder.build()).execute()
                    upstreamResponse.use { response ->
                        val responseBytes = response.body?.bytes() ?: ByteArray(0)
                        val contentType = response.header("Content-Type") ?: "application/json; charset=utf-8"
                        writeHttpResponse(output, response.code, contentType, responseBytes)
                    }
                    return
                }

                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                if (contentLength <= 0) {
                    writeHttpResponse(output, 400, "application/json; charset=utf-8", "{\"error\":\"Request body is required\"}")
                    return
                }
                if (contentLength > 2_000_000) {
                    writeHttpResponse(output, 413, "application/json; charset=utf-8", "{\"error\":\"Payload too large\"}")
                    return
                }

                val bodyBytes = readExactBytes(input, contentLength)
                val forwardRequest = extractForwardRequest(bodyBytes)
                val targetUrl = cleanBaseUrl(settings.baseUrl) + "chat/completions"

                val requestBuilder = Request.Builder()
                    .url(targetUrl)
                    .post(forwardRequest.payload.toRequestBody("application/json; charset=utf-8".toMediaType()))

                if (settings.apiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer ${settings.apiKey}")
                }

                val upstreamResponse = httpClient.newCall(requestBuilder.build()).execute()
                upstreamResponse.use { response ->
                    val contentType = response.header("Content-Type") ?: "application/json; charset=utf-8"
                    if (forwardRequest.shouldStream && response.isSuccessful) {
                        streamHttpResponse(output, response.code, contentType) { chunkWriter ->
                            val source = response.body?.source() ?: throw IOException("Empty response body")
                            val buffer = Buffer()
                            while (true) {
                                val readCount = source.read(buffer, 8192)
                                if (readCount == -1L) break
                                if (buffer.size > 0L) {
                                    chunkWriter(buffer.readByteArray())
                                }
                            }
                        }
                    } else {
                        val responseBytes = response.body?.bytes() ?: ByteArray(0)
                        writeHttpResponse(output, response.code, contentType, responseBytes)
                    }
                }
            } catch (e: IllegalArgumentException) {
                writeHttpResponse(output, 400, "application/json; charset=utf-8", "{\"error\":\"${escapeJson(e.message ?: "Invalid request body")}\"}")
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: e.message ?: "Upstream forwarding failed"
                writeHttpResponse(output, 502, "application/json; charset=utf-8", "{\"error\":\"${escapeJson(msg)}\"}")
            }
        }
    }

    private data class ForwardRequest(
        val payload: String,
        val shouldStream: Boolean
    )

    private fun extractForwardRequest(bodyBytes: ByteArray): ForwardRequest {
        val raw = bodyBytes.toString(Charsets.UTF_8).trim()
        if (raw.isBlank()) {
            throw IllegalArgumentException("Empty request body")
        }

        val parsed = JSONTokener(raw).nextValue()
        return when (parsed) {
            is JSONObject -> {
                if (parsed.has("body")) {
                    when (val bodyNode = parsed.get("body")) {
                        is JSONObject -> ForwardRequest(
                            payload = bodyNode.toString(),
                            shouldStream = bodyNode.optBoolean("stream", false)
                        )
                        is JSONArray -> ForwardRequest(
                            payload = bodyNode.toString(),
                            shouldStream = false
                        )
                        else -> throw IllegalArgumentException("Field 'body' must be a JSON object or array")
                    }
                } else {
                    ForwardRequest(
                        payload = parsed.toString(),
                        shouldStream = parsed.optBoolean("stream", false)
                    )
                }
            }

            is JSONArray -> ForwardRequest(
                payload = parsed.toString(),
                shouldStream = false
            )
            else -> throw IllegalArgumentException("Body must be valid JSON")
        }
    }

    private fun writeHttpResponse(output: BufferedOutputStream, code: Int, contentType: String, body: String) {
        writeHttpResponse(output, code, contentType, body.toByteArray(Charsets.UTF_8))
    }

    private fun writeHttpResponse(output: BufferedOutputStream, code: Int, contentType: String, body: ByteArray) {
        val reason = reasonPhrase(code)
        val header = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }

        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(body)
        output.flush()
    }

    private fun streamHttpResponse(
        output: BufferedOutputStream,
        code: Int,
        contentType: String,
        writeBody: (chunkWriter: (ByteArray) -> Unit) -> Unit
    ) {
        val reason = reasonPhrase(code)
        val header = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Transfer-Encoding: chunked\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }

        output.write(header.toByteArray(Charsets.UTF_8))
        output.flush()

        try {
            writeBody { chunk ->
                if (chunk.isNotEmpty()) {
                    writeChunk(output, chunk)
                    output.flush()
                }
            }
        } catch (_: Exception) {
            // The stream is already committed at this point; close it cleanly.
        } finally {
            writeChunkTerminator(output)
            output.flush()
        }
    }

    private fun writeChunk(output: BufferedOutputStream, chunk: ByteArray) {
        output.write("${chunk.size.toString(16)}\r\n".toByteArray(Charsets.UTF_8))
        output.write(chunk)
        output.write("\r\n".toByteArray(Charsets.UTF_8))
    }

    private fun writeChunkTerminator(output: BufferedOutputStream) {
        output.write("0\r\n\r\n".toByteArray(Charsets.UTF_8))
    }

    private fun reasonPhrase(code: Int): String {
        return when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            405 -> "Method Not Allowed"
            413 -> "Payload Too Large"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            else -> "HTTP"
        }
    }

    private fun cleanBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("Configured API endpoint is empty")
        }
        return if (trimmed.endsWith('/')) trimmed else "$trimmed/"
    }

    private fun buildGetTargetUrl(baseUrl: String, rawPath: String): String {
        val normalizedPath = rawPath.trim().ifBlank { "/models" }
        val targetPath = normalizedPath.removePrefix("/")
        return cleanBaseUrl(baseUrl) + targetPath
    }

    private fun readHttpLine(input: BufferedInputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) {
                if (buffer.size() == 0) return null
                break
            }
            if (b == '\n'.code) {
                break
            }
            if (b != '\r'.code) {
                buffer.write(b)
            }
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    private fun readExactBytes(input: BufferedInputStream, size: Int): ByteArray {
        val bytes = ByteArray(size)
        var read = 0
        while (read < size) {
            val count = input.read(bytes, read, size - read)
            if (count == -1) {
                throw IOException("Unexpected end of stream")
            }
            read += count
        }
        return bytes
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
