package com.ai.phoneagent

import java.util.concurrent.atomic.AtomicInteger
import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.util.Log
import com.ai.phoneagent.BuildConfig
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.ai.phoneagent.core.overlay.OverlayTaskBridge
import com.ai.phoneagent.core.settings.AppUiPrefs
import com.ai.phoneagent.core.settings.Haptics
import com.ai.phoneagent.core.utils.DisplayUtils
import com.ai.phoneagent.system.startActivityWithMaterialForwardTransition
import com.ai.phoneagent.viewmodel.AutomationViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AutomationOverlay(
    private val liveNotification: AutomationLiveNotification,
) {

    private var wm: WindowManager? = null
    private var container: OverlayContainer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // 设置页“悬浮窗开关”与真实窗口状态的唯一真相源。
    private val _visible = MutableStateFlow(false)
    val visibleFlow: StateFlow<Boolean> = _visible.asStateFlow()

    /** 是否有任务正在执行（决定“＋”是否可用、窗口显示待机还是执行态）。 */
    @Volatile private var taskRunning: Boolean = false

    // 用户手动隐藏后的“显示”文字窗；与主窗互斥显示。
    private var overlayContext: Context? = null
    private var hiddenTagView: TextView? = null
    private var hiddenTagParams: WindowManager.LayoutParams? = null
    private var userHidden = false

    // “隐藏/显示”回到原位用的锚点：隐藏那一刻“隐藏”两字中心相对窗口左上角的偏移。
    private var hideAnchorOffsetX = 0
    private var hideAnchorOffsetY = 0

    /** 用户此刻是否处于“隐藏”态（窗口收起、只留“显示”小字）。 */
    fun isUserHidden(): Boolean = userHidden

    private var maxSteps: Int = 1
    private var launchMainOnClick: Boolean = false
    
    // 【优化】预估的总步骤数（从模型思考中解析）
    private var estimatedTotalSteps: Int = 0
    // 【优化】是否已解析过预估步骤数
    private var hasEstimatedSteps: Boolean = false
    // 【优化新增】流式思考显示
    private var isShowingThinking: Boolean = false
    private var thinkingText: String = ""
    // 【修复】临时隐藏引用计数，防止并发调用导致竞态
    private val hideCounter = AtomicInteger(0)
    @Volatile private var inputVerifyHighlightActive: Boolean = false
    @Volatile private var pausedState: Boolean = false

    private inline fun runOnMain(crossinline action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post { action() }
        }
    }

    fun canDrawOverlays(context: Context): Boolean {
        if (PhoneAgentAccessibilityService.instance != null) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun openOverlayPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

        fun show(
            context: Context,
            title: String,
            subtitle: String,
            maxSteps: Int,
            activity: Activity? = null,
            navigateMainOnClick: Boolean = false,
            onStopTask: () -> Unit = {},
            taskActive: Boolean = true,
        ): Boolean {
        hide()

        this.maxSteps = maxSteps.coerceAtLeast(1)
        this.launchMainOnClick = navigateMainOnClick
        this.pausedState = false
        this.taskRunning = taskActive
        // 【优化】重置预估步骤数
        this.estimatedTotalSteps = 0
        this.hasEstimatedSteps = false
        // 【修复】重置隐藏计数器
        this.hideCounter.set(0)

        val appCtx = context.applicationContext
        // 统一使用应用级 WindowManager，避免依赖无障碍服务生命周期导致窗口被系统移除。
        val w = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        overlayContext = appCtx
        val view =
            OverlayContainer(
                appCtx,
                onStopTask = {
                    Haptics.tap(appCtx)
                    handleStopClick(onStopTask)
                },
                onHideTask = { setUserHidden(true) },
                onSubmitTask = { text -> handleSubmitTask(text) },
                isTaskRunning = { taskRunning },
            )
        view.setTexts(title, subtitle)
        view.setProgress(0f)
        view.setInputVerifyHighlight(inputVerifyHighlightActive)
        view.setPaused(false)
        view.setOnClickListener {
            val i =
                if (launchMainOnClick) {
                    Intent(appCtx, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        )
                        putExtra(MainActivity.EXTRA_SCROLL_TO_BOTTOM, true)
                        putExtra(MainActivity.EXTRA_SHOW_AUTOMATION_STOP, true)
                    }
                } else {
                    Intent(appCtx, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        )
                        putExtra(AutomationViewModel.EXTRA_FORCE_TOP_ON_ENTRY, true)
                    }
                }
            appCtx.startActivityWithMaterialForwardTransition(i)
        }

        val overlayW =
                minOf(
                        DisplayUtils.dp(appCtx, 302),
                        (appCtx.resources.displayMetrics.widthPixels - DisplayUtils.dp(appCtx, 20))
                                .coerceAtLeast(DisplayUtils.dp(appCtx, 200))
                )
        val overlayH =
                minOf(
                        DisplayUtils.dp(appCtx, 238),
                        (appCtx.resources.displayMetrics.heightPixels - DisplayUtils.dp(appCtx, 120))
                                .coerceAtLeast(DisplayUtils.dp(appCtx, 150))
                )

        val flags = DisplayUtils.createOverlayFlags()

        val dm = appCtx.resources.displayMetrics
        val baseX = (dm.widthPixels - overlayW - DisplayUtils.dp(appCtx, 10)).coerceAtLeast(0)
        val baseY = DisplayUtils.dp(appCtx, 76).coerceAtLeast(0)

        // 位置/尺寸记忆：优先恢复上次的框架（含用户拖动、四角缩放、“显示”拖动回推的结果），
        // 屏幕尺寸变化（旋转、换屏）后统一回归默认位置并夹取到当前屏幕内。
        val saved = AppUiPrefs.overlayFrame(appCtx)
        val frameW =
                saved?.let {
                    it.width.coerceIn(
                            DisplayUtils.dp(appCtx, 150),
                            dm.widthPixels.coerceAtMost(DisplayUtils.dp(appCtx, 2000))
                    )
                } ?: overlayW
        val frameH =
                saved?.let {
                    it.height.coerceIn(
                            DisplayUtils.dp(appCtx, 110),
                            dm.heightPixels.coerceAtMost(DisplayUtils.dp(appCtx, 3000))
                    )
                } ?: overlayH
        val screenChanged =
                saved != null &&
                        saved.screenWidth > 0 &&
                        (saved.screenWidth != dm.widthPixels || saved.screenHeight != dm.heightPixels)
        val startX =
                if (saved == null || screenChanged) baseX
                else saved.x.coerceIn(0, (dm.widthPixels - frameW).coerceAtLeast(0))
        val startY =
                if (saved == null || screenChanged) baseY
                else saved.y.coerceIn(0, (dm.heightPixels - frameH).coerceAtLeast(0))

        val typeCandidates = DisplayUtils.getOverlayTypeCandidates()

        var lastError: Throwable? = null
        for (type in typeCandidates.distinct()) {
            val lp =
                    WindowManager.LayoutParams(
                            frameW,
                            frameH,
                            type,
                            flags,
                            android.graphics.PixelFormat.TRANSLUCENT
                    )
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = startX
            lp.y = startY

            view.attachWindowManager(w, lp)
            val ok =
                    runCatching {
                                w.addView(view, lp)
                                true
                            }
                    .getOrElse {
                        lastError = it
                        false
                    }
            if (ok) {
                wm = w
                container = view
                view.startSpinner()
                _visible.value = true
                AppUiPrefs.setOverlayFrame(appCtx, startX, startY, frameW, frameH)
                return true
            }
        }

        wm = null
        container = null
        _visible.value = false

        if (lastError != null) {
            Log.e("AutomationOverlay", "Overlay addView failed", lastError)
            Toast.makeText(
                            appCtx,
                            "悬浮窗显示失败：${lastError?.javaClass?.simpleName}",
                            Toast.LENGTH_SHORT
                    )
                    .show()
        }
        return false
    }

    /** 设置里改了悬浮窗字号或颗粒开关后调用，已显示的窗口立即生效。 */
    fun refreshAppearance() {
        runOnMain { container?.applyAppearancePrefs() }
    }

    /** 主窗是否真的挂在 WindowManager 上（用户点了“隐藏”时为 false，只剩“显示”小字）。 */
    fun isShowing(): Boolean = container != null && wm != null && !userHidden

    /** 悬浮窗功能是否处于“开启”状态：隐藏态仍然算开启，因为它还在屏幕上。 */
    fun isOverlayEnabled(): Boolean = container != null && wm != null

    /**
     * 设置里打开“悬浮窗开关”时显示窗口：
     * 有任务在跑就按执行态显示，空闲时显示待机文案；
     * 如果用户之前只是点了“隐藏”，这一步等价于点“显示”（窗口仍在，只恢复显示）。
     */
    fun showIdle(context: Context, taskActive: Boolean = false): Boolean {
        if (isShowing()) {
            _visible.value = true
            return true
        }
        if (userHidden && container != null && wm != null) {
            setUserHidden(false)
            _visible.value = true
            return true
        }
        return show(
            context = context,
            title =
                context.getString(
                    if (taskActive) R.string.ui_v2_overlay_waiting_model
                    else R.string.ui_v2_overlay_idle
                ),
            subtitle = "",
            maxSteps = 1,
            onStopTask = {},
            taskActive = taskActive,
        )
    }

    /** 用户在“结束任务”上的点击：只结束任务，窗口保留。 */
    private fun handleStopClick(fallback: () -> Unit) {
        val ctx = overlayContext
        if (!taskRunning) {
            if (ctx != null) {
                Toast.makeText(ctx, R.string.ui_v2_overlay_no_running_task, Toast.LENGTH_SHORT)
                    .show()
            }
            return
        }
        val handler = OverlayTaskBridge.onStopTask
        if (handler != null) handler() else fallback()
    }

    /** 悬浮窗输入框提交的新任务。 */
    private fun handleSubmitTask(text: String) {
        val goal = text.trim()
        if (goal.isBlank()) return
        val ctx = overlayContext ?: return
        if (taskRunning) {
            Toast.makeText(ctx, R.string.ui_v2_overlay_task_busy, Toast.LENGTH_SHORT).show()
            return
        }
        val handler = OverlayTaskBridge.onSubmitTask
        if (handler == null) {
            Toast.makeText(ctx, R.string.ui_v2_overlay_need_main, Toast.LENGTH_SHORT).show()
            return
        }
        handler(goal)
    }

    fun setOverlayVisible(visible: Boolean) {
        val v = container ?: return
        v.post {
            v.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            v.alpha = if (visible) 1f else 0f
        }
    }

    fun setInputVerifyHighlight(active: Boolean) {
        inputVerifyHighlightActive = active
        runOnMain {
            container?.setInputVerifyHighlight(active)
        }
    }
    
    /**
     * 临时隐藏悬浮窗（执行点击操作时使用，防止点击到悬浮窗）
     * 使用引用计数：多处并发调用 temporaryHide 时，只有所有调用方都
     * restoreVisibility 后才真正恢复显示，避免竞态导致悬浮窗消失。
     */
    fun temporaryHide() {
        val v = container ?: return
        val count = hideCounter.incrementAndGet()
        if (count == 1) {
            // 仅第一次隐藏时才设置 GONE
            v.post { v.visibility = View.GONE }
        }
    }
    
    /**
     * 恢复悬浮窗显示（与 temporaryHide 配对调用）
     */
    fun restoreVisibility() {
        val v = container ?: return
        val count = hideCounter.decrementAndGet()
        if (count <= 0) {
            // 所有调用方都已恢复，真正设为 VISIBLE
            hideCounter.set(0) // 防止负数
            v.post { v.visibility = View.VISIBLE }
        }
    }
    
    /**
     * 更新预估总步骤数（用于动态进度显示）
     * 只在第一次解析出有效预估值时设置，后续不再更新
     */
    fun updateEstimatedSteps(estimated: Int) {
        liveNotification.updateEstimatedSteps(estimated)
        if (estimated > 0 && !hasEstimatedSteps) {
            this.estimatedTotalSteps = estimated
            this.hasEstimatedSteps = true
            if (BuildConfig.DEBUG) Log.d("AutomationOverlay", "设置预估总步骤数: $estimated")
        }
    }
    
    /**
     * 开始流式思考显示
     */
    fun startThinking() {
        isShowingThinking = true
        thinkingText = ""
        liveNotification.updateState(
            title = "思考中",
            subtitle = "模型推理中",
            detail = "准备生成下一步动作",
        )
        runOnMain {
            val v = container ?: return@runOnMain
            v.setStatus(v.context.getString(R.string.ui_v2_overlay_waiting_model), v.currentStep())
        }
    }
    
    /**
     * 更新流式思考内容
     * @param delta 新增的思考内容片段
     */
    fun updateThinking(delta: String) {
        if (!isShowingThinking) return
        thinkingText += delta
        val displayText = extractRealtimeThinking(thinkingText)
        liveNotification.updateState(
            title = "思考中",
            subtitle = "模型推理中",
            detail = displayText,
        )
        runOnMain {
            val v = container
            if (v == null) {
                Log.w("AutomationOverlay", "Container is null in updateThinking, attempting to restore")
                isShowingThinking = false
                return@runOnMain
            }
            try {
                v.setStatus(displayText, v.currentStep())
            } catch (e: Exception) {
                Log.w("AutomationOverlay", "Failed to update thinking display: ${e.message}")
            }
        }
    }
    
    /**
     * 结束流式思考显示
     */
    fun stopThinking() {
        isShowingThinking = false
        thinkingText = ""
    }
    
    /**
     * 实时提取关键思考信息（优先显示最新的分析步骤）
     */
    private fun extractRealtimeThinking(thinking: String): String {
        val text = thinking.trim()
        if (text.isBlank()) return "等待界面稳定..."
        
        // 关键词列表（按优先级排序）
        val keywordPatterns = listOf(
            "需要", "应该", "点击", "输入", "滚动", "等待", "打开", "找到", "看到",
            "验证", "检查", "分析", "确认", "选择", "搜索", "返回", "关闭", "长按",
            "向", "拖动", "位置", "内容", "信息", "用户", "页面", "按钮", "输入框"
        )
        
        // 策略1：找到最新的包含关键词的部分（从后向前扫描）
        for (keyword in keywordPatterns) {
            val lastIndex = text.lastIndexOf(keyword)
            if (lastIndex >= 0) {
                // 提取关键词及其后的内容，最多30字
                val startIdx = maxOf(0, lastIndex - 5)  // 向前多取5个字，获得上下文
                val endIdx = minOf(text.length, lastIndex + 20)
                val segment = text.substring(startIdx, endIdx).trim()
                if (segment.length > 2) {
                    return segment
                }
            }
        }
        
        // 策略2：如果没有关键词，显示最后的30字
        val lastPart = text.takeLast(30).trim()
        return if (lastPart.isNotEmpty()) lastPart else "分析中..."
    }

    /**
     * 从思考文本中提取有意义的关键信息（保留用于其他用途）
     */
    private fun extractMeaningfulThinking(thinking: String): String {
        val text = thinking.trim()
        if (text.isBlank()) return ""
        
        // 匹配常见的行动关键词
        val actionPatterns = listOf(
            "需要点击", "点击", "输入", "滚动", "查找", "等待", 
            "打开", "关闭", "返回", "确认", "取消", "保存",
            "在", "找到", "看到", "应该", "可以", "正在"
        )
        
        // 尝试提取包含行动关键词的句子
        val sentences = text.split("[。！？\n]".toRegex())
        for (sentence in sentences) {
            val trimmed = sentence.trim()
            if (trimmed.length in 10..50) { // 适中的句子长度
                for (pattern in actionPatterns) {
                    if (trimmed.contains(pattern)) {
                        return trimmed
                    }
                }
            }
        }
        
        // 如果没有找到合适的句子，返回前面的内容
        return text.take(40)
    }

    /**
     * 计算智能进度百分比
     * - 如果有预估步骤数：使用 step/estimated 计算真实百分比
     * - 如果没有预估：使用平滑的假进度曲线（0-90%渐进）
     */
    private fun calculateProgressPercent(step: Int): Int {
        return if (hasEstimatedSteps && estimatedTotalSteps > 0) {
            // 有预估步骤数时，使用真实计算
            // 允许超过100%（任务可能比预估更长），但显示时限制在100%
            val realPercent = (step.toFloat() / estimatedTotalSteps * 100).toInt()
            realPercent.coerceIn(0, 100)
        } else {
            // 无预估时，使用平滑的假进度曲线
            // 使用对数曲线：快速上升到60%，然后缓慢增长到90%
            val normalized = step.toFloat() / 15f // 假设15步达到较高进度
            val fakeProgress = when {
                normalized < 0.3f -> normalized * 2f // 0-30% 快速增长
                normalized < 0.6f -> 0.6f + (normalized - 0.3f) * 0.8f // 60-84%
                else -> 0.84f + kotlin.math.min((normalized - 0.6f) * 0.15f, 0.06f) // 84-90%
            }
            (fakeProgress * 100).toInt().coerceIn(0, 90)
        }
    }

    private fun calculateProgressFraction(step: Int, phaseInStep: Float): Float {
        val s = step.coerceAtLeast(0)
        val curr = calculateProgressPercent(s)
        val next = calculateProgressPercent(s + 1)
        val t = phaseInStep.coerceIn(0f, 1f)
        val percent = curr + ((next - curr) * t)
        return (percent / 100f).coerceIn(0f, 1f)
    }

    fun updateStep(step: Int, maxSteps: Int? = null, subtitle: String? = null) {
        updateProgress(step = step, phaseInStep = 0f, maxSteps = maxSteps, subtitle = subtitle)
    }

    fun updateProgress(step: Int, phaseInStep: Float, maxSteps: Int? = null, subtitle: String? = null) {
        liveNotification.updateProgress(step, phaseInStep, maxSteps, subtitle)
        runOnMain {
            val v = container ?: return@runOnMain
            if (maxSteps != null && !hasEstimatedSteps) {
                this.maxSteps = maxSteps.coerceAtLeast(1)
            }
            // 模型只能估算，总量未知；保留流动的不确定进度条样式。
            val frac = 0f
            v.setProgress(frac)
            val sub = subtitle?.trim().orEmpty().ifBlank { v.context.getString(R.string.ui_v2_overlay_running) }
            v.setStatus(sub, step, if (hasEstimatedSteps) estimatedTotalSteps else 0)
        }
    }

    /** 暂停/恢复时在悬浮窗角落给出极小的状态提示。 */
    fun setPaused(paused: Boolean) {
        pausedState = paused
        runOnMain {
            container?.setPaused(paused)
        }
    }

    private val feedTagPatterns: List<Pair<String, String>> = listOf(
        "修复思考：" to "思考",
        "修复思考:" to "思考",
        "思考：" to "思考",
        "思考:" to "思考",
        "修复输出：" to "输出",
        "修复输出:" to "输出",
        "输出：" to "输出",
        "输出:" to "输出",
        "当前动作：" to "动作",
        "当前动作:" to "动作",
        "执行操作：" to "执行",
        "执行操作:" to "执行",
        "执行结果：" to "结果",
        "执行结果:" to "结果",
        "已暂停" to "暂停",
        "继续执行" to "继续",
        "已停止" to "停止",
        "结束：" to "完成",
        "结束:" to "完成",
    )

    private fun resolveFeedLine(rawLine: String): Triple<Int, String, String>? {
        val raw = rawLine.trim()
        if (raw.isBlank()) return null
        val step = parseStep(raw) ?: 0
        val payload = simplifyLine(raw).trim()
        if (payload.isBlank()) return null

        feedTagPatterns.firstOrNull { (prefix, _) -> payload.startsWith(prefix) }?.let { (prefix, tag) ->
            val body = payload.removePrefix(prefix).trim()
            if (body.isNotBlank()) return Triple(step, tag, body)
        }

        val isFailure =
            payload.startsWith("异常") ||
                payload.startsWith("无法") ||
                payload.contains("失败") ||
                payload.contains("不可用")
        if (isFailure) {
            return Triple(step, "异常", payload)
        }
        return null
    }

    fun updateFromLogLine(line: String) {
        val trimmed = simplifyLine(line).trim()
        if (trimmed.isBlank()) return
        liveNotification.updateFromLogLine(line)
        val feedLine = resolveFeedLine(line) ?: return
        runOnMain {
            val v = container ?: return@runOnMain
            v.appendFeed(feedLine.first, feedLine.second, feedLine.third)
        }
    }

    fun updateAiContent(step: Int, thinking: String?, answer: String?, action: String?) {
        runOnMain {
            val v = container ?: return@runOnMain
            thinking?.trim()?.takeIf { it.isNotBlank() }?.let {
                v.appendFeed(step, "思考", it)
            }
            answer?.trim()?.takeIf { it.isNotBlank() }?.let {
                v.appendFeed(step, "输出", it)
            }
            action?.trim()?.takeIf { it.isNotBlank() }?.let {
                v.appendFeed(step, "动作", it)
            }
        }
    }

    fun complete(message: String, success: Boolean = true) {
        liveNotification.complete(message, success)
        taskRunning = false
        runOnMain {
            val v = container ?: return@runOnMain
            v.setProgress(1f)
            val status = v.context.getString(if (success) R.string.ui_v2_success else R.string.ui_v2_failed)
            v.setStatus(status, v.currentStep())
            v.appendFeed(0, if (success) "完成" else "失败", message.ifBlank { status })
            v.playCompleteEffect {
                // 任务结束只收敛动画，不再关闭窗口：保留日志，方便回看或直接下发新任务。
                runOnMain { if (container === v) v.stopSpinner() }
            }
        }
    }

    /** 用户主动“结束任务”后的收尾：停在当前状态，窗口保留。 */
    fun markFinished(message: String = "任务已结束") {
        liveNotification.complete(message, true)
        taskRunning = false
        pausedState = false
        runOnMain {
            val v = container ?: return@runOnMain
            v.stopSpinner()
            v.setPaused(false)
            v.setStatus(v.context.getString(R.string.ui_v2_overlay_finished), v.currentStep())
            v.appendFeed(0, "结束", message)
        }
    }

    fun hide() {
        liveNotification.hide()
        val w = wm
        val v = container
        val tag = hiddenTagView
        container = null
        wm = null
        hiddenTagView = null
        hiddenTagParams = null
        userHidden = false
        hideAnchorOffsetX = 0
        hideAnchorOffsetY = 0
        launchMainOnClick = false
        inputVerifyHighlightActive = false
        taskRunning = false
        _visible.value = false
        if (w != null) {
            // 可能从文字点击的 UP 事件里调用，延后一帧再移除窗口。
            mainHandler.post {
                if (v != null) runCatching { w.removeView(v) }
                if (tag != null) runCatching { w.removeView(tag) }
            }
        }
    }

    /**
     * 用户点“隐藏”后移除内容窗，只留一个可拖动的“显示”文字，且“显示”出现在刚刚“隐藏”
     * 两个字所在的位置；再点“显示”时反过来用拖动后的位置回推整个窗口，让“隐藏”继续停在
     * 用户最后放下的地方。
     *
     * 注意：这里**不**改动 [_visible]——“隐藏”只是把内容窗收起来，悬浮窗功能仍然是开着的。
     */
    fun setUserHidden(hidden: Boolean) {
        val w = wm ?: return
        val v = container ?: return
        if (hidden == userHidden) return
        userHidden = hidden
        // 延后一帧：按钮/文字的 UP 事件仍在分发中，立即 removeView 会被系统拒绝。
        mainHandler.post {
            if (hidden) {
                val params = v.windowParams()
                // 记录“隐藏”两字中心相对窗口左上角的偏移，供“显示”回到原位、以及反算窗口坐标。
                val anchor = v.hideAnchorCenter()
                hideAnchorOffsetX = anchor?.first ?: ((params?.width ?: v.width) - dpPx(v.context, 28))
                hideAnchorOffsetY = anchor?.second ?: ((params?.height ?: v.height) - dpPx(v.context, 14))
                val centerX = (params?.x ?: 0) + hideAnchorOffsetX
                val centerY = (params?.y ?: 0) + hideAnchorOffsetY
                runCatching { w.removeView(v) }
                showHiddenTag(w, centerX, centerY)
            } else {
                val tagParams = hiddenTagParams
                removeHiddenTag(w)
                val params = v.windowParams() ?: return@post
                if (tagParams != null) {
                    val dm = v.resources.displayMetrics
                    // “显示”被拖动过就以它当前位置为准：整窗平移，使“隐藏”落回原处。
                    val targetX =
                            tagParams.x + tagParams.width / 2 - hideAnchorOffsetX
                    val targetY =
                            tagParams.y + tagParams.height / 2 - hideAnchorOffsetY
                    params.x =
                            targetX.coerceIn(0, (dm.widthPixels - params.width).coerceAtLeast(0))
                    params.y =
                            targetY.coerceIn(0, (dm.heightPixels - params.height).coerceAtLeast(0))
                }
                runCatching {
                    w.addView(v, params)
                    v.startSpinner()
                    AppUiPrefs.setOverlayFrame(
                            v.context,
                            params.x,
                            params.y,
                            params.width,
                            params.height,
                    )
                }
            }
        }
    }

    // 这是悬浮的小标签，拖动与点击共用一套触摸处理，语义上不是普通按钮；
    // performClick 已在下方内部覆写，这里只是让 lint 不再要求额外的 OnClickListener。
    @SuppressLint("ClickableViewAccessibility")
    private fun showHiddenTag(w: WindowManager, centerX: Int, centerY: Int) {
        if (hiddenTagView != null) return
        val ctx = overlayContext ?: return
        // 用匿名子类覆写 performClick，满足无障碍对自定义触摸视图的要求。
        val tv =
                object : androidx.appcompat.widget.AppCompatTextView(ctx) {
                            override fun performClick(): Boolean {
                                super.performClick()
                                return true
                            }
                        }
                        .apply {
                    text = ctx.getString(R.string.ui_v2_overlay_show)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, AppUiPrefs.hideTextSizeSp(ctx))
                    setTextColor(
                            ContextCompat.getColor(
                                    ctx,
                                    com.ai.phoneagent.core.designsystem.R.color.m3t_primary
                            )
                    )
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setPadding(dpPx(ctx, 10), dpPx(ctx, 6), dpPx(ctx, 10), dpPx(ctx, 6))
                    setShadowLayer(4f, 0f, 1f, 0xCC000000.toInt())
                }

        val dm = ctx.resources.displayMetrics
        val type = DisplayUtils.getOverlayTypeCandidates().firstOrNull()
                ?: WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params =
                WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        type,
                        DisplayUtils.createOverlayFlags(),
                        android.graphics.PixelFormat.TRANSLUCENT
                )
        params.gravity = Gravity.TOP or Gravity.START
        // 文字宽度要等测量后才知道，先用“隐藏”按钮的尺寸估算，保证“显示”落点与“隐藏”重合。
        val approxW = dpPx(ctx, 28) + dpPx(ctx, 20)
        val approxH = dpPx(ctx, 16) + dpPx(ctx, 12)
        params.x = (centerX - approxW / 2).coerceIn(0, (dm.widthPixels - approxW).coerceAtLeast(0))
        params.y = (centerY - approxH / 2).coerceIn(0, (dm.heightPixels - approxH).coerceAtLeast(0))

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        tv.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    if (!moved &&
                                    (kotlin.math.abs(dx) >= dpPx(ctx, 6) ||
                                            kotlin.math.abs(dy) >= dpPx(ctx, 6))
                    ) {
                        moved = true
                    }
                    if (moved) {
                        val maxX = (dm.widthPixels - view.width).coerceAtLeast(0)
                        val maxY = (dm.heightPixels - view.height).coerceAtLeast(0)
                        params.x = (startX + dx.toInt()).coerceIn(0, maxX)
                        params.y = (startY + dy.toInt()).coerceIn(0, maxY)
                        runCatching { w.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        // 无障碍要求：识别到点击时回调 performClick。
                        view.performClick()
                        Haptics.tap(ctx)
                        setUserHidden(false)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }

        val added = runCatching { w.addView(tv, params); true }.getOrElse { false }
        if (added) {
            hiddenTagView = tv
            hiddenTagParams = params
        }
    }

    private fun removeHiddenTag(w: WindowManager) {
        val tv = hiddenTagView ?: return
        hiddenTagView = null
        hiddenTagParams = null
        runCatching { w.removeView(tv) }
    }

    private fun dpPx(context: Context, value: Int): Int =
            TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            value.toFloat(),
                            context.resources.displayMetrics
                    )
                    .toInt()

    private fun parseStep(line: String): Int? {
        val idx = line.indexOf("[Step")
        if (idx < 0) return null
        val m = Regex("\\[Step\\s+(\\d+)\\]").find(line) ?: return null
        return m.groupValues.getOrNull(1)?.toIntOrNull()
    }

    private fun simplifyLine(line: String): String {
        val raw = line.trim()
        val m = Regex("\\[Step\\s+\\d+\\]\\s*").find(raw)
        return if (m != null && m.range.first == 0) {
            raw.substring(m.range.last + 1).trimStart()
        } else {
            raw
        }
    }

    private fun dp(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        dp.toFloat(),
                        context.resources.displayMetrics
                )
                .toInt()
    }

    private class OverlayContainer(
        context: Context,
        private val onStopTask: () -> Unit,
        private val onHideTask: () -> Unit,
        private val onSubmitTask: (String) -> Unit,
        private val isTaskRunning: () -> Boolean,
    ) : FrameLayout(context) {

        private data class FeedLine(val step: Int, val tag: String, val text: String)

        private val ring = EdgeFlowView(context)
        private val header = TextView(context)
        private val newTaskAction = TextView(context)
        private val feedView = TextView(context)
        private val feedScroll = android.widget.ScrollView(context)
        private val bottomBar = android.widget.LinearLayout(context)
        private val stopAction = TextView(context)
        private val hideAction = TextView(context)
        private val inputRow = android.widget.LinearLayout(context)
        private val inputField = android.widget.EditText(context)
        private val inputSend = TextView(context)
        private val feedLines = ArrayDeque<FeedLine>()
        private val surfaceColor = ContextCompat.getColor(context, com.ai.phoneagent.core.designsystem.R.color.m3t_floating_surface)
        private val outlineColor = ContextCompat.getColor(context, com.ai.phoneagent.core.designsystem.R.color.m3t_outline_variant)
        private val onSurfaceColor = ContextCompat.getColor(context, com.ai.phoneagent.core.designsystem.R.color.m3t_on_surface)
        private val variantColor = ContextCompat.getColor(context, com.ai.phoneagent.core.designsystem.R.color.m3t_on_surface_variant)
        private val primaryColor = ContextCompat.getColor(context, com.ai.phoneagent.core.designsystem.R.color.m3t_primary)
        private val errorColor = ContextCompat.getColor(context, com.ai.phoneagent.core.designsystem.R.color.m3t_error)
        private val successColor = ContextCompat.getColor(context, com.ai.phoneagent.core.designsystem.R.color.m3t_success)

        private var lp: WindowManager.LayoutParams? = null
        private var wm: WindowManager? = null

        private var downRawX = 0f
        private var downRawY = 0f
        private var downX = 0
        private var downY = 0
        private var downW = 0
        private var downH = 0
        private var dragging = false
        private var resizing = false
        private var resizeCorner = 0
        private var inputMode = false

        private var statusText: String = context.getString(R.string.ui_v2_overlay_running)
        private var stepValue: Int = 0
        private var maxStepValue: Int = 0
        private var paused: Boolean = false

        init {
            setPadding(0, 0, 0, 0)
            background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(12).toFloat()
                        setColor(ColorUtils.setAlphaComponent(surfaceColor, 248))
                        setStroke(dp(1), ColorUtils.setAlphaComponent(outlineColor, 90))
                    }
            elevation = dp(6).toFloat()
            clipToPadding = false
            setWillNotDraw(false)

            addView(
                    ring,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )

            val content = android.widget.LinearLayout(context)
            content.orientation = android.widget.LinearLayout.VERTICAL
            content.setPadding(dp(10), dp(5), dp(10), dp(1))
            addView(
                    content,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )

            val headerRow = android.widget.LinearLayout(context)
            headerRow.orientation = android.widget.LinearLayout.HORIZONTAL
            headerRow.gravity = Gravity.CENTER_VERTICAL

            header.setTextColor(primaryColor)
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, AppUiPrefs.textSizeSp(context))
            header.typeface = android.graphics.Typeface.DEFAULT_BOLD
            header.maxLines = 1
            header.ellipsize = android.text.TextUtils.TruncateAt.END
            header.includeFontPadding = false

            // 右上角“＋”：展开内嵌输入框，输入完直接下发新任务。
            newTaskAction.text = context.getString(R.string.ui_v2_overlay_new_task_symbol)
            newTaskAction.setTextColor(primaryColor)
            newTaskAction.setTextSize(TypedValue.COMPLEX_UNIT_SP, AppUiPrefs.plusTextSizeSp(context))
            newTaskAction.typeface = android.graphics.Typeface.DEFAULT_BOLD
            newTaskAction.gravity = Gravity.CENTER
            newTaskAction.includeFontPadding = false
            newTaskAction.setPadding(dp(8), 0, dp(6), 0)
            // 给足触摸面积，避免字号调小后点不中。
            newTaskAction.minWidth = dp(32)
            newTaskAction.minHeight = dp(28)
            newTaskAction.isClickable = true
            newTaskAction.setOnClickListener { toggleInputMode() }

            headerRow.addView(
                    header,
                    android.widget.LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            )
            headerRow.addView(
                    newTaskAction,
                    android.widget.LinearLayout.LayoutParams(
                            LayoutParams.WRAP_CONTENT,
                            LayoutParams.WRAP_CONTENT
                    )
            )
            content.addView(
                    headerRow,
                    android.widget.LinearLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.WRAP_CONTENT
                    )
            )

            val divider = View(context)
            divider.setBackgroundColor(ColorUtils.setAlphaComponent(outlineColor, 46))
            val dividerLp =
                    android.widget.LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(1))
            dividerLp.topMargin = dp(4)
            dividerLp.bottomMargin = dp(3)
            content.addView(divider, dividerLp)

            feedView.setTextColor(onSurfaceColor)
            feedView.setTextSize(TypedValue.COMPLEX_UNIT_SP, AppUiPrefs.textSizeSp(context))
            feedView.includeFontPadding = false
            feedView.setLineSpacing(dp(1).toFloat(), 1.0f)
            feedView.typeface = android.graphics.Typeface.DEFAULT

            feedScroll.isVerticalScrollBarEnabled = false
            feedScroll.isFillViewport = false
            feedScroll.addView(
                    feedView,
                    android.widget.FrameLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.WRAP_CONTENT
                    )
            )
            // 高度用 weight 占满剩余空间，窗口缩放时内容区自动跟随。
            content.addView(
                    feedScroll,
                    android.widget.LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            )

            // 新任务输入行：默认收起，点右上角“＋”后展开，样式与主页输入框同源（圆角胶囊 + 发送）。
            inputRow.orientation = android.widget.LinearLayout.HORIZONTAL
            inputRow.gravity = Gravity.CENTER_VERTICAL
            inputRow.visibility = View.GONE
            inputRow.setPadding(0, dp(2), 0, dp(2))

            inputField.hint = context.getString(R.string.ui_v2_overlay_new_task_hint)
            inputField.setTextSize(TypedValue.COMPLEX_UNIT_SP, AppUiPrefs.textSizeSp(context))
            inputField.setTextColor(onSurfaceColor)
            inputField.setHintTextColor(ColorUtils.setAlphaComponent(variantColor, 200))
            inputField.includeFontPadding = false
            inputField.maxLines = 2
            inputField.isSingleLine = false
            inputField.inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            inputField.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            inputField.background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(12).toFloat()
                        setColor(ColorUtils.setAlphaComponent(surfaceColor, 255))
                        setStroke(dp(1), ColorUtils.setAlphaComponent(primaryColor, 90))
                    }
            inputField.setPadding(dp(8), dp(4), dp(8), dp(4))
            inputField.minHeight = dp(26)
            inputField.setOnEditorActionListener { _, actionId, event ->
                val isSend =
                        actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                                (event != null &&
                                        event.keyCode == android.view.KeyEvent.KEYCODE_ENTER &&
                                        event.action == android.view.KeyEvent.ACTION_DOWN)
                if (isSend) {
                    submitInput()
                    true
                } else {
                    false
                }
            }

            inputSend.text = context.getString(R.string.ui_v2_overlay_new_task_send)
            inputSend.setTextColor(primaryColor)
            inputSend.setTextSize(TypedValue.COMPLEX_UNIT_SP, AppUiPrefs.textSizeSp(context))
            inputSend.typeface = android.graphics.Typeface.DEFAULT_BOLD
            inputSend.gravity = Gravity.CENTER
            inputSend.includeFontPadding = false
            inputSend.setPadding(dp(8), dp(4), dp(2), dp(4))
            inputSend.isClickable = true
            inputSend.setOnClickListener { submitInput() }

            inputRow.addView(
                    inputField,
                    android.widget.LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            )
            inputRow.addView(
                    inputSend,
                    android.widget.LinearLayout.LayoutParams(
                            LayoutParams.WRAP_CONTENT,
                            LayoutParams.WRAP_CONTENT
                    )
            )
            content.addView(
                    inputRow,
                    android.widget.LinearLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.WRAP_CONTENT
                    )
            )

            bottomBar.orientation = android.widget.LinearLayout.HORIZONTAL
            bottomBar.gravity = Gravity.CENTER_VERTICAL

            stopAction.text = context.getString(R.string.ui_v2_overlay_end_task)
            stopAction.setTextColor(ColorUtils.setAlphaComponent(errorColor, 230))
            stopAction.setTextSize(TypedValue.COMPLEX_UNIT_SP, AppUiPrefs.stopTextSizeSp(context))
            stopAction.typeface = android.graphics.Typeface.DEFAULT_BOLD
            stopAction.includeFontPadding = false
            stopAction.setPadding(dp(12), dp(4), dp(8), dp(4))
            stopAction.isClickable = true
            stopAction.setOnClickListener {
                onStopTask()
            }

            hideAction.text = context.getString(R.string.ui_v2_overlay_hide)
            hideAction.setTextColor(ColorUtils.setAlphaComponent(variantColor, 230))
            hideAction.setTextSize(TypedValue.COMPLEX_UNIT_SP, AppUiPrefs.hideTextSizeSp(context))
            hideAction.includeFontPadding = false
            hideAction.gravity = Gravity.END
            hideAction.setPadding(dp(8), dp(4), dp(12), dp(4))
            hideAction.isClickable = true
            hideAction.setOnClickListener {
                onHideTask()
            }

            bottomBar.addView(
                    stopAction,
                    android.widget.LinearLayout.LayoutParams(
                            LayoutParams.WRAP_CONTENT,
                            LayoutParams.WRAP_CONTENT
                    )
            )
            bottomBar.addView(
                    View(context),
                    android.widget.LinearLayout.LayoutParams(0, 1, 1f)
            )
            bottomBar.addView(
                    hideAction,
                    android.widget.LinearLayout.LayoutParams(
                            LayoutParams.WRAP_CONTENT,
                            LayoutParams.WRAP_CONTENT
                    )
            )
            content.addView(
                    bottomBar,
                    android.widget.LinearLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.WRAP_CONTENT
                    )
            )

            isClickable = true
            isFocusable = false
            renderHeader()
        }

        /** 把当前偏好里的字号与颗粒开关应用到已有控件上（幂等）。 */
        fun applyAppearancePrefs() {
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, AppUiPrefs.textSizeSp(context))
            newTaskAction.setTextSize(TypedValue.COMPLEX_UNIT_SP, AppUiPrefs.plusTextSizeSp(context))
            feedView.setTextSize(TypedValue.COMPLEX_UNIT_SP, AppUiPrefs.textSizeSp(context))
            inputField.setTextSize(TypedValue.COMPLEX_UNIT_SP, AppUiPrefs.textSizeSp(context))
            inputSend.setTextSize(TypedValue.COMPLEX_UNIT_SP, AppUiPrefs.textSizeSp(context))
            stopAction.setTextSize(TypedValue.COMPLEX_UNIT_SP, AppUiPrefs.stopTextSizeSp(context))
            hideAction.setTextSize(TypedValue.COMPLEX_UNIT_SP, AppUiPrefs.hideTextSizeSp(context))
            val particle = AppUiPrefs.particleEnabled(context)
            ring.setParticlesEnabled(particle)
            // 只有任务在跑时才重新起动画，避免结束后无故转圈。
            if (particle && isTaskRunning()) ring.start() else if (!particle) ring.stop()
            renderFeed()
        }

        fun attachWindowManager(wm: WindowManager, lp: WindowManager.LayoutParams) {
            this.wm = wm
            this.lp = lp
        }

        fun windowParams(): WindowManager.LayoutParams? = lp

        /**
         * “隐藏”两字中心在本窗口内的偏移量。
         *
         * 收起窗口时用它把“显示”摆到“隐藏”刚才所在的位置；恢复窗口时再用同一个偏移反算
         * 窗口坐标，从而做到「“显示”被拖到哪，“隐藏”就回到哪」。
         */
        fun hideAnchorCenter(): Pair<Int, Int>? {
            if (hideAction.width <= 0 || hideAction.height <= 0) return null
            val anchor = IntArray(2)
            val origin = IntArray(2)
            hideAction.getLocationOnScreen(anchor)
            getLocationOnScreen(origin)
            val localX = anchor[0] - origin[0] + hideAction.width / 2
            val localY = anchor[1] - origin[1] + hideAction.height / 2
            if (localX < 0 || localY < 0) return null
            return localX to localY
        }

        /** 把当前窗口位置/尺寸写进偏好，下次打开时原样恢复。 */
        fun persistFrame() {
            val p = lp ?: return
            val viewW = if (p.width > 0) p.width else width
            val viewH = if (p.height > 0) p.height else height
            if (viewW <= 0 || viewH <= 0) return
            AppUiPrefs.setOverlayFrame(context, p.x, p.y, viewW, viewH)
        }

        fun currentStep(): Int = stepValue

        fun setTexts(t: String, s: String, detailText: String? = null) {
            statusText = t.ifBlank { context.getString(R.string.ui_v2_overlay_running) }
            if (s.isNotBlank() && s != t) {
                appendFeed(0, "任务", s)
            }
            if (!detailText.isNullOrBlank() && detailText != s) {
                appendFeed(0, "状态", detailText)
            }
            renderHeader()
        }

        fun setStatus(status: String, step: Int, maxSteps: Int = 0) {
            statusText = status.ifBlank { context.getString(R.string.ui_v2_overlay_running) }
            stepValue = step.coerceAtLeast(0)
            if (maxSteps > 0) {
                maxStepValue = maxSteps
            }
            renderHeader()
        }

        fun setPaused(value: Boolean) {
            paused = value
            renderHeader()
        }

        fun appendFeed(step: Int, tag: String, text: String) {
            // 保留换行与完整内容：模型返回什么就展示什么，只压缩连续空格。
            val body =
                    text.trim()
                            .replace("\r\n", "\n")
                            .replace("\r", "\n")
                            .replace(Regex("[ \t]{2,}"), " ")
            if (body.isBlank()) return
            val clipped = if (body.length > 6000) body.take(5999) + "…" else body
            val last = feedLines.lastOrNull()
            if (last != null && last.tag == tag && last.step == step) {
                if (last.text == clipped) return
                // 先到的截断版本被后到的全文覆盖。
                if (clipped.startsWith(last.text) || last.text.startsWith(clipped)) {
                    if (clipped.length <= last.text.length) return
                    feedLines.removeLast()
                }
            }
            feedLines.addLast(FeedLine(step, tag, clipped))
            while (feedLines.size > 300) {
                feedLines.removeFirst()
            }
            renderFeed()
        }

        private fun renderHeader() {
            val dot = if (paused) "❚❚" else "●"
            val status =
                if (paused) {
                    context.getString(R.string.ui_v2_overlay_paused_hint)
                } else {
                    statusText
                }
            header.text = context.getString(R.string.ui_v2_overlay_header_format, dot, status)
            header.setTextColor(if (paused) errorColor else primaryColor)
            // 进度数字（S1/S1-9）已彻底移除：日志行自带步骤编号，足够判断所处阶段。
        }

        /** 右上角“＋”：展开/收起内嵌输入框。 */
        private fun toggleInputMode() {
            if (!inputMode && isTaskRunning()) {
                Toast.makeText(context, R.string.ui_v2_overlay_task_busy, Toast.LENGTH_SHORT).show()
                return
            }
            setInputMode(!inputMode)
        }

        private fun setInputMode(active: Boolean) {
            if (inputMode == active) return
            inputMode = active
            inputRow.visibility = if (active) View.VISIBLE else View.GONE
            val params = lp
            val manager = wm
            if (active) {
                // 输入需要窗口可获焦点：临时去掉 FLAG_NOT_FOCUSABLE（否则键盘不会出现）。
                if (params != null && manager != null) {
                    params.flags =
                            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    params.softInputMode =
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                                    WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
                    // 键盘会吃掉下半屏，先把窗口抬到上半屏，保证输入行不被遮挡。
                    val dm = resources.displayMetrics
                    val maxY = (dm.heightPixels * 0.5f).toInt() - params.height
                    if (params.y > maxY) {
                        params.y = (dm.heightPixels * 0.04f).toInt().coerceAtLeast(0)
                    }
                    runCatching { manager.updateViewLayout(this, params) }
                    persistFrame()
                }
                // 等窗口参数生效后再要焦点，否则 IME 拿不到可聚焦窗口。
                postDelayed(
                        {
                            inputField.requestFocus()
                            val imm =
                                    context.getSystemService(Context.INPUT_METHOD_SERVICE)
                                            as? android.view.inputmethod.InputMethodManager
                            imm?.showSoftInput(
                                    inputField,
                                    android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT
                            )
                        },
                        180L
                )
            } else {
                val imm =
                        context.getSystemService(Context.INPUT_METHOD_SERVICE)
                                as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(inputField.windowToken, 0)
                inputField.clearFocus()
                inputField.setText("")
                if (params != null && manager != null) {
                    params.flags =
                            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    runCatching { manager.updateViewLayout(this, params) }
                }
            }
        }

        private fun submitInput() {
            val text = inputField.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) return
            setInputMode(false)
            onSubmitTask(text)
        }

        private fun renderFeed() {
            if (feedLines.isEmpty()) {
                feedView.text = ""
                return
            }
            val sb = android.text.SpannableStringBuilder()
            feedLines.forEachIndexed { index, line ->
                if (index > 0) sb.append('\n')
                if (line.step > 0) {
                    val start = sb.length
                    sb.append(line.step.toString()).append('·')
                    sb.setSpan(
                            android.text.style.RelativeSizeSpan(0.72f),
                            start,
                            sb.length,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.setSpan(
                            android.text.style.ForegroundColorSpan(
                                    ColorUtils.setAlphaComponent(variantColor, 170)
                            ),
                            start,
                            sb.length,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.append(' ')
                }
                val tagStart = sb.length
                sb.append(line.tag)
                sb.setSpan(
                        android.text.style.RelativeSizeSpan(0.85f),
                        tagStart,
                        sb.length,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sb.setSpan(
                        android.text.style.ForegroundColorSpan(tagColor(line.tag)),
                        tagStart,
                        sb.length,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sb.append(' ')
                val bodyStart = sb.length
                sb.append(line.text)
                // “当前动作”整行加粗，执行动作在信息流里一眼可见。
                if (line.tag == "动作") {
                    sb.setSpan(
                            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                            tagStart,
                            sb.length,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                sb.setSpan(
                        android.text.style.RelativeSizeSpan(1.04f),
                        bodyStart,
                        sb.length,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            feedView.text = sb
            feedScroll.post { feedScroll.fullScroll(View.FOCUS_DOWN) }
        }

        private fun tagColor(tag: String): Int =
                when (tag) {
                    "思考", "输出", "动作" -> primaryColor
                    "异常", "失败" -> errorColor
                    "完成" -> successColor
                    "任务", "状态" -> variantColor
                    else -> ColorUtils.setAlphaComponent(onSurfaceColor, 210)
                }

        fun setProgress(p: Float) {
            ring.setProgress(p)
        }

        fun setInputVerifyHighlight(active: Boolean) {
            ring.setInputVerifyHighlight(active)
        }

        fun startSpinner() {
            // 每次拉起窗口都重新读一遍设置，改完开关不必等下次启动。
            ring.setParticlesEnabled(AppUiPrefs.particleEnabled(context))
            ring.start()
        }

        /** 任务结束后停转边框流光，窗口本身保留。 */
        fun stopSpinner() {
            ring.stop()
        }

        fun playCompleteEffect(onEnd: () -> Unit) {
            ring.playCompleteEffect(onEnd)
        }

        private fun dp(v: Int): Int {
            return TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            v.toFloat(),
                            resources.displayMetrics
                    )
                    .toInt()
        }

        /** 四角缩放手势热区：半径由设置里的“触控热区”决定（像素）。 */
        private fun cornerAt(x: Float, y: Float): Int {
            // “＋”的点击优先级高于拖动热区：热区半径再大也不能吃掉按钮的触摸。
            if (isInsideBar(newTaskAction, x, y, dp(6))) return 0
            if (isInsideBar(bottomBar, x, y)) return 0
            if (inputMode && isInsideBar(inputRow, x, y)) return 0
            val s = AppUiPrefs.cornerRadiusPx(context).toFloat()
            val inLeft = x <= s
            val inRight = x >= width - s
            val inTop = y <= s
            val inBottom = y >= height - s
            return when {
                inLeft && inTop -> 1
                inRight && inTop -> 2
                inLeft && inBottom -> 3
                inRight && inBottom -> 4
                else -> 0
            }
        }

        private fun isInsideBar(bar: View, x: Float, y: Float, slop: Int = dp(2)): Boolean {
            if (bar.width <= 0 || bar.height <= 0 || bar.visibility != View.VISIBLE) return false
            val top = topInRoot(bar)
            val bottom = top + bar.height
            val left = leftInRoot(bar)
            val right = left + bar.width
            return y >= top - slop && y <= bottom + slop && x >= left - slop && x <= right + slop
        }

        private fun topInRoot(view: View): Int {
            var top = 0
            var cur: View? = view
            while (cur != null && cur !== this) {
                top += cur.top
                cur = cur.parent as? View
            }
            return top
        }

        private fun leftInRoot(view: View): Int {
            var left = 0
            var cur: View? = view
            while (cur != null && cur !== this) {
                left += cur.left
                cur = cur.parent as? View
            }
            return left
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            val layoutParams = lp
            if (layoutParams == null || wm == null) return super.dispatchTouchEvent(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val corner = cornerAt(ev.x, ev.y)
                    if (corner != 0) {
                        resizeCorner = corner
                        resizing = true
                        dragging = false
                        downRawX = ev.rawX
                        downRawY = ev.rawY
                        downX = layoutParams.x
                        downY = layoutParams.y
                        downW = if (layoutParams.width > 0) layoutParams.width else width
                        downH = if (layoutParams.height > 0) layoutParams.height else height
                        // 抓住边角的一刻就把边框点亮成实色蓝，松手再恢复。
                        ring.setCornerHighlight(true)
                        return true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (resizing) {
                        applyResize(ev.rawX, ev.rawY)
                        return true
                    }
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (resizing) {
                        resizing = false
                        resizeCorner = 0
                        ring.setCornerHighlight(false)
                        // 缩放结束即记下尺寸与位置，下次打开沿用。
                        persistFrame()
                        return true
                    }
                }
            }
            return super.dispatchTouchEvent(ev)
        }

        private fun applyResize(rawX: Float, rawY: Float) {
            val w = wm ?: return
            val layoutParams = lp ?: return
            val dx = (rawX - downRawX).toInt()
            val dy = (rawY - downRawY).toInt()
            val dm = resources.displayMetrics
            val minW = dp(150)
            val minH = dp(110)

            var newW = downW
            var newH = downH
            when (resizeCorner) {
                1 -> {
                    newW = downW - dx
                    newH = downH - dy
                }
                2 -> {
                    newW = downW + dx
                    newH = downH - dy
                }
                3 -> {
                    newW = downW - dx
                    newH = downH + dy
                }
                4 -> {
                    newW = downW + dx
                    newH = downH + dy
                }
            }
            newW = newW.coerceIn(minW, dm.widthPixels)
            newH = newH.coerceIn(minH, (dm.heightPixels - dp(24)).coerceAtLeast(minH))

            val anchorRight = resizeCorner == 1 || resizeCorner == 3
            val anchorBottom = resizeCorner == 1 || resizeCorner == 2
            val newX =
                    if (anchorRight) {
                        (downX + (downW - newW)).coerceIn(0, (dm.widthPixels - newW).coerceAtLeast(0))
                    } else {
                        downX.coerceIn(0, (dm.widthPixels - newW).coerceAtLeast(0))
                    }
            val newY =
                    if (anchorBottom) {
                        (downY + (downH - newH)).coerceIn(0, (dm.heightPixels - newH).coerceAtLeast(0))
                    } else {
                        downY.coerceIn(0, (dm.heightPixels - newH).coerceAtLeast(0))
                    }

            layoutParams.width = newW
            layoutParams.height = newH
            layoutParams.x = newX
            layoutParams.y = newY
            runCatching { w.updateViewLayout(this, layoutParams) }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val w = wm
            val layoutParams = lp
            if (w == null || layoutParams == null) return super.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downX = layoutParams.x
                    downY = layoutParams.y
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging) {
                        val slop = dp(6).toFloat()
                        if (kotlin.math.abs(dx) >= slop || kotlin.math.abs(dy) >= slop) {
                            dragging = true
                        }
                    }

                    if (dragging) {
                        val dm = resources.displayMetrics
                        val viewW = if (layoutParams.width > 0) layoutParams.width else width
                        val viewH = if (layoutParams.height > 0) layoutParams.height else height
                        val maxX = (dm.widthPixels - viewW).coerceAtLeast(0)
                        val maxY = (dm.heightPixels - viewH).coerceAtLeast(0)
                        layoutParams.x = (downX + dx.toInt()).coerceIn(0, maxX)
                        layoutParams.y = (downY + dy.toInt()).coerceIn(0, maxY)
                        runCatching { w.updateViewLayout(this, layoutParams) }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    return if (dragging) {
                        // 拖动结束记下位置，关闭/重开后仍回到这里。
                        persistFrame()
                        true
                    } else {
                        performClick()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) persistFrame()
                    dragging = false
                    return true
                }
            }

            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }


    private class EdgeFlowView(context: Context) : View(context) {

        /** 悬浮窗专属强调色 #3964FE：边框、流光、颗粒点统一使用。 */
        private val accent = 0xFF3964FE.toInt()

        private val colorPrimary = accent
        private val colorPrimaryContainer = ColorUtils.blendARGB(accent, 0xFFFFFFFF.toInt(), 0.45f)
        private val colorSecondaryContainer = ColorUtils.blendARGB(accent, 0xFFFFFFFF.toInt(), 0.60f)
        private val colorSuccess = ContextCompat.getColor(context, com.ai.phoneagent.core.designsystem.R.color.m3t_success)
        private val colorSuccessContainer = ContextCompat.getColor(context, com.ai.phoneagent.core.designsystem.R.color.m3t_dialog_surface_alt)
        private val colorSurface = ContextCompat.getColor(context, com.ai.phoneagent.core.designsystem.R.color.m3t_surface)
        private val colorOnSurface = ContextCompat.getColor(context, com.ai.phoneagent.core.designsystem.R.color.m3t_on_surface)

        private val trackPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = accent
                }

        private val progressPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = accent
                }

        private val headGlowPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = colorPrimaryContainer
                }

        private val outerGlowPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = colorSecondaryContainer
                }

        private val sparkOuterPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = ColorUtils.setAlphaComponent(accent, 120)
                }

        private val sparkCorePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = ColorUtils.setAlphaComponent(accent, 255)
                }

        private var progress: Float = 0f

        private val borderPath = Path()
        private val segmentPath = Path()
        private val headPath = Path()
        private var measure: PathMeasure? = null
        private var length: Float = 0f

        private var shader: Shader? = null
        private val shaderMatrix = Matrix()
        private var phase: Float = 0f
        private var inputVerifyHighlight: Boolean = false

        /**
         * 四角缩放热区是否被按住。
         *
         * 按下热区时整圈描边切换为实色品牌蓝（与进度弧同色），松手立刻回到原本的浅色描边——
         * 让用户明确知道“现在抓住的是边角缩放”，而不是在拖窗口。
         */
        private var cornerHighlight: Boolean = false

        /** 是否运行边框颗粒动画。关闭时连 animator 都不启动，避免空转耗电。 */
        private var particlesEnabled: Boolean = true

        private val spinner = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400L
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
        }

        private var completeAnim: android.animation.ValueAnimator? = null

        fun setProgress(p: Float) {
            progress = p
            invalidate()
        }

        fun setInputVerifyHighlight(active: Boolean) {
            if (inputVerifyHighlight == active) return
            inputVerifyHighlight = active
            rebuildPath()
            invalidate()
        }

        /** 四角缩放热区按下/松开时切换整圈描边的高亮状态。 */
        fun setCornerHighlight(active: Boolean) {
            if (cornerHighlight == active) return
            cornerHighlight = active
            rebuildPath()
            invalidate()
        }

        fun start() {
            // 颗粒被设置关掉时直接不启动动画，onDraw 也只画静态边框。
            if (!particlesEnabled) {
                runCatching { spinner.cancel() }
                invalidate()
                return
            }
            if (!spinner.isStarted) spinner.start()
        }

        /** 任务结束后停止转圈颗粒，边框保持静态。 */
        fun stop() {
            runCatching { spinner.cancel() }
        }

        /** 设置里的“边框颗粒”开关：关闭后立即停掉动画并重绘为静态边框。 */
        fun setParticlesEnabled(enabled: Boolean) {
            if (particlesEnabled == enabled) return
            particlesEnabled = enabled
            if (!enabled) {
                runCatching { spinner.cancel() }
                phase = 0f
            }
            invalidate()
        }

        fun isParticlesEnabled(): Boolean = particlesEnabled

        fun playCompleteEffect(onEnd: () -> Unit) {
            completeAnim?.cancel()
            val anim = android.animation.ValueAnimator.ofFloat(0f, 1f)
            completeAnim = anim
            anim.duration = 900L
            anim.interpolator = LinearInterpolator()
            anim.addUpdateListener { invalidate() }
            anim.addListener(
                    object : android.animation.Animator.AnimatorListener {
                        override fun onAnimationStart(animation: android.animation.Animator) {}
                        override fun onAnimationCancel(animation: android.animation.Animator) {}
                        override fun onAnimationRepeat(animation: android.animation.Animator) {}
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            postDelayed({ onEnd() }, 900L)
                        }
                    }
            )
            anim.start()
        }

        override fun onDetachedFromWindow() {
            runCatching { spinner.cancel() }
            completeAnim?.cancel()
            completeAnim = null
            super.onDetachedFromWindow()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            rebuildPath()
        }

        private fun rebuildPath() {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val stroke = (w.coerceAtMost(h) * 0.026f).coerceIn(dp(1.2f), dp(2.0f))
            val pad = (stroke * 0.5f) + dp(0.8f)
            val rect = RectF(pad, pad, w - pad, h - pad)
            val baseR = dp(12f)
            val r = (baseR - pad).coerceAtLeast(0f)
                    .coerceAtMost(rect.width() / 2f)
                    .coerceAtMost(rect.height() / 2f)

            trackPaint.strokeWidth = stroke * 0.85f
            progressPaint.strokeWidth = stroke
            headGlowPaint.strokeWidth = stroke * 1.05f
            outerGlowPaint.strokeWidth = stroke * 1.55f

            if (inputVerifyHighlight) {
                trackPaint.color = colorSuccess
                progressPaint.color = colorSuccess
                headGlowPaint.color = ColorUtils.blendARGB(colorSuccess, colorOnSurface, 0.20f)
                outerGlowPaint.color = ColorUtils.blendARGB(colorSuccess, colorSuccessContainer, 0.35f)
                sparkOuterPaint.color = ColorUtils.setAlphaComponent(colorSuccess, 120)
                sparkCorePaint.color = ColorUtils.setAlphaComponent(colorOnSurface, 230)
            } else {
                trackPaint.color = colorPrimary
                progressPaint.color = colorPrimary
                headGlowPaint.color = ColorUtils.blendARGB(colorPrimary, colorPrimaryContainer, 0.50f)
                outerGlowPaint.color = colorSecondaryContainer
                sparkOuterPaint.color = ColorUtils.setAlphaComponent(colorPrimary, 120)
                sparkCorePaint.color = ColorUtils.setAlphaComponent(colorOnSurface, 230)
            }

            // 边框使用非常浅的 #3964FE（约 12% 不透明度），进度/流光才是实色主蓝。
            // 按住四角缩放热区时把描边提到实色，松手恢复浅色。
            trackPaint.alpha = if (cornerHighlight) 255 else 30
            progressPaint.alpha = if (cornerHighlight) 255 else 170

            borderPath.reset()
            borderPath.addRoundRect(rect, r, r, Path.Direction.CW)
            measure = PathMeasure(borderPath, true)
            length = measure?.length ?: 0f

            val gradientColors =
                    if (inputVerifyHighlight) {
                        intArrayOf(
                                colorSuccess,
                                ColorUtils.blendARGB(colorSuccess, colorOnSurface, 0.35f),
                                ColorUtils.blendARGB(colorSuccessContainer, colorSurface, 0.35f),
                                colorSuccess
                        )
                    } else {
                        intArrayOf(
                                colorPrimary,
                                ColorUtils.blendARGB(colorPrimary, colorSecondaryContainer, 0.35f),
                                ColorUtils.blendARGB(colorPrimaryContainer, colorSurface, 0.40f),
                                colorPrimary
                        )
                    }
            shader =
                    LinearGradient(
                            -w,
                            0f,
                            w * 2f,
                            h,
                            gradientColors,
                            floatArrayOf(0f, 0.48f, 0.74f, 1f),
                            Shader.TileMode.CLAMP
                    )
        }

        private fun dp(v: Float): Float {
            return TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            v,
                            resources.displayMetrics
                    )
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            if (measure == null || length <= 0f) {
                rebuildPath()
            }
            val pm = measure ?: return
            val total = length
            if (total <= 0f) return

            // 颗粒关闭：只画静态边框与进度段，不进入流光/颗粒绘制与相位计算。
            if (!particlesEnabled) {
                canvas.drawPath(borderPath, trackPaint)
                val staticProgress = progress.coerceIn(0f, 1f)
                if (staticProgress > 0f) {
                    segmentPath.reset()
                    pm.getSegment(0f, total * staticProgress, segmentPath, true)
                    canvas.drawPath(segmentPath, progressPaint)
                }
                return
            }

            canvas.drawPath(borderPath, trackPaint)

            val p = progress.coerceIn(0f, 1f)
            val end = total * p
            val headLen = (total * 0.045f).coerceIn(dp(6f), dp(12f))

            if (end <= dp(0.5f)) {
                // 进度几乎为 0：显示一段沿边缘流动的细流光（不填充进度）
                val center = total * phase
                val start = center - (headLen * 0.55f)
                val stop = center + (headLen * 0.45f)

                fun drawGlowSeg(from: Float, to: Float) {
                    headPath.reset()
                    pm.getSegment(from.coerceIn(0f, total), to.coerceIn(0f, total), headPath, true)
                    val pulse =
                            (0.55f +
                                            0.45f *
                                                    kotlin.math.abs(
                                                            kotlin.math.sin(phase * 6.2831853f)
                                                    ))
                                    .coerceIn(0f, 1f)
                    headGlowPaint.alpha = (120 + 120 * pulse).toInt().coerceIn(0, 255)
                    outerGlowPaint.alpha = (35 + 55 * pulse).toInt().coerceIn(0, 120)
                    shader?.let {
                        shaderMatrix.reset()
                        shaderMatrix.setTranslate(w * (phase - 0.5f), h * (0.5f - phase))
                        it.setLocalMatrix(shaderMatrix)
                        outerGlowPaint.shader = it
                        headGlowPaint.shader = it
                    }
                    canvas.drawPath(headPath, outerGlowPaint)
                    canvas.drawPath(headPath, headGlowPaint)
                    headGlowPaint.shader = null
                    outerGlowPaint.shader = null
                }

                if (start >= 0f && stop <= total) {
                    drawGlowSeg(start, stop)
                } else {
                    val s = (start % total + total) % total
                    val t = (stop % total + total) % total
                    if (s <= total) drawGlowSeg(s, total)
                    if (t >= 0f) drawGlowSeg(0f, t)
                }

                val sparkD = ((center % total) + total) % total
                val pos = FloatArray(2)
                pm.getPosTan(sparkD, pos, null)
                val pulse =
                        (0.55f +
                                        0.45f *
                                                kotlin.math.abs(
                                                        kotlin.math.sin(phase * 6.2831853f)
                                                ))
                                .coerceIn(0f, 1f)
                sparkOuterPaint.alpha = (55 + (85 * pulse)).toInt().coerceIn(0, 160)
                sparkCorePaint.alpha = (190 + (65 * pulse)).toInt().coerceIn(0, 255)
                canvas.drawCircle(pos[0], pos[1], dp(2.2f), sparkOuterPaint)
                canvas.drawCircle(pos[0], pos[1], dp(1.1f), sparkCorePaint)
            } else {
                segmentPath.reset()
                pm.getSegment(0f, end, segmentPath, true)
                shader?.let {
                    shaderMatrix.reset()
                    shaderMatrix.setTranslate(w * (phase - 0.5f) * 1.1f, h * (0.5f - phase) * 1.1f)
                    it.setLocalMatrix(shaderMatrix)
                    progressPaint.shader = it
                }
                canvas.drawPath(segmentPath, progressPaint)
                progressPaint.shader = null

                val headStart = (end - headLen).coerceAtLeast(0f)
                headPath.reset()
                pm.getSegment(headStart, end, headPath, true)

                val glowA = (completeAnim?.animatedValue as? Float) ?: 0f
                val pulse =
                        (0.55f +
                                        0.45f *
                                                kotlin.math.abs(
                                                        kotlin.math.sin(phase * 6.2831853f)
                                                ))
                                .coerceIn(0f, 1f)
                val alphaBase = (85 + 160 * pulse).toInt().coerceIn(0, 255)
                val coreA = (alphaBase + (70 * glowA).toInt()).coerceIn(0, 255)
                val outerA = (30 + (70 * pulse)).toInt().coerceIn(0, 120)

                outerGlowPaint.alpha = outerA
                headGlowPaint.alpha = coreA
                shader?.let {
                    shaderMatrix.reset()
                    shaderMatrix.setTranslate(w * (phase - 0.5f), h * (0.5f - phase))
                    it.setLocalMatrix(shaderMatrix)
                    outerGlowPaint.shader = it
                    headGlowPaint.shader = it
                }
                canvas.drawPath(headPath, outerGlowPaint)
                canvas.drawPath(headPath, headGlowPaint)
                headGlowPaint.shader = null
                outerGlowPaint.shader = null

                val pos = FloatArray(2)
                pm.getPosTan(end.coerceIn(0f, total), pos, null)
                sparkOuterPaint.alpha = (70 + (90 * pulse)).toInt().coerceIn(0, 170)
                sparkCorePaint.alpha = (205 + (50 * pulse)).toInt().coerceIn(0, 255)
                canvas.drawCircle(pos[0], pos[1], dp(2.2f), sparkOuterPaint)
                canvas.drawCircle(pos[0], pos[1], dp(1.1f), sparkCorePaint)
            }

            val ca = (completeAnim?.animatedValue as? Float) ?: 0f
            if (ca > 0f) {
                val a = (40 + (170 * ca)).toInt().coerceIn(0, 255)
                headGlowPaint.alpha = a
                canvas.drawPath(borderPath, headGlowPaint)
            }
        }
    }

}
