package com.ai.phoneagent.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 会话持久化数据库（Room）。
 *
 * 采用单例懒构建，进程内共享同一实例。
 */
@Database(
    entities = [ConversationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AquariusDatabase : RoomDatabase() {
    /** 会话数据访问对象。 */
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var instance: AquariusDatabase? = null

        /** 双重检查锁获取单例。 */
        fun getInstance(context: Context): AquariusDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AquariusDatabase::class.java,
                    // 历史库名，禁止改名：改名会让既有会话全部“消失”。
                    "aries.db",
                ).build().also { instance = it }
            }
        }
    }
}
