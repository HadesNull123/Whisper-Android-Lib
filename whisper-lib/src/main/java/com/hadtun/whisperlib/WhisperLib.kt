package com.hadtun.whisperlib

import android.content.Context
import android.util.Log
import com.hadtun.whisperlib.asr.Recorder
import com.hadtun.whisperlib.asr.Whisper
import com.hadtun.whisperlib.utils.WaveUtil
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * WhisperLib - Main API for Whisper Speech Recognition
 * 
 * Usage:
 * 1. Initialize: WhisperLib.init(context)
 * 2. Load model: whisperLib.loadModel(modelPath, isMultilingual)
 * 3. Start recording: whisperLib.startRecording()
 * 4. Stop recording: whisperLib.stopRecording()
 * 5. Get transcription: whisperLib.getTranscription()
 */
class WhisperLib private constructor() {
    
    companion object {
        private const val TAG = "WhisperLib"
        private const val DEFAULT_MODEL = "whisper-tiny.tflite"
        private val EXTENSIONS_TO_COPY = arrayOf("tflite", "bin", "wav", "pcm")
        
        @Volatile
        private var INSTANCE: WhisperLib? = null
        
        /**
         * Initialize WhisperLib singleton
         */
        fun init(context: Context): WhisperLib {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WhisperLib().also { 
                    INSTANCE = it
                    it.initialize(context)
                }
            }
        }
        
        /**
         * Get current instance
         */
        fun getInstance(): WhisperLib {
            return INSTANCE ?: throw IllegalStateException("WhisperLib not initialized. Call init() first.")
        }
    }
    
    private var context: Context? = null
    private var mRecorder: Recorder? = null
    private var mWhisper: Whisper? = null
    private var sdcardDataFolder: File? = null
    private var isInitialized = false
    private var lastTranscription = ""
    
    private fun initialize(context: Context) {
        this.context = context
        sdcardDataFolder = context.getExternalFilesDir(null)
        
        // Copy assets to external storage
        copyAssetsToSdcard(context, sdcardDataFolder!!, EXTENSIONS_TO_COPY)
        
        // Initialize components
        mRecorder = Recorder(context)
        mWhisper = Whisper(context)
        
        // Set up recorder listener
        mRecorder?.setListener(object : Recorder.RecorderListener {
            override fun onUpdateReceived(message: String) {
                Log.d(TAG, "Recorder update: $message")
            }
            
            override fun onDataReceived(samples: FloatArray) {
                // Audio data received - can be used for real-time processing
                Log.d(TAG, "Audio data received: ${samples.size} samples")
            }
        })
        
        isInitialized = true
        Log.d(TAG, "WhisperLib initialized successfully")
    }
    
    /**
     * Load Whisper model
     * @param modelPath Path to the .tflite model file
     * @param isMultilingual Whether the model supports multiple languages
     */
    fun loadModel(modelPath: String, isMultilingual: Boolean = true): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "WhisperLib not initialized")
            return false
        }
        
        return try {
            val vocabFile = if (isMultilingual) "filters_vocab_multilingual.bin" else "filters_vocab_en.bin"
            mWhisper?.loadModel(modelPath, vocabFile, isMultilingual)
            Log.d(TAG, "Model loaded successfully: $modelPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            false
        }
    }
    
    /**
     * Load default model from assets
     */
    fun loadDefaultModel(): Boolean {
        val defaultModelPath = File(sdcardDataFolder, DEFAULT_MODEL).absolutePath
        return loadModel(defaultModelPath, true)
    }
    
    /**
     * Start recording audio
     */
    fun startRecording(): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "WhisperLib not initialized")
            return false
        }
        
        return try {
            mRecorder?.start()
            Log.d(TAG, "Recording started")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            false
        }
    }
    
    /**
     * Stop recording audio
     */
    fun stopRecording(): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "WhisperLib not initialized")
            return false
        }
        
        return try {
            mRecorder?.stop()
            Log.d(TAG, "Recording stopped")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            false
        }
    }
    
    /**
     * Get the last transcription result
     */
    fun getTranscription(): String {
        return lastTranscription
    }
    
    /**
     * Transcribe a specific audio file
     * @param audioFilePath Path to the audio file
     */
    fun transcribeFile(audioFilePath: String): String {
        if (!isInitialized) {
            Log.e(TAG, "WhisperLib not initialized")
            return ""
        }
        
        return try {
            val result = mWhisper?.transcribeFile(audioFilePath) ?: ""
            lastTranscription = result
            Log.d(TAG, "Transcription result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error transcribing file", e)
            ""
        }
    }
    
    /**
     * Check if recording is in progress
     */
    fun isRecording(): Boolean {
        return mRecorder?.isInProgress ?: false
    }
    
    /**
     * Get the path to the recorded audio file
     */
    fun getRecordedAudioPath(): String? {
        return mRecorder?.let { recorder ->
            val externalDir = context?.getExternalFilesDir(null)
            File(externalDir, WaveUtil.RECORDING_FILE).absolutePath
        }
    }
    
    /**
     * Release resources
     */
    fun release() {
        mRecorder?.stop()
        mWhisper?.stop()
        isInitialized = false
        Log.d(TAG, "WhisperLib released")
    }
    
    /**
     * Copy assets to external storage
     */
    private fun copyAssetsToSdcard(context: Context, sdcardDataFolder: File, extensionsToCopy: Array<String>) {
        try {
            val assetManager = context.assets
            val files = assetManager.list("")
            
            if (files != null) {
                for (file in files) {
                    val extension = file.substringAfterLast(".", "")
                    if (extensionsToCopy.contains(extension)) {
                        val destinationFile = File(sdcardDataFolder, file)
                        if (!destinationFile.exists()) {
                            assetManager.open(file).use { inputStream ->
                                FileOutputStream(destinationFile).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            Log.d(TAG, "Copied asset: $file")
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error copying assets", e)
        }
    }
}
