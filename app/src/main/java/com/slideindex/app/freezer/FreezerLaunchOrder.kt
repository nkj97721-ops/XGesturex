package com.slideindex.app.freezer

import android.content.Context
import com.slideindex.app.data.AppInfo
import java.io.File

/**
 * 「在冰箱页启动过的应用排到最前面，冻结（或暂停）后回到原来的位置」。
 *
 * 做法：启动时记一个时间戳；排序时，记录里的应用只要还处于「启用」状态就排到最前面
 * （最近启动的在最前），一旦它被冻结或暂停，记录当场清掉，列表里它就回到原来的位置。
 * 置顶的应用排在启动过的后面，其余保持原有顺序。
 *
 * 用普通文件保存：冰箱面板可能在不同进程里打开。
 */
internal object FreezerLaunchOrder {
    private const val MAX_ENTRIES = 40

    private fun file(context: Context) = File(context.applicationContext.filesDir, "freezer_launched.txt")

    private fun read(context: Context): LinkedHashMap<String, Long> {
        val result = LinkedHashMap<String, Long>()
        val f = file(context)
        if (!f.exists()) return result
        runCatching {
            f.readLines().forEach { line ->
                val parts = line.split('\t')
                val time = parts.getOrNull(1)?.toLongOrNull()
                if (parts.size == 2 && time != null) result[parts[0]] = time
            }
        }
        return result
    }

    private fun write(context: Context, map: Map<String, Long>) {
        runCatching {
            file(context).writeText(map.entries.joinToString("\n") { "${it.key}\t${it.value}" })
        }
    }

    @Synchronized
    fun mark(context: Context, key: String) {
        val map = read(context)
        map.remove(key)
        map[key] = System.currentTimeMillis()
        while (map.size > MAX_ENTRIES) map.remove(map.keys.first())
        write(context, map)
    }

    @Synchronized
    private fun clear(context: Context, keys: Collection<String>) {
        val map = read(context)
        if (keys.any { map.remove(it) != null }) write(context, map)
    }

    /** 排序：最近启动且仍启用的 → 置顶的 → 其余（保持原顺序）。 */
    fun arrange(context: Context, apps: List<AppInfo>): List<AppInfo> {
        val launched = read(context)
        val inList = apps.mapTo(HashSet()) { it.packageName }
        val rank = HashMap<String, Long>()
        val stale = ArrayList<String>()
        for ((key, time) in launched) {
            if (key !in inList) continue
            if (FreezerOperations.stateOf(context, key).isActive) rank[key] = time else stale += key
        }
        if (stale.isNotEmpty()) clear(context, stale)
        return apps.sortedWith(
            compareByDescending<AppInfo> { rank[it.packageName] ?: 0L }
                .thenByDescending { FreezerPinStore.isPinned(context, it.packageName) }
        )
    }
}
