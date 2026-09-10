package com.gambitai.engine

import android.content.Context
import java.io.File
import java.util.Locale

class TrainingDataStore(private val context: Context) {
    private val file: File get() = File(context.filesDir, "gambit_training.jsonl")

    fun append(r: TrainingRecord) {
        file.appendText(toJson(r) + "\n", Charsets.UTF_8)
    }

    fun path(): String = file.absolutePath
    fun sizeBytes(): Long = if (file.exists()) file.length() else 0L
    fun count(): Long = if (!file.exists()) 0 else file.useLines { it.count().toLong() }

    fun exportTo(destination: File) {
        file.copyTo(destination, overwrite = true)
    }

    private fun esc(s: String): String = s.replace("\\","\\\\").replace("\"","\\\"")
    private fun arr(xs: List<String>): String = xs.joinToString(",","[","]") { "\"${esc(it)}\"" }

    private fun toJson(r: TrainingRecord): String = buildString {
        append("{")
        append("\"timestamp\":${r.timestamp},")
        append("\"history\":${arr(r.history)},")
        append("\"hotSymbol\":${r.hotSymbol?.let { "\"${esc(it)}\"" } ?: "null"},")
        append("\"predicted\":\"${esc(r.predicted)}\",")
        append("\"top4\":${arr(r.top4)},")
        append("\"actual\":\"${esc(r.actual)}\",")
        append("\"confidence\":${String.format(Locale.US, "%.6f", r.confidence)},")
        append("\"correct\":${r.correct},")
        append("\"top4Correct\":${r.top4Correct},")
        append("\"entropy\":${String.format(Locale.US, "%.6f", r.entropy)},")
        append("\"repetition\":${String.format(Locale.US, "%.6f", r.repetition)},")
        append("\"cyclePrediction\":${r.cyclePrediction?.let { "\"${esc(it)}\"" } ?: "null"},")
        append("\"fingerprintPrediction\":${r.fingerprintPrediction?.let { "\"${esc(it)}\"" } ?: "null"}")
        append("}")
    }
}
