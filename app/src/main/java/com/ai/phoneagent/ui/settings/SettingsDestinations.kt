package com.ai.phoneagent.ui.settings

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.ai.phoneagent.R
import com.ai.phoneagent.navigation.Routes
import com.ai.phoneagent.viewmodel.AutomationViewModel
import com.ai.phoneagent.viewmodel.SettingsViewModel
import com.ai.phoneagent.core.designsystem.R as DesignR
import com.ai.phoneagent.feature.settings.R as SettingsR

/**
 * 设置二级页的四个“需要外部状态”的目的地。
 *
 * 其余二级页（外观 / 语言 / 存储 / 关于）本身就是自包含页面，直接在
 * [com.ai.phoneagent.navigation.AquariusNavGraph] 里接 `popBackStack` 即可。
 */

/** 「模型与 API」：读写共享的 [SettingsViewModel]（API Key、接入方式、连接状态都在它里面）。 */
@Composable
fun SettingsModelApiPage(
    navController: NavController,
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    // 组合期解析资源，Configuration 变化随重组刷新（LocalContextGetResourceValueCall）
    val aquariusLoginFailedText = stringResource(R.string.aquarius_login_failed)
    val aquariusLogoutSuccessText = stringResource(R.string.aquarius_logout_success)

    DrawerModelApiConfigScreen(
        currentApiMode = viewModel.currentApiMode,
        apiInput = viewModel.apiInputText,
        apiBaseUrl = viewModel.apiBaseUrlText,
        apiModel = viewModel.apiModelText,
        apiStatus = viewModel.apiStatusText,
        apiStatusPositive = viewModel.apiStatusPositive,
        qwenButtonText = viewModel.qwenButtonText,
        qwenButtonEnabled = viewModel.qwenButtonEnabled,
        showAquariusApiSection = viewModel.showAquariusApiSection,
        aquariusLoggedInUser = viewModel.aquariusLoggedInUser,
        aquariusSelectedModel = viewModel.aquariusSelectedModel,
        onChangeAquariusModel = { viewModel.openAquariusModelSelectionDialog() },
        onBack = { navController.popBackStack() },
        onApiModeChange = { mode ->
            viewModel.onApiModeChange(mode) { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        },
        onApiInputChange = { value -> viewModel.onApiInputChanged(value) },
        onOpenApiKeyPage = { viewModel.openApiKeyPage(context) },
        onOpenMembership = {
            navController.navigate(Routes.SettingsMembership.route) { launchSingleTop = true }
        },
        onAquariusLoginClick = {
            val activity = context as? Activity
            if (activity == null) {
                Toast.makeText(context, aquariusLoginFailedText, Toast.LENGTH_SHORT).show()
            } else {
                viewModel.submitAquariusSsoLogin(
                    activity = activity,
                    onSuccess = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
                    onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() },
                )
            }
        },
        onAquariusLogout = {
            viewModel.aquariusLogout()
            Toast.makeText(context, aquariusLogoutSuccessText, Toast.LENGTH_SHORT).show()
        },
        onApiBaseUrlChange = { value -> viewModel.onApiBaseUrlChange(value) },
        onApiModelChange = { value -> viewModel.onApiModelChange(value) },
        onCheckApi = {
            viewModel.checkApiConnection { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        },
        onDownloadQwenModel = {
            viewModel.enqueueQwenDownloads { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        },
    )

    if (viewModel.showAquariusModelDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAquariusModelDialog() },
            title = { Text(stringResource(R.string.aquarius_model_select_title)) },
            text = {
                LazyColumn {
                    items(viewModel.aquariusAvailableModels) { model ->
                        Text(
                            text = model.id,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectAquariusModel(model.id) }
                                    .then(
                                        Modifier.height(
                                            dimensionResource(DesignR.dimen.m3t_compact_button_height),
                                        ),
                                    ),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAquariusModelDialog() }) {
                    Text(stringResource(SettingsR.string.action_cancel))
                }
            },
        )
    }
}

/** 「会员」：展示当前账号与生效模型。 */
@Composable
fun SettingsMembershipPage(
    navController: NavController,
    viewModel: SettingsViewModel,
) {
    MembershipScreen(
        accountName = viewModel.aquariusLoggedInUser,
        effectiveModel = viewModel.resolveApiModel(),
        onBack = { navController.popBackStack() },
    )
}

/** 「自动化」：执行模式开关、自动确认、无障碍状态与自动化面板入口。 */
@Composable
fun SettingsAutomationPage(
    navController: NavController,
    automationViewModel: AutomationViewModel,
) {
    AutomationPreferencesScreen(
        onBack = { navController.popBackStack() },
        onOpenControl = {
            navController.navigate(Routes.Automation.route) { launchSingleTop = true }
        },
        onOpenPermissionGuide = {
            navController.navigate(Routes.PermissionGuide.route) { launchSingleTop = true }
        },
        automationViewModel = automationViewModel,
    )
}

/** 「悬浮窗设置」：开关与字号/热区。 */
@Composable
fun SettingsOverlayPage(
    navController: NavController,
    automationViewModel: AutomationViewModel,
) {
    val overlayVisible by automationViewModel.overlayVisibleFlow().collectAsState()
    OverlaySettingsScreen(
        onNavigateBack = { navController.popBackStack() },
        overlayVisible = overlayVisible,
        onOverlayToggle = { enabled -> automationViewModel.setOverlayEnabled(enabled) },
    )
}
