package com.gambitai.engine

import android.content.Context
import ai.onnxruntime.*
import java.nio.FloatBuffer

class OnnxModel(context: Context, assetName: String) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    init {
        val bytes = context.assets.open("models/$assetName").use { it.readBytes() }
        session = env.createSession(bytes)
    }

    fun predict(input: FloatArray): FloatArray {
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 20, FeatureExtractor.FEATURE_DIM))
        tensor.use {
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                val v = result[0].value
                return when (v) {
                    is Array<*> -> flatten(v).map { (it as Number).toFloat() }.toFloatArray()
                    is FloatArray -> v
                    else -> error("Unexpected ONNX output")
                }
            }
        }
    }

    private fun flatten(x: Any?): List<Any> = when (x) {
        is Array<*> -> x.flatMap { flatten(it) }
        is FloatArray -> x.toList()
        else -> listOf(x as Any)
    }

    override fun close() { session.close() }
}
