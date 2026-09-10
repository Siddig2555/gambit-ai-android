package com.gambitai.engine

class Fingerprint(private val depth: Int = 5) {
    private val table = HashMap<String, IntArray>()

    fun observe(history: List<String>, actual: String) {
        for (d in 1..minOf(depth, history.size)) {
            val key = history.takeLast(d).joinToString("")
            val counts = table.getOrPut(key) { IntArray(Symbols.keys.size) }
            Symbols.keys.indexOf(actual).takeIf { it >= 0 }?.let { counts[it]++ }
        }
    }

    fun predict(history: List<String>): String? {
        for (d in minOf(depth, history.size) downTo 1) {
            val key = history.takeLast(d).joinToString("")
            val counts = table[key] ?: continue
            val best = counts.indices.maxByOrNull { counts[it] } ?: continue
            if (counts[best] > 0) return Symbols.keys[best]
        }
        return null
    }
}
