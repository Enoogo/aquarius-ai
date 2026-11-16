package com.ai.phoneagent.core.settings

import android.content.Context
import android.content.SharedPreferences
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
