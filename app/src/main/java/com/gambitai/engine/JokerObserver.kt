package com.gambitai.engine

import kotlin.math.abs

class JokerObserver(private val graph: RelationGraph) {
    var confidence: Double = 0.1
        private set
    var internalState: Double = 0.0
        private set

    fun observeRhythm(history: List<String>) {
        if (history.size < 4) { internalState = 0.0; return }
        val recent = history.takeLast(8)
        val changes = (1 until recent.size).count { recent[it] != recent[it - 1] }
        val ratio = changes.toDouble() / (recent.size - 1)
        internalState = (ratio - 0.5) * 2.0
    }

    fun subtleBias(last: String): Map<String, Double> {
        val bias = HashMap<String, Double>()
        if (confidence < 0.2) return bias
        val dist = graph.distribution(last)
        if (dist.isNotEmpty()) {
            val w = confidence * 0.1
            for (s in Symbols.keys) bias[s] = (dist[s] ?: 0.0) * w
        }
        if (internalState > 0.3) {
            for (s in Symbols.keys) {
                val add = if (s != last) confidence * 0.05 else -confidence * 0.05
                bias[s] = (bias[s] ?: 0.0) + add
            }
        } else if (internalState < -0.3) {
            for (s in Symbols.keys) {
                val add = if (s == last) confidence * 0.05 else -confidence * 0.02
                bias[s] = (bias[s] ?: 0.0) + add
            }
        }
        return bias
    }

    fun isActive(): Boolean = abs(internalState) > 0.3 && confidence >= 0.2

    fun updateWisdom(wasCorrect: Boolean) {
        confidence += if (wasCorrect) 0.01 else -0.005
        confidence = confidence.coerceIn(0.0, 0.5)
    }

    fun serialize(): String = "$confidence,$internalState"

    fun restore(data: String) {
        if (data.isBlank()) return
        val parts = data.trim().split(",")
        if (parts.size == 2) {
            confidence = parts[0].toDoubleOrNull() ?: 0.1
            internalState = parts[1].toDoubleOrNull() ?: 0.0
        }
    }
}
