package com.ai.phoneagent.navigation

/** 导航路由定义，`route` 为 Compose Navigation 使用的路径字符串。 */
sealed class Routes(val route: String) {
    data object Home : Routes("home")
    data object Settings : Routes("settings")

    /**
     * 设置二级页。
     *
     * 每一级都是独立目的地（不再在设置首页内部叠一层自绘容器）：
     * 前进/返回统一走 NavHost 的转场与预测性返回，层级再多也只有一条返回链。
     */
    data object SettingsAppearance : Routes("settings/appearance")
    data object SettingsLanguage : Routes("settings/language")
    data object SettingsOverlay : Routes("settings/overlay")
    data object SettingsStorage : Routes("settings/storage")
    data object SettingsAutomation : Routes("settings/automation")
    data object SettingsModelApi : Routes("settings/modelApi")
    data object SettingsMembership : Routes("settings/membership")
    data object SettingsAbout : Routes("settings/about")

    data object UserAgreement : Routes("userAgreement")
    data object Licenses : Routes("licenses")
    data object Automation : Routes("automation")
    data object UpdateHistory : Routes("updateHistory")
    data object PermissionGuide : Routes("permissionGuide")

    /** 引导页路由，支持通过 `flow` 参数切换展示形态。 */
    data object Onboarding : Routes("onboarding") {
        const val FLOW_ARG = "flow"
        const val FLOW_ONBOARDING = "onboarding"
        const val FLOW_VIEW_ONLY = "view_only"
        const val FLOW_PERMISSION_ONLY = "permission_only"

        /** 带可选 flow 参数的路由模板。 */
        val routeWithOptionalFlow: String = "$route?$FLOW_ARG={$FLOW_ARG}"

        /** 构造指定 flow 的引导页路由。 */
        fun withFlow(flow: String): String = "$route?$FLOW_ARG=$flow"
    }
}
