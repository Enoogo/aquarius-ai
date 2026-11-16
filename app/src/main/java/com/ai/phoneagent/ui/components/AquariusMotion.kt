package com.ai.phoneagent.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Material Design 3 运动令牌。
 *
 * 设置页的返回不再依赖 AnimatedContent 的转场动画——那种做法在返回时才组合新页面，
 * 首次渲染需要一两帧，旧页退出后新页还没画好，视觉上就是“闪一下”。
 * 现在设置首页常驻组合树，二级页只是覆盖在其上的一层，返回时把覆盖层滑走即可，
 * 下面的首页早已渲染完成，无需等待首次渲染。
 *
 * 预测性返回（Predictive Back）只用一条规则：当前页水平右移，目标页带一点视差跟进。
 * 不加投影、不加圆角、不做缩放——这些都是系统级窗口动画的视觉语言，
 * 在应用内页面上会显得生硬，且与 M3 的“滑出 + 视差”动画不一致。
 */
object M3Motion {
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /**
     * 目标页的视差比例：覆盖层滑走多少，被露出的页面就同向跟进多少。
     *
     * 取值只影响“背景层是否跟着动”，不影响手势跟手度；0.12 既能让返回目标
     * 在松手前就有明确的推进感，又不会让页面在滑动中露出边缘空白。
     */
    const val PredictiveParallax: Float = 0.12f
}
