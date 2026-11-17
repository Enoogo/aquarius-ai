package com.ai.phoneagent.ui.settings

import com.ai.phoneagent.ui.components.AquariusPageTopBar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.ai.phoneagent.R
import com.ai.phoneagent.core.designsystem.theme.AquariusGlassButton
import com.ai.phoneagent.core.designsystem.theme.AquariusGlassCard
import com.ai.phoneagent.viewmodel.AutomationViewModel
import com.ai.phoneagent.core.designsystem.theme.AquariusSettingsSwitchItem
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Smartphone

/**
 * 自动化偏好设置页：任务状态、执行模式、自动确认开关、无障碍状态，以及自动化面板入口。
 *
 * 布局顺序刻意固定为「开关 → 无障碍 → 打开自动化面板」：
 * 无障碍是执行自动化的前置条件，必须排在“进面板跑任务”之前。
 * 原来在设置首页“自动化”卡片里作为第三行的无障碍，之前只显示一行摘要、
 * 点进去也只是跳走，现在把它的完整状态与开启入口都放在这里。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutomationPreferencesScreen(
    onBack: () -> Unit,
    onOpenControl: () -> Unit,
    onOpenPermissionGuide: () -> Unit,
    automationViewModel: AutomationViewModel,
) {
    val vm = automationViewModel
    val task by vm.taskSnapshot.collectAsState()
    // 无任务或任务已结束时才允许修改配置
    val editable = task == null || task?.status?.isTerminal == true
    val spacing = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aquarius_page_padding)
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    val iconSize = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aquarius_suggestion_icon)
    LaunchedEffect(Unit) { vm.onRefreshStatus() }
    Scaffold(containerColor = Color.Transparent, topBar = {
        AquariusPageTopBar(stringResource(R.string.settings_entry_automation_title), onBack)
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(spacing), verticalArrangement = Arrangement.spacedBy(spacing)) {
            item { Text(vm.statusSummary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { AquariusSettingsSwitchItem(headlineText = stringResource(R.string.ui_v2_background_execution), checked = vm.isBackgroundMode, enabled = editable, onCheckedChange = vm::onExecutionModeChange) }
            item { AquariusSettingsSwitchItem(headlineText = stringResource(R.string.ui_v2_shizuku_execution), checked = vm.useShizukuInteraction, enabled = editable, onCheckedChange = vm::onShizukuModeChange) }
            item { AquariusSettingsSwitchItem(headlineText = stringResource(R.string.ui_v2_auto_confirmation), checked = vm.autoApprove, enabled = editable, onCheckedChange = vm::onAutoApproveChange) }
            item { Text(stringResource(R.string.ui_v2_auto_confirm_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            // 无障碍：原先只作为设置首页卡片里的一行摘要，现在把完整状态与开启入口放在这里。
            item {
                AquariusGlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacingSm)) {
                        Icon(Lucide.Smartphone, null, Modifier.size(iconSize), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = stringResource(R.string.automation_status_accessibility_short_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = vm.accessibilityStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacingSm)) {
                        // 走应用内的权限引导页（无障碍 + 悬浮窗 + Shizuku 一键授权），
                        // 比直接跳系统设置更容易一次配好。
                        AquariusGlassButton(onClick = onOpenPermissionGuide) {
                            Text(stringResource(R.string.automation_open_accessibility))
                        }
                        if (vm.showShizukuAuthorize) {
                            AquariusGlassButton(onClick = { vm.authorizeShizukuAndAccessibility() }) {
                                Text(stringResource(R.string.automation_one_tap_shizuku_authorize))
                            }
                        }
                    }
                }
            }
            // 面板入口：文案沿用“打开自动化控制中心”，点进去就是完整的自动化界面。
            item { Button(onClick = onOpenControl, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_v2_open_control)) } }
        }
    }
}
