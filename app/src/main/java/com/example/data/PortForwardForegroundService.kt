package com.example.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.net.ServerSocket

class PortForwardForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: AppRepository
    private var localServer: LocalPortForwardServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var startJob: Job? = null
    private var notificationKeepAliveJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(applicationContext)
        createNotificationChannel()
        _stateFlow.value = _stateFlow.value.copy(message = "端口转发服务已就绪")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForwarding()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START,
            null -> {
                val preferredPort = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT)
                    ?: DEFAULT_PORT
                startForeground(NOTIFICATION_ID, buildNotification("端口转发启动中..."))
                startForwarding(preferredPort)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForwarding()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForwarding(preferredPort: Int) {
        if (stateFlow.value.isRunning && stateFlow.value.port == preferredPort) {
            return
        }

        startJob?.cancel()
        startJob = serviceScope.launch {
            try {
                acquireLocks()

                localServer?.stop()
                localServer = LocalPortForwardServer(
                    loadSettings = { repository.getSettings() },
                    onStatusChanged = { message ->
                        _stateFlow.value = _stateFlow.value.copy(message = message)
                        updateNotification(message)
                    }
                )

                if (preferredPort != DEFAULT_PORT) {
                    _stateFlow.value = PortForwardServiceState(
                        isRunning = false,
                        port = null,
                        message = "端口转发启动失败: 固定端口必须是 $DEFAULT_PORT"
                    )
                    updateNotification(_stateFlow.value.message)
                    stopForegroundCompat()
                    return@launch
                }

                if (!isPortAvailable(DEFAULT_PORT)) {
                    _stateFlow.value = PortForwardServiceState(
                        isRunning = false,
                        port = null,
                        message = "端口转发启动失败: 端口 $DEFAULT_PORT 已被占用"
                    )
                    updateNotification(_stateFlow.value.message)
                    stopForegroundCompat()
                    return@launch
                }

                val startResult = localServer?.start(serviceScope, DEFAULT_PORT)
                if (startResult?.isSuccess == true) {
                    _stateFlow.value = PortForwardServiceState(
                        isRunning = true,
                        port = DEFAULT_PORT,
                        message = "端口转发已启动，监听 0.0.0.0:$DEFAULT_PORT，转发到已配置 API 端点"
                    )
                    ensureForegroundNotification(_stateFlow.value.message)
                    startNotificationKeepAlive()
                } else {
                    val error = startResult?.exceptionOrNull()?.localizedMessage
                        ?: startResult?.exceptionOrNull()?.message
                        ?: "未知错误"
                    _stateFlow.value = PortForwardServiceState(
                        isRunning = false,
                        port = null,
                        message = "端口转发启动失败: $error"
                    )
                    updateNotification(_stateFlow.value.message)
                    stopNotificationKeepAlive()
                    stopForegroundCompat()
                }
            } catch (e: Exception) {
                _stateFlow.value = PortForwardServiceState(
                    isRunning = false,
                    port = null,
                    message = "端口转发启动失败: ${e.localizedMessage ?: e.message ?: e.toString()}"
                )
                updateNotification(_stateFlow.value.message)
                stopNotificationKeepAlive()
                stopForegroundCompat()
            }
        }
    }

    private fun stopForwarding() {
        startJob?.cancel()
        startJob = null
        localServer?.stop()
        localServer = null
        releaseLocks()
        _stateFlow.value = PortForwardServiceState(
            isRunning = false,
            port = null,
            message = "端口转发未启动"
        )
        stopNotificationKeepAlive()
        updateNotification(_stateFlow.value.message)
        stopForegroundCompat()
    }

    private fun acquireLocks() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:port_forward_wakelock").apply {
                setReferenceCounted(false)
                acquire()
            }
        }

        if (wifiLock == null) {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "$packageName:port_forward_wifilock").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null

        wifiLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wifiLock = null
    }

    private fun isPortAvailable(port: Int): Boolean {
        return runCatching {
            ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress("0.0.0.0", port))
            }
            true
        }.getOrDefault(false)
    }

    private fun buildNotification(message: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("端口转发运行中")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureForegroundNotification(message: String) {
        val notification = buildNotification(message)
        startForeground(NOTIFICATION_ID, notification)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(message: String) {
        val notification = buildNotification(message)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun startNotificationKeepAlive() {
        notificationKeepAliveJob?.cancel()
        notificationKeepAliveJob = serviceScope.launch {
            while (isActive && _stateFlow.value.isRunning) {
                ensureForegroundNotification(_stateFlow.value.message)
                delay(3000)
            }
        }
    }

    private fun stopNotificationKeepAlive() {
        notificationKeepAliveJob?.cancel()
        notificationKeepAliveJob = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "端口转发服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持局域网端口转发服务在前台运行"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val CHANNEL_ID = "port_forward_service"
        private const val NOTIFICATION_ID = 8787
        private const val DEFAULT_PORT = 8787
        private const val EXTRA_PORT = "extra_port"
        private const val ACTION_START = "com.example.action.PORT_FORWARD_START"
        private const val ACTION_STOP = "com.example.action.PORT_FORWARD_STOP"

        data class PortForwardServiceState(
            val isRunning: Boolean = false,
            val port: Int? = null,
            val message: String = "端口转发未启动"
        )

        internal val _stateFlow = MutableStateFlow(PortForwardServiceState())
        val stateFlow = _stateFlow.asStateFlow()

        fun start(context: Context, preferredPort: Int) {
            val intent = Intent(context, PortForwardForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, preferredPort)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PortForwardForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
