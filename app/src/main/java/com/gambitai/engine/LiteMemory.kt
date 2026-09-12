package com.gambitai.engine

data class MemoryEntry(val history: List<String>, val predicted: String, val actual: String, val confidence: Double)

class LiteMemory(private val capacity: Int = 300) {
    private val entries = ArrayDeque<MemoryEntry>()

    fun size(): Int = entries.size

    fun add(e: MemoryEntry) {
        if (entries.size >= capacity) entries.removeFirst()
        entries.addLast(e)
    }

    fun vote(history: List<String>): Map<String, Double> {
        if (entries.isEmpty()) return emptyMap()
        val recent = history.takeLast(8)
        val scores = HashMap<String, Double>()
        for (e in entries) {
            val overlap = e.history.takeLast(8).zip(recent).count { it.first == it.second }
            if (overlap > 0) scores[e.actual] = (scores[e.actual] ?: 0.0) + overlap
        }
        return scores
    }

    fun serialize(): String = entries.joinToString("\n") { e ->
        "${e.history.joinToString(",")}#${e.predicted}#${e.actual}#${e.confidence}"
    }

    fun restore(data: String) {
        entries.clear()
        if (data.isBlank()) return
        data.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split("#")
            if (parts.size == 4) {
                val hist = if (parts[0].isBlank()) emptyList() else parts[0].split(",")
                val conf = parts[3].toDoubleOrNull() ?: 1.0
                entries.addLast(MemoryEntry(hist, parts[1], parts[2], conf))
            }
        }
    }
}
