package com.gambitai

import android.app.Activity
import android.os.Bundle
import android.widget.*
import com.gambitai.engine.*
import java.io.File

class MainActivity : Activity() {
    private lateinit var engine: V12LiteEngine
    private lateinit var historyText: TextView
    private lateinit var predictionText: TextView
    private var history = mutableListOf<String>()
    private var pending: Prediction? = null
    private var pendingHistory = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = V12LiteEngine(this)
        engine.loadModels()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16,16,16,16)
        }
        historyText = TextView(this)
        predictionText = TextView(this)
        root.addView(historyText)
        root.addView(predictionText)

        val predict = Button(this).apply {
            text = "Predict"
            setOnClickListener {
                if (history.isNotEmpty()) {
                    pendingHistory = history.toList()
                    pending = engine.predict(history)
                    showPrediction()
                } else Toast.makeText(this@MainActivity, "Add history first", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(predict)

        val grid = GridLayout(this).apply { columnCount = 5 }
        Symbols.keys.forEach { s ->
            grid.addView(Button(this@MainActivity).apply {
                text = Symbols.emoji[s]
                setOnClickListener {
                    val p = pending
                    if (p != null) {
                        engine.completeRound(pendingHistory, null, p, s)
                        pending = null
                    }
                    history.add(s)
                    if (history.size > 100) history = history.takeLast(100).toMutableList()
                    updateHistory()
                    predictionText.text = "Tap Predict"
                }
            })
        }
        root.addView(grid)

        val export = Button(this).apply {
            text = "Export Training Data"
            setOnClickListener {
                val src = File(engine.trainingFilePath())
                if (!src.exists()) {
                    Toast.makeText(this@MainActivity, "No training data yet", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "application/x-ndjson"
                    putExtra(android.content.Intent.EXTRA_TITLE, "gambit_training.jsonl")
                }
                startActivityForResult(intent, 91)
            }
        }
        root.addView(export)
        setContentView(root)
        updateHistory()
    }

    private fun showPrediction() {
        val p = pending ?: return
        predictionText.text = "Prediction: ${Symbols.emoji[p.symbol]}  Confidence: ${"%.1f".format(p.confidence*100)}%\nTop 4: ${p.top4.joinToString { Symbols.emoji[it] ?: it }}"
    }

    private fun updateHistory() {
        historyText.text = "History: ${history.takeLast(20).joinToString(" ") { Symbols.emoji[it] ?: it }}"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 91 && resultCode == RESULT_OK && data?.data != null) {
            contentResolver.openOutputStream(data.data!!)?.use { out ->
                File(engine.trainingFilePath()).inputStream().use { it.copyTo(out) }
            }
            Toast.makeText(this, "Training file exported", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        engine.closeModels()
        super.onDestroy()
    }
}
