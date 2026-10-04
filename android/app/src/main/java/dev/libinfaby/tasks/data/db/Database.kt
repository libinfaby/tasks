package dev.libinfaby.tasks.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

/**
 * Cached open task. The server's full JSON (tags, subtasks, group) is kept in [json]; the other columns
 * exist for the local views and the reminder scheduler.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val priority: Int,
    val date: String?,
    val reminder: String?,
    val reminderRepeat: String?,
    val groupId: Long?,
    val json: String,
)

/** Cached server lists that are read whole (tag types, groups). */
@Entity(tableName = "blobs")
data class BlobEntity(@PrimaryKey val key: String, val json: String)

/** What the phone has an alarm set for, so a sync can tell which alarms changed. */
@Entity(tableName = "scheduled_reminders")
data class ScheduledReminderEntity(
    @PrimaryKey val taskId: Long,
    val reminder: String,
    val repeat: String?,
    val title: String,
    val body: String?,
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY priority DESC, id DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun all(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Query("DELETE FROM tasks")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Transaction
    suspend fun replaceAll(tasks: List<TaskEntity>) {
        clear()
        insertAll(tasks)
    }
}

@Dao
interface BlobDao {
    @Query("SELECT json FROM blobs WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Query("SELECT json FROM blobs WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(blob: BlobEntity)

    @Query("DELETE FROM blobs")
    suspend fun clear()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM scheduled_reminders")
    suspend fun all(): List<ScheduledReminderEntity>

    @Query("SELECT * FROM scheduled_reminders WHERE taskId = :taskId")
    suspend fun get(taskId: Long): ScheduledReminderEntity?

    @Upsert
    suspend fun upsert(reminder: ScheduledReminderEntity)

    @Query("DELETE FROM scheduled_reminders WHERE taskId = :taskId")
    suspend fun delete(taskId: Long)

    @Query("DELETE FROM scheduled_reminders")
    suspend fun clear()
}

@Database(
    entities = [TaskEntity::class, BlobEntity::class, ScheduledReminderEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TasksDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun blobs(): BlobDao
    abstract fun reminders(): ReminderDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun db(@ApplicationContext context: Context): TasksDatabase =
        Room.databaseBuilder(context, TasksDatabase::class.java, "tasks.db")
            // The database is only a cache of the server, so a schema change can simply rebuild it.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun taskDao(db: TasksDatabase) = db.tasks()
    @Provides fun blobDao(db: TasksDatabase) = db.blobs()
    @Provides fun reminderDao(db: TasksDatabase) = db.reminders()
}
