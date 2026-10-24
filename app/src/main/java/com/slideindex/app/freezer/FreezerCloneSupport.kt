package com.slideindex.app.freezer

import android.content.Context
import com.slideindex.app.BuildConfig
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 分身（多开）应用在冰箱列表里用「包名#用户ID」标识，例如 com.tencent.mm#999。
 * 列表存储本来就是字符串集合，所以不用改存储格式；底层操作再把它拆回包名和用户 ID。
 */
internal object FreezerCloneKey {
    private const val SEPARATOR = '#'

    /** 分身所在的用户 ID 范围。 */
    val USER_IDS: IntProgression = 999 downTo 995

    fun isClone(key: String): Boolean = userIdOf(key) != null

    fun basePackage(key: String): String = key.substringBefore(SEPARATOR)

    fun userIdOf(key: String): Int? {
        val index = key.indexOf(SEPARATOR)
        if (index <= 0) return null
        return key.substring(index + 1).toIntOrNull()
    }

    fun make(packageName: String, userId: Int): String = "$packageName$SEPARATOR$userId"

    /** 用户 999 显示为 1，998 显示为 2，以此类推。 */
    fun indexOf(userId: Int): Int = 1000 - userId
}

internal object FreezerCloneSupport {
    private const val SEEN_PREFS = "freezer_clone_seen"
    private const val SEEN_KEY = "seen"

    /**
     * 解析冰箱成员：普通应用走原有逻辑；分身借用主应用的名称和拼音，名字前加数字，
     * 并紧跟在主应用后面。
     */
    suspend fun resolveMembers(appRepository: AppRepository, packages: Set<String>): List<AppInfo> {
        val plain = packages.filterNot { FreezerCloneKey.isClone(it) }.toSet()
        val result = appRepository.resolveFreezerMembers(plain)
        val cloneKeys = packages.filter { FreezerCloneKey.isClone(it) }
        if (cloneKeys.isEmpty()) return result

        val bases = appRepository
            .resolveFreezerMembers(cloneKeys.map { FreezerCloneKey.basePackage(it) }.toSet())
            .associateBy { it.packageName }
        val clones = cloneKeys.mapNotNull { key ->
            val base = bases[FreezerCloneKey.basePackage(key)] ?: return@mapNotNull null
            val userId = FreezerCloneKey.userIdOf(key) ?: return@mapNotNull null
            base.copy(packageName = key, label = "${FreezerCloneKey.indexOf(userId)} ${base.label}")
        }.sortedBy { FreezerCloneKey.userIdOf(it.packageName) ?: 0 }.reversed()

        val byBase = clones.groupBy { FreezerCloneKey.basePackage(it.packageName) }
        val merged = ArrayList<AppInfo>()
        for (app in result) {
            merged += app
            byBase[app.packageName]?.let { merged += it }
        }
        val placed = result.map { it.packageName }.toSet()
        byBase.filterKeys { it !in placed }.values.forEach { merged += it }
        return merged
    }

    /**
     * 打开面板时，把分身用户里的第三方应用自动加进冰箱列表。
     * 每个分身只会加一次：你把它移出列表以后不会再被自动加回来。
     */
    suspend fun syncCloneMembers(context: Context, settingsRepository: SettingsRepository) {
        withContext(Dispatchers.IO) {
            if (!TaskManagerUtil.hasPrivilegedAccess()) return@withContext
            val prefs = context.applicationContext.getSharedPreferences(SEEN_PREFS, Context.MODE_PRIVATE)
            val seen = prefs.getStringSet(SEEN_KEY, emptySet()).orEmpty().toMutableSet()
            var changed = false
            for (userId in FreezerCloneKey.USER_IDS) {
                val output = TaskManagerUtil.runShellCommandLine(
                    "pm list packages -3 --user $userId",
                    useRoot = false,
                ).output
                for (rawLine in output.lineSequence()) {
                    val line = rawLine.trim()
                    if (!line.startsWith("package:")) continue
                    val packageName = line.removePrefix("package:").trim()
                    if (packageName.isEmpty()) continue
                    if (packageName == BuildConfig.APPLICATION_ID || packageName.startsWith("com.slideindex.app")) continue
                    val key = FreezerCloneKey.make(packageName, userId)
                    if (!seen.add(key)) continue
                    settingsRepository.addFreezerApp(key)
                    changed = true
                }
            }
            if (changed) prefs.edit().putStringSet(SEEN_KEY, seen).apply()
        }
    }

    /** 在指定用户里直接启动分身应用（不经过系统的"选主应用还是分身"弹窗）。 */
    suspend fun launchClone(key: String): Boolean {
        val userId = FreezerCloneKey.userIdOf(key) ?: return false
        return launchAsUser(FreezerCloneKey.basePackage(key), userId)
    }

    /** 主应用也用同样的方式启动，绕开系统弹窗；失败时调用方回退到原来的启动方式。 */
    suspend fun launchMain(packageName: String): Boolean =
        launchAsUser(packageName, android.os.Process.myUserHandle().hashCode())

    private suspend fun launchAsUser(packageName: String, userId: Int): Boolean =
        withContext(Dispatchers.IO) {
            val command = "am start --user $userId -n \$(cmd package resolve-activity --brief --user $userId " +
                "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $packageName | tail -n 1)"
            val output = TaskManagerUtil.runShellCommandLine("sh -c '$command'", useRoot = false).output
            !output.contains("Error", ignoreCase = true) && !output.contains("Exception", ignoreCase = true)
        }
}
