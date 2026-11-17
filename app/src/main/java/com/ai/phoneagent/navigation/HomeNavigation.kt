package com.ai.phoneagent.navigation

/** 首页返回键应执行的动作。 */
enum class HomeBackAction { DISMISS_OVERLAY, SETTINGS_PARENT, HOME, SYSTEM }

/**
 * 根据当前页与状态决定返回键行为。
 *
 * 优先级：浮层打开时关闭浮层 → 设置页非根时回上级 → 非首页时回首页 → 其余交给系统。
 *
 * 说明：设置二级页现在是 NavHost 的独立目的地（不再嵌在 pager 里），因此
 * [settingsAtRoot] 实际恒为 `true`；保留该参数只是为了兼容既有调用与
 * `HomeNavigationTest` 里的用例。
 */
fun homeBackAction(page: Int, settingsAtRoot: Boolean = true, overlayOpen: Boolean = false): HomeBackAction = when {
    overlayOpen -> HomeBackAction.DISMISS_OVERLAY
    page == 2 && !settingsAtRoot -> HomeBackAction.SETTINGS_PARENT
    page != 1 -> HomeBackAction.HOME
    else -> HomeBackAction.SYSTEM
}
