package com.ai.phoneagent.core.designsystem.theme.glass

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

/**
 * 顶部页签导航（Material Design 3）。
 *
 * 旧实现是「液态玻璃胶囊 + 可拖动滑块 + 背景折射」：一条 3 段胶囊，
 * 滑块跟随 pager 逐帧位移，按压时透镜折射。改 M3 后使用 [PrimaryTabRow]：
 * - 标准 M3 主色指示条（indicator）由框架绘制，不透明、无背景采样；
 * - 页签等宽，点击切换，选中态由 `MaterialTheme` 的 primary/onSurface 决定；
 * - pager 左右滑动仍然可用（由调用方 pager 处理），只是页签条本身不再可拖。
 *
 * API 与原实现保持一致（`progress` / `pagerScrolling` / `backdrop` / `onEdgeHaptic`
 * 仅为兼容保留），因此调用点无需改动。
 */
@Composable
fun AquariusLiquidTabs(
    selectedIndex: Int,
    onSelect: (index: Int) -> Unit,
    progress: () -> Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tabCount: Int = 3,
    pagerScrolling: () -> Boolean = { false },
    onEdgeHaptic: () -> Unit = {},
    content: @Composable (index: Int) -> Unit,
) {
    PrimaryTabRow(
        selectedTabIndex = selectedIndex.coerceIn(0, (tabCount - 1).coerceAtLeast(0)),
        modifier = modifier.fillMaxWidth().height(TabRowHeight).testTag(AquariusTabRowTestTag),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = {},
    ) {
        repeat(tabCount) { index ->
            Tab(
                selected = index == selectedIndex,
                onClick = { if (index != selectedIndex) onSelect(index) },
                modifier = Modifier.height(TabRowHeight),
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                content(index)
            }
        }
    }
}

/** 页签条高度：与设计令牌 `aquarius_nav_height` 对齐（Material 3 主色指示条 + 图标文字）。 */
private val TabRowHeight = 64.dp

/** UI 测试钩子：页签条根节点。 */
const val AquariusTabRowTestTag = "aquarius_tab_row"

/** 兼容占位：旧实现用于逐帧读取滑块状态，M3 版不再需要。 */
class AquariusLiquidTabsStateReader internal constructor() {
    var sliderPosition: Float = 0f
        internal set
    var retargetVelocity: Float = 0f
        internal set
}

/** 兼容占位：M3 版不读取该 Local。 */
val LocalAquariusLiquidTabsStateReader = androidx.compose.runtime.staticCompositionLocalOf<AquariusLiquidTabsStateReader?> { null }

/** 兼容占位：旧实现的页签缩放 Local。 */
val LocalAquariusTabScale = androidx.compose.runtime.staticCompositionLocalOf { { 1f } }
