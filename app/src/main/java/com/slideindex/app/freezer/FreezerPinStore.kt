package com.slideindex.app.freezer

import android.content.Context
import java.io.File

/**
 * 冰箱列表的「置顶」与「白名单」。
 *
 * - 置顶：只影响排序，置顶的应用排在最前面。
 * - 白名单：「按档位收回」「全部暂停」「任务切换器的全部停用」等批量动作会跳过它们。
 *
 * 用普通文件保存（每次按修改时间决定要不要重读），因为悬浮层和主界面可能不在同一个进程。
 * 键是冰箱列表里的包名（分身是「包名#用户ID」）。
 */
internal object FreezerPinStore {
    private class Cache(var stamp: Long = -1L, var set: Set<String> = emptySet())

    private val pins = Cache()
    private val whites = Cache()

    private fun file(context: Context, name: String) =
        File(context.applicationContext.filesDir, "freezer_$name.txt")

    private fun read(context: Context, name: String, cache: Cache): Set<String> {
        val f = file(context, name)
        val stamp = if (f.exists()) f.lastModified() else 0L
        synchronized(cache) {
            if (cache.stamp != stamp) {
                cache.set = if (f.exists()) {
                    runCatching { f.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
                        .getOrDefault(emptySet())
                } else {
                    emptySet()
                }
                cache.stamp = stamp
            }
            return cache.set
        }
    }

    private fun write(context: Context, name: String, cache: Cache, key: String, on: Boolean) {
        synchronized(cache) {
            val set = read(context, name, cache).toMutableSet()
            if (on) set.add(key) else set.remove(key)
            val f = file(context, name)
            runCatching { f.writeText(set.joinToString("\n")) }
            cache.set = set
            cache.stamp = if (f.exists()) f.lastModified() else 0L
        }
    }

    fun isPinned(context: Context, key: String): Boolean = key in read(context, "pins", pins)

    fun isWhitelisted(context: Context, key: String): Boolean = key in read(context, "whites", whites)

    fun setPinned(context: Context, key: String, pinned: Boolean) = write(context, "pins", pins, key, pinned)

    fun setWhitelisted(context: Context, key: String, whitelisted: Boolean) =
        write(context, "whites", whites, key, whitelisted)

    /** 名字前面的小标记：★ 置顶  🔒 白名单。 */
    fun marker(context: Context, key: String): String =
        (if (isPinned(context, key)) "★ " else "") + (if (isWhitelisted(context, key)) "🔒 " else "")
}
