package com.ai.phoneagent.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ai.phoneagent.R
import com.ai.phoneagent.core.cache.ScreenshotArchive
import com.ai.phoneagent.core.settings.Haptics
import com.ai.phoneagent.ui.settings.ScreenshotViewerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 任务日志里的截图带。
 *
 * 「应用截图发送给 AI 的图片，要全部能在日志里面查看」——这里把本次任务（runId）
 * 每一步发给模型的画面按步骤顺序罗列出来，点开即为原图。
 */
@Composable
fun TaskScreenshotStrip(
    runId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var shots by remember(runId) { mutableStateOf<List<ScreenshotArchive.Shot>>(emptyList()) }
    var preview by remember(runId) { mutableStateOf<ScreenshotArchive.Shot?>(null) }

    LaunchedEffect(runId) {
        if (runId.isBlank()) return@LaunchedEffect
        shots = withContext(Dispatchers.IO) { ScreenshotArchive.listForRun(context, runId) }
    }

    if (shots.isEmpty()) return

    Column(modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text = stringResource(R.string.task_log_shots_title, shots.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(end = 8.dp),
        ) {
            items(shots, key = { "${it.inAlbum}-${it.id}" }) { shot ->
                Box(
                    Modifier
                        .width(84.dp)
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            Haptics.tap(context)
                            preview = shot
                        },
                ) {
                    AsyncImage(
                        model = ScreenshotArchive.uriFor(context, shot),
                        contentDescription = shot.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (shot.step > 0) {
                        Text(
                            text = "S${shot.step}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(3.dp)
                                    .background(
                                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 3.dp),
                        )
                    }
                }
            }
        }
    }

    preview?.let { shot ->
        ScreenshotViewerDialog(shot = shot, onDismiss = { preview = null })
    }
}
