package com.ai.phoneagent.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.ai.phoneagent.R
import com.ai.phoneagent.core.cache.ScreenshotArchive
import com.ai.phoneagent.core.settings.AppUiPrefs
import com.ai.phoneagent.core.settings.Haptics
import com.ai.phoneagent.ui.components.AquariusPageTopBar
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Images
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「设置 → 存储」。
 *
 * 这里是“AI 到底看到了什么”的唯一出口：每一次发给模型的截图都会存下来，
 * 可以按网格逐张查看、单张删除，也可以一键清理；还能切换存放位置——
 * 默认放在应用内部，也可以改成手机相册里的 `Pictures/AquariusAI`，
 * 或者用系统文件选择器指定任意文件夹（SAF 目录树 + 持久化 Uri 授权）。
 */
@Composable
fun StorageScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inner = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)
    val padding = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aquarius_page_padding)
    val gap = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aquarius_setting_gap)
    val cardShape = RoundedCornerShape(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aquarius_card_radius))

    var revision by remember { mutableIntStateOf(0) }
    var storageMode by remember { mutableStateOf(AppUiPrefs.screenshotStorageMode(context)) }
    var customLabel by remember { mutableStateOf<String?>(null) }
    var shots by remember { mutableStateOf<List<ScreenshotArchive.Shot>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var previewShot by remember { mutableStateOf<ScreenshotArchive.Shot?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // 系统文件选择器：选完立刻持久化读/写授权，之后后台任务也能直接写这个目录。
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                AppUiPrefs.setScreenshotStorageTreeUri(context, uri)
                AppUiPrefs.setScreenshotStorageMode(context, AppUiPrefs.SCREENSHOT_STORAGE_CUSTOM)
                storageMode = AppUiPrefs.SCREENSHOT_STORAGE_CUSTOM
                revision++
            }
        }

    LaunchedEffect(revision) {
        loading = true
        customLabel = withContext(Dispatchers.IO) { ScreenshotArchive.customFolderLabel(context) }
        shots = withContext(Dispatchers.IO) { ScreenshotArchive.list(context) }
        loading = false
    }

    val customFolderReady = ScreenshotArchive.hasCustomFolder(context)
    val resolvedCustomLabel = customLabel
    val customSubtitle =
        when {
            resolvedCustomLabel != null -> resolvedCustomLabel
            customFolderReady -> stringResource(R.string.storage_location_custom_missing)
            else -> stringResource(R.string.storage_location_custom_unset)
        }

    val totalText =
        remember(shots) {
            val bytes = ScreenshotArchive.totalBytes(shots)
            formatBytes(bytes)
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AquariusPageTopBar(stringResource(R.string.settings_section_storage), onNavigateBack) },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding(),
            contentPadding = PaddingValues(padding),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(Modifier.padding(inner)) {
                        Text(
                            stringResource(R.string.storage_location_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.storage_location_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                        )
                        StorageLocationRow(
                            title = stringResource(R.string.storage_location_app),
                            subtitle = stringResource(R.string.storage_location_app_desc),
                            selected = storageMode == AppUiPrefs.SCREENSHOT_STORAGE_APP,
                            onSelect = {
                                Haptics.tap(context)
                                AppUiPrefs.setScreenshotStorageMode(context, AppUiPrefs.SCREENSHOT_STORAGE_APP)
                                storageMode = AppUiPrefs.SCREENSHOT_STORAGE_APP
                                revision++
                            },
                        )
                        StorageLocationRow(
                            title = stringResource(R.string.storage_location_album),
                            subtitle =
                                stringResource(
                                    R.string.storage_location_album_desc,
                                    ScreenshotArchive.ALBUM_RELATIVE_PATH,
                                ),
                            selected = storageMode == AppUiPrefs.SCREENSHOT_STORAGE_ALBUM,
                            onSelect = {
                                Haptics.tap(context)
                                AppUiPrefs.setScreenshotStorageMode(context, AppUiPrefs.SCREENSHOT_STORAGE_ALBUM)
                                storageMode = AppUiPrefs.SCREENSHOT_STORAGE_ALBUM
                                revision++
                            },
                        )
                        StorageLocationRow(
                            title = stringResource(R.string.storage_location_custom),
                            subtitle = customSubtitle,
                            selected = storageMode == AppUiPrefs.SCREENSHOT_STORAGE_CUSTOM,
                            onSelect = {
                                Haptics.tap(context)
                                if (customFolderReady) {
                                    AppUiPrefs.setScreenshotStorageMode(context, AppUiPrefs.SCREENSHOT_STORAGE_CUSTOM)
                                    storageMode = AppUiPrefs.SCREENSHOT_STORAGE_CUSTOM
                                    revision++
                                } else {
                                    folderPicker.launch(null)
                                }
                            },
                        )
                        Text(
                            text = stringResource(R.string.storage_location_custom_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 40.dp, end = 4.dp),
                        )
                        TextButton(
                            onClick = {
                                Haptics.tap(context)
                                folderPicker.launch(null)
                            },
                        ) {
                            Text(
                                stringResource(
                                    if (customFolderReady) {
                                        R.string.storage_location_custom_change
                                    } else {
                                        R.string.storage_location_custom_pick
                                    },
                                ),
                            )
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(Modifier.padding(inner)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Lucide.Images,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                stringResource(R.string.storage_summary, shots.size, totalText),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                            )
                            if (loading) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        }
                        Text(
                            stringResource(R.string.storage_summary_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        FilledTonalButton(
                            onClick = {
                                Haptics.tap(context)
                                showClearConfirm = true
                            },
                            enabled = shots.isNotEmpty() && !loading,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Icon(Lucide.Trash2, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                stringResource(R.string.storage_clear_all),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }

            if (!loading && shots.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.storage_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(shots, key = { "${it.inAlbum}-${it.id}" }) { shot ->
                StorageThumb(
                    shot = shot,
                    onClick = {
                        Haptics.tap(context)
                        previewShot = shot
                    },
                )
            }
        }
    }

    previewShot?.let { shot ->
        ScreenshotViewerDialog(
            shot = shot,
            onDismiss = { previewShot = null },
            onDelete = {
                scope.launch {
                    withContext(Dispatchers.IO) { ScreenshotArchive.delete(context, shot) }
                    previewShot = null
                    revision++
                }
            },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.storage_clear_confirm_title)) },
            text = { Text(stringResource(R.string.storage_clear_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        scope.launch {
                            withContext(Dispatchers.IO) { ScreenshotArchive.clear(context) }
                            revision++
                        }
                    },
                ) {
                    Text(stringResource(R.string.storage_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun StorageLocationRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onSelect)
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageThumb(shot: ScreenshotArchive.Shot, onClick: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.62f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = ScreenshotArchive.uriFor(context, shot),
            contentDescription = shot.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = if (shot.step > 0) "S${shot.step}" else formatTime(shot.savedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/** 全屏看图：设置 → 存储 与 任务日志 共用同一个看图弹窗。 */
@Composable
fun ScreenshotViewerDialog(
    shot: ScreenshotArchive.Shot,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.94f),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text =
                                if (shot.step > 0) {
                                    stringResource(R.string.storage_preview_step, shot.step)
                                } else {
                                    formatTime(shot.savedAt)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${formatTime(shot.savedAt)} · ${formatBytes(shot.sizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Lucide.Trash2,
                                contentDescription = stringResource(R.string.storage_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                }
                AsyncImage(
                    model = ScreenshotArchive.uriFor(context, shot),
                    contentDescription = shot.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0L) return ""
    val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    return fmt.format(Date(millis))
}

private fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
