package com.slideindex.app.freezer

import android.content.Context

/**
 * 冰箱列表的「置顶」与「白名单」。
 *
 * - 置顶：只影响排序，置顶的应用排在最前面。
 * - 白名单：「按档位收回」「全部暂停」这类批量动作会跳过白名单里的应用。
 *
 * 用独立的 SharedPreferences 保存，不动设置仓库。键是冰箱列表里的包名（分身是「包名#用户ID」）。
 */
internal object FreezerPinStore {
    private const val PREFS = "freezer_pin_store"
    private const val KEY_PINS = "pins"
    private const val KEY_WHITES = "whites"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(context: Context, name: String): Set<String> =
        prefs(context).getStringSet(name, emptySet()).orEmpty()

    private fun write(context: Context, name: String, key: String, on: Boolean) {
        val set = read(context, name).toMutableSet()
        if (on) set.add(key) else set.remove(key)
        prefs(context).edit().putStringSet(name, set).apply()
    }

    fun isPinned(context: Context, key: String): Boolean = key in read(context, KEY_PINS)

    fun isWhitelisted(context: Context, key: String): Boolean = key in read(context, KEY_WHITES)

    fun setPinned(context: Context, key: String, pinned: Boolean) = write(context, KEY_PINS, key, pinned)

    fun setWhitelisted(context: Context, key: String, whitelisted: Boolean) =
        write(context, KEY_WHITES, key, whitelisted)

    /** 名字前面的小标记：★ 置顶  🔒 白名单。 */
    fun marker(context: Context, key: String): String {
        val pins = read(context, KEY_PINS)
        val whites = read(context, KEY_WHITES)
        return (if (key in pins) "★ " else "") + (if (key in whites) "🔒 " else "")
    }
}
