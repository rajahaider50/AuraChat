package com.aura.chat.data

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val personaId: String,
    val systemPrompt: String,
    val model: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val role: String,
    val content: String,
    val isError: Boolean = false,
    val feedback: Int = 0,
    val createdAt: Long,
)

@Entity(tableName = "prompts")
data class PromptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val text: String,
    val createdAt: Long,
)

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)

/** Long-term memory. Every row is also mirrored to a Markdown file on disk. */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    /** profile | preference | project | fact | other */
    val category: String,
    /** chat | call | manual | import */
    val source: String,
    val conversationId: String? = null,
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A skill installed from a GitHub repository or any raw Markdown URL. */
@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val sourceUrl: String,
    val instructions: String,
    val enabled: Boolean = false,
    val installedAt: Long,
)

/** A custom MCP / REST API / application connector. */
@Entity(tableName = "connectors")
data class ConnectorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** mcp | api | app */
    val type: String,
    val url: String,
    val apiKey: String = "",
    /** JSON object of extra headers. */
    val headers: String = "{}",
    val description: String = "",
    val enabled: Boolean = true,
    val createdAt: Long,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("conversationId"), Index("messageId")],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long = 0,
    val conversationId: String,
    val name: String,
    val mime: String,
    val localPath: String,
    val size: Long,
    /** image | audio | pdf | pdf_scan | zip | text | file */
    val kind: String,
    /** Cached text extracted from documents / audio transcription. */
    val extractedText: String = "",
    val createdAt: Long,
)

data class MessageStat(
    val conversationId: String,
    val role: String,
    val createdAt: Long,
    val len: Int,
)

data class ConversationWithPreview(
    @Embedded val conversation: ConversationEntity,
    val preview: String?,
)

@Dao
interface ConversationDao {
    @Query(
        "SELECT c.*, (SELECT content FROM messages WHERE conversationId = c.id ORDER BY id DESC LIMIT 1) AS preview " +
            "FROM conversations c ORDER BY c.pinned DESC, c.updatedAt DESC"
    )
    fun observeAll(): Flow<List<ConversationWithPreview>>

    @Query(
        "SELECT c.*, (SELECT content FROM messages WHERE conversationId = c.id ORDER BY id DESC LIMIT 1) AS preview " +
            "FROM conversations c WHERE c.title LIKE '%' || :q || '%' " +
            "OR EXISTS (SELECT 1 FROM messages m WHERE m.conversationId = c.id AND m.content LIKE '%' || :q || '%') " +
            "ORDER BY c.updatedAt DESC"
    )
    fun search(q: String): Flow<List<ConversationWithPreview>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun all(): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun pin(id: String, pinned: Boolean)

    @Query("UPDATE conversations SET updatedAt = :time WHERE id = :id")
    suspend fun touch(id: String, time: Long)

    @Query("UPDATE conversations SET systemPrompt = :systemPrompt, model = :model WHERE id = :id")
    suspend fun updateConfig(id: String, systemPrompt: String, model: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY createdAt ASC, id ASC")
    fun observe(id: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY createdAt ASC, id ASC")
    suspend fun list(id: String): List<MessageEntity>

    @Insert
    suspend fun insert(m: MessageEntity): Long

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :id")
    suspend fun clear(id: String)

    @Query("UPDATE messages SET feedback = :feedback WHERE id = :id")
    suspend fun setFeedback(id: Long, feedback: Int)

    @Query("SELECT conversationId, role, createdAt, LENGTH(content) AS len FROM messages WHERE isError = 0")
    fun observeStats(): Flow<List<MessageStat>>
}

@Dao
interface PromptDao {
    @Query("SELECT * FROM prompts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PromptEntity>>

    @Query("SELECT COUNT(*) FROM prompts")
    suspend fun count(): Int

    @Insert
    suspend fun insert(p: PromptEntity): Long

    @Query("DELETE FROM prompts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY id")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY id")
    suspend fun all(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun get(id: Long): ProviderEntity?

    @Insert
    suspend fun insert(p: ProviderEntity): Long

    @Update
    suspend fun update(p: ProviderEntity)

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query(
        "SELECT * FROM memories WHERE title LIKE '%' || :q || '%' OR content LIKE '%' || :q || '%' " +
            "ORDER BY pinned DESC, updatedAt DESC"
    )
    fun observeSearch(q: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY pinned DESC, updatedAt DESC")
    suspend fun all(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY pinned DESC, updatedAt DESC")
    suspend fun byCategory(category: String): List<MemoryEntity>

    @Query(
        "SELECT * FROM memories WHERE title LIKE '%' || :q || '%' OR content LIKE '%' || :q || '%' " +
            "ORDER BY pinned DESC, updatedAt DESC LIMIT :limit"
    )
    suspend fun search(q: String, limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE pinned = 1 ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun pinned(limit: Int): List<MemoryEntity>

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM memories WHERE content = :content")
    suspend fun countContent(content: String): Int

    @Insert
    suspend fun insert(m: MemoryEntity): Long

    @Update
    suspend fun update(m: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY installedAt DESC")
    fun observeAll(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills ORDER BY installedAt DESC")
    suspend fun all(): List<SkillEntity>

    @Query("SELECT * FROM skills WHERE id = :id")
    suspend fun get(id: Long): SkillEntity?

    @Query("SELECT COUNT(*) FROM skills")
    suspend fun count(): Int

    @Insert
    suspend fun insert(s: SkillEntity): Long

    @Update
    suspend fun update(s: SkillEntity)

    @Query("DELETE FROM skills WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ConnectorDao {
    @Query("SELECT * FROM connectors ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ConnectorEntity>>

    @Query("SELECT * FROM connectors ORDER BY createdAt DESC")
    suspend fun all(): List<ConnectorEntity>

    @Query("SELECT * FROM connectors WHERE id = :id")
    suspend fun get(id: Long): ConnectorEntity?

    @Query("SELECT COUNT(*) FROM connectors")
    suspend fun count(): Int

    @Insert
    suspend fun insert(c: ConnectorEntity): Long

    @Update
    suspend fun update(c: ConnectorEntity)

    @Query("DELETE FROM connectors WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeForConversation(conversationId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun listForConversation(conversationId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE messageId = :messageId ORDER BY createdAt ASC")
    suspend fun listForMessage(messageId: Long): List<AttachmentEntity>

    @Insert
    suspend fun insert(a: AttachmentEntity): Long

    @Query("UPDATE attachments SET messageId = :messageId WHERE conversationId = :conversationId AND messageId = 0")
    suspend fun attachPending(conversationId: String, messageId: Long)

    @Query("UPDATE attachments SET extractedText = :text, kind = :kind WHERE id = :id")
    suspend fun setExtracted(id: Long, text: String, kind: String)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM attachments WHERE messageId = 0 AND conversationId = :conversationId")
    suspend fun pending(conversationId: String): List<AttachmentEntity>
}

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        PromptEntity::class,
        ProviderEntity::class,
        MemoryEntity::class,
        SkillEntity::class,
        ConnectorEntity::class,
        AttachmentEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun prompts(): PromptDao
    abstract fun providers(): ProviderDao
    abstract fun memories(): MemoryDao
    abstract fun skills(): SkillDao
    abstract fun connectors(): ConnectorDao
    abstract fun attachments(): AttachmentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN feedback INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `memories` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `content` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, `conversationId` TEXT, `pinned` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `skills` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, `sourceUrl` TEXT NOT NULL, `instructions` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, `installedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `connectors` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, `url` TEXT NOT NULL, `apiKey` TEXT NOT NULL, " +
                        "`headers` TEXT NOT NULL, `description` TEXT NOT NULL, `enabled` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attachments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `messageId` INTEGER NOT NULL, " +
                        "`conversationId` TEXT NOT NULL, `name` TEXT NOT NULL, `mime` TEXT NOT NULL, " +
                        "`localPath` TEXT NOT NULL, `size` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                        "`extractedText` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_conversationId` ON `attachments` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_messageId` ON `attachments` (`messageId`)")
            }
        }
    }
}
