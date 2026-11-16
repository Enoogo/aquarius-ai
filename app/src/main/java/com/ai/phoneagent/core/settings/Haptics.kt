package com.ai.phoneagent.core.settings

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * 全局触感入口。设置里的“振动”开关关闭后，这里所有调用直接短路，
 * 保证软件内不再产生任何振动。
 *
 * 手感沿用系统原生键盘轻触：先尝试 KEYBOARD_TAP（时长与强度交给系统），
 * 拿不到再用 EFFECT_TICK 兜底。
 */
object Haptics {

    fun isEnabled(context: Context): Boolean = AppUiPrefs.hapticsEnabled(context)

    fun setEnabled(context: Context, enabled: Boolean) {
        AppUiPrefs.setHapticsEnabled(context, enabled)
    }

    /** 用挂载的 View 触发系统原生触感；view 为空时退回 Vibrator。 */
    fun tap(context: Context?, view: View? = null) {
        val ctx = context ?: view?.context ?: return
        if (!isEnabled(ctx)) return
        try {
            if (view != null && view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)) {
                return
            }
        } catch (_: Throwable) {
            // 继续走 Vibrator 兜底
        }
        vibratorTick(ctx)
    }

    /** 语义化的“中等”反馈；为了与轻触保持一致的听感，同样使用系统键盘轻触。 */
    fun medium(context: Context?, view: View? = null) = tap(context, view)

    fun cancel(context: Context) {
        runCatching { resolveVibrator(context)?.cancel() }
    }

    private fun vibratorTick(context: Context) {
        runCatching {
            val vibrator = resolveVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
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
