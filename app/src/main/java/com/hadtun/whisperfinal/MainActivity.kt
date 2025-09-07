package com.hadtun.whisperfinal

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.hadtun.whisperfinal.asr.Recorder
import com.hadtun.whisperfinal.asr.Whisper
import com.hadtun.whisperfinal.utils.WaveUtil
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.ArrayList

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val DEFAULT_MODEL_TO_USE = "whisper-tiny.tflite"
        private val EXTENSIONS_TO_COPY = arrayOf("tflite", "bin", "wav", "pcm")
        private const val SILENCE_THRESHOLD_MS = 2000L // 2 seconds of silence to cut sentence
    }

    private lateinit var tvStatus: TextView
    private lateinit var tvResult: TextView
    private lateinit var fabCopy: FloatingActionButton
    private lateinit var btnChat: Button

    private var mRecorder: Recorder? = null
    private var mWhisper: Whisper? = null

    private var sdcardDataFolder: File? = null
    private var selectedTfliteFile: File? = null

    // Recording state variables
    private var isRecording = false
    private var currentSentenceBuffer = ByteArrayOutputStream()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Copy assets to external storage
        sdcardDataFolder = this.getExternalFilesDir(null)
        copyAssetsToSdcard(this, sdcardDataFolder!!, EXTENSIONS_TO_COPY)

        // Initialize default model
        selectedTfliteFile = File(sdcardDataFolder, DEFAULT_MODEL_TO_USE)

        setupViews()
        setupAudioComponents()

        // Check record permission
        checkRecordPermission()
    }

    private fun setupViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvResult = findViewById(R.id.tvResult)
        fabCopy = findViewById(R.id.fabCopy)
        btnChat = findViewById(R.id.btnChat)

        // Setup button press and hold listener
        btnChat.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startRecording()
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    stopRecording()
                    true
                }
                else -> false
            }
        }

        // Setup copy button
        fabCopy.setOnClickListener {
            copyTextToClipboard()
        }

        // Initialize status
        updateStatus("Press and hold CHAT to speak")
        updateResult("No transcription yet...")
    }

    private fun setupAudioComponents() {
        try {
            mRecorder = Recorder(this)
            mWhisper = Whisper(this)
            
            // Set up recorder listener for audio data collection
            mRecorder?.setListener(object : Recorder.RecorderListener {
                override fun onUpdateReceived(message: String) {
                    updateStatus(message)
                }
                
                override fun onDataReceived(samples: FloatArray) {
                    // Collect audio data for transcription
                    addSamplesToBuffer(samples)
                }
            })
            
            // Load the default model
            loadModel()
            
            Log.d(TAG, "Audio components initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing audio components", e)
            updateStatus("Error initializing audio components")
        }
    }

    private fun loadModel() {
        try {
            val isMultilingual = !selectedTfliteFile?.name?.contains(".en.tflite")!!
            val vocabFile = if (isMultilingual) "filters_vocab_multilingual.bin" else "filters_vocab_en.bin"
            
            mWhisper?.loadModel(selectedTfliteFile?.absolutePath!!, vocabFile, isMultilingual)
            Log.d(TAG, "Model loaded successfully: ${selectedTfliteFile?.name}")
            updateStatus("Model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            updateStatus("Error loading model")
        }
    }

    private fun startRecording() {
        if (isRecording) return
        
        try {
            isRecording = true
            updateStatus("Recording... Hold to speak")
            btnChat.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            
            // Clear previous buffer
            currentSentenceBuffer.reset()
            
            // Start recording
            mRecorder?.start()
            
            Log.d(TAG, "Recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            updateStatus("Error starting recording")
            isRecording = false
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        
        try {
            isRecording = false
            updateStatus("Processing...")
            
            // Stop recording
            mRecorder?.stop()
            
            // Process recorded audio
            if (currentSentenceBuffer.size() > 0) {
                processCurrentSentence()
            } else {
                updateStatus("No audio recorded")
            }
            
            // Reset UI
            btnChat.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
            updateStatus("Press and hold CHAT to speak")
            
            Log.d(TAG, "Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            updateStatus("Error stopping recording")
        }
    }

    private fun addSamplesToBuffer(samples: FloatArray) {
        // Convert float samples to bytes and add to buffer
        val byteBuffer = java.nio.ByteBuffer.allocate(samples.size * 2)
        byteBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            val shortValue = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            byteBuffer.putShort(shortValue.toShort())
        }
        currentSentenceBuffer.write(byteBuffer.array())
    }

    private fun processCurrentSentence() {
        if (currentSentenceBuffer.size() == 0) return
        
        try {
            // Save current sentence to file
            val sentenceFile = File(sdcardDataFolder, "current_sentence.wav")
            val audioData = currentSentenceBuffer.toByteArray()
            
            // Create WAV file from audio data
            WaveUtil.createWaveFile(
                sentenceFile.absolutePath,
                audioData,
                16000, // Sample rate
                1, // Mono
                2 // 16-bit
            )
            
            // Transcribe the sentence
            updateStatus("Transcribing...")
            val transcription = mWhisper?.transcribeFile(sentenceFile.absolutePath) ?: ""
            
            if (transcription.isNotEmpty()) {
                updateResult(transcription)
                Log.d(TAG, "Transcription: $transcription")
            } else {
                updateStatus("No speech detected")
            }
            
            // Clear buffer for next recording
            currentSentenceBuffer.reset()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing sentence", e)
            updateStatus("Error processing audio")
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            tvStatus.text = message
        }
    }

    private fun updateResult(text: String) {
        runOnUiThread {
            tvResult.text = text
        }
    }

    private fun copyTextToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Transcription", tvResult.text)
        clipboard.setPrimaryClip(clip)
        updateStatus("Text copied to clipboard")
    }

    private fun checkRecordPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        mRecorder?.stop()
        mWhisper?.stop()
    }

    // Utility functions from original code
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