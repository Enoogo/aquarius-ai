package com.ai.phoneagent.core.automation

import android.content.Context
import android.content.Intent

/** 自动化日志广播桥：通过广播把自动化执行日志分发给 UI 层展示。 */
object AutomationLogBridge {
    /** 广播 Action，用于注册接收器。 */
    const val ACTION_AUTOMATION_LOG =
        "com.ai.phoneagent.action.AUTOMATION_LOG"

    /** 任务开始广播：主界面据此立即建立历史对话。 */
    const val ACTION_AUTOMATION_TASK_STARTED =
        "com.ai.phoneagent.action.AUTOMATION_TASK_STARTED"

    /** 任务状态变化广播（暂停/继续/结束）。 */
    const val ACTION_AUTOMATION_TASK_STATE =
        "com.ai.phoneagent.action.AUTOMATION_TASK_STATE"

    private const val EXTRA_LOG_LINE = "automation_log_line"
    private const val EXTRA_RUN_ID = "automation_run_id"
    private const val EXTRA_GOAL = "automation_goal"
    private const val EXTRA_STARTED_AT = "automation_started_at"
    private const val EXTRA_STATUS = "automation_status"
    private const val EXTRA_MESSAGE = "automation_message"

    /** 发布一条日志广播；空白行会被忽略。runId 用于把日志精确归属到历史会话。 */
    fun publish(context: Context, line: String, runId: String = "") {
        val normalized = line.trim()
        if (normalized.isBlank()) return
        val intent = Intent(ACTION_AUTOMATION_LOG).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_LOG_LINE, normalized)
            if (runId.isNotBlank()) putExtra(EXTRA_RUN_ID, runId)
        }
        context.sendBroadcast(intent)
    }

    /** 从广播 Intent 中还原日志行，空白返回 null。 */
    fun extract(intent: Intent?): String? {
        val line = intent?.getStringExtra(EXTRA_LOG_LINE)?.trim().orEmpty()
        return line.ifBlank { null }
    }

    /** 从任意自动化广播中读取 runId。 */
    fun extractRunId(intent: Intent?): String? {
        return intent?.getStringExtra(EXTRA_RUN_ID)?.trim()?.ifBlank { null }
    }

    /** 任务开始：携带 runId / 目标 / 开始时间，便于主界面建立历史会话。 */
    fun publishTaskStarted(context: Context, runId: String, goal: String, startedAt: Long) {
        if (runId.isBlank()) return
        val intent = Intent(ACTION_AUTOMATION_TASK_STARTED).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_RUN_ID, runId)
            putExtra(EXTRA_GOAL, goal)
            putExtra(EXTRA_STARTED_AT, startedAt)
        }
        context.sendBroadcast(intent)
    }

    /** 任务状态变化：暂停、继续或进入终态。 */
    fun publishTaskState(
        context: Context,
        runId: String,
        status: String,
        message: String = "",
    ) {
        if (runId.isBlank()) return
        val intent = Intent(ACTION_AUTOMATION_TASK_STATE).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_RUN_ID, runId)
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_MESSAGE, message)
        }
        context.sendBroadcast(intent)
    }

    data class TaskStartedPayload(val runId: String, val goal: String, val startedAt: Long)

    fun extractTaskStarted(intent: Intent?): TaskStartedPayload? {
        intent ?: return null
        val runId = intent.getStringExtra(EXTRA_RUN_ID)?.trim().orEmpty()
        if (runId.isBlank()) return null
        return TaskStartedPayload(
            runId = runId,
            goal = intent.getStringExtra(EXTRA_GOAL)?.trim().orEmpty(),
            startedAt = intent.getLongExtra(EXTRA_STARTED_AT, 0L),
        )
    }

    data class TaskStatePayload(val runId: String, val status: String, val message: String)

    fun extractTaskState(intent: Intent?): TaskStatePayload? {
        intent ?: return null
        val runId = intent.getStringExtra(EXTRA_RUN_ID)?.trim().orEmpty()
        if (runId.isBlank()) return null
        return TaskStatePayload(
            runId = runId,
            status = intent.getStringExtra(EXTRA_STATUS)?.trim().orEmpty(),
            message = intent.getStringExtra(EXTRA_MESSAGE)?.trim().orEmpty(),
        )
    }
}
