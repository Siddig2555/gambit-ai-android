package com.gambitai.engine

data class TrainingRecord(
    val timestamp: Long,
    val history: List<String>,
    val hotSymbol: String?,
    val predicted: String,
    val top4: List<String>,
    val actual: String,
    val confidence: Double,
    val correct: Boolean,
    val top4Correct: Boolean,
    val entropy: Double,
    val repetition: Double,
    val cyclePrediction: String?,
    val fingerprintPrediction: String?
)
