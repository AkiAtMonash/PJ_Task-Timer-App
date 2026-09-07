package com.aki.tasktimer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL
import com.aki.tasktimer.data.db.dao.PresetDao
import com.aki.tasktimer.data.db.dao.SessionDao
import com.aki.tasktimer.data.db.dao.SyncQueueDao
import com.aki.tasktimer.data.db.entity.ExtensionEntity
import com.aki.tasktimer.data.db.entity.ExtensionPresetEntity
import com.aki.tasktimer.data.db.entity.SessionEntity
import com.aki.tasktimer.data.db.entity.SyncQueueEntity
import com.aki.tasktimer.data.db.entity.TaskPresetEntity

/**
 * アプリ唯一のデータベース。
 *
 * スキーマを変えるときは version を上げて **本物のマイグレーション** を書く。
 * fallbackToDestructiveMigration を仕込むと、実運用に入ったあと記録が消える事故になる。
 * マイグレーションの SQL は app/schemas/ に出力される JSON の createSql と一字一句そろえること。
 * 手で書いて NOT NULL の有無がずれると、起動時に Room が検証で落ちる。
 */
@Database(
    entities = [
        SessionEntity::class,
        ExtensionEntity::class,
        TaskPresetEntity::class,
        ExtensionPresetEntity::class,
        SyncQueueEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TaskTimerDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    abstract fun presetDao(): PresetDao

    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        private const val DB_NAME = "tasktimer.db"

        /**
         * 延長プリセットの初期シード（docs/01_SPEC.md 3.5）。
         *
         * INSERT OR IGNORE にしているのは minutes の UNIQUE 制約を利用した保険。
         * 下の onCreate は Room の実装経路によってどちらのオーバーロードが呼ばれるかが変わり、
         * 万一両方走っても重複が入らないようにしてある。
         *
         * タグはシード対象ではない。固定 6 種なので enum（data/model/Tag.kt）で持つ。
         */
        private val SEED_SQL: List<String> = listOf(5, 10, 15, 30).map { minutes ->
            "INSERT OR IGNORE INTO extension_presets (minutes, useCount, isPinned) " +
                "VALUES ($minutes, 0, 0)"
        }

        private val seedCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                SEED_SQL.forEach(db::execSQL)
            }

            override fun onCreate(connection: SQLiteConnection) {
                SEED_SQL.forEach(connection::execSQL)
            }
        }

        /**
         * v1 → v2（Phase 2.5: Notion 同期）。
         * - sessions に notionPageId を足す（既存行は null）
         * - 送信待ち行列 sync_queue を作る
         *
         * SQL は app/schemas/com.aki.tasktimer.data.db.TaskTimerDatabase/2.json の createSql と同一。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `sessions` ADD COLUMN `notionPageId` TEXT")
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_queue` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, " +
                        "`operation` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`retryCount` INTEGER NOT NULL, " +
                        "`lastError` TEXT, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sync_queue_status` ON `sync_queue` (`status`)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sync_queue_sessionId` ON `sync_queue` (`sessionId`)",
                )
            }
        }

        fun build(context: Context): TaskTimerDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                TaskTimerDatabase::class.java,
                DB_NAME,
            )
                .addCallback(seedCallback)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
