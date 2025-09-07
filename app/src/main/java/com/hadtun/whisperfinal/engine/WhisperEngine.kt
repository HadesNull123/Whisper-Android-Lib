package com.hadtun.whisperfinal.engine

import java.io.IOException

interface WhisperEngine {
    val isInitialized: Boolean
    fun initialize(modelPath: String, vocabPath: String, multilingual: Boolean): Boolean
    fun deinitialize()
    fun transcribeFile(wavePath: String): String
    fun transcribeBuffer(samples: FloatArray): String
}
