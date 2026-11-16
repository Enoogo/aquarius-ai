package com.ai.phoneagent.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 自动化任务历史数据库。
 *
 * 单独使用 `aries_tasks.db`（历史库名，禁止改名，否则已记录的任务历史会丢失），
 * 避免影响既有会话库的表结构版本。
 */
@Database(
    entities = [AutomationTaskEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AutomationTaskDatabase : RoomDatabase() {

    abstract fun taskDao(): AutomationTaskDao

    companion object {
        @Volatile
        private var instance: AutomationTaskDatabase? = null

        fun getInstance(context: Context): AutomationTaskDatabase {
            return instance
                ?: synchronized(this) {
                    instance
                        ?: Room.databaseBuilder(
                            context.applicationContext,
                            AutomationTaskDatabase::class.java,
                            "aries_tasks.db",
                        ).build().also { instance = it }
                }
        }
    }
}
