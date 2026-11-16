package com.ai.phoneagent.core.settings

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.TypedValue
import androidx.core.content.edit

/**
 * 界面级轻量偏好（振动、悬浮窗、触控热区、字号）。
 *
 * 这些值需要在振动回调、View 触摸分发等同步路径上立即读取，
 * DataStore 的 suspend 语义不适合，因此单独放在 SharedPreferences 里。
 *
 * 所有数值型偏好都不做上下限约束，用户填多少就是多少；
 * 读取时只做“非法值回退到默认”的保护，避免脏数据把界面画坏。
 */
object AppUiPrefs {

    private const val PREFS_NAME = "aquarius_ui_prefs"

    private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
    private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
    private const val KEY_CORNER_RADIUS_PX = "overlay_corner_radius_px"
    private const val KEY_PARTICLE_ENABLED = "overlay_particle_enabled"
    private const val KEY_TEXT_SIZE_SP = "overlay_text_size_sp"
    private const val KEY_STOP_SIZE_SP = "overlay_stop_size_sp"
    private const val KEY_HIDE_SIZE_SP = "overlay_hide_size_sp"
    private const val KEY_PLUS_SIZE_SP = "overlay_plus_size_sp"
    private const val KEY_LANGUAGE_TAG = "language_tag"
    private const val KEY_OVERLAY_X = "overlay_frame_x"
    private const val KEY_OVERLAY_Y = "overlay_frame_y"
    private const val KEY_OVERLAY_W = "overlay_frame_w"
    private const val KEY_OVERLAY_H = "overlay_frame_h"
    private const val KEY_OVERLAY_SCREEN_W = "overlay_frame_screen_w"
    private const val KEY_OVERLAY_SCREEN_H = "overlay_frame_screen_h"
    private const val KEY_SCREENSHOT_STORAGE = "screenshot_storage_mode"
    private const val KEY_SCREENSHOT_TREE_URI = "screenshot_storage_tree_uri"

    /** 截图存储位置：应用内部（默认）。 */
    const val SCREENSHOT_STORAGE_APP = "app"

    /** 截图存储位置：手机本地相册文件夹（Pictures/AquariusAI）。 */
    const val SCREENSHOT_STORAGE_ALBUM = "album"

    /** 截图存储位置：用户自己用系统文件选择器指定的任意文件夹（SAF 目录树）。 */
    const val SCREENSHOT_STORAGE_CUSTOM = "custom"

    /** 语言：简体中文（默认）。 */
    const val LANGUAGE_ZH = "zh"

    /** 语言：English。 */
    const val LANGUAGE_EN = "en"

    /** 可选语言（顺序即设置页展示顺序）。 */
    val SUPPORTED_LANGUAGES = listOf(LANGUAGE_ZH, LANGUAGE_EN)

    /** 当前语言标签；未设置过时返回默认中文。 */
    fun languageTag(context: Context): String {
        val stored = prefs(context).getString(KEY_LANGUAGE_TAG, LANGUAGE_ZH).orEmpty()
        return if (stored in SUPPORTED_LANGUAGES) stored else LANGUAGE_ZH
    }

    fun setLanguageTag(context: Context, tag: String) {
        val safe = if (tag in SUPPORTED_LANGUAGES) tag else LANGUAGE_ZH
        prefs(context).edit { putString(KEY_LANGUAGE_TAG, safe) }
    }

    /** 触控热区半径默认值，单位 dp（与历史行为一致）。 */
    const val DEFAULT_CORNER_RADIUS_DP = 40

    /** 悬浮窗正文字号默认值（sp）。 */
    const val DEFAULT_TEXT_SIZE_SP = 8.5f

    /** “结束任务”文字默认字号（sp）。 */
    const val DEFAULT_STOP_SIZE_SP = 10f

    /** “隐藏 / 显示”文字默认字号（sp）。 */
    const val DEFAULT_HIDE_SIZE_SP = 10f

    /** “＋”按钮默认字号（sp）。 */
    const val DEFAULT_PLUS_SIZE_SP = 13f

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hapticsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAPTICS_ENABLED, true)

    fun setHapticsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_HAPTICS_ENABLED, enabled) }
    }

    fun overlayEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERLAY_ENABLED, true)

    fun setOverlayEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_OVERLAY_ENABLED, enabled) }
    }

    /**
     * 悬浮窗上次的位置与尺寸（像素，相对屏幕左上角）。
     *
     * 关闭悬浮窗、退出应用、甚至进程被杀之后再次打开，都按这个位置摆放，
     * 不再每次都跳回右上角。记录时一并保存当时的屏幕尺寸，旋转/换屏后用于判断是否需要重新夹取。
     */
    data class OverlayFrame(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val screenWidth: Int,
        val screenHeight: Int,
    )

    /** 读取上次保存的悬浮窗框架；从未保存过或数据非法时返回 null。 */
    fun overlayFrame(context: Context): OverlayFrame? {
        val p = prefs(context)
        val w = p.getInt(KEY_OVERLAY_W, 0)
        val h = p.getInt(KEY_OVERLAY_H, 0)
        if (w <= 0 || h <= 0) return null
        val x = p.getInt(KEY_OVERLAY_X, 0)
        val y = p.getInt(KEY_OVERLAY_Y, 0)
        if (x < 0 || y < 0) return null
        return OverlayFrame(
            x = x,
            y = y,
            width = w,
            height = h,
            screenWidth = p.getInt(KEY_OVERLAY_SCREEN_W, 0),
            screenHeight = p.getInt(KEY_OVERLAY_SCREEN_H, 0),
        )
    }

    fun setOverlayFrame(context: Context, x: Int, y: Int, width: Int, height: Int) {
        val dm = context.resources.displayMetrics
        prefs(context).edit {
            putInt(KEY_OVERLAY_X, x.coerceAtLeast(0))
            putInt(KEY_OVERLAY_Y, y.coerceAtLeast(0))
            putInt(KEY_OVERLAY_W, width.coerceAtLeast(0))
            putInt(KEY_OVERLAY_H, height.coerceAtLeast(0))
            putInt(KEY_OVERLAY_SCREEN_W, dm.widthPixels)
            putInt(KEY_OVERLAY_SCREEN_H, dm.heightPixels)
        }
    }

    /** 截图存放位置：[SCREENSHOT_STORAGE_APP]、[SCREENSHOT_STORAGE_ALBUM] 或 [SCREENSHOT_STORAGE_CUSTOM]。 */
    fun screenshotStorageMode(context: Context): String {
        val stored = prefs(context).getString(KEY_SCREENSHOT_STORAGE, SCREENSHOT_STORAGE_APP)
        return when (stored) {
            SCREENSHOT_STORAGE_ALBUM -> SCREENSHOT_STORAGE_ALBUM
            // 自定义目录必须有一次已授权的目录树；授权丢了（用户删目录、清授权、换机恢复）
            // 就自动退回应用内部，避免后台任务因为写不进去而丢截图。
            SCREENSHOT_STORAGE_CUSTOM ->
                if (screenshotStorageTreeUri(context) != null) SCREENSHOT_STORAGE_CUSTOM else SCREENSHOT_STORAGE_APP
            else -> SCREENSHOT_STORAGE_APP
        }
    }

    fun setScreenshotStorageMode(context: Context, mode: String) {
        val safe =
            when (mode) {
                SCREENSHOT_STORAGE_ALBUM -> SCREENSHOT_STORAGE_ALBUM
                SCREENSHOT_STORAGE_CUSTOM -> SCREENSHOT_STORAGE_CUSTOM
                else -> SCREENSHOT_STORAGE_APP
            }
        prefs(context).edit { putString(KEY_SCREENSHOT_STORAGE, safe) }
    }

    /** 用户选定的自定义目录（SAF 目录树 Uri）；未选择过返回 null。 */
    fun screenshotStorageTreeUri(context: Context): Uri? {
        val raw = prefs(context).getString(KEY_SCREENSHOT_TREE_URI, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    fun setScreenshotStorageTreeUri(context: Context, uri: Uri?) {
        prefs(context).edit {
            if (uri == null) remove(KEY_SCREENSHOT_TREE_URI) else putString(KEY_SCREENSHOT_TREE_URI, uri.toString())
        }
    }

    /**
     * 四角触控热区半径（像素）。用户填多少就是多少，输入 0 表示关闭四角拖拽。
     * 未设置过时按 [DEFAULT_CORNER_RADIUS_DP] 换算。
     */
    fun cornerRadiusPx(context: Context): Int {
        val stored = prefs(context).getInt(KEY_CORNER_RADIUS_PX, -1)
        if (stored >= 0) return stored
        return defaultCornerRadiusPx(context)
    }

    fun setCornerRadiusPx(context: Context, px: Int) {
        prefs(context).edit { putInt(KEY_CORNER_RADIUS_PX, px.coerceAtLeast(0)) }
    }

    fun defaultCornerRadiusPx(context: Context): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            DEFAULT_CORNER_RADIUS_DP.toFloat(),
            context.resources.displayMetrics,
        ).toInt()

    /** 边框流光颗粒是否启用。关闭后动画协程直接不启动，而不是画完再隐藏。 */
    fun particleEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PARTICLE_ENABLED, true)

    fun setParticleEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_PARTICLE_ENABLED, enabled) }
    }

    /** 悬浮窗正文字号（sp），作用于状态行、日志正文与内嵌输入框。 */
    fun textSizeSp(context: Context): Float =
        positiveOrDefault(prefs(context).getFloat(KEY_TEXT_SIZE_SP, DEFAULT_TEXT_SIZE_SP), DEFAULT_TEXT_SIZE_SP)

    fun setTextSizeSp(context: Context, sp: Float) {
        prefs(context).edit { putFloat(KEY_TEXT_SIZE_SP, sp.coerceAtLeast(0f)) }
    }

    /** “结束任务”文字字号（sp）。 */
    fun stopTextSizeSp(context: Context): Float =
        positiveOrDefault(prefs(context).getFloat(KEY_STOP_SIZE_SP, DEFAULT_STOP_SIZE_SP), DEFAULT_STOP_SIZE_SP)

    fun setStopTextSizeSp(context: Context, sp: Float) {
        prefs(context).edit { putFloat(KEY_STOP_SIZE_SP, sp.coerceAtLeast(0f)) }
    }

    /** “隐藏 / 显示”文字字号（sp）。 */
    fun hideTextSizeSp(context: Context): Float =
        positiveOrDefault(prefs(context).getFloat(KEY_HIDE_SIZE_SP, DEFAULT_HIDE_SIZE_SP), DEFAULT_HIDE_SIZE_SP)

    fun setHideTextSizeSp(context: Context, sp: Float) {
        prefs(context).edit { putFloat(KEY_HIDE_SIZE_SP, sp.coerceAtLeast(0f)) }
    }

    /** “＋”按钮字号（sp）。 */
    fun plusTextSizeSp(context: Context): Float =
        positiveOrDefault(prefs(context).getFloat(KEY_PLUS_SIZE_SP, DEFAULT_PLUS_SIZE_SP), DEFAULT_PLUS_SIZE_SP)

    fun setPlusTextSizeSp(context: Context, sp: Float) {
        prefs(context).edit { putFloat(KEY_PLUS_SIZE_SP, sp.coerceAtLeast(0f)) }
    }

    private fun positiveOrDefault(value: Float, fallback: Float): Float =
        if (value.isFinite() && value > 0f) value else fallback
}
