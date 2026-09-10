package com.gambitai.engine

object CycleDetector {
    fun predict(history: List<String>, maxLag: Int = 20): String? {
        if (history.size < 6) return null
        val max = minOf(maxLag, history.size / 2)
        var bestLag = -1
        var bestScore = -1.0
        for (lag in 1..max) {
            var same = 0
            var total = 0
            for (i in lag until history.size) {
                total++
                if (history[i] == history[i-lag]) same++
            }
            val score = if (total == 0) 0.0 else same.toDouble()/total
            if (score > bestScore) { bestScore = score; bestLag = lag }
        }
        return if (bestLag > 0 && bestScore >= 0.55) history[history.size - bestLag] else null
    }
}
