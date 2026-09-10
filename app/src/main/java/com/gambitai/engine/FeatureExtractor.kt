package com.gambitai.engine

import kotlin.math.*

object FeatureExtractor {
    const val FEATURE_DIM = 15

    fun oneHot(symbol: String): FloatArray {
        val x = FloatArray(FEATURE_DIM)
        val i = Symbols.keys.indexOf(symbol)
        if (i >= 0) x[i] = 1f
        return x
    }

    fun features(history: List<String>, index: Int): FloatArray {
        val s = history.getOrNull(index) ?: "C"
        val x = oneHot(s)
        val recent = history.take(index + 1).takeLast(20)
        val n = recent.size.coerceAtLeast(1)

        val repeat = recent.count { it == s }.toFloat() / n
        val prevSame = if (index > 0 && history[index - 1] == s) 1f else 0f
        val opposite = if (index > 0 && Symbols.opposite[history[index - 1]] == s) 1f else 0f
        val family = if (index > 0 && Symbols.colorFamily[history[index - 1]] == s) 1f else 0f
        val meat = if (s in Symbols.meat) 1f else 0f

        x[10] = repeat
        x[11] = prevSame
        x[12] = opposite
        x[13] = family
        x[14] = meat
        return x
    }

    fun sequence(history: List<String>, length: Int = 20): FloatArray {
        val out = FloatArray(length * FEATURE_DIM)
        val start = max(0, history.size - length)
        val h = history.subList(start, history.size)
        val pad = length - h.size
        h.forEachIndexed { j, _ ->
            val idx = start + j
            val f = features(history, idx)
            System.arraycopy(f, 0, out, (pad + j) * FEATURE_DIM, FEATURE_DIM)
        }
        return out
    }

    fun entropy(history: List<String>): Double {
        if (history.isEmpty()) return 0.0
        val counts = IntArray(Symbols.keys.size)
        history.takeLast(100).forEach { s ->
            val i = Symbols.keys.indexOf(s); if (i >= 0) counts[i]++
        }
        val n = counts.sum().toDouble().coerceAtLeast(1.0)
        var e = 0.0
        counts.filter { it > 0 }.forEach { c ->
            val p = c / n
            e -= p * ln(p)
        }
        return e
    }

    fun repetition(history: List<String>): Double {
        val h = history.takeLast(20)
        if (h.isEmpty()) return 0.0
        val last = h.last()
        return h.count { it == last }.toDouble() / h.size
    }
}
