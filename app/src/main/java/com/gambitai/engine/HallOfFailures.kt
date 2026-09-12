package com.gambitai.engine

import kotlin.math.exp

data class Failure(val pattern: String, val wrong: String, val timestamp: Long, var count: Int)

class HallOfFailures(private val capacity: Int = 500) {
    private val failures = ArrayDeque<Failure>()

    fun record(history: List<String>, wrong: String, actual: String) {
        if (wrong == actual) return
        val pattern = history.takeLast(5).joinToString("")
        val found = failures.find { it.pattern == pattern && it.wrong == wrong }
        if (found != null) found.count++ else {
            if (failures.size >= capacity) failures.removeFirst()
            failures.addLast(Failure(pattern, wrong, System.currentTimeMillis(), 1))
        }
    }

    fun penalty(history: List<String>, candidate: String): Double {
        val pattern = history.takeLast(5).joinToString("")
        val now = System.currentTimeMillis()
        return failures.filter { it.pattern == pattern && it.wrong == candidate }
            .sumOf {
                val days = (now - it.timestamp).coerceAtLeast(0L) / 86_400_000.0
                it.count * exp(-days / 7.0)
            }
    }

    fun size(): Int = failures.size

    fun topStats(n: Int = 3): String {
        if (failures.isEmpty()) return "لا توجد بيانات بعد"
        return failures.sortedByDescending { it.count }
            .take(n)
            .joinToString(" | ") { "'${it.pattern}'→'${it.wrong}'(×${it.count})" }
    }

    fun serialize(): String = failures.joinToString("\n") { f ->
        "${f.pattern}#${f.wrong}#${f.timestamp}#${f.count}"
    }

    fun restore(data: String) {
        failures.clear()
        if (data.isBlank()) return
        data.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split("#")
            if (parts.size == 4) {
                val ts = parts[2].toLongOrNull() ?: System.currentTimeMillis()
                val cnt = parts[3].toIntOrNull() ?: 1
                failures.addLast(Failure(parts[0], parts[1], ts, cnt))
            }
        }
    }
}
