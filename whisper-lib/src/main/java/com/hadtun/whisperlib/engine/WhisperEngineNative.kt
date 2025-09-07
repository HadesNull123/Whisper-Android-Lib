package com.hadtun.whisperlib.engine

import android.content.Context
import android.util.Log

class WhisperEngineNative(private val mContext: Context) : WhisperEngine {
    companion object {
        private const val TAG = "WhisperEngineNative"
        
        init {
            System.loadLibrary("audioEngine")
        }
    }

    private val nativePtr: Long // Native pointer to the TFLiteEngine instance
    private var mIsInitialized = false

    init {
        nativePtr = createTFLiteEngine()
    }

    override val isInitialized: Boolean
        get() = mIsInitialized

    override fun initialize(modelPath: String, vocabPath: String, multilingual: Boolean): Boolean {
        loadModel(modelPath, multilingual)
        Log.d(TAG, "Model is loaded...$modelPath")

        mIsInitialized = true
        return true
    }

    override fun deinitialize() {
        freeModel()
    }

    override fun transcribeBuffer(samples: FloatArray): String {
        return transcribeBuffer(nativePtr, samples)
    }

    override fun transcribeFile(wavePath: String): String {
        return transcribeFile(nativePtr, wavePath)
    }

    private fun loadModel(modelPath: String, isMultilingual: Boolean): Int {
        return loadModel(nativePtr, modelPath, isMultilingual)
    }

    private fun freeModel() {
        freeModel(nativePtr)
    }

    // Native methods
    private external fun createTFLiteEngine(): Long
    private external fun loadModel(nativePtr: Long, modelPath: String, isMultilingual: Boolean): Int
    private external fun freeModel(nativePtr: Long)
    private external fun transcribeBuffer(nativePtr: Long, samples: FloatArray): String
    private external fun transcribeFile(nativePtr: Long, waveFile: String): String
}
