package com.ai.phoneagent.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ai.phoneagent.ui.automation.AutomationScreen
import com.ai.phoneagent.ui.onboarding.OnboardingRoute
import com.ai.phoneagent.ui.settings.AppearanceScreen
import com.ai.phoneagent.ui.settings.LanguageScreen
import com.ai.phoneagent.ui.settings.LicensesScreen
import com.ai.phoneagent.ui.settings.PermissionGuideScreen
import com.ai.phoneagent.ui.settings.SettingsAboutPage
import com.ai.phoneagent.ui.settings.SettingsAutomationPage
import com.ai.phoneagent.ui.settings.SettingsMembershipPage
import com.ai.phoneagent.ui.settings.SettingsModelApiPage
import com.ai.phoneagent.ui.settings.SettingsOverlayPage
import com.ai.phoneagent.ui.settings.SettingsRoute
import com.ai.phoneagent.ui.settings.StorageScreen
import com.ai.phoneagent.ui.settings.UserAgreementScreen
import com.ai.phoneagent.ui.updates.UpdateHistoryScreen
import com.ai.phoneagent.viewmodel.AutomationViewModel
import com.ai.phoneagent.viewmodel.SettingsViewModel

/**
 * 应用主导航图。
 *
 * 转场与返回完全照搬 RikkaHub（Navigation 3 `NavDisplay`）的规格：
 * - 前进：新页从右侧整屏滑入，旧页左移半屏、缩到 0.7 并淡出；
 * - 返回 / 预测性返回：上一页从左半屏滑入、从 0.7 放大回 1 并淡入，当前页整屏向右滑出；
 * - 不做投影、不做圆角：过渡完全靠位移 + 缩放 + 透明度，滑到一半就能看清要返回的界面。
 *
 * 返回手势**不额外拦截**：NavHost 自己注册 `OnBackInvokedCallback`，并用可寻址
 * （seekable）转场跟随手势进度，所以预测性返回是原生的，不需要再包一层
 * `PredictiveBackHandler`（以前那样包一层会把事件吞掉，导致“滑到一半看不到目标页”）。
 *
 * 底部三个主标签（主页 / 历史记录 / 设置）仍是 pager 的水平平移，见 `HomeScreen`。
 *
 * @param homeContent 首页内容（由调用方注入，避免导航层依赖具体首页实现）。
 * @param onStartNextTask 在任务详情页触发「开始下一个任务」的回调。
 */
@Composable
fun AquariusNavGraph(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    automationViewModel: AutomationViewModel,
    homeContent: @Composable () -> Unit,
    onStartNextTask: () -> Unit,
) {
    val close: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = Routes.Home.route,
        enterTransition = { RikkaPushEnter },
        exitTransition = { RikkaPushExit },
        popEnterTransition = { RikkaPopEnter },
        popExitTransition = { RikkaPopExit },
    ) {
        composable(Routes.Home.route) { homeContent() }
        composable(Routes.Settings.route) {
            SettingsRoute(
                navController = navController,
                viewModel = settingsViewModel,
                automationViewModel = automationViewModel,
            )
        }

        // ── 设置二级页（每一级都是独立目的地，返回即出栈）────────────────────────
        composable(Routes.SettingsAppearance.route) {
            AppearanceScreen(
                onNavigateBack = close,
                onNavigateLanguage = { navController.navigate(Routes.SettingsLanguage.route) },
            )
        }
        composable(Routes.SettingsLanguage.route) {
            LanguageScreen(onNavigateBack = close)
        }
        composable(Routes.SettingsOverlay.route) {
            SettingsOverlayPage(navController = navController, automationViewModel = automationViewModel)
        }
        composable(Routes.SettingsStorage.route) {
            StorageScreen(onNavigateBack = close)
        }
        composable(Routes.SettingsAutomation.route) {
            SettingsAutomationPage(navController = navController, automationViewModel = automationViewModel)
        }
        composable(Routes.SettingsModelApi.route) {
            SettingsModelApiPage(navController = navController, viewModel = settingsViewModel)
        }
        composable(Routes.SettingsMembership.route) {
            SettingsMembershipPage(navController = navController, viewModel = settingsViewModel)
        }
        composable(Routes.SettingsAbout.route) {
            SettingsAboutPage(
                onBack = close,
                onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } },
            )
        }

        // ── 任务与自动化 ──────────────────────────────────────────────────────
        composable("task/{runId}", arguments = listOf(navArgument("runId") { type = NavType.StringType })) { entry ->
            com.ai.phoneagent.ui.automation.TaskScreen(navController, entry.arguments?.getString("runId").orEmpty(), onStartNextTask = onStartNextTask)
        }
        composable("task/{runId}/result", arguments = listOf(navArgument("runId") { type = NavType.StringType })) { entry ->
            com.ai.phoneagent.ui.automation.TaskScreen(navController, entry.arguments?.getString("runId").orEmpty(), true, onStartNextTask)
        }
        composable(Routes.Automation.route) { AutomationScreen(navController = navController) }

        // ── 其它单层页面 ──────────────────────────────────────────────────────
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

// RikkaHub 的四种转场（`NavDisplay` 的 transitionSpec / popTransitionSpec /
// predictivePopTransitionSpec 一一对应）。刻意沿用它原来的默认动画曲线与时长，
// 让手感与参考实现一致，不另外自定义 easing。
private val RikkaPushEnter: EnterTransition = slideInHorizontally { it }
private val RikkaPushExit: ExitTransition =
    slideOutHorizontally { -it / 2 } + scaleOut(targetScale = 0.7f) + fadeOut()
private val RikkaPopEnter: EnterTransition =
    slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f) + fadeIn()
private val RikkaPopExit: ExitTransition = slideOutHorizontally { it }
