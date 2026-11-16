package com.ai.phoneagent.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.ai.phoneagent.core.designsystem.theme.glass.LocalAquariusGlassBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 背景样式。
 *
 * @property dark 是否为深色模式。
 * @property amoled 是否为 AMOLED 纯黑模式。
 * @property defaultAccent 是否为默认强调色。
 */
data class AquariusBackdropStyle(val dark: Boolean, val amoled: Boolean, val defaultAccent: Boolean)

/** 当前背景样式，由 [AquariusMaterialTheme] 向下提供。 */
val LocalAquariusBackdrop = staticCompositionLocalOf { AquariusBackdropStyle(false, false, true) }

/**
 * 背景宿主：Material Design 3 的纯色 surface 背景。
 *
 * 旧版在这里绘制多色光晕供「液态玻璃」做折射取样；改用 M3 后背景回归
 * `colorScheme.background` 单色层，毛玻璃/折射材质同步退场。
 * 仍保留 backdrop 录制层：少数遗留调用点（如个别 drawBackdrop 修饰符）需要它才不崩溃。
 */
@Composable
fun AquariusBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val backdrop = rememberLayerBackdrop()
    val background = MaterialTheme.colorScheme.background

    CompositionLocalProvider(LocalAquariusGlassBackdrop provides backdrop) {
        Box(modifier.fillMaxSize().background(background).layerBackdrop(backdrop)) {
            content()
        }
    }
}
