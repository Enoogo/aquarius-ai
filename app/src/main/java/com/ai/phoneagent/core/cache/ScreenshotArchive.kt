/*
 * Aquarius AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.ai.phoneagent.core.cache

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Base64
import com.ai.phoneagent.core.settings.AppUiPrefs
import java.io.File
import java.io.InputStream

/**
 * 截图存档。
 *
 * 每一次“发给模型的截图”都会落一份原图，供两个地方消费：
 * 1. 设置 → 存储：网格浏览全部截图、单张删除、一键清理；
 * 2. 任务日志：按 runId 找回本次任务的全部截图，点开即看。
 *
 * 存放位置由设置里的「存储位置」决定：
 * - [AppUiPrefs.SCREENSHOT_STORAGE_APP]：应用内部目录 `files/screenshots`（默认，自动保留最近 [MAX_APP_SHOTS] 张）；
 * - [AppUiPrefs.SCREENSHOT_STORAGE_ALBUM]：手机相册 `Pictures/AquariusAI`，用 MediaStore 写入，
 *   不需要任何存储权限（Android 10+ 应用可以自由写入自己贡献的媒体文件）；
 * - [AppUiPrefs.SCREENSHOT_STORAGE_CUSTOM]：用户自己挑的任意文件夹，通过 SAF 目录树
 *   （`ACTION_OPEN_DOCUMENT_TREE` 授权、持久化 Uri 权限）写入，同样不需要存储权限。
 */
object ScreenshotArchive {

    /** 相册模式下的相对目录。 */
    const val ALBUM_RELATIVE_PATH = "Pictures/AquariusAI"

    /** 应用内部模式最多保留的张数，超出后自动删除最旧的。 */
    const val MAX_APP_SHOTS = 200

    private const val APP_DIR = "screenshots"
    private const val FILE_PREFIX = "aq_"
    private const val MIME_PNG = "image/png"

    /** 自定义目录（SAF 目录树）列文件时用到的列。 */
    private val TREE_PROJECTION =
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )

    /** 一条存档记录。 */
    data class Shot(
        val id: String,
        val displayName: String,
        val savedAt: Long,
        val sizeBytes: Long,
        val inAlbum: Boolean,
        val runId: String,
        val step: Int,
        /** 是否存放在用户自定义目录（SAF 目录树）里；此时 [id] 就是 document Uri。 */
        val inTree: Boolean = false,
    ) {
        /** 是否是相册里的文件。 */
        val fromAlbum: Boolean get() = inAlbum
    }

    /** 当前设置里选择的存储位置。 */
    fun currentStorage(context: Context): String = AppUiPrefs.screenshotStorageMode(context)

    /** 用户自定义目录是否已经授权可用。 */
    fun hasCustomFolder(context: Context): Boolean = AppUiPrefs.screenshotStorageTreeUri(context) != null

    /**
     * 自定义目录的展示名（目录最后一级的名字）。
     *
     * 拿不到（授权失效、目录被删）时返回 null，调用方据此提示重新选择。
     */
    fun customFolderLabel(context: Context): String? {
        val appCtx = context.applicationContext
        val tree = AppUiPrefs.screenshotStorageTreeUri(appCtx) ?: return null
        val documentUri = treeDocumentUri(appCtx) ?: return null
        val displayName =
            runCatching {
                appCtx.contentResolver
                    .query(documentUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull()
        if (!displayName.isNullOrBlank()) return displayName
        // 部分 provider 不返回 DISPLAY_NAME，退回文档 ID 里的路径部分。
        return fallbackTreeLabel(tree)
    }

    /**
     * 保存一张截图。
     *
     * @param pngBytes 图片原始字节（PNG/JPEG 均可，直接落盘）
     * @return 落盘后的记录；失败返回 null（不阻断任务）
     */
    fun save(
        context: Context,
        pngBytes: ByteArray,
        runId: String,
        step: Int,
    ): Shot? {
        if (pngBytes.isEmpty()) return null
        val appCtx = context.applicationContext
        val now = System.currentTimeMillis()
        val name = buildName(now, runId, step)
        return when (currentStorage(appCtx)) {
            AppUiPrefs.SCREENSHOT_STORAGE_ALBUM ->
                saveToAlbum(appCtx, name, pngBytes, now, runId, step)
                    ?: saveToAppDir(appCtx, name, pngBytes, now, runId, step)
            AppUiPrefs.SCREENSHOT_STORAGE_CUSTOM ->
                saveToTree(appCtx, name, pngBytes, now, runId, step)
                    ?: saveToAppDir(appCtx, name, pngBytes, now, runId, step)
            else -> saveToAppDir(appCtx, name, pngBytes, now, runId, step)
        }
    }

    /** base64 便捷入口（Agent 拿到的正是 base64）。 */
    fun saveBase64(
        context: Context,
        base64Png: String,
        runId: String,
        step: Int,
    ): Shot? {
        if (base64Png.isBlank()) return null
        val bytes =
            runCatching { Base64.decode(base64Png, Base64.DEFAULT) }.getOrNull() ?: return null
        return save(context, bytes, runId, step)
    }

    /** 全部截图，新的在前（应用内部 + 相册 + 自定义目录都列出来，切换存储位置后旧图不会消失）。 */
    fun list(context: Context): List<Shot> {
        val appCtx = context.applicationContext
        val shots = ArrayList<Shot>()
        shots.addAll(listAppShots(appCtx))
        shots.addAll(listAlbumShots(appCtx))
        shots.addAll(listTreeShots(appCtx))
        shots.sortByDescending { it.savedAt }
        return shots
    }

    /** 某次任务（runId）产生的截图，按步骤升序。 */
    fun listForRun(context: Context, runId: String): List<Shot> {
        val key = runKey(runId)
        if (key.isEmpty()) return emptyList()
        return list(context)
            .filter { it.runId == key }
            .sortedWith(compareBy({ it.step }, { it.savedAt }))
    }

    /** 打开图片流，交给 Coil / BitmapFactory 读取。 */
    fun openStream(context: Context, shot: Shot): InputStream? {
        val appCtx = context.applicationContext
        return runCatching {
            when {
                shot.inTree -> appCtx.contentResolver.openInputStream(Uri.parse(shot.id))
                shot.inAlbum -> {
                    val uri = ContentUris.withAppendedId(albumCollection(), shot.id.toLong())
                    appCtx.contentResolver.openInputStream(uri)
                }
                else -> File(appDir(appCtx), shot.id).inputStream()
            }
        }.getOrNull()
    }

    /** 图片 URI（自定义目录/相册返回 content://，应用内部返回 file://）。 */
    fun uriFor(context: Context, shot: Shot): Uri? =
        runCatching {
            when {
                shot.inTree -> Uri.parse(shot.id)
                shot.inAlbum -> ContentUris.withAppendedId(albumCollection(), shot.id.toLong())
                else -> Uri.fromFile(File(appDir(context.applicationContext), shot.id))
            }
        }.getOrNull()

    fun delete(context: Context, shot: Shot): Boolean {
        val appCtx = context.applicationContext
        return runCatching {
            when {
                shot.inTree -> DocumentsContract.deleteDocument(appCtx.contentResolver, Uri.parse(shot.id))
                shot.inAlbum -> {
                    val uri = ContentUris.withAppendedId(albumCollection(), shot.id.toLong())
                    appCtx.contentResolver.delete(uri, null, null) > 0
                }
                else -> File(appDir(appCtx), shot.id).delete()
            }
        }.getOrDefault(false)
    }

    /** 清空：应用内部目录整目录删除，相册与自定义目录里本应用写入的图片逐条删除。 */
    fun clear(context: Context): Int {
        val appCtx = context.applicationContext
        var removed = 0
        runCatching {
            appDir(appCtx).listFiles()?.forEach { if (it.delete()) removed++ }
        }
        listAlbumShots(appCtx).forEach { shot ->
            if (delete(appCtx, shot)) removed++
        }
        listTreeShots(appCtx).forEach { shot ->
            if (delete(appCtx, shot)) removed++
        }
        return removed
    }

    /** 合计占用字节数。 */
    fun totalBytes(shots: List<Shot>): Long = shots.sumOf { it.sizeBytes }

    /** 目录/相册的人类可读位置描述用到的路径。 */
    fun appDirPath(context: Context): String = appDir(context.applicationContext).absolutePath

    // ── 内部实现 ────────────────────────────────────────────────────────────

    private fun appDir(context: Context): File =
        File(context.filesDir, APP_DIR).apply { if (!exists()) mkdirs() }

    private fun albumCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    /** 名字里带时间戳与 runId 前缀，既能排序也能按任务找回。 */
    private fun buildName(now: Long, runId: String, step: Int): String {
        val key = runKey(runId).ifEmpty { "local" }
        val safeStep = step.coerceAtLeast(0)
        return "${FILE_PREFIX}${now}_${key}_s$safeStep.png"
    }

    /** runId 只保留字母数字并截断，避免文件名非法或过长。 */
    private fun runKey(runId: String): String =
        runId.filter { it.isLetterOrDigit() }.take(12)

    private fun parseName(name: String): Triple<String, Int, Long>? {
        // aq_<ts>_<runKey>_s<step>.png
        if (!name.startsWith(FILE_PREFIX) || !name.endsWith(".png")) return null
        val body = name.removePrefix(FILE_PREFIX).removeSuffix(".png")
        val parts = body.split('_')
        if (parts.size < 3) return null
        val ts = parts[0].toLongOrNull() ?: return null
        val runPart = parts[1]
        val step = parts[2].removePrefix("s").toIntOrNull() ?: 0
        return Triple(runPart, step, ts)
    }

    private fun saveToTree(
        context: Context,
        name: String,
        bytes: ByteArray,
        savedAt: Long,
        runId: String,
        step: Int,
    ): Shot? {
        val parent = treeDocumentUri(context) ?: return null
        val resolver = context.contentResolver
        val created =
            runCatching {
                DocumentsContract.createDocument(resolver, parent, MIME_PNG, name)
            }.getOrNull() ?: return null
        val written =
            runCatching {
                resolver.openOutputStream(created, "w")?.use { it.write(bytes) } != null
            }.getOrDefault(false)
        if (!written) {
            runCatching { DocumentsContract.deleteDocument(resolver, created) }
            return null
        }
        return Shot(
            id = created.toString(),
            displayName = name,
            savedAt = savedAt,
            sizeBytes = bytes.size.toLong(),
            inAlbum = false,
            runId = runKey(runId).ifEmpty { "local" },
            step = step,
            inTree = true,
        )
    }

    private fun listTreeShots(context: Context): List<Shot> {
        val tree = AppUiPrefs.screenshotStorageTreeUri(context) ?: return emptyList()
        return runCatching {
            val resolver = context.contentResolver
            val childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val out = ArrayList<Shot>()
            resolver.query(childrenUri, TREE_PROJECTION, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(0) ?: continue
                    val displayName = cursor.getString(1) ?: continue
                    // 目录可能被用户复用：只认本应用写进去的命名规则。
                    val parsed = parseName(displayName) ?: continue
                    val modified = if (cursor.isNull(2)) 0L else cursor.getLong(2)
                    val size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                    out.add(
                        Shot(
                            id = DocumentsContract.buildDocumentUriUsingTree(tree, documentId).toString(),
                            displayName = displayName,
                            savedAt = if (modified > 0) modified else parsed.third,
                            sizeBytes = size,
                            inAlbum = false,
                            runId = parsed.first,
                            step = parsed.second,
                            inTree = true,
                        ),
                    )
                }
            }
            out
        }.getOrDefault(emptyList())
    }

    /** SAF 目录树里“目录本身”对应的 document Uri（新建文件都挂在这一层）。 */
    private fun treeDocumentUri(context: Context): Uri? {
        val tree = AppUiPrefs.screenshotStorageTreeUri(context) ?: return null
        return runCatching {
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        }.getOrNull()
    }

    /** 文档 ID 形如 `primary:Download/Aquarius`，取冒号后的路径作为可读名。 */
    private fun fallbackTreeLabel(tree: Uri): String {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull().orEmpty()
        val path = documentId.substringAfter(':', documentId).trim('/')
        return path.ifBlank { documentId }
    }

    private fun saveToAppDir(
        context: Context,
        name: String,
        bytes: ByteArray,
        savedAt: Long,
        runId: String,
        step: Int,
    ): Shot? =
        runCatching {
            val dir = appDir(context)
            val file = File(dir, name)
            file.writeBytes(bytes)
            pruneAppDir(dir)
            Shot(
                id = name,
                displayName = name,
                savedAt = savedAt,
                sizeBytes = file.length(),
                inAlbum = false,
                runId = runKey(runId).ifEmpty { "local" },
                step = step,
            )
        }.getOrNull()

    private fun pruneAppDir(dir: File) {
        val all = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: return
        if (all.size <= MAX_APP_SHOTS) return
        all.drop(MAX_APP_SHOTS).forEach { runCatching { it.delete() } }
    }

    private fun saveToAlbum(
        context: Context,
        name: String,
        bytes: ByteArray,
        savedAt: Long,
        runId: String,
        step: Int,
    ): Shot? =
        runCatching {
            val resolver = context.contentResolver
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/AquariusAI",
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    put(MediaStore.Images.Media.DATE_ADDED, savedAt / 1000)
                }
            val uri = resolver.insert(albumCollection(), values) ?: return null
            val ok =
                runCatching {
                            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                            true
                        }
                        .getOrDefault(false)
            if (!ok) {
                runCatching { resolver.delete(uri, null, null) }
                return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                runCatching { resolver.update(uri, done, null, null) }
            }
            Shot(
                id = ContentUris.parseId(uri).toString(),
                displayName = name,
                savedAt = savedAt,
                sizeBytes = bytes.size.toLong(),
                inAlbum = true,
                runId = runKey(runId).ifEmpty { "local" },
                step = step,
            )
        }.getOrNull()

    private fun listAppShots(context: Context): List<Shot> =
        runCatching {
                    appDir(context)
                        .listFiles()
                        ?.filter { it.isFile && it.name.endsWith(".png") }
                        ?.mapNotNull { file ->
                            val parsed = parseName(file.name)
                            val savedAt = parsed?.third ?: file.lastModified()
                            val runKey = parsed?.first ?: "local"
                            val step = parsed?.second ?: 0
                            Shot(
                                id = file.name,
                                displayName = file.name,
                                savedAt = savedAt,
                                sizeBytes = file.length(),
                                inAlbum = false,
                                runId = runKey,
                                step = step,
                            )
                        }
                        .orEmpty()
                }
                .getOrDefault(emptyList())

    private fun listAlbumShots(context: Context): List<Shot> =
        runCatching {
                    val projection =
                        arrayOf(
                            MediaStore.Images.Media._ID,
                            MediaStore.Images.Media.DISPLAY_NAME,
                            MediaStore.Images.Media.SIZE,
                            MediaStore.Images.Media.DATE_ADDED,
                        )
                    val selection =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                        } else {
                            "${MediaStore.Images.Media.DATA} LIKE ?"
                        }
                    val args =
                        arrayOf(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                "${Environment.DIRECTORY_PICTURES}/AquariusAI%"
                            } else {
                                "%/Pictures/AquariusAI%"
                            }
                        )
                    val out = ArrayList<Shot>()
                    context.contentResolver
                        .query(albumCollection(), projection, selection, args, null)
                        ?.use { cursor ->
                            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                            val nameIdx =
                                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                            val dateIdx =
                                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                            while (cursor.moveToNext()) {
                                val id = cursor.getLong(idIdx)
                                val name = cursor.getString(nameIdx) ?: continue
                                val size = cursor.getLong(sizeIdx)
                                val seconds = cursor.getLong(dateIdx)
                                val parsed = parseName(name)
                                out.add(
                                    Shot(
                                        id = id.toString(),
                                        displayName = name,
                                        savedAt = if (seconds > 0) seconds * 1000 else (parsed?.third ?: 0L),
                                        sizeBytes = size,
                                        inAlbum = true,
                                        runId = parsed?.first ?: "local",
                                        step = parsed?.second ?: 0,
                                    )
                                )
                            }
                        }
                    out
                }
                .getOrDefault(emptyList())

    /** 供 UI 用的图片尺寸探测（相册模式下拿不到文件直接读宽度）。 */
    fun readSize(context: Context, shot: Shot): Pair<Int, Int>? =
        runCatching {
            openStream(context, shot)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, opts)
                if (opts.outWidth > 0) opts.outWidth to opts.outHeight else null
            }
        }.getOrNull()
}
