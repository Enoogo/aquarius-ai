package com.ai.phoneagent.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** 自动化任务记录数据访问对象。 */
@Dao
interface AutomationTaskDao {

    @Upsert
    suspend fun upsert(item: AutomationTaskEntity)

    @Query("SELECT * FROM automation_tasks WHERE runId = :runId LIMIT 1")
    suspend fun findByRunId(runId: String): AutomationTaskEntity?

    @Query("SELECT * FROM automation_tasks ORDER BY updatedAt DESC")
    suspend fun getAll(): List<AutomationTaskEntity>

    @Query("SELECT * FROM automation_tasks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AutomationTaskEntity>>

    @Query("DELETE FROM automation_tasks WHERE runId = :runId")
    suspend fun deleteByRunId(runId: String)

    @Query(
        "DELETE FROM automation_tasks WHERE runId NOT IN " +
            "(SELECT runId FROM automation_tasks ORDER BY updatedAt DESC LIMIT :keep)"
    )
    suspend fun prune(keep: Int)
}
