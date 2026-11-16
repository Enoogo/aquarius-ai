package com.ai.phoneagent.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.dimensionResource
import com.ai.phoneagent.core.designsystem.R

/**
 * Material Design 3 表面：填充容器色 + 1dp 描边。
 *
 * 旧实现是「带边缘反光的透明玻璃」（drawWithCache 画渐变镜面 + 高光描边），
 * 全站改 M3 后改为标准 M3 填充面：
 * - 默认 `surfaceContainer`（浅色 #F0F0F3 / 深色 #252528），与 `background` 形成层级差；
 * - 选中态叠加 `secondaryContainer` 着色，语义与 M3 可选列表一致；
 * - `surfaceColor` 仍可覆盖填充色（聊天气泡等需要自己指定角色的地方）。
 *
 * 调用点遍布全站（卡片、输入框、气泡、按钮），因此这里保持 API 完全不变。
 */
@Composable
fun Modifier.aquariusGlass(
    shape: Shape,
    selected: Boolean = false,
    surfaceColor: Color? = null,
    tint: Color = Color.Transparent,
): Modifier {
    val colors = MaterialTheme.colorScheme
    val fill = surfaceColor ?: colors.surfaceContainer
    val stroke = colors.outlineVariant
    var base = this.clip(shape).background(fill)
    if (tint.alpha > 0f) base = base.background(tint)
    if (selected) base = base.background(colors.secondaryContainer.copy(alpha = .55f))
    return base.border(dimensionResource(R.dimen.aquarius_hairline), stroke, shape)
}

/** 与 [aquariusGlass] 同源的卡片内边距，供需要手动对齐的调用点复用。 */
@Composable
fun aquariusSurfacePadding(): Modifier = Modifier.padding(dimensionResource(R.dimen.m3t_spacing_md))
