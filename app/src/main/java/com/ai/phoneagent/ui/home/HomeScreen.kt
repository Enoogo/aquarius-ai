package com.ai.phoneagent.ui.home

import android.content.Context
import android.os.Build
import com.ai.phoneagent.core.settings.Haptics
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ai.phoneagent.core.designsystem.theme.glass.AquariusLiquidTabs
import com.ai.phoneagent.core.designsystem.theme.glass.LocalAquariusGlassBackdrop
import com.ai.phoneagent.core.designsystem.theme.glass.NavBarHeight

import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.pager.HorizontalPager

import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.History
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Settings
import com.ai.phoneagent.core.designsystem.R as DesignR
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import com.ai.phoneagent.R
import kotlin.math.roundToInt
import com.ai.phoneagent.ui.debug.DebugRecomposeLogger
import com.ai.phoneagent.ui.drawer.ConversationDrawer
import com.ai.phoneagent.ui.drawer.DrawerConversationUiItem
import com.ai.phoneagent.ui.messages.CodeBlockPrefs
import com.ai.phoneagent.ui.messages.StreamingTranscriptMessageState
import com.ai.phoneagent.ui.messages.TranscriptEmptyHintCard
import com.ai.phoneagent.ui.messages.TranscriptMessageUi
import com.ai.phoneagent.ui.messages.conversationTranscriptItem
import com.ai.phoneagent.ui.messages.conversationTranscriptItems
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun HomeScreen(
    drawerState: DrawerState,
    drawerGesturesEnabled: Boolean,
    onOpenDrawer: () -> Unit,
    drawerSearchQuery: String,
    drawerItems: List<DrawerConversationUiItem>,
    drawerEmptyMessage: String,
    onDrawerSearchQueryChange: (String) -> Unit,
    onDrawerConversationClick: (Long) -> Unit,
    onDrawerConversationLongClick: (Long) -> Unit,
    onNewChatClick: () -> Unit = {},
    transcriptPaneContent: @Composable (topOverlayPadding: Dp, bottomOverlayPadding: Dp, spacingMd: Dp, spacingXxxs: Dp) -> Unit,
    inputBarContent: @Composable () -> Unit,
    contentAlpha: Float,
    contentScale: Float,
    onboardingContent: @Composable (() -> Unit)? = null,
    historyDialogContent: @Composable (() -> Unit)? = null,
    onDrawerClosed: () -> Unit = {},
    selectedPage: Int = 1,
    onPageSelected: (Int) -> Unit = {},
    settingsContent: @Composable (Boolean) -> Unit = {},
) {
    val spacingMd = dimensionResource(DesignR.dimen.aquarius_page_padding)
    val spacingSm = dimensionResource(DesignR.dimen.m3t_spacing_sm)
    val spacingXxxs = dimensionResource(DesignR.dimen.m3t_spacing_xxxs)

    val motionMs = integerResource(DesignR.integer.aquarius_page_motion_ms)
    val density = LocalDensity.current
    val hapticContext = LocalContext.current
    val pager = rememberPagerState(initialPage = selectedPage) { 3 }
    val defaultPageConnection = PagerDefaults.pageNestedScrollConnection(pager, Orientation.Horizontal)
    val horizontalPageConnection = remember(defaultPageConnection) {
        HorizontalPagerScrollConnection(defaultPageConnection)
    }
    val scope = rememberCoroutineScope()
    var navigationJob by remember { mutableStateOf<Job?>(null) }
    var lastReportedPage by remember { mutableIntStateOf(selectedPage) }
    // 飞行中的程序导航目的页：tween 过程中 pager.currentPage 会先穿过中间页签
    // （foundation 1.12：currentPage 表示“最接近对齐位的页”），它只是去重凭据；
    // pager 本身是唯一事实源，tween 结束后清空。
    var navigationTarget by remember { mutableIntStateOf(-1) }
    val navigate: (Int) -> Unit = { page ->
        if (navigationTarget != page) {
            navigationTarget = page
            navigationJob?.cancel()
            navigationJob = scope.launch {
                try {
                    pager.animateScrollToPage(page, animationSpec = tween(motionMs, easing = FastOutSlowInEasing))
                } finally {
                    if (navigationTarget == page) navigationTarget = -1
                }
            }
        }
    }
    LaunchedEffect(pager) {
        // 本页签保存期间，外层路由可能请求切换到其他页签。
        // 在把恢复的页码上报给 Activity 之前，先应用该选择。
        if (pager.currentPage != selectedPage) pager.scrollToPage(selectedPage)
        // 上报“已落定”页：tween 过程中 currentPage 会穿过主页，不能把中间值
        // 上报给 Activity（会污染持久化的页签，甚至拉起错误的恢复页）。
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { lastReportedPage = it; onPageSelected(it) }
    }
    LaunchedEffect(selectedPage) {
        // 外层路由请求切页：仅当 pager 未在向该页飞行（含已抵达）时才导航，防止回声。
        if (pager.settledPage != selectedPage && navigationTarget != selectedPage) navigate(selectedPage)
    }
    // 兼容旧的回调逻辑：过去需要等待抽屉关闭。
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) {
            navigate(0)
            drawerState.close()
        } else onDrawerClosed()
    }
    BackHandler(enabled = com.ai.phoneagent.navigation.homeBackAction(pager.settledPage) == com.ai.phoneagent.navigation.HomeBackAction.HOME) {
        navigate(1)
    }
    // 标签栏已移到底部：顶部空间归还内容，各页只保留状态栏高度作为起始留白。
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 底部标签栏占位高度（含上下内边距），各页内容据此上移，避免被遮挡。
    val bottomNavSpace = NavBarHeight + spacingSm * 2
    var bottomOverlayHeightPx by remember { mutableIntStateOf(0) }
    // M3 下不再有折射材质：backdrop 仅为兼容 AquariusLiquidTabs 的签名保留（空实现）。
    val navBackdrop = LocalAquariusGlassBackdrop.current
    // 页签高亮跟随导航意图（tween 进行中即高亮目标页签）；胶囊位置则贴 pager 进度。
    val navHighlight = if (navigationTarget >= 0) navigationTarget else pager.settledPage

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            pageNestedScrollConnection = horizontalPageConnection,
            userScrollEnabled = drawerGesturesEnabled,
            flingBehavior = PagerDefaults.flingBehavior(
                pager,
                // 对齐参考 DampedDragAnimation 的 spring(1f, 1000f)：临界阻尼、无回弹，
                // 与胶囊吸附同源，避免松手后页面和滑块两套手感。
                snapAnimationSpec = spring(
                    dampingRatio = 1f,
                    stiffness = 1000f,
                )
            ),
        ) { page ->
            when (page) {
                0 -> Box(Modifier.fillMaxSize().clipToBounds().navigationBarsPadding()
                    .padding(top = topInset + dimensionResource(DesignR.dimen.aquarius_home_hero_gap))
                    .padding(
                        start = dimensionResource(DesignR.dimen.aquarius_page_padding),
                        end = dimensionResource(DesignR.dimen.aquarius_page_padding),
                        bottom = bottomNavSpace + dimensionResource(DesignR.dimen.aquarius_home_hero_gap),
                    )) {
                    ConversationDrawer(
                        searchQuery = drawerSearchQuery, items = drawerItems, emptyMessage = drawerEmptyMessage,
                        onSearchQueryChange = onDrawerSearchQueryChange,
                        onConversationClick = { onDrawerConversationClick(it); navigate(1) },
                        onConversationLongClick = onDrawerConversationLongClick,
                        onSettingsClick = { navigate(2) }, showSettingsEntry = false,
                        onNewChatClick = { navigate(1); onNewChatClick() },
                    )
                }
                1 -> Box(Modifier.fillMaxSize().clipToBounds().navigationBarsPadding().imePadding().padding(bottom = bottomNavSpace)) {
                    val bottomFadeHeight = dimensionResource(R.dimen.m3t_home_bottom_fade_height)
                    val bottomOverlayHeight =
                        if (bottomOverlayHeightPx > 0) with(density) { bottomOverlayHeightPx.toDp() }
                        else bottomFadeHeight
                    Box(Modifier.fillMaxSize().clipToBounds()) {
                        transcriptPaneContent(topInset, bottomOverlayHeight, spacingMd, spacingXxxs)
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(bottomFadeHeight + bottomOverlayHeight)
                            .background(
                                // 越往下越深：输入栏是液态玻璃，必须由渐变承接住下方的
                                // 背景文字，否则滚动内容会透过玻璃与前景混在一起。
                                // stops 前移 + 中段加深：键盘/输入态时下半屏近乎不透明。
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Transparent,
                                        0.22f to MaterialTheme.colorScheme.background.copy(alpha = 0.32f),
                                        0.45f to MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                        0.68f to MaterialTheme.colorScheme.background.copy(alpha = 0.93f),
                                        1f to MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
                                    ),
                                ),
                            ),
                    )
                    HomeBottomOverlay(
                        inputBarContent = inputBarContent,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                if (bottomOverlayHeightPx != size.height) bottomOverlayHeightPx = size.height
                            },
                    )
                }
                // 标签页里的内容一律裁剪到本页范围内：任何一页内部的位移/缩放都不会
                // 溢出到相邻页（之前设置页里的自绘容器位移，曾在主页右边留下一条残影）。
                2 -> Box(Modifier.fillMaxSize().clipToBounds().padding(top = topInset).padding(bottom = bottomNavSpace)) {
                    settingsContent(pager.currentPage == 2)
                }
            }
        }
        // 底部主导航：常驻显示，M3 Navigation Bar 样式。三页内容已分别为其让位，
        // 顶部空间归还给各页面，不再有滚动/点击收起逻辑。
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = spacingMd, vertical = spacingSm),
        ) {
            AquariusLiquidTabs(
                selectedIndex = pager.settledPage,
                onSelect = { page -> hapticTick(hapticContext); navigate(page) },
                progress = { pager.currentPage + pager.currentPageOffsetFraction },
                pagerScrolling = { pager.isScrollInProgress && navigationTarget < 0 },
                backdrop = navBackdrop,
                modifier = Modifier.fillMaxWidth(),
                tabCount = 3,
                onEdgeHaptic = { hapticTick(hapticContext) },
            ) { index ->
                HomeNavTab(index = index, selected = navHighlight == index)
            }
        }
        onboardingContent?.invoke()
        historyDialogContent?.invoke()
    }
}

/** M3 Navigation Bar 单个标签内容：图标在上、文字在下的垂直排列。 */
@Composable
private fun HomeNavTab(index: Int, selected: Boolean) {
    val icon = when (index) {
        0 -> Lucide.History
        1 -> Lucide.House
        else -> Lucide.Settings
    }
    val label = stringResource(
        when (index) {
            0 -> R.string.ui_v2_history
            1 -> R.string.ui_v2_home
            else -> R.string.drawer_settings
        }
    )
    val colors = MaterialTheme.colorScheme
    // 选中态文字/图标用 onSecondaryContainer，与药丸 indicator 的 secondaryContainer 成对。
    val activeColor = colors.onSecondaryContainer
    val inactiveColor = colors.onSurfaceVariant
    Column(
        Modifier
            .fillMaxSize()
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(dimensionResource(DesignR.dimen.aquarius_nav_icon)),
            tint = if (selected) activeColor else inactiveColor,
        )
        Text(
            label,
            Modifier.padding(top = dimensionResource(DesignR.dimen.m3t_spacing_xxxs)),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = with(LocalDensity.current) {
                    dimensionResource(DesignR.dimen.aquarius_nav_text).toSp()
                },
            ),
            color = if (selected) activeColor else inactiveColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
fun HomeTranscriptPane(
    transcriptItems: ImmutableList<TranscriptMessageUi>,
    streamingTranscriptItem: StreamingTranscriptMessageState?,
    transcriptResetKey: Long,
    thinkingExpandedByDefault: Boolean,
    codeBlockPrefs: CodeBlockPrefs,
    onCopyMessage: (TranscriptMessageUi) -> Unit,
    onRetryMessage: (TranscriptMessageUi) -> Unit,
    onEditMessage: (TranscriptMessageUi) -> Unit,
    onAutomationAction: (TranscriptMessageUi) -> Unit,
    onEmptySuggestionClick: (String) -> Unit,
    scrollToBottomSignal: Long,
    topOverlayPadding: Dp,
    bottomOverlayPadding: Dp,
    spacingMd: Dp,
    spacingXxxs: Dp,
) {
    DebugRecomposeLogger(scope = "HomeTranscriptPane")
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    if (transcriptItems.isEmpty() && streamingTranscriptItem == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacingMd),
        ) {
            TranscriptEmptyHintCard(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(top = dimensionResource(DesignR.dimen.aquarius_home_hero_gap) + topOverlayPadding, bottom = bottomOverlayPadding),
                onSuggestionClick = onEmptySuggestionClick,
            )
        }
        return
    }

    val conversationId = remember(
        transcriptItems,
        streamingTranscriptItem,
        transcriptResetKey,
    ) {
        streamingTranscriptItem
            ?.conversationId
            ?.takeIf { it >= 0L }
            ?: transcriptItems.firstOrNull()?.conversationId
            ?: transcriptResetKey
    }
    val listKey = remember(conversationId, transcriptResetKey) {
        "$conversationId-$transcriptResetKey"
    }
    val listState = rememberLazyListState()
    val bottomListIndex = transcriptItems.size + if (streamingTranscriptItem != null) 1 else 0

    HomeTranscriptAutoFollowController(
        listState = listState,
        listKey = listKey,
        bottomListIndex = bottomListIndex,
        scrollToBottomSignal = scrollToBottomSignal,
        transcriptItems = transcriptItems,
        streamingTranscriptItem = streamingTranscriptItem,
        bottomOverlayPadding = bottomOverlayPadding,
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacingMd),
        contentPadding = PaddingValues(top = topOverlayPadding + spacingXxxs),
        state = listState,
    ) {
        conversationTranscriptItems(
            items = transcriptItems,
            onCopyMessage = onCopyMessage,
            onRetryMessage = onRetryMessage,
            onAutomationAction = onAutomationAction,
            thinkingExpandedByDefault = thinkingExpandedByDefault,
            onEditMessage = onEditMessage,
            codeBlockPrefs = codeBlockPrefs,
        )

        streamingTranscriptItem?.let { item ->
            conversationTranscriptItem(
                item = item,
                onCopyMessage = onCopyMessage,
                onRetryMessage = onRetryMessage,
                onAutomationAction = onAutomationAction,
                thinkingExpandedByDefault = thinkingExpandedByDefault,
                onEditMessage = onEditMessage,
                codeBlockPrefs = codeBlockPrefs,
            )
        }

        item(key = "bottom_overlay_spacer", contentType = "bottom_spacer") {
            Spacer(modifier = Modifier.height(bottomOverlayPadding))
        }
    }
}

@Composable
private fun HomeTranscriptAutoFollowController(
    listState: androidx.compose.foundation.lazy.LazyListState,
    listKey: String,
    bottomListIndex: Int,
    scrollToBottomSignal: Long,
    transcriptItems: ImmutableList<TranscriptMessageUi>,
    streamingTranscriptItem: StreamingTranscriptMessageState?,
    bottomOverlayPadding: Dp,
) {
    val streamingContentVersion = remember(
        transcriptItems.lastOrNull()?.id,
        transcriptItems.lastOrNull()?.body?.length,
        transcriptItems.lastOrNull()?.thinking?.length,
        streamingTranscriptItem?.bodyLayoutVersion,
        streamingTranscriptItem?.thinkingLayoutVersion,
        bottomOverlayPadding,
    ) {
        listOf(
            transcriptItems.lastOrNull()?.id,
            transcriptItems.lastOrNull()?.body?.length,
            transcriptItems.lastOrNull()?.thinking?.length,
            streamingTranscriptItem?.bodyLayoutVersion,
            streamingTranscriptItem?.thinkingLayoutVersion,
            bottomOverlayPadding.value,
        )
    }
    val isAtBottom by remember(listState) {
        derivedStateOf {
            !listState.canScrollForward
        }
    }
    var autoFollowBottom by remember(listKey) { mutableStateOf(true) }

    LaunchedEffect(listKey) {
        autoFollowBottom = true
        listState.scrollToItem(bottomListIndex)
    }

    LaunchedEffect(listState, listKey) {
        snapshotFlow { listState.isScrollInProgress to isAtBottom }
            .distinctUntilChanged()
            .collect { (isScrollInProgress, atBottom) ->
                if (isScrollInProgress) {
                    autoFollowBottom = atBottom
                }
            }
    }

    LaunchedEffect(scrollToBottomSignal, listKey, bottomListIndex) {
        if (scrollToBottomSignal > 0L) {
            autoFollowBottom = true
            listState.scrollToItem(bottomListIndex)
        }
    }

    LaunchedEffect(
        listKey,
        streamingContentVersion,
        autoFollowBottom,
        listState.isScrollInProgress,
        bottomListIndex,
    ) {
        if (autoFollowBottom && !listState.isScrollInProgress && listState.canScrollForward) {
            listState.scrollToItem(bottomListIndex)
        }
    }
}

@Composable
private fun HomeBottomOverlay(
    inputBarContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    DebugRecomposeLogger(scope = "HomeBottomOverlay")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = dimensionResource(DesignR.dimen.aquarius_composer_bottom_gap)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        inputBarContent()
    }
}

private fun hapticTick(context: Context) {
    // 统一走全局触感入口，设置里的“振动”开关对这里同样生效。
    Haptics.tap(context)
}
