package com.ai.phoneagent.data.local

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 自动化任务历史仓库。
 *
 * 每次任务按 runId 落库，日志随执行过程增量更新；最多保留最近 [MAX_RECORDS] 条。
 */
class AutomationTaskRepository(context: Context) {

    private val dao = AutomationTaskDatabase.getInstance(context).taskDao()

    fun observeAll(): Flow<List<AutomationTaskEntity>> = dao.observeAll()

    fun observe(runId: String): Flow<AutomationTaskEntity?> =
        dao.observeAll().map { list -> list.firstOrNull { it.runId == runId } }

    suspend fun get(runId: String): AutomationTaskEntity? = dao.findByRunId(runId)

    suspend fun getAll(): List<AutomationTaskEntity> = dao.getAll()

    suspend fun save(item: AutomationTaskEntity) {
        dao.upsert(item)
        dao.prune(MAX_RECORDS)
    }

    suspend fun delete(runId: String) {
        dao.deleteByRunId(runId)
    }

    suspend fun updateStatus(runId: String, status: String, message: String = "") {
        val existing = dao.findByRunId(runId) ?: return
        dao.upsert(
            existing.copy(
                status = status,
                message = message.ifBlank { existing.message },
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    companion object {
        const val MAX_RECORDS = 100
    }
}
