package com.ai.phoneagent.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ai.phoneagent.ui.automation.AutomationScreen
import com.ai.phoneagent.ui.onboarding.OnboardingRoute
import com.ai.phoneagent.ui.settings.SettingsRoute
import com.ai.phoneagent.ui.settings.AboutRoute
import com.ai.phoneagent.ui.settings.LicensesScreen
import com.ai.phoneagent.ui.settings.PermissionGuideScreen
import com.ai.phoneagent.ui.settings.UserAgreementScreen
import com.ai.phoneagent.ui.updates.UpdateHistoryScreen

/**
 * 应用主导航图。
 *
 * 返回手势**不在这里拦截**：navigation-compose 2.8 起，NavHost 自己会注册
 * `OnBackInvokedCallback` 并用可寻址（seekable）的转场跟随系统返回手势，
 * 用户滑到一半就能看到即将返回的界面。之前这里包了一层 `PredictiveBackHandler`
 * 把返回事件吞掉、松手才出栈，预测性返回因此完全失效。
 *
 * @param homeContent 首页内容（由调用方注入，避免导航层依赖具体首页实现）。
 * @param onStartNextTask 在任务详情页触发「开始下一个任务」的回调。
 */
@Composable
fun AquariusNavGraph(
    navController: NavHostController,
    homeContent: @Composable () -> Unit,
    onStartNextTask: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.Home.route,
    ) {
        composable(Routes.Home.route) { homeContent() }
        composable(Routes.Settings.route) { SettingsRoute(navController = navController) }
        composable(Routes.About.route) { AboutRoute(navController = navController) }
        composable("task/{runId}", arguments = listOf(navArgument("runId") { type = NavType.StringType })) { entry ->
            com.ai.phoneagent.ui.automation.TaskScreen(navController, entry.arguments?.getString("runId").orEmpty(), onStartNextTask = onStartNextTask)
        }
        composable("task/{runId}/result", arguments = listOf(navArgument("runId") { type = NavType.StringType })) { entry ->
            com.ai.phoneagent.ui.automation.TaskScreen(navController, entry.arguments?.getString("runId").orEmpty(), true, onStartNextTask)
        }
        composable(Routes.Automation.route) { AutomationScreen(navController = navController) }
        composable(Routes.UpdateHistory.route) { UpdateHistoryScreen(navController = navController) }
        composable(Routes.PermissionGuide.route) { PermissionGuideScreen(navController = navController) }
        composable(Routes.UserAgreement.route) { UserAgreementScreen(navController = navController) }
        composable(Routes.Licenses.route) { LicensesScreen(navController = navController) }
        composable(Routes.Onboarding.route) {
            OnboardingRoute(
                navController = navController,
                flow = null,
            )
        }
        composable(
            route = Routes.Onboarding.routeWithOptionalFlow,
            arguments =
                listOf(
                    navArgument(Routes.Onboarding.FLOW_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = Routes.Onboarding.FLOW_ONBOARDING
                    },
                ),
        ) { entry ->
            OnboardingRoute(
                navController = navController,
                flow = entry.arguments?.getString(Routes.Onboarding.FLOW_ARG),
            )
        }
    }
}

/** 占位页面（预留路由使用）。 */
@Composable
private fun PlaceholderRouteScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "TODO — T13/T14/T15 will fill this")
    }
}
