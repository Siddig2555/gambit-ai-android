package com.gambitai.engine

data class MemoryEntry(val history: List<String>, val predicted: String, val actual: String, val confidence: Double)

class LiteMemory(private val capacity: Int = 300) {
    private val entries = ArrayDeque<MemoryEntry>()

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
}
