package com.ai.phoneagent.core.designsystem.theme.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

/**
 * 底部主导航（Material Design 3 Navigation Bar 视觉规范）。
 *
 * 采用 M3 NavigationBar 的标志性元素：
 * - 圆角容器（`surfaceContainer` 底色 + 大圆角）；
 * - 一个药丸形（pill）active indicator（`secondaryContainer` 底色）在选中项之间**实时滑动**；
 * - 选中内容色 `onSecondaryContainer`，未选中 `onSurfaceVariant`。
 *
 * 指示器位置直接取 [progress]（pager 的当前页码 + 偏移），不追自己的动画：
 * - 点击标签 → pager 立即开始滚动，胶囊同帧跟上，没有“先按下一层阴影、再滑过去”的两段式；
 * - 手指拖动页面 → 胶囊跟着手指走，松手吸附与页面同一套 spring。
 *
 * 标签项本身不带 ripple/state layer：胶囊的移动就是唯一的点击反馈，
 * 再叠一层按压阴影会显得迟钝（而且那层阴影会先亮后灭，与胶囊错位）。
 */
@Composable
fun AquariusLiquidTabs(
    selectedIndex: Int,
    onSelect: (index: Int) -> Unit,
    progress: () -> Float,
    @Suppress("UNUSED_PARAMETER") backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tabCount: Int = 3,
    @Suppress("UNUSED_PARAMETER") pagerScrolling: () -> Boolean = { false },
    @Suppress("UNUSED_PARAMETER") onEdgeHaptic: () -> Unit = {},
    content: @Composable (index: Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val safeSelected = selectedIndex.coerceIn(0, (tabCount - 1).coerceAtLeast(0))
    val lastIndex = (tabCount - 1).coerceAtLeast(0).toFloat()

    var containerWidth by remember { mutableIntStateOf(0) }
    val paddingPx = with(density) { NavItemPadding.toPx() }
    val spacingPx = with(density) { NavItemSpacing.toPx() }
    val itemWidthPx = if (tabCount > 0 && containerWidth > 0) {
        ((containerWidth - paddingPx * 2 - spacingPx * (tabCount - 1)) / tabCount).coerceAtLeast(0f)
    } else 0f
    val indicatorWidth = with(density) { itemWidthPx.toDp() }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(NavBarHeight)
            .clip(NavContainerShape)
            .background(colors.surfaceContainer)
            .onSizeChanged { containerWidth = it.width }
            .testTag(AquariusTabRowTestTag),
    ) {
        // 实时胶囊：绝对定位到“内边距 + 进度 ×（item 宽 + 间距）”。
        // 在 offset lambda（布局阶段）里读 progress，逐帧只做布局、不触发整块重组。
        if (itemWidthPx > 0f) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        val p = progress().coerceIn(0f, lastIndex)
                        val x = paddingPx + p * (itemWidthPx + spacingPx)
                        IntOffset(x.roundToInt(), 0)
                    }
                    .width(indicatorWidth)
                    .height(IndicatorHeight)
                    .clip(IndicatorShape)
                    .background(colors.secondaryContainer),
            )
        }

        // 标签项铺在胶囊之上（透明容器，只有点击与内容）。
        Row(
            Modifier
                .matchParentSize()
                .padding(horizontal = NavItemPadding),
            horizontalArrangement = Arrangement.spacedBy(NavItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(tabCount) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(IndicatorHeight)
                        .clip(IndicatorShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { if (index != safeSelected) onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.widthIn(max = IndicatorContentMaxWidth)) {
                        content(index)
                    }
                }
            }
        }
    }
}

/** 容器高度：M3 Navigation Bar item 高 56dp，加上下内边距取 72dp。对外公开，供页面为底部导航让位。 */
val NavBarHeight = 72.dp

/** 容器圆角：M3 Navigation Bar 容器的连续圆角，取 28dp。 */
private val NavContainerShape = RoundedCornerShape(28.dp)

/** 药丸形 active indicator：完全圆角。 */
private val IndicatorShape = RoundedCornerShape(50)

/** 药丸高度：容纳垂直排列的图标 + 文字，取 56dp（M3 Navigation Bar item 高度）。 */
private val IndicatorHeight = 56.dp

private val NavItemPadding = 8.dp
private val NavItemSpacing = 4.dp
private val IndicatorContentMaxWidth = 120.dp

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
