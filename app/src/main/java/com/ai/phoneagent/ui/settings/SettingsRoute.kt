package com.ai.phoneagent.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.ai.phoneagent.core.settings.Haptics
import com.ai.phoneagent.feature.settings.R as SettingsR
import com.ai.phoneagent.helper.FeedbackLogExporter
import com.ai.phoneagent.navigation.Routes
import com.ai.phoneagent.ui.AboutScreen
import com.ai.phoneagent.viewmodel.AboutViewModel
import com.ai.phoneagent.viewmodel.AutomationViewModel
import com.ai.phoneagent.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 设置首页（列表）。
 *
 * 二级页不再在内部自绘一层容器：外观 / 语言 / 悬浮窗 / 存储 / 自动化 / 模型与 API /
 * 会员 / 关于 全部是 NavHost 里的独立目的地。这样
 * 1. 每一级的进入与返回都走同一套转场（见 `AquariusNavGraph` 的 transitionSpec）；
 * 2. 预测性返回由 NavHost 原生驱动，不会出现“自绘覆盖层 + pager 平移”叠加后的残影。
 */
@Composable
fun SettingsRoute(
    navController: NavController,
    viewModel: SettingsViewModel,
    automationViewModel: AutomationViewModel,
    isActive: Boolean = true,
    embedded: Boolean = false,
    onExitToChat: (() -> Unit)? = null,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // 首页列表状态常驻持有（回到首页时保留滚动位置）。
    val settingsListState = rememberLazyListState()
    val overlayVisible by automationViewModel.overlayVisibleFlow().collectAsState()

    LaunchedEffect(isActive) {
        if (isActive) {
            viewModel.refreshLocalModelState()
            // 语言切换会重建 Activity；状态文案由 ViewModel 缓存，这里按当前 Locale 重新解析。
            viewModel.refreshLocalizedState()
            automationViewModel.onRefreshStatus()
        }
    }
    DisposableEffect(lifecycleOwner, viewModel, isActive) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && isActive) {
                    viewModel.refreshLocalModelState()
                    if (!embedded) viewModel.restoreSettings()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val open: (Routes) -> Unit = remember(navController) {
        { route: Routes -> navController.navigate(route.route) { launchSingleTop = true } }
    }

    // 嵌在首页 pager 第 2 页时，上层已经用 padding 让出了状态栏高度；把该 inset 标记为已消费，
    // 嵌套页面里的 TopAppBar 才不会再加一遍。独立路由（Routes.Settings）时保持原样。
    Box(
        Modifier
            .fillMaxSize()
            .then(if (embedded) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
    ) {
        DrawerSettingsScreen(
            onBack = {
                if (onExitToChat != null) onExitToChat() else navController.popBackStack()
            },
            embedded = embedded,
            accountName = viewModel.aquariusLoggedInUser,
            modelSummary = viewModel.resolveApiModel(),
            onOpenAccount = { open(Routes.SettingsModelApi) },
            onOpenAppearance = { open(Routes.SettingsAppearance) },
            onOpenOverlay = { open(Routes.SettingsOverlay) },
            onOpenStorage = { open(Routes.SettingsStorage) },
            onOpenModelApi = { open(Routes.SettingsModelApi) },
            onOpenAutomation = { open(Routes.SettingsAutomation) },
            onOpenAbout = { open(Routes.SettingsAbout) },
            overlayVisible = overlayVisible,
            onOverlayToggle = { enabled -> automationViewModel.setOverlayEnabled(enabled) },
            listState = settingsListState,
        )
    }
}

// ── 关于与帮助 ────────────────────────────────────────────────────────────────

/** 「设置 → 关于与帮助」二级页（独立目的地）。 */
@Composable
fun SettingsAboutPage(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: AboutViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showFeedbackConfirmDialog by remember { mutableStateOf(false) }
    var isExportingFeedbackBundle by remember { mutableStateOf(false) }

    // 组合期解析资源，Configuration 变化随重组刷新（LocalContextGetResourceValueCall）
    val sourceCodeUrl = stringResource(SettingsR.string.about_source_code_url)
    val feedbackChooserTitle = stringResource(SettingsR.string.about_feedback_share_chooser)
    val feedbackShareSubject = stringResource(SettingsR.string.about_feedback_share_subject)

    // 统一走全局触感入口（EFFECT_TICK），与标签页切换的震动完全一致。
    val vibrateLight = { Haptics.tap(context) }

    AboutScreen(
        appVersionText = uiState.appVersionText,
        promptVersionText = uiState.promptVersionText,
        checkUpdateButtonText = uiState.checkUpdateButtonText,
        onBack = {
            vibrateLight()
            onBack()
        },
        onCheckUpdate = {
            vibrateLight()
            viewModel.checkForUpdates()
        },
        onOpenChangelog = {
            vibrateLight()
            onNavigate(Routes.UpdateHistory.route)
        },
        onOpenUserAgreement = {
            vibrateLight()
            onNavigate(Routes.UserAgreement.route)
        },
        onOpenLicenses = {
            vibrateLight()
            onNavigate(Routes.Licenses.route)
        },
        // 官网入口已下线：本项目是自建改版，没有独立官网，避免指向失效域名。
        onOpenWebsite = {},
        onOpenSourceCode = {
            vibrateLight()
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(sourceCodeUrl),
                    ),
                )
            } catch (_: Exception) {
                Toast.makeText(context, SettingsR.string.about_open_url_failed, Toast.LENGTH_SHORT).show()
            }
        },
        onTaskFeedback = {
            vibrateLight()
            showFeedbackConfirmDialog = true
        },
        onCopyContact = {
            vibrateLight()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("text", "enoogo@qq.com"))
            Toast.makeText(context, SettingsR.string.about_contact_copied, Toast.LENGTH_SHORT).show()
        },
        onDeveloperTap = {
            vibrateLight()
            viewModel.handleDeveloperTap()
        },
        // 底部 Enoogo：点击打开作者 GitHub 主页，长按保留隐藏入口（连点解锁）。
        onAliasTap = {
            Haptics.tap(context)
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(SettingsR.string.about_github_url))),
                )
            }
        },
        onAliasLongClick = {
            Haptics.tap(context)
            viewModel.handleAliasTap()
        },
    )

    if (showFeedbackConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackConfirmDialog = false },
            title = { Text(stringResource(SettingsR.string.about_feedback_confirm_title)) },
            text = { Text(stringResource(SettingsR.string.about_feedback_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFeedbackConfirmDialog = false
                        isExportingFeedbackBundle = true
                        scope.launch {
                            val result = FeedbackLogExporter.exportSanitizedBundle(context)
                            isExportingFeedbackBundle = false
                            result.onSuccess { bundleFile ->
                                FeedbackLogExporter.shareBundle(
                                    context = context,
                                    file = bundleFile,
                                    chooserTitle = feedbackChooserTitle,
                                    subject = feedbackShareSubject,
                                ).onFailure {
                                    showExportFailedToast(context, it.message.orEmpty())
                                }
                            }.onFailure {
                                showExportFailedToast(context, it.message.orEmpty())
                            }
                        }
                    },
                ) {
                    Text(stringResource(SettingsR.string.about_feedback_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackConfirmDialog = false }) {
                    Text(stringResource(SettingsR.string.action_cancel))
                }
            },
        )
    }

    if (isExportingFeedbackBundle) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(SettingsR.string.about_feedback_confirm_title)) },
            text = {
                Column {
                    CircularProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(SettingsR.string.about_feedback_exporting))
                }
            },
            confirmButton = {},
        )
    }

    uiState.updateDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = {
                Text(
                    stringResource(SettingsR.string.m3t_updates_found) + " ${state.entry.versionTag}",
                )
            },
            text = {
                Text(state.entry.body.ifBlank { stringResource(SettingsR.string.m3t_updates_no_changelog) })
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissUpdateDialog()
                        viewModel.handleDownload(state.entry)
                    },
                ) {
                    Text(stringResource(SettingsR.string.about_check_updates))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissUpdateDialog()
                        onNavigate(Routes.UpdateHistory.route)
                    },
                ) {
                    Text(stringResource(SettingsR.string.about_changelog))
                }
            },
        )
    }

    uiState.errorDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissErrorDialog() },
            title = { Text(stringResource(SettingsR.string.about_check_failed)) },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissErrorDialog() }) {
                    Text(stringResource(SettingsR.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissErrorDialog()
                        onNavigate(Routes.UpdateHistory.route)
                    },
                ) {
                    Text(stringResource(SettingsR.string.about_changelog))
                }
            },
        )
    }

    uiState.upToDateDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpToDateDialog() },
            title = { Text(stringResource(SettingsR.string.about_up_to_date)) },
            text = {
                Text(
                    stringResource(SettingsR.string.about_current_version_format, state.currentVersion),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissUpToDateDialog() }) {
                    Text(stringResource(SettingsR.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissUpToDateDialog()
                        onNavigate(Routes.UpdateHistory.route)
                    },
                ) {
                    Text(stringResource(SettingsR.string.about_changelog))
                }
            },
        )
    }

    uiState.downloadOptionsDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDownloadOptionsDialog() },
            title = { Text(stringResource(SettingsR.string.m3t_updates_choose_source)) },
            text = {
                Column {
                    state.options.forEach { option ->
                        TextButton(
                            onClick = {
                                viewModel.dismissDownloadOptionsDialog()
                                viewModel.openReleaseUrlWithFeedback(option.second)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(option.first)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDownloadOptionsDialog() }) {
                    Text(stringResource(SettingsR.string.action_cancel))
                }
            },
        )
    }
}

// 导出失败的 Toast 在非 @Composable 函数中按事件时点解析资源：组合期解析的值在 Configuration 变化后会过期。
private fun showExportFailedToast(context: Context, reason: String) {
    Toast.makeText(
        context,
        context.getString(SettingsR.string.about_feedback_export_failed, reason),
        Toast.LENGTH_LONG,
    ).show()
}
