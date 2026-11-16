package com.ai.phoneagent.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import com.ai.phoneagent.R
import com.ai.phoneagent.core.designsystem.R as DesignR

/**
 * 页面顶部栏（Material Design 3）。
 *
 * 返回键使用 M3 标准返回图标（`ic_arrow_back_24`，Material Symbols arrow_back，
 * 支持 RTL 镜像）+ [IconButton]：48dp 触控区、圆形状态层、标准无障碍语义。
 * 旧版是液态玻璃圆钮 + Lucide 线性箭头，与 M3 导航图标体系不一致，这里整体替换。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AquariusPageTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    val density = LocalDensity.current
    TopAppBar(
        title = {
            Text(
                title,
                modifier = Modifier.padding(start = dimensionResource(DesignR.dimen.aquarius_toolbar_title_gap)),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = with(density) { dimensionResource(DesignR.dimen.aquarius_type_title).toSp() },
                ),
                fontWeight = FontWeight.SemiBold,
            )
        },
        expandedHeight = dimensionResource(DesignR.dimen.aquarius_toolbar_height),
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back_24),
                        contentDescription = stringResource(R.string.ui_v2_back),
                        modifier = Modifier.size(dimensionResource(DesignR.dimen.aquarius_back_icon)),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}
