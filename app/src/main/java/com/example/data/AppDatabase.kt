package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettings)
}

@Dao
interface NpcDao {
    @Query("SELECT * FROM npcs ORDER BY createdAt DESC")
    fun getAllNpcsFlow(): Flow<List<NpcCharacter>>

    @Query("SELECT * FROM npcs WHERE id = :id LIMIT 1")
    suspend fun getNpcById(id: Long): NpcCharacter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNpc(npc: NpcCharacter): Long

    @Query("DELETE FROM npcs WHERE id = :id")
    suspend fun deleteNpcById(id: Long)
}

@Dao
interface AgentDao {
    @Query("SELECT * FROM agents ORDER BY createdAt DESC")
    fun getAllAgentsFlow(): Flow<List<AgentConfig>>

    @Query("SELECT * FROM agents WHERE id = :id LIMIT 1")
    suspend fun getAgentById(id: Long): AgentConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgent(agent: AgentConfig): Long

    @Query("DELETE FROM agents WHERE id = :id")
    suspend fun deleteAgentById(id: Long)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun getAllSessionsFlow(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): ChatSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession): Long

    @Update
    suspend fun updateSession(session: ChatSession)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages")
    fun getAllMessagesFlow(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesFlow(sessionId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: Long): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySessionId(sessionId: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Update
    suspend fun updateMessage(message: ChatMessage)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId AND id > :messageId")
    suspend fun deleteMessagesAfterId(sessionId: Long, messageId: Long)
}

@Dao
interface McpToolDao {
    @Query("SELECT * FROM mcp_tools ORDER BY createdAt DESC")
    fun getAllMcpToolsFlow(): Flow<List<McpTool>>

    @Query("SELECT * FROM mcp_tools WHERE id = :id LIMIT 1")
    suspend fun getMcpToolById(id: Long): McpTool?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMcpTool(tool: McpTool): Long

    @Query("DELETE FROM mcp_tools WHERE id = :id")
    suspend fun deleteMcpToolById(id: Long)
}

@Database(
    entities = [
        AppSettings::class,
        NpcCharacter::class,
        AgentConfig::class,
        ChatSession::class,
        ChatMessage::class,
        McpTool::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun npcDao(): NpcDao
    abstract fun agentDao(): AgentDao
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun mcpToolDao(): McpToolDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "agent_hub_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
