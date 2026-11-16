package com.ai.phoneagent.core.designsystem.theme.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 胶囊按钮（Material Design 3）。
 *
 * 旧实现是带模糊/折射的液态玻璃按钮；改 M3 后用 [FilledTonalButton]：
 * 容器色 `secondaryContainer`、标准状态层、默认最小高度 40dp。
 * `tier` / `surfaceColor` / `backdrop` 语义已失效，仅保留签名兼容。
 */
@Composable
fun AquariusLiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    tier: AquariusGlassTier = AquariusGlassTier.Pill,
    surfaceColor: Color = Color.Unspecified,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    onClickLabel: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = contentColor,
        ),
        contentPadding = contentPadding,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

/** 圆形图标按钮（M3 [FilledTonalIconButton]）。 */
@Composable
fun AquariusLiquidCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    touchSize: Dp = maxOf(size, 48.dp),
    enabled: Boolean = true,
    tier: AquariusGlassTier = AquariusGlassTier.Pill,
    surfaceColor: Color = Color.Unspecified,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.size(touchSize), contentAlignment = Alignment.Center) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            modifier = Modifier.size(size),
            colors = IconButtonDefaults.filledTonalIconButtonColors(contentColor = contentColor),
        ) {
            // M3 IconButton 的内容是普通 lambda，这里补一层 Box 以兼容原 BoxScope 签名。
            Box(contentAlignment = Alignment.Center) { content() }
        }
    }
}
