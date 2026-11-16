package com.ai.phoneagent.core.overlay

/**
 * 悬浮窗与任务调度之间的桥。
 *
 * 悬浮窗是 WindowManager 里的裸 View，拿不到 ViewModel；这里由
 * [com.ai.phoneagent.viewmodel.AutomationViewModel] 在存活期间注册回调，
 * 悬浮窗点“结束任务”“＋”时通过它回到任务调度。
 */
object OverlayTaskBridge {

    /** 提交一个新任务（悬浮窗内输入完成）。 */
    @Volatile
    var onSubmitTask: ((String) -> Unit)? = null

    /** 结束当前任务（悬浮窗内点“结束任务”）。 */
    @Volatile
    var onStopTask: (() -> Unit)? = null

    /** 设置里改了字号/颗粒开关后，让已显示的悬浮窗立即重刷外观。 */
    @Volatile
    var onAppearanceChanged: (() -> Unit)? = null

    fun clear() {
        onSubmitTask = null
        onStopTask = null
        onAppearanceChanged = null
    }
}
