package com.gambitai.engine

import android.content.Context
import java.io.File
import kotlin.math.exp

data class Prediction(
    val symbol: String,
    val top4: List<String>,
    val confidence: Double,
    val probabilities: FloatArray,
    val entropy: Double,
    val cyclePrediction: String?,
    val fingerprintPrediction: String?,
    val memorySupport: Map<String, Double> = emptyMap(),
    val agreement: Double = 0.0,
    val bypassActive: Boolean = false,
    val jokerActive: Boolean = false
)

class EngineStats {
    var total = 0
    var correct = 0
    var correctTop4 = 0
    val topAccuracy: Double get() = if (total == 0) 0.0 else correct.toDouble() / total * 100.0
    val top4Accuracy: Double get() = if (total == 0) 0.0 else correctTop4.toDouble() / total * 100.0
    fun reset() { total = 0; correct = 0; correctTop4 = 0 }

    fun serialize(): String = "$total,$correct,$correctTop4"
    fun restore(data: String) {
        if (data.isBlank()) return
        val parts = data.trim().split(",")
        if (parts.size == 3) {
            total = parts[0].toIntOrNull() ?: 0
            correct = parts[1].toIntOrNull() ?: 0
            correctTop4 = parts[2].toIntOrNull() ?: 0
        }
    }
}

class V12LiteEngine(private val context: Context) {
    private val memory = LiteMemory()
    private val fingerprint = Fingerprint()
    private val hall = HallOfFailures()
    private val store = TrainingDataStore(context)
    val graph = RelationGraph()
    val joker = JokerObserver(graph)
    private val biasDetector = FrequencyBiasDetector()
    val stats = EngineStats()

    private var lstm: OnnxModel? = null
    private var transformer: OnnxModel? = null
    private var dynamic: OnnxModel? = null

    private val stateFile: File get() = File(context.filesDir, "engine_state.txt")

    fun loadModels() {
        closeModels()
        fun tryLoad(name: String): OnnxModel? = runCatching { OnnxModel(context, name) }.getOrNull()
        lstm = tryLoad("lstm.onnx")
        transformer = tryLoad("transformer.onnx")
        dynamic = tryLoad("dynamic.onnx")
        if (lstm == null && transformer == null && dynamic == null) {
            lstm = tryLoad("model.onnx")
        }
    }

    fun neuralExpertsLoaded(): Int = listOfNotNull(lstm, transformer, dynamic).size
    fun memoryCount(): Int = memory.size()
    fun shieldCount(): Int = hall.size()
    fun hallStats(): String = hall.topStats()
    fun graphTopAfter(last: String): List<Pair<String, Double>> = graph.topSymbols(last)

    private val sectionMarkers = listOf("MEMORY", "FINGERPRINT", "HALL", "GRAPH", "JOKER", "STATS")

    fun saveState() {
        try {
            val sb = StringBuilder()
            sb.append("##MEMORY##\n").append(memory.serialize()).append("\n")
            sb.append("##FINGERPRINT##\n").append(fingerprint.serialize()).append("\n")
            sb.append("##HALL##\n").append(hall.serialize()).append("\n")
            sb.append("##GRAPH##\n").append(graph.serialize()).append("\n")
            sb.append("##JOKER##\n").append(joker.serialize()).append("\n")
            sb.append("##STATS##\n").append(stats.serialize()).append("\n")
            stateFile.writeText(sb.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
        }
    }

    fun loadState() {
        try {
            if (!stateFile.exists()) return
            val text = stateFile.readText(Charsets.UTF_8)
            val sections = splitSections(text)
            sections["MEMORY"]?.let { memory.restore(it) }
            sections["FINGERPRINT"]?.let { fingerprint.restore(it) }
            sections["HALL"]?.let { hall.restore(it) }
            sections["GRAPH"]?.let { graph.restore(it) }
            sections["JOKER"]?.let { joker.restore(it) }
            sections["STATS"]?.let { stats.restore(it) }
        } catch (e: Exception) {
        }
    }

    private fun splitSections(text: String): Map<String, String> {
        val result = HashMap<String, String>()
        var current: String? = null
        val buffer = StringBuilder()
        text.lines().forEach { line ->
            val marker = sectionMarkers.find { line.trim() == "##$it##" }
            if (marker != null) {
                if (current != null) result[current!!] = buffer.toString().trimEnd('\n')
                current = marker
                buffer.clear()
            } else if (current != null) {
                buffer.append(line).append("\n")
            }
        }
        if (current != null) result[current!!] = buffer.toString().trimEnd('\n')
        return result
    }

    fun predict(history: List<String>, hot: String? = null): Prediction {
        require(history.isNotEmpty()) { "History cannot be empty" }
        val input = FeatureExtractor.sequence(history)
        val expertOutputs = listOfNotNull(lstm, transformer, dynamic)
            .mapNotNull { runCatching { it.predict(input) }.getOrNull() }

        val scores = DoubleArray(Symbols.keys.size) { 1.0 }
        if (expertOutputs.isNotEmpty()) {
            expertOutputs.forEach { out ->
                val n = minOf(out.size, scores.size)
                for (i in 0 until n) scores[i] += out[i].toDouble()
            }
        } else {
            history.takeLast(20).forEach { s ->
                val i = Symbols.keys.indexOf(s)
                if (i >= 0) scores[i] += 0.15
            }
        }

        val last = history.last()
        joker.observeRhythm(history)

        val memoryVotes = memory.vote(history)
        val memorySupport = HashMap<String, Double>()
        val memTotal = memoryVotes.values.sum().coerceAtLeast(1.0)
        memoryVotes.forEach { (s, v) ->
            val i = Symbols.keys.indexOf(s)
            if (i >= 0) { scores[i] += 0.25 * v; memorySupport[s] = v / memTotal }
        }

        val cyclePick = CycleDetector.predict(history)
        cyclePick?.let { s -> val i = Symbols.keys.indexOf(s); if (i >= 0) scores[i] += 0.5 }

        val fpPick = fingerprint.predict(history)
        fpPick?.let { s -> val i = Symbols.keys.indexOf(s); if (i >= 0) scores[i] += 0.5 }

        val graphDist = graph.distribution(last)
        graphDist.forEach { (s, p) ->
            val i = Symbols.keys.indexOf(s); if (i >= 0) scores[i] += p * 1.0
        }

        val jokerBias = joker.subtleBias(last)
        jokerBias.forEach { (s, b) ->
            val i = Symbols.keys.indexOf(s); if (i >= 0) scores[i] += b
        }

        val freqBias = biasDetector.bias(history)
        freqBias.forEach { (s, b) ->
            val i = Symbols.keys.indexOf(s); if (i >= 0) scores[i] += b * 0.3
        }

        Symbols.keys.forEachIndexed { i, s -> scores[i] -= hall.penalty(history, s) * 0.15 }

        val rep = FeatureExtractor.repetition(history)
        if (rep > 0.55) {
            val i = Symbols.keys.indexOf(last)
            if (i >= 0) scores[i] += 0.20
        }

        val entropyVal = FeatureExtractor.entropy(history)
        val bypassActive = entropyVal > 1.8

        val max = scores.maxOrNull() ?: 1.0
        val exps = scores.map { exp((it - max).coerceAtLeast(-20.0)) }
        val sum = exps.sum().coerceAtLeast(1e-9)
        val probs = exps.map { (it / sum).toFloat() }.toFloatArray()
        val order = probs.indices.sortedByDescending { probs[it] }
        val top4 = order.take(4).map { Symbols.keys[it] }
        val confidence = probs[order.first()].toDouble().coerceIn(0.0, 1.0)

        val picks = listOfNotNull(
            cyclePick,
            fpPick,
            memoryVotes.maxByOrNull { it.value }?.key,
            graphDist.maxByOrNull { it.value }?.key,
            Symbols.keys[order.first()]
        )
        val agreement = if (picks.isEmpty()) 0.0 else
            picks.groupingBy { it }.eachCount().values.max().toDouble() / picks.size

        return Prediction(
            symbol = Symbols.keys[order.first()],
            top4 = top4,
            confidence = confidence,
            probabilities = probs,
            entropy = entropyVal,
            cyclePrediction = cyclePick,
            fingerprintPrediction = fpPick,
            memorySupport = memorySupport,
            agreement = agreement,
            bypassActive = bypassActive,
            jokerActive = joker.isActive()
        )
    }

    fun completeRound(historyBefore: List<String>, hot: String?, p: Prediction, actual: String) {
        val wasCorrect = p.symbol == actual
        val inTop4 = actual in p.top4

        val r = TrainingRecord(
            System.currentTimeMillis(), historyBefore, hot, p.symbol, p.top4, actual,
            p.confidence, wasCorrect, inTop4, p.entropy,
            FeatureExtractor.repetition(historyBefore), p.cyclePrediction, p.fingerprintPrediction
        )
        store.append(r)
        memory.add(MemoryEntry(historyBefore, p.symbol, actual, p.confidence))
        fingerprint.observe(historyBefore, actual)
        hall.record(historyBefore, p.symbol, actual)

        if (historyBefore.isNotEmpty()) graph.update(historyBefore.last(), actual)
        joker.updateWisdom(wasCorrect)

        stats.total++
        if (wasCorrect) stats.correct++
        if (inTop4) stats.correctTop4++

        saveState()
    }

    fun trainingFilePath(): String = store.path()
    fun trainingFileSize(): Long = store.sizeBytes()
    fun trainingRecordCount(): Long = store.count()

    fun closeModels() {
        lstm?.close(); transformer?.close(); dynamic?.close()
        lstm = null; transformer = null; dynamic = null
    }
}
