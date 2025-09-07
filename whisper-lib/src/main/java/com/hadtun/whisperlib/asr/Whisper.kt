package com.hadtun.whisperlib.asr

import android.content.Context
import android.util.Log
import com.hadtun.whisperlib.engine.WhisperEngine
import com.hadtun.whisperlib.engine.WhisperEngineJava
import com.hadtun.whisperlib.engine.WhisperEngineNative
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class Whisper(private val context: Context) {

    interface WhisperListener {
        fun onUpdateReceived(message: String)
        fun onResultReceived(result: String)
    }

    companion object {
        private const val TAG = "Whisper"
        const val MSG_PROCESSING = "Processing..."
        const val MSG_PROCESSING_DONE = "Processing done...!"
        const val MSG_FILE_NOT_FOUND = "Input file doesn't exist..!"

        val ACTION_TRANSCRIBE = Action.TRANSCRIBE
        val ACTION_TRANSLATE = Action.TRANSLATE
    }

    enum class Action {
        TRANSLATE, TRANSCRIBE
    }

    private val mInProgress = AtomicBoolean(false)
    private val audioBufferQueue: Queue<FloatArray> = LinkedList()
    private val mWhisperEngine: WhisperEngine
    private var mAction: Action? = null
    private var mWavFilePath: String? = null
    private var mUpdateListener: WhisperListener? = null

    private val taskLock: Lock = ReentrantLock()
    private val hasTask: Condition = taskLock.newCondition()
    private var taskAvailable = false

    init {
        // Use native engine for better performance
        this.mWhisperEngine = WhisperEngineNative(context)

        // Start thread for file transcription
        val threadTranscbFile = Thread(this::transcribeFileLoop)
        threadTranscbFile.start()

        // Start thread for buffer transcription for live mic feed transcription
        val threadTranscbBuffer = Thread(this::transcribeBufferLoop)
        threadTranscbBuffer.start()
    }

    fun setListener(listener: WhisperListener) {
        this.mUpdateListener = listener
    }

    fun loadModel(modelPath: File, vocabPath: File, isMultilingual: Boolean) {
        loadModel(modelPath.absolutePath, vocabPath.absolutePath, isMultilingual)
    }

    fun loadModel(modelPath: String, vocabPath: String, isMultilingual: Boolean) {
        try {
            mWhisperEngine.initialize(modelPath, vocabPath, isMultilingual)
        } catch (e: IOException) {
            Log.e(TAG, "Error initializing model...", e)
            sendUpdate("Model initialization failed")
        }
    }

    fun unloadModel() {
        mWhisperEngine.deinitialize()
    }

    fun setAction(action: Action) {
        this.mAction = action
    }

    fun setFilePath(wavFile: String) {
        this.mWavFilePath = wavFile
    }

    fun start() {
        if (!mInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Execution is already in progress...")
            return
        }
        taskLock.lock()
        try {
            taskAvailable = true
            hasTask.signal()
        } finally {
            taskLock.unlock()
        }
    }

    fun stop() {
        mInProgress.set(false)
    }
    
    // Public method to transcribe a file directly
    fun transcribeFile(filePath: String): String {
        return try {
            if (mWhisperEngine.isInitialized) {
                val waveFile = File(filePath)
                if (waveFile.exists()) {
                    synchronized(mWhisperEngine) {
                        mWhisperEngine.transcribeFile(filePath) ?: ""
                    }
                } else {
                    Log.e(TAG, "File does not exist: $filePath")
                    ""
                }
            } else {
                Log.e(TAG, "Whisper engine not initialized")
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error transcribing file: $filePath", e)
            ""
        }
    }

    val isInProgress: Boolean
        get() = mInProgress.get()

    private fun transcribeFileLoop() {
        while (!Thread.currentThread().isInterrupted) {
            taskLock.lock()
            try {
                while (!taskAvailable) {
                    hasTask.await()
                }
                transcribeFile()
                taskAvailable = false
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                taskLock.unlock()
            }
        }
    }

    private fun transcribeFile() {
        try {
            if (mWhisperEngine.isInitialized && mWavFilePath != null) {
                val waveFile = File(mWavFilePath!!)
                if (waveFile.exists()) {
                    val startTime = System.currentTimeMillis()
                    sendUpdate(MSG_PROCESSING)

                    val result: String?
                    synchronized(mWhisperEngine) {
                        result = when (mAction) {
                            Action.TRANSCRIBE -> mWhisperEngine.transcribeFile(mWavFilePath!!)
                            Action.TRANSLATE -> {
                                Log.d(TAG, "TRANSLATE feature is not implemented")
                                null
                            }
                            null -> null
                        }
                    }
                    sendResult(result)

                    val timeTaken = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Time Taken for transcription: ${timeTaken}ms")
                    sendUpdate(MSG_PROCESSING_DONE)
                } else {
                    sendUpdate(MSG_FILE_NOT_FOUND)
                }
            } else {
                sendUpdate("Engine not initialized or file path not set")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription", e)
            sendUpdate("Transcription failed: ${e.message}")
        } finally {
            mInProgress.set(false)
        }
    }

    private fun sendUpdate(message: String) {
        mUpdateListener?.onUpdateReceived(message)
    }

    private fun sendResult(message: String?) {
        if (message != null) {
            mUpdateListener?.onResultReceived(message)
        }
    }

    /////////////////////// Live MIC feed transcription calls /////////////////////////////////
    private fun transcribeBufferLoop() {
        while (!Thread.currentThread().isInterrupted) {
            val samples = readBuffer()
            if (samples != null) {
                synchronized(mWhisperEngine) {
                    val result = mWhisperEngine.transcribeBuffer(samples)
                    sendResult(result)
                }
            }
        }
    }

    fun writeBuffer(samples: FloatArray) {
        synchronized(audioBufferQueue) {
            audioBufferQueue.add(samples)
            (audioBufferQueue as Object).notify()
        }
    }

    private fun readBuffer(): FloatArray? {
        synchronized(audioBufferQueue) {
            while (audioBufferQueue.isEmpty()) {
                try {
                    (audioBufferQueue as Object).wait()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
            }
            return audioBufferQueue.poll()
        }
    }
}
