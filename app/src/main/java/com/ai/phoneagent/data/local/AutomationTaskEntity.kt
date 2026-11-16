package com.ai.phoneagent.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 自动化任务运行记录。
 *
 * 与“最近一次结果”不同，这里按 runId 保留每一次任务（包括暂停、终止、完成）的
 * 完整操作日志，供历史对话与任务详情页回看。
 */
@Entity(tableName = "automation_tasks")
data class AutomationTaskEntity(
    @PrimaryKey val runId: String,
    val goal: String,
    val status: String,
    val steps: Int,
    val log: String,
    val message: String,
    val startedAt: Long,
    val updatedAt: Long,
)
