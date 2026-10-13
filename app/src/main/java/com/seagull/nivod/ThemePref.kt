package com.seagull.nivod

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** 明暗外观偏好：跟随系统 / 浅色 / 深色 */
object ThemePref {

    private const val SP = "nivod_prefs"
    private const val KEY = "night_mode"

    const val FOLLOW = 0
    const val LIGHT = 1
    const val DARK = 2

    fun mode(ctx: Context): Int = sp(ctx).getInt(KEY, FOLLOW)

    fun set(ctx: Context, mode: Int) {
        sp(ctx).edit().putInt(KEY, mode).apply()
        apply(mode)
    }

    fun apply(mode: Int) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    /** 启动时调用 */
    fun applySaved(ctx: Context) = apply(mode(ctx))

    private fun sp(ctx: Context) = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
}
