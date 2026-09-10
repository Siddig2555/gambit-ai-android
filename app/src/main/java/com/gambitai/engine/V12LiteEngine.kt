package com.gambitai.engine

import android.content.Context
import kotlin.math.exp

data class Prediction(
    val symbol: String,
    val top4: List<String>,
    val confidence: Double,
    val probabilities: FloatArray,
    val entropy: Double,
    val cyclePrediction: String?,
    val fingerprintPrediction: String?
)

class V12LiteEngine(private val context: Context) {
    private val memory = LiteMemory()
    private val fingerprint = Fingerprint()
    private val hall = HallOfFailures()
    private val store = TrainingDataStore(context)

    private var lstm: OnnxModel? = null
    private var transformer: OnnxModel? = null
    private var dynamic: OnnxModel? = null

    fun loadModels() {
        closeModels()
        fun tryLoad(name: String): OnnxModel? = runCatching { OnnxModel(context, name) }.getOrNull()
        lstm = tryLoad("lstm.onnx")
        transformer = tryLoad("transformer.onnx")
        dynamic = tryLoad("dynamic.onnx")
    }

    fun predict(history: List<String>, hot: String? = null): Prediction {
        require(history.isNotEmpty()) { "History cannot be empty" }
        val input = FeatureExtractor.sequence(history)
        val outputs = listOfNotNull(lstm, transformer, dynamic).mapNotNull { runCatching { it.predict(input) }.getOrNull() }

        val scores = DoubleArray(Symbols.keys.size) { 1.0 }
        if (outputs.isNotEmpty()) {
            outputs.forEach { out ->
                val n = minOf(out.size, scores.size)
                for (i in 0 until n) scores[i] += out[i].toDouble()
            }
        } else {
            // Safe fallback when models are not yet installed.
            history.takeLast(20).forEach { s ->
                val i = Symbols.keys.indexOf(s)
                if (i >= 0) scores[i] += 0.15
            }
        }

        memory.vote(history).forEach { (s,v) ->
            val i = Symbols.keys.indexOf(s); if (i >= 0) scores[i] += 0.25 * v
        }
        CycleDetector.predict(history)?.let { s ->
            val i = Symbols.keys.indexOf(s); if (i >= 0) scores[i] += 0.5
        }
        fingerprint.predict(history)?.let { s ->
            val i = Symbols.keys.indexOf(s); if (i >= 0) scores[i] += 0.5
        }

        Symbols.keys.forEachIndexed { i,s -> scores[i] -= hall.penalty(history,s) * 0.15 }

        val rep = FeatureExtractor.repetition(history)
        if (rep > 0.55) {
            val last = history.last()
            val i = Symbols.keys.indexOf(last)
            if (i >= 0) scores[i] += 0.20
        }

        val max = scores.maxOrNull() ?: 1.0
        val exps = scores.map { exp((it-max).coerceAtLeast(-20.0)) }
        val sum = exps.sum().coerceAtLeast(1e-9)
        val probs = exps.map { (it/sum).toFloat() }.toFloatArray()
        val order = probs.indices.sortedByDescending { probs[it] }
        val top4 = order.take(4).map { Symbols.keys[it] }
        val confidence = probs[order.first()].toDouble().coerceIn(0.0,1.0)

        return Prediction(
            Symbols.keys[order.first()], top4, confidence, probs,
            FeatureExtractor.entropy(history),
            CycleDetector.predict(history),
            fingerprint.predict(history)
        )
    }

    fun completeRound(historyBefore: List<String>, hot: String?, p: Prediction, actual: String) {
        val r = TrainingRecord(
            System.currentTimeMillis(), historyBefore, hot, p.symbol, p.top4, actual,
            p.confidence, p.symbol == actual, actual in p.top4, p.entropy,
            FeatureExtractor.repetition(historyBefore), p.cyclePrediction, p.fingerprintPrediction
        )
        store.append(r)
        memory.add(MemoryEntry(historyBefore, p.symbol, actual, p.confidence))
        fingerprint.observe(historyBefore, actual)
        hall.record(historyBefore, p.symbol, actual)
    }

    fun trainingFilePath(): String = store.path()
    fun trainingFileSize(): Long = store.sizeBytes()
    fun trainingRecordCount(): Long = store.count()

    fun closeModels() {
        lstm?.close(); transformer?.close(); dynamic?.close()
        lstm = null; transformer = null; dynamic = null
    }
}
