package com.ai.phoneagent.ui.settings

import android.Manifest
import com.ai.phoneagent.R
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.ai.phoneagent.feature.settings.R as SettingsR
import com.ai.phoneagent.helper.FeedbackLogExporter
import com.ai.phoneagent.navigation.Routes
import com.ai.phoneagent.ui.AboutScreen
import com.ai.phoneagent.ui.automation.AutomationControlScreen
import com.ai.phoneagent.ui.components.M3Motion
import com.ai.phoneagent.viewmodel.AboutViewModel
import com.ai.phoneagent.viewmodel.AutomationViewModel
import com.ai.phoneagent.viewmodel.SettingsViewModel
import com.ai.phoneagent.core.designsystem.R as DesignR
import com.ai.phoneagent.core.settings.Haptics
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsRoute(
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel(),
    isActive: Boolean = true,
    onExitToChat: (() -> Unit)? = null,
    embedded: Boolean = false,
) {
    val context = LocalContext.current
    val automationOwner = remember(context) {
        var current = context
        while (current is android.content.ContextWrapper && current !is Activity) current = current.baseContext
        current as androidx.lifecycle.ViewModelStoreOwner
    }
    val automationVm: AutomationViewModel = koinViewModel(viewModelStoreOwner = automationOwner)
    val lifecycleOwner = LocalLifecycleOwner.current
    var isExitingSettingsRoute by remember { mutableStateOf(false) }

    // 组合期解析资源，Configuration 变化随重组刷新（LocalContextGetResourceValueCall）
    val aquariusLoginFailedText = stringResource(R.string.aquarius_login_failed)
    val aquariusLogoutSuccessText = stringResource(R.string.aquarius_logout_success)

    // 悬浮窗开关与真实窗口状态同步。
    val overlayVisible by automationVm.overlayVisibleFlow().collectAsState()

    val exitSettingsRoute = remember(navController, onExitToChat) {
        exitSettingsRoute@{
            if (onExitToChat != null) { onExitToChat(); return@exitSettingsRoute }
            if (isExitingSettingsRoute) return@exitSettingsRoute
            isExitingSettingsRoute = true
            if (!navController.popBackStack()) {
                isExitingSettingsRoute = false
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(isActive) {
        if (isActive) {
            viewModel.refreshLocalModelState()
            // 语言切换会重建 Activity；状态文案由 ViewModel 缓存，这里按当前 Locale 重新解析。
            viewModel.refreshLocalizedState()
            automationVm.onRefreshStatus()
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

    val currentPage by remember { derivedStateOf { viewModel.currentPage } }
    val isSubPage = currentPage != SettingsViewModel.SettingsPage.Home

    // 首页列表状态常驻持有（首页始终在底层渲染，滚动位置天然保留）。
    val settingsListState = rememberLazyListState()

    // 覆盖层位移：0 = 二级页完整显示；1 = 二级页被推到屏外，露出底层首页。
    // 关键：返回时底层首页一直在渲染，覆盖层滑走即刻露出，无需等待首页首次渲染 → 不闪。
    val dismissAnim = remember { Animatable(1f) }
    var backInFlight by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dismissSpring = spring<Float>(dampingRatio = 1f, stiffness = 420f)

    // 进入二级页：从屏外滑入
    LaunchedEffect(currentPage) {
        if (isSubPage) {
            dismissAnim.snapTo(1f)
            dismissAnim.animateTo(0f, animationSpec = spring<Float>(dampingRatio = 1f, stiffness = 340f))
        }
    }

    // 返回按钮与手势共用：先把覆盖层滑出，再真正切回首页
    val commitBack: () -> Unit = {
        if (!backInFlight && isSubPage) {
            backInFlight = true
            scope.launch {
                dismissAnim.animateTo(1f, animationSpec = dismissSpring)
                viewModel.openHomePage()
                backInFlight = false
            }
        }
    }

    // 系统返回手势：跟手推走覆盖层；松手滑出并提交；取消则弹回原位
    PredictiveBackHandler(enabled = isSubPage && isActive && !backInFlight) { events ->
        try {
            events.collect { event -> dismissAnim.snapTo(event.progress.coerceIn(0f, 1f)) }
            dismissAnim.animateTo(1f, animationSpec = dismissSpring)
            viewModel.openHomePage()
        } catch (_: CancellationException) {
            dismissAnim.animateTo(0f, animationSpec = dismissSpring)
        }
    }

    // 嵌入首页（pager 第 2 页）时，上层已经用 padding 让出了状态栏高度；这里把该 inset
    // 标记成“已消费”，二级页自己的 TopAppBar 才不会再加一遍状态栏高度——否则标题栏和
    // 第一行会整体下移一个状态栏，看起来像“第一行没到顶”。
    Box(
        Modifier
            .fillMaxSize()
            .then(if (embedded) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
    ) {
        // 底层：设置首页常驻渲染。手势返回时它带一点点视差跟着被推走，
        // 所以松手之前用户就已经看得到要返回的界面，返回不再是“先闪一下再到位”。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val f = dismissAnim.value.coerceIn(0f, 1f)
                    // f=0（二级页盖满）时目标页偏移最多，f=1（已返回）时精确回到 0。
                    translationX = -size.width * M3Motion.PredictiveParallax * (1f - f)
                },
        ) {
        DrawerSettingsScreen(
            onBack = exitSettingsRoute,
                    embedded = embedded,
                    accountName = viewModel.aquariusLoggedInUser,
                    modelSummary = viewModel.resolveApiModel(),
                    onOpenAccount = { viewModel.openModelApiPage() },
                    onOpenAppearance = { viewModel.navigateTo(SettingsViewModel.SettingsPage.Appearance) },
                    onOpenOverlay = { viewModel.navigateTo(SettingsViewModel.SettingsPage.Overlay) },
                    onOpenStorage = { viewModel.navigateTo(SettingsViewModel.SettingsPage.Storage) },
                    onOpenModelApi = { viewModel.openModelApiPage() },
                    onOpenAutomation = { viewModel.navigateTo(SettingsViewModel.SettingsPage.Automation) },
                    onOpenAbout = { viewModel.navigateTo(SettingsViewModel.SettingsPage.About) },
                    overlayVisible = overlayVisible,
                    onOverlayToggle = { enabled -> automationVm.setOverlayEnabled(enabled) },
                    listState = settingsListState,
                )
        } // 底层视差 Box

        // 覆盖层：二级页在首页之上独立渲染。横向滑入/滑出，返回只动这一层，
        // 底下的首页始终存在，不会因重新组合而闪烁或丢失滚动位置。
        if (isSubPage) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .graphicsLayer {
                        // M3 Predictive Back：当前页跟手水平右移，露出下面已经渲染好的首页。
                        // 不做投影、不加圆角、不缩放——那些是系统窗口动画的视觉语言，
                        // 叠在应用内页面上会显得生硬；这里只保留位移，滑动手感最直接。
                        val f = dismissAnim.value.coerceIn(0f, 1f)
                        translationX = size.width * f
                    }
                    // 覆盖层填满整个屏幕并吞掉未被子组件消费的触摸，防止穿透到底层首页。
                    .pointerInput(Unit) {
                        forEachGesture {
                            awaitPointerEventScope {
                                awaitFirstDown(requireUnconsumed = true)
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                    },
            ) {
            when (currentPage) {
                // 首页在底层已渲染；此分支理论不可达（isSubPage 为 true 才进覆盖层）。
                SettingsViewModel.SettingsPage.Home -> Unit
                SettingsViewModel.SettingsPage.ModelApi -> {
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
                    onBack = { commitBack() },
                    onApiModeChange = { mode ->
                        viewModel.onApiModeChange(mode) { message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    onApiInputChange = { value -> viewModel.onApiInputChanged(value) },
                    onOpenApiKeyPage = { viewModel.openApiKeyPage(context) },
                    onOpenMembership = { viewModel.openMembershipPage() },
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
                // Aquarius 模型选择对话框
                if (viewModel.showAquariusModelDialog) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissAquariusModelDialog() },
                        title = { Text(stringResource(com.ai.phoneagent.R.string.aquarius_model_select_title)) },
                        text = {
                            LazyColumn {
                                items(viewModel.aquariusAvailableModels) { model ->
                                    Text(
                                        text = model.id,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.selectAquariusModel(model.id) }
                                            .then(
                                                Modifier.height(
                                                    dimensionResource(DesignR.dimen.m3t_compact_button_height)
                                                )
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

            SettingsViewModel.SettingsPage.Automation -> {
                AutomationPreferencesScreen(
                    onBack = { commitBack() },
                    onOpenControl = { navController.navigate(Routes.Automation.route) },
                    onOpenPermissionGuide = { navController.navigate(Routes.PermissionGuide.route) },
                )
            }

            SettingsViewModel.SettingsPage.Membership -> {
                MembershipScreen(
                    accountName = viewModel.aquariusLoggedInUser,
                    effectiveModel = viewModel.resolveApiModel(),
                    onBack = { commitBack() },
                )
            }

            SettingsViewModel.SettingsPage.Appearance -> {
                AppearanceScreen(
                    onNavigateBack = { commitBack() },
                    onNavigateLanguage = { viewModel.navigateTo(SettingsViewModel.SettingsPage.Language) },
                )
            }

            SettingsViewModel.SettingsPage.Language -> {
                LanguageScreen(onNavigateBack = { commitBack() })
            }

            SettingsViewModel.SettingsPage.Overlay -> {
                OverlaySettingsScreen(
                    onNavigateBack = { commitBack() },
                    overlayVisible = overlayVisible,
                    onOverlayToggle = { enabled -> automationVm.setOverlayEnabled(enabled) },
                )
            }

            SettingsViewModel.SettingsPage.Storage -> {
                StorageScreen(onNavigateBack = { commitBack() })
            }

            SettingsViewModel.SettingsPage.About -> {
                SettingsAboutContent(
                    onBack = { commitBack() },
                    onNavigate = { route -> navController.navigate(route) },
                )
            }
            } // when
            } // overlay Box
        } // if (isSubPage)
    } // outer Box
}

// 导出失败的 Toast 在非 @Composable 函数中按事件时点解析资源：组合期解析的值在 Configuration 变化后会过期，
// 事件时点解析始终取当前配置（LocalContextGetResourceValueCall）。
private fun showExportFailedToast(context: Context, reason: String) {
    Toast.makeText(
        context,
        context.getString(SettingsR.string.about_feedback_export_failed, reason),
        Toast.LENGTH_LONG,
    ).show()
}

// ── About content embedded in Settings ───────────────────────────────────────

@Composable
private fun SettingsAboutContent(
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

    // 统一走全局触感入口（EFFECT_TICK），与标签页切换的震动完全一致，
    // 不再自定义 30ms 时长与振幅。
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
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
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
