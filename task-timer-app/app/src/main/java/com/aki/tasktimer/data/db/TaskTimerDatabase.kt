package com.aki.tasktimer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL
import com.aki.tasktimer.data.db.dao.PresetDao
import com.aki.tasktimer.data.db.dao.SessionDao
import com.aki.tasktimer.data.db.entity.ExtensionEntity
import com.aki.tasktimer.data.db.entity.ExtensionPresetEntity
import com.aki.tasktimer.data.db.entity.SessionEntity
import com.aki.tasktimer.data.db.entity.TaskPresetEntity

/**
 * アプリ唯一のデータベース。
 *
 * 開発中にスキーマを変えたくなったら、破壊的マイグレーションを足すのではなく
 * `adb uninstall com.aki.tasktimer` で作り直すこと。
 * fallbackToDestructiveMigration を仕込むと、実運用に入ったあと事故る。
 */
@Database(
    entities = [
        SessionEntity::class,
        ExtensionEntity::class,
        TaskPresetEntity::class,
        ExtensionPresetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TaskTimerDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    abstract fun presetDao(): PresetDao

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

        fun build(context: Context): TaskTimerDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                TaskTimerDatabase::class.java,
                DB_NAME,
            )
                .addCallback(seedCallback)
                .build()
    }
}
