package com.ai.phoneagent.core.designsystem.theme.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.dimensionResource
import com.ai.phoneagent.core.designsystem.R
import com.ai.phoneagent.core.designsystem.theme.aquariusActionStyle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.isRuntimeShaderSupported

/** 交互控件独立材质；不改变 Card、Chip、Bubble 以及静态卡片的共享参数。 */
@Composable
internal fun Modifier.aquariusActionGlass(
    shape: Shape,
    press: AquariusPressState,
    enabled: Boolean,
    backdrop: Backdrop,
    surfaceColor: Color,
    selected: Boolean,
    observeGestures: Boolean = true,
    tier: AquariusGlassTier = AquariusGlassTier.Pill,
): Modifier {
    val style = aquariusActionStyle()
    val spec = aquariusGlassSpec(tier)
    val blurRadius = spec.blur
    val lensHeight = if (tier == AquariusGlassTier.Pill) {
        dimensionResource(R.dimen.aquarius_liquid_lens_pill_height)
    } else spec.lensHeight
    val lensAmount = if (tier == AquariusGlassTier.Pill) {
        dimensionResource(R.dimen.aquarius_liquid_lens_pill_amount)
    } else spec.lensAmount
    val rim = dimensionResource(R.dimen.aquarius_hairline)
    val grow = dimensionResource(R.dimen.aquarius_liquid_press_grow)
    val supportsLens = isRuntimeShaderSupported()
    val base = surfaceColor.takeUnless { it == Color.Unspecified } ?: style.glassSurface
    val transformed = graphicsLayer {
        this.shape = shape
        clip = true
        val p = if (enabled) press.pressProgress else 0f
        val scale = 1f + grow.toPx() / size.height.coerceAtLeast(1f) * p
        scaleX = scale
        scaleY = scale
    }
    val surface = if (isRenderEffectSupported()) {
        transformed.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blurRadius.toPx())
                if (supportsLens && enabled) {
                    val p = press.pressProgress
                    lens(lensHeight.toPx() * p, lensAmount.toPx() * p)
                }
            },
            highlight = null,
            shadow = null,
            onDrawSurface = {
                val path = aquariusShapePath(shape)
                clipPath(path) {
                    drawPath(path, base)
                    if (selected) drawPath(path, style.selectedTint)
                    drawPath(path, style.glassBorder, style = Stroke(rim.toPx() * 2))
                }
            },
        )
    } else {
        // API 30 同样保持有底色的控件，不创建无效的模糊或折射链。
        transformed.drawWithCache {
            val path = aquariusOutlinePath(shape.createOutline(size, layoutDirection, this))
            onDrawBehind {
                drawPath(path, base)
                if (selected) drawPath(path, style.selectedTint)
                clipPath(path) { drawPath(path, style.glassBorder, style = Stroke(rim.toPx() * 2)) }
            }
        }
    }
    return surface.clip(shape)
        .then(if (enabled) press.actionHighlightModifier(shape, style.glassHighlight) else Modifier)
        .then(if (enabled && observeGestures) press.gestureModifier else Modifier)
}

/** 实色主操作只绘制渐变与有界高光，不再叠加实时模糊。 */
@Composable
fun Modifier.aquariusPrimaryActionSurface(shape: Shape, press: AquariusPressState, brush: Brush): Modifier {
    val style = aquariusActionStyle()
    return clip(shape).drawWithCache {
        val path = aquariusOutlinePath(shape.createOutline(size, layoutDirection, this))
        onDrawBehind { drawPath(path, brush) }
    }.then(press.actionHighlightModifier(shape, style.glassHighlight.copy(alpha = style.pressHighlightAlpha)))
        .then(press.gestureModifier)
}
