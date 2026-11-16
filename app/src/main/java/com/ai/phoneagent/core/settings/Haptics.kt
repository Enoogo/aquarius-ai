package com.ai.phoneagent.core.settings

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View

/**
 * 全局触感入口。设置里的“振动”开关关闭后，这里所有调用直接短路，
 * 保证软件内不再产生任何振动。
 *
 * 整个 App 只保留一种手感：与顶部「历史 / 主页 / 设置」标签页切换时完全一致的轻触震动
 * （系统预置 [VibrationEffect.EFFECT_TICK]）。按钮、开关、列表项、悬浮窗等所有调用点
 * 都走这里，避免出现“某些地方重、某些地方轻”的不一致。
 */
object Haptics {

    fun isEnabled(context: Context): Boolean = AppUiPrefs.hapticsEnabled(context)

    fun setEnabled(context: Context, enabled: Boolean) {
        AppUiPrefs.setHapticsEnabled(context, enabled)
    }

    /**
     * 统一的轻触反馈。
     *
     * 始终使用 [VibrationEffect.EFFECT_TICK]，不再按调用方是否传入 View 分流到
     * `performHapticFeedback`，这样标签页切换、发送按钮、设置项、悬浮窗按钮的
     * 震动手感完全一致。[view] 参数仅为兼容既有调用保留。
     */
    @Suppress("UNUSED_PARAMETER")
    fun tap(context: Context?, view: View? = null) {
        val ctx = context ?: view?.context ?: return
        if (!isEnabled(ctx)) return
        vibratorTick(ctx)
    }

    /** 语义化的“中等”反馈；为保证全应用手感一致，同样使用轻触震动。 */
    fun medium(context: Context?, view: View? = null) = tap(context, view)

    fun cancel(context: Context) {
        runCatching { resolveVibrator(context)?.cancel() }
    }

    private fun vibratorTick(context: Context) {
        runCatching {
            val vibrator = resolveVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(10)
            }
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
