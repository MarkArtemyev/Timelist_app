package com.example.todotimerapp2.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

@Database(entities = [TodoEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN dayOfWeek INTEGER NOT NULL DEFAULT 1")
                val today = LocalDate.now().dayOfWeek.value
                db.execSQL("UPDATE todos SET dayOfWeek = $today WHERE dayOfWeek IS NULL OR dayOfWeek < 1")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN position INTEGER NOT NULL DEFAULT 2147483647")
                // Заполним позиции внутри каждого дня по updatedAt DESC
                for (day in 1..7) {
                    // Room не даёт курсорного UPDATE тут — используем временную таблицу
                    db.execSQL("""
                        CREATE TEMP TABLE tmp_ids AS
                        SELECT id FROM todos WHERE dayOfWeek=$day ORDER BY updatedAt DESC
                    """.trimIndent())
                    // Пробежимся и выставим индекс
                    db.query("SELECT rowid, id FROM tmp_ids").use { cursor ->
                        var pos = 0
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(1)
                            db.execSQL("UPDATE todos SET position=$pos WHERE id=$id")
                            pos++
                        }
                    }
                    db.execSQL("DROP TABLE tmp_ids")
                }
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "todo_timer_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
