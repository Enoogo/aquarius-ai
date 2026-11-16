package com.ai.phoneagent.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.phoneagent.R
import com.ai.phoneagent.core.settings.AppUiPrefs
import com.ai.phoneagent.ui.components.AquariusPageTopBar
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Smartphone

/**
 * 「设置 → 悬浮窗设置」独立页面。
 *
 * 设置首页只保留一个入口行（默认折叠，点击才进入本页），避免一长串
 * 数值输入框把其它设置项挤到屏幕外。
 *
 * 所有数值型偏好都不设上下限：用户填多少就按多少生效，只在读取时对非法值回退默认。
 */
@Composable
fun OverlaySettingsScreen(
    onNavigateBack: () -> Unit,
    overlayVisible: Boolean,
    onOverlayToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    // revision 只用来在写入偏好后触发重组，值本身始终从 AppUiPrefs 读。
    var revision by remember { mutableIntStateOf(0) }
    val inner = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)
    val cardShape = RoundedCornerShape(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aquarius_card_radius))

    val cornerPx = remember(revision) { AppUiPrefs.cornerRadiusPx(context) }
    val particleOn = remember(revision) { AppUiPrefs.particleEnabled(context) }
    val bodySp = remember(revision) { AppUiPrefs.textSizeSp(context) }
    val stopSp = remember(revision) { AppUiPrefs.stopTextSizeSp(context) }
    val hideSp = remember(revision) { AppUiPrefs.hideTextSizeSp(context) }
    val plusSp = remember(revision) { AppUiPrefs.plusTextSizeSp(context) }

    fun commit(block: () -> Unit) {
        block()
        revision++
        // 已显示的悬浮窗立即重刷字号/颗粒，不必等下次任务。
        com.ai.phoneagent.core.overlay.OverlayTaskBridge.onAppearanceChanged?.invoke()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AquariusPageTopBar(stringResource(R.string.settings_section_overlay), onNavigateBack) },
    ) { innerPadding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aquarius_page_padding)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aquarius_setting_gap)),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(Modifier.padding(inner)) {
                        Text(
                            stringResource(R.string.settings_section_overlay_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OverlaySwitchRow(
                            title = stringResource(R.string.settings_overlay_enable_title),
                            subtitle = stringResource(
                                if (overlayVisible) R.string.settings_overlay_enable_on
                                else R.string.settings_overlay_enable_off
                            ),
                            checked = overlayVisible,
                            onCheckedChange = onOverlayToggle,
                            icon = Lucide.Smartphone,
                        )
                        OverlaySwitchRow(
                            title = stringResource(R.string.settings_overlay_particle_title),
                            subtitle = stringResource(
                                if (particleOn) R.string.settings_overlay_particle_on
                                else R.string.settings_overlay_particle_off
                            ),
                            checked = particleOn,
                            onCheckedChange = { enabled -> commit { AppUiPrefs.setParticleEnabled(context, enabled) } },
                            icon = Lucide.Sparkles,
                        )
                        NumericSettingRow(
                            title = stringResource(R.string.settings_overlay_corner_title),
                            subtitle = stringResource(R.string.settings_overlay_corner_subtitle),
                            unit = stringResource(R.string.settings_overlay_corner_unit),
                            value = cornerPx.toFloat(),
                            allowDecimal = false,
                            onCommit = { value -> commit { AppUiPrefs.setCornerRadiusPx(context, value.toInt()) } },
                            onReset = {
                                focusManager.clearFocus()
                                commit { AppUiPrefs.setCornerRadiusPx(context, AppUiPrefs.defaultCornerRadiusPx(context)) }
                            },
                        )
                        NumericSettingRow(
                            title = stringResource(R.string.settings_overlay_text_size_title),
                            subtitle = stringResource(R.string.settings_overlay_text_size_subtitle),
                            unit = "sp",
                            value = bodySp,
                            allowDecimal = true,
                            onCommit = { value -> commit { AppUiPrefs.setTextSizeSp(context, value) } },
                            onReset = {
                                focusManager.clearFocus()
                                commit { AppUiPrefs.setTextSizeSp(context, AppUiPrefs.DEFAULT_TEXT_SIZE_SP) }
                            },
                        )
                        NumericSettingRow(
                            title = stringResource(R.string.settings_overlay_stop_size_title),
                            subtitle = stringResource(R.string.settings_overlay_size_subtitle),
                            unit = "sp",
                            value = stopSp,
                            allowDecimal = true,
                            onCommit = { value -> commit { AppUiPrefs.setStopTextSizeSp(context, value) } },
                            onReset = {
                                focusManager.clearFocus()
                                commit { AppUiPrefs.setStopTextSizeSp(context, AppUiPrefs.DEFAULT_STOP_SIZE_SP) }
                            },
                        )
                        NumericSettingRow(
                            title = stringResource(R.string.settings_overlay_hide_size_title),
                            subtitle = stringResource(R.string.settings_overlay_size_subtitle),
                            unit = "sp",
                            value = hideSp,
                            allowDecimal = true,
                            onCommit = { value -> commit { AppUiPrefs.setHideTextSizeSp(context, value) } },
                            onReset = {
                                focusManager.clearFocus()
                                commit { AppUiPrefs.setHideTextSizeSp(context, AppUiPrefs.DEFAULT_HIDE_SIZE_SP) }
                            },
                        )
                        NumericSettingRow(
                            title = stringResource(R.string.settings_overlay_plus_size_title),
                            subtitle = stringResource(R.string.settings_overlay_size_subtitle),
                            unit = "sp",
                            value = plusSp,
                            allowDecimal = true,
                            onCommit = { value -> commit { AppUiPrefs.setPlusTextSizeSp(context, value) } },
                            onReset = {
                                focusManager.clearFocus()
                                commit { AppUiPrefs.setPlusTextSizeSp(context, AppUiPrefs.DEFAULT_PLUS_SIZE_SP) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlaySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val gap = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aquarius_home_icon_gap)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        androidx.compose.material3.Icon(
            icon,
            null,
            Modifier.width(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aquarius_composer_icon)),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 把 8.5f 显示成 “8.5”，10f 显示成 “10”。 */
private fun formatNumeric(value: Float): String {
    if (!value.isFinite()) return ""
    return if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
}

/**
 * 通用数值输入行：标题 + 说明 + 输入框 + 单位 + 恢复默认。
 *
 * 不做范围校验，输入期间也不回写，避免「想输 150 先敲出 1」被归一化打断；
 * 失焦或回车时才提交。
 */
@Composable
private fun NumericSettingRow(
    title: String,
    subtitle: String,
    unit: String,
    value: Float,
    allowDecimal: Boolean,
    onCommit: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var text by remember(value) { mutableStateOf(formatNumeric(value)) }
    val fieldShape = RoundedCornerShape(10.dp)

    fun commitValue(raw: String) {
        val parsed = raw.toFloatOrNull()
        if (parsed != null && parsed >= 0f && parsed.isFinite()) {
            onCommit(parsed)
        } else {
            text = formatNumeric(value)
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    val filtered =
                        if (allowDecimal) {
                            val cleaned = raw.filter { it.isDigit() || it == '.' }.take(8)
                            val firstDot = cleaned.indexOf('.')
                            if (firstDot >= 0) {
                                cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "")
                            } else {
                                cleaned
                            }
                        } else {
                            raw.filter { it.isDigit() }.take(8)
                        }
                    text = filtered
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    commitValue(text)
                    focusManager.clearFocus()
                }),
                modifier = Modifier
                    .width(72.dp)
                    .onFocusChanged { state -> if (!state.isFocused) commitValue(text) },
                decorationBox = { innerTextField ->
                    Box(
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, fieldShape)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) { innerTextField() }
                },
            )
            Text(
                " " + unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.settings_overlay_corner_reset),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp).clickable { onReset() },
        )
    }
}
