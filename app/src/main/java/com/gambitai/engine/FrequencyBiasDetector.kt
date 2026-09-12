package com.gambitai.engine

import kotlin.math.abs

class FrequencyBiasDetector(private val windows: List<Int> = listOf(10, 20, 50, 100)) {
    fun bias(history: List<String>): Map<String, Double> {
        val expected = 1.0 / Symbols.keys.size
        val scores = HashMap<String, Double>()
        for (s in Symbols.keys) scores[s] = 0.0
        for (w in windows) {
            if (history.size < w) continue
            val recent = history.takeLast(w)
            val counts = recent.groupingBy { it }.eachCount()
            val weight = 1.0 / w
            for (s in Symbols.keys) {
                val observed = (counts[s] ?: 0).toDouble() / w
                scores[s] = scores[s]!! + (observed - expected) * weight
            }
        }
        val total = scores.values.sumOf { abs(it) }.let { if (it == 0.0) 1.0 else it }
        return scores.mapValues { it.value / total }
    }
}
