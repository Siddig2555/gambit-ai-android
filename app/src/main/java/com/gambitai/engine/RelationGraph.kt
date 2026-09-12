package com.gambitai.engine

class RelationGraph {
    private val matrix = Array(Symbols.keys.size) { DoubleArray(Symbols.keys.size) }
    private var updatesSinceDecay = 0
    private val decayEvery = 50
    private val decayFactor = 0.98

    fun update(prev: String, actual: String) {
        val i = Symbols.keys.indexOf(prev)
        val j = Symbols.keys.indexOf(actual)
        if (i >= 0 && j >= 0) matrix[i][j] += 1.0
        updatesSinceDecay++
        if (updatesSinceDecay >= decayEvery) {
            for (row in matrix) for (k in row.indices) row[k] *= decayFactor
            updatesSinceDecay = 0
        }
    }

    fun distribution(last: String): Map<String, Double> {
        val i = Symbols.keys.indexOf(last)
        if (i < 0) return emptyMap()
        val row = matrix[i]
        val sum = row.sum()
        if (sum <= 0.0) return emptyMap()
        return Symbols.keys.indices.associate { Symbols.keys[it] to row[it] / sum }
    }

    fun topSymbols(last: String, n: Int = 3): List<Pair<String, Double>> {
        val i = Symbols.keys.indexOf(last)
        if (i < 0) return emptyList()
        val row = matrix[i]
        return row.indices.sortedByDescending { row[it] }.take(n)
            .filter { row[it] > 0 }.map { Symbols.keys[it] to row[it] }
    }
}
