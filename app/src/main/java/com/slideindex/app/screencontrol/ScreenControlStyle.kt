package com.slideindex.app.screencontrol

import android.content.Context
import kotlin.math.roundToInt

/**
 * 「屏幕控制」触控板面板的深色样式与不透明度。
 *
 * 不透明度有 6 档（30 / 45 / 60 / 75 / 90 / 100%），点一下面板顶部的三点横条切到下一档，
 * 选择会保存下来。
 */
internal object ScreenControlStyle {
    private const val PREFS = "screen_control_style"
    private const val KEY_OPACITY = "opacity_percent"
    private const val DEFAULT_PERCENT = 75
    private val LEVELS = intArrayOf(30, 45, 60, 75, 90, 100)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun percent(context: Context): Int = prefs(context).getInt(KEY_OPACITY, DEFAULT_PERCENT)

    /** 切到下一档并保存，返回新的百分比。 */
    fun cycle(context: Context): Int {
        val current = percent(context)
        val next = LEVELS.firstOrNull { it > current } ?: LEVELS.first()
        prefs(context).edit().putInt(KEY_OPACITY, next).apply()
        return next
    }

    /** 面板底色的 alpha（0..255）。模糊开启时再压低一点，因为模糊本身会让背景更亮。 */
    fun panelAlpha(context: Context, blur: Boolean): Int {
        val base = percent(context) / 100f * 255f
        return (if (blur) base * 0.8f else base).roundToInt().coerceIn(0, 255)
    }

    /** 触控区底色的 alpha：比面板略淡，让触控区在深色面板上能被看出来。 */
    fun padAlpha(context: Context, blur: Boolean): Int =
        (panelAlpha(context, blur) * 0.85f).roundToInt().coerceIn(0, 255)
}
