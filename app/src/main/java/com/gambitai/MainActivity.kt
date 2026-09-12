package com.gambitai

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.gambitai.engine.*
import java.io.File

class MainActivity : Activity() {
    private lateinit var engine: V12LiteEngine

    private var history = mutableListOf<String>()
    private var tempRes = mutableListOf<String>()
    private var tempHot: String? = null
    private var currMode = "results"
    private var pending: Prediction? = null
    private var pendingHistory: List<String> = listOf()

    private val PREFS_NAME = "gambitai_prefs"
    private val KEY_HISTORY = "history"

    // ألوان الثيم الغامق (نفس ألوان النسخة الأصلية)
    private val bgDark = Color.parseColor("#0f172a")
    private val cardBg = Color.parseColor("#1e293b")
    private val cardBorder = Color.parseColor("#334155")
    private val textLight = Color.parseColor("#e2e8f0")
    private val textMuted = Color.parseColor("#94a3b8")
    private val accentBlue = Color.parseColor("#4fc3f7")
    private val progressTrack = Color.parseColor("#334155")
    private val progressFill = Color.parseColor("#0072ff")
    private val memBadge = Color.parseColor("#6200ea")
    private val jokerBadge = Color.parseColor("#e65100")
    private val bypassBadge = Color.parseColor("#dc2626")
    private val cycleBadge = Color.parseColor("#0891b2")
    private val fpBadge = Color.parseColor("#059669")
    private val agreeBadge = Color.parseColor("#d97706")

    // عناصر واجهة يتم تحديثها
    private lateinit var statsText: TextView
    private lateinit var modeInputBtn: Button
    private lateinit var modeHotBtn: Button
    private lateinit var currentDisplay: TextView
    private lateinit var resultText: TextView
    private lateinit var cardsContainer: LinearLayout
    private lateinit var summaryText: TextView
    private lateinit var statePanel: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger(this).install()
        engine = V12LiteEngine(this)
        engine.loadModels()
        loadHistory()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgDark)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        // شريط الإحصائيات
        statsText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundedDrawable(Color.parseColor("#1a1f2e"), 8f)
        }
        root.addView(statsText, matchWrap().withMarginBottom(8))

        // زرارين تبديل الوضع (إدخال / HOT)
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        modeInputBtn = Button(this).apply {
            text = "🎮 إدخال"
            setOnClickListener { currMode = "results"; refreshModeButtons() }
        }
        modeHotBtn = Button(this).apply {
            text = "🔥 HOT"
            setOnClickListener { currMode = "hot"; refreshModeButtons() }
        }
        modeRow.addView(modeInputBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        modeRow.addView(modeHotBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(modeRow, matchWrap().withMarginBottom(8))

        // مربع العرض الحالي
        currentDisplay = TextView(this).apply {
            setTextColor(accentBlue)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(14), dp(10), dp(14))
            background = roundedDrawable(Color.parseColor("#1e2433"), 8f)
        }
        root.addView(currentDisplay, matchWrap().withMarginBottom(10))

        // شبكة الرموز (صفين)
        val keys = Symbols.keys.toList()
        val half = (keys.size + 1) / 2
        listOf(keys.take(half), keys.drop(half)).forEach { rowKeys ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowKeys.forEach { s ->
                val btn = Button(this@MainActivity).apply {
                    text = Symbols.emoji[s]
                    textSize = 20f
                    setBackgroundColor(Color.parseColor("#2a2f3e"))
                    setTextColor(Color.WHITE)
                    setOnClickListener { onSymbolTapped(s) }
                }
                row.addView(btn, LinearLayout.LayoutParams(0, dp(56), 1f).withMargin(2))
            }
            root.addView(row, matchWrap())
        }
        root.addView(spacer(8))

        // أزرار التحكم
        val ctrlRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val clearBtn = Button(this).apply {
            text = "🗑️ مسح"
            setOnClickListener { tempRes.clear(); updateCurrentDisplay() }
        }
        val predictBtn = Button(this).apply {
            text = "🔮 العقل"
            setBackgroundColor(Color.parseColor("#16a34a"))
            setTextColor(Color.WHITE)
            setOnClickListener { onConsultMind() }
        }
        val saveBtn = Button(this).apply {
            text = "💾 حفظ"
            setBackgroundColor(Color.parseColor("#0ea5e9"))
            setTextColor(Color.WHITE)
            setOnClickListener { exportTrainingData() }
        }
        val resetBtn = Button(this).apply {
            text = "🔄 تصفير"
            setBackgroundColor(bypassBadge)
            setTextColor(Color.WHITE)
            setOnClickListener { resetAll() }
        }
        listOf(clearBtn, predictBtn, saveBtn, resetBtn).forEach {
            ctrlRow.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).withMargin(2))
        }
        root.addView(ctrlRow, matchWrap().withMarginBottom(8))

        // سطر النتيجة (يظهر بعد كل جولة)
        resultText = TextView(this).apply {
            setTextColor(textLight)
            textSize = 13f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root.addView(resultText, matchWrap().withMarginBottom(6))

        // حاوية كروت التوقع (top4)
        cardsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(cardsContainer, matchWrap())

        // سطر ملخص المصادر
        summaryText = TextView(this).apply {
            setTextColor(textMuted)
            textSize = 11f
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(summaryText, matchWrap())

        // لوحة الحالة الداخلية
        statePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.parseColor("#1e293b"), 8f)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        root.addView(statePanel, matchWrap().withMarginTop(6))

        val scroll = ScrollView(this).apply { setBackgroundColor(bgDark) }
        scroll.addView(root)
        setContentView(scroll)

        refreshModeButtons()
        updateCurrentDisplay()
        updateStatsBar()
        renderStatePanel(null)
    }

    // ---------- منطق التفاعل ----------

    private fun onSymbolTapped(s: String) {
        if (currMode == "results") tempRes.add(s) else tempHot = s
        updateCurrentDisplay()
    }

    private fun onConsultMind() {
        if (tempRes.isEmpty()) {
            Toast.makeText(this, "دوس على رمز الأول", Toast.LENGTH_SHORT).show()
            return
        }
        // فترة إحماء أولية (زي النسخة الأصلية): لو العدد الكلي لسه أقل من 15، بس اجمع من غير توقع
        if (history.size + tempRes.size < 15) {
            history.addAll(tempRes)
            tempRes.clear()
            saveHistory()
            updateCurrentDisplay()
            updateStatsBar()
            return
        }

        val actual = tempRes.last()

        if (pending != null) {
            val p = pending!!
            engine.completeRound(pendingHistory, tempHot, p, actual)
            val wasCorrect = p.symbol == actual
            resultText.visibility = View.VISIBLE
            resultText.text = "${if (wasCorrect) "✅" else "❌"} توقعنا: ${Symbols.emoji[p.symbol]} | النتيجة: ${Symbols.emoji[actual]} | ${if (wasCorrect) "SUCCESS" else "FAILURE"}"
        }

        history.addAll(tempRes)
        if (history.size > 500) history = history.takeLast(500).toMutableList()
        saveHistory()

        val newPrediction = engine.predict(history, tempHot)
        pending = newPrediction
        pendingHistory = history.toList()

        renderCards(newPrediction)
        renderSummary(newPrediction)
        renderStatePanel(newPrediction)
        updateStatsBar()

        tempRes.clear()
        updateCurrentDisplay()
    }

    private fun resetAll() {
        history.clear()
        tempRes.clear()
        tempHot = null
        pending = null
        pendingHistory = listOf()
        engine.stats.reset()
        saveHistory()
        updateCurrentDisplay()
        updateStatsBar()
        cardsContainer.removeAllViews()
        summaryText.text = ""
        resultText.visibility = View.GONE
        renderStatePanel(null)
        Toast.makeText(this, "تم تصفير كل شيء", Toast.LENGTH_SHORT).show()
    }

    private fun exportTrainingData() {
        val src = File(engine.trainingFilePath())
        if (!src.exists()) {
            Toast.makeText(this, "لا توجد بيانات تدريب بعد", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/x-ndjson"
            putExtra(Intent.EXTRA_TITLE, "gambit_training.jsonl")
        }
        startActivityForResult(intent, 91)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 91 && resultCode == RESULT_OK && data?.data != null) {
            contentResolver.openOutputStream(data.data!!)?.use { out ->
                File(engine.trainingFilePath()).inputStream().use { it.copyTo(out) }
            }
            Toast.makeText(this, "تم الحفظ بنجاح", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- بناء الواجهة الديناميكية ----------

    private fun renderCards(p: Prediction) {
        cardsContainer.removeAllViews()
        val order = p.probabilities.indices.sortedByDescending { p.probabilities[it] }
        order.take(4).forEachIndexed { i, idx ->
            val sym = Symbols.keys[idx]
            val prob = p.probabilities[idx]
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = strokedDrawable(cardBg, cardBorder, 8f)
                setPadding(dp(10), dp(10), dp(10), dp(10))
            }
            val title = TextView(this).apply {
                text = "#${i + 1}  ${Symbols.emoji[sym]}  $sym"
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
            }
            val percentRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val percentText = TextView(this).apply {
                text = "${"%.1f".format(prob * 100)}%  "
                setTextColor(Color.WHITE)
                textSize = 14f
            }
            percentRow.addView(percentText)

            if (i == 0) {
                if (p.memorySupport[sym] != null && p.memorySupport[sym]!! > 0.1) {
                    percentRow.addView(badge("Mem:${(p.memorySupport[sym]!! * 100).toInt()}%", memBadge))
                }
                if (p.jokerActive) percentRow.addView(badge("🃏", jokerBadge))
                if (p.bypassActive) percentRow.addView(badge("⚡Bypass", bypassBadge))
                if (p.cyclePrediction == sym) percentRow.addView(badge("🔄", cycleBadge))
                if (p.fingerprintPrediction == sym) percentRow.addView(badge("🧬", fpBadge))
                if (p.agreement > 0.6) percentRow.addView(badge("🤝", agreeBadge))
            }

            val track = FrameLayout(this).apply {
                background = roundedDrawable(progressTrack, 3f)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)).withMarginTop(6)
            }
            val fill = View(this).apply { setBackgroundColor(progressFill) }
            val fillWidth = (prob * 100).toInt().coerceIn(0, 100)
            track.addView(fill, FrameLayout.LayoutParams(0, dp(4)).also {
                it.width = (fillWidth * 3)
            })

            card.addView(title)
            card.addView(percentRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER })
            card.addView(track)
            cardsContainer.addView(card, matchWrap().withMarginBottom(8))
        }
    }

    private fun renderSummary(p: Prediction) {
        val loaded = engine.neuralExpertsLoaded()
        val expertsInfo = if (loaded > 0) "🧠 نماذج عصبية محمّلة: $loaded" else "🧠 النماذج العصبية: تعمل بالاحتياطي (fallback)"
        summaryText.text = "$expertsInfo | 🃏 Joker:${"%+.2f".format(engine.joker.internalState)}\n" +
                "🔄 Cycle:${if (p.cyclePrediction != null) "نشط" else "—"} | 🧬 FP:${if (p.fingerprintPrediction != null) "نشط" else "—"} | 🤝 Agr:${(p.agreement * 100).toInt()}%"
    }

    private fun renderStatePanel(p: Prediction?) {
        statePanel.removeAllViews()
        val title = TextView(this).apply {
            text = "🧠 حالة V12 Lite الداخلية"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 0, 0, dp(6))
        }
        statePanel.addView(title)

        if (p == null) {
            statePanel.addView(TextView(this).apply {
                text = "لا توجد بيانات بعد — دوس العقل بعد إدخال 15 نتيجة على الأقل"
                setTextColor(textMuted)
                textSize = 11f
            })
            return
        }

        val entColor = if (p.entropy > 1.8) bypassBadge else Color.parseColor("#10b981")
        val entLabel = if (p.entropy > 1.8) "🚨 طوارئ" else "✅ طبيعي"
        addStateLine("Entropy: ${"%.3f".format(p.entropy)} $entLabel", entColor)
        addStateLine("Bypass: ${if (p.bypassActive) "⚡ نشط — الذاكرة تقود" else "—"}", textMuted)

        val agrColor = if (p.agreement > 0.6) Color.parseColor("#10b981")
                        else if (p.agreement > 0.4) Color.parseColor("#f59e0b")
                        else bypassBadge
        val agrLabel = if (p.agreement > 0.6) "✅ متفقون" else if (p.agreement > 0.4) "⚠️ متوسط" else "❌ خلاف"
        addStateLine("🤝 Agreement: ${(p.agreement * 100).toInt()}% $agrLabel", agrColor)

        val last = history.lastOrNull()
        if (last != null) {
            val top = engine.graphTopAfter(last)
            val graphStr = if (top.isEmpty()) "لا توجد بيانات بعد"
                else top.joinToString("  ") { "${Symbols.emoji[it.first]} ×${it.second.toInt()}" }
            addStateLine("🕸️ Knowledge Graph بعد ${Symbols.emoji[last]}: $graphStr", textMuted)
        }

        addStateLine("⚠️ Hall of Failures: ${engine.hallStats()}", textMuted)
        addStateLine("💾 الذاكرة: ${engine.memoryCount()} تجربة | 🛡️ Shield: ${engine.shieldCount()} | 🃏 Joker Confidence: ${(engine.joker.confidence * 100).toInt()}%", textMuted)
    }

    private fun addStateLine(text: String, color: Int) {
        statePanel.addView(TextView(this).apply {
            this.text = text
            setTextColor(color)
            textSize = 11f
            setPadding(0, dp(2), 0, dp(2))
        })
    }

    private fun refreshModeButtons() {
        modeInputBtn.setBackgroundColor(if (currMode == "results") Color.parseColor("#374151") else Color.parseColor("#1f2937"))
        modeHotBtn.setBackgroundColor(if (currMode == "hot") Color.parseColor("#374151") else Color.parseColor("#1f2937"))
        modeInputBtn.setTextColor(Color.WHITE)
        modeHotBtn.setTextColor(Color.WHITE)
    }

    private fun updateCurrentDisplay() {
        currentDisplay.text = if (currMode == "results") {
            if (tempRes.isEmpty()) "..." else tempRes.joinToString(" ") { Symbols.emoji[it] ?: it }
        } else {
            if (tempHot == null) "HOT: ..." else "HOT: ${Symbols.emoji[tempHot]}"
        }
    }

    private fun updateStatsBar() {
        statsText.text = "الجولات: ${engine.stats.total} | Top1: ${"%.1f".format(engine.stats.topAccuracy)}% | " +
                "Top4: ${"%.1f".format(engine.stats.top4Accuracy)}% | الذاكرة: ${engine.memoryCount()} | " +
                "🛡️ Shield: ${engine.shieldCount()} | 🃏 Joker: ${(engine.joker.confidence * 100).toInt()}%"
    }

    // ---------- تخزين محلي ----------

    private fun saveHistory() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(KEY_HISTORY, history.joinToString(",")).apply()
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val saved = prefs.getString(KEY_HISTORY, "") ?: ""
        if (saved.isNotEmpty()) {
            history = saved.split(",").filter { it.isNotBlank() }.toMutableList()
        }
    }

    // ---------- أدوات مساعدة للرسم ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun LinearLayout.LayoutParams.withMarginBottom(v: Int): LinearLayout.LayoutParams {
        bottomMargin = dp(v); return this
    }
    private fun LinearLayout.LayoutParams.withMarginTop(v: Int): LinearLayout.LayoutParams {
        topMargin = dp(v); return this
    }
    private fun LinearLayout.LayoutParams.withMargin(v: Int): LinearLayout.LayoutParams {
        setMargins(dp(v), dp(v), dp(v), dp(v)); return this
    }

    private fun spacer(h: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(h))
    }

    private fun roundedDrawable(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius * resources.displayMetrics.density
    }

    private fun strokedDrawable(fillColor: Int, strokeColor: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(fillColor)
        cornerRadius = radius * resources.displayMetrics.density
        setStroke(dp(1), strokeColor)
    }

    private fun badge(text: String, color: Int): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 10f
        background = roundedDrawable(color, 4f)
        setPadding(dp(5), dp(2), dp(5), dp(2))
        (layoutParams as? LinearLayout.LayoutParams)?.leftMargin = dp(4)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.leftMargin = dp(4) }
    }

    override fun onDestroy() {
        engine.closeModels()
        super.onDestroy()
    }
}
