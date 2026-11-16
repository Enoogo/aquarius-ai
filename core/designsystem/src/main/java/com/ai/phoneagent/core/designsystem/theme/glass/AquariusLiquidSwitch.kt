package com.ai.phoneagent.core.designsystem.theme.glass

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kyant.backdrop.Backdrop

/**
 * 设置项开关（Material Design 3）。
 *
 * 旧实现是「液态玻璃滑钮 + 轨道折射」，全站改 M3 后直接使用 M3 [Switch]：
 * 取得标准轨道/滑块尺寸、状态层、弹簧动画与无障碍语义，行为与系统设置一致。
 * 参数保持与原签名一致，`backdrop` 仅为兼容保留。
 */
@Composable
fun AquariusLiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop = LocalAquariusGlassBackdrop.current,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    )
}
