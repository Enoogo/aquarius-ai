package com.ai.phoneagent.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import com.ai.phoneagent.core.designsystem.R
import com.ai.phoneagent.core.designsystem.theme.glass.AquariusLiquidButton

/** M3 填充卡片容器：统一圆角、内边距与纵向间距。 */
@Composable
fun AquariusGlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(dimensionResource(R.dimen.aquarius_card_radius))
    Surface(
        modifier = modifier.aquariusGlass(shape),
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.padding(dimensionResource(R.dimen.aquarius_setting_padding)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.m3t_spacing_sm)),
            content = content,
        )
    }
}

/** 分隔线：M3 outlineVariant 细线。 */
@Composable
fun AquariusSoftHairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(dimensionResource(R.dimen.aquarius_hairline))
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** 按钮（M3 填充色调按钮）。 */
@Composable
fun AquariusGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    content: @Composable RowScope.() -> Unit,
) {
    AquariusLiquidButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(dimensionResource(R.dimen.aquarius_action_radius)),
        contentColor = contentColor,
        content = content,
    )
}

/** 图标按钮（M3 [IconButton]）。 */
@Composable
fun AquariusGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(dimensionResource(R.dimen.aquarius_touch_target)),
        enabled = enabled,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
