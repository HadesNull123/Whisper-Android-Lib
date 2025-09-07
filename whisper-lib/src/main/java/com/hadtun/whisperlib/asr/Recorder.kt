package com.hadtun.whisperlib.asr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.hadtun.whisperlib.utils.WaveUtil
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class Recorder(private val mContext: Context) {

    interface RecorderListener {
        fun onUpdateReceived(message: String)
        fun onDataReceived(samples: FloatArray)
    }

    companion object {
        private const val TAG = "Recorder"
        const val ACTION_STOP = "Stop"
        const val ACTION_RECORD = "Record"
        const val MSG_RECORDING = "Recording..."
        const val MSG_RECORDING_DONE = "Recording done...!"
        const val RECORDING_DURATION = 60 // 60 seconds
    }

    private val mInProgress = AtomicBoolean(false)
    private var mWavFilePath: String? = null
    private var mListener: RecorderListener? = null
    private val lock: Lock = ReentrantLock()
    private val hasTask: Condition = lock.newCondition()
    private val fileSavedLock = Any() // Lock object for wait/notify
    private var shouldStartRecording = false
    private val workerThread: Thread
    
    // Audio level detection
    private var currentAudioLevel = 0.0f
    private val audioLevelLock = Any()

    init {
        // Initialize and start the worker thread
        workerThread = Thread(this::recordLoop)
        workerThread.start()
    }

    fun setListener(listener: RecorderListener) {
        this.mListener = listener
    }

    fun setFilePath(wavFile: String) {
        this.mWavFilePath = wavFile
    }

    fun start() {
        if (!mInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Recording is already in progress...")
            return
        }
        
        // Set default file path if not already set
        if (mWavFilePath == null) {
            val externalDir = mContext.getExternalFilesDir(null)
            mWavFilePath = File(externalDir, WaveUtil.RECORDING_FILE).absolutePath
        }
        
        lock.lock()
        try {
            shouldStartRecording = true
            hasTask.signal()
        } finally {
            lock.unlock()
        }
    }

    fun stop() {
        mInProgress.set(false)

        // Wait for the recording thread to finish
        synchronized(fileSavedLock) {
            try {
                (fileSavedLock as Object).wait() // Wait until notified by the recording thread
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt() // Restore interrupted status
            }
        }
    }

    val isInProgress: Boolean
        get() = mInProgress.get()

    private fun sendUpdate(message: String) {
        mListener?.onUpdateReceived(message)
    }

    private fun sendData(samples: FloatArray) {
        mListener?.onDataReceived(samples)
    }

    private fun recordLoop() {
        while (true) {
            lock.lock()
            try {
                while (!shouldStartRecording) {
                    hasTask.await()
                }
                shouldStartRecording = false
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            } finally {
                lock.unlock()
            }

            // Start recording process
            try {
                recordAudio()
            } catch (e: Exception) {
                Log.e(TAG, "Recording error...", e)
                sendUpdate(e.message ?: "Unknown error")
            } finally {
                mInProgress.set(false)
            }
        }
    }

    private fun recordAudio() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "AudioRecord permission is not granted")
            sendUpdate("Permission not granted for recording")
            return
        }

        sendUpdate(MSG_RECORDING)

        val channels = 1
        val bytesPerSample = 2
        val sampleRateInHz = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val audioSource = MediaRecorder.AudioSource.MIC

        val bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        val audioRecord = AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSize)
        audioRecord.startRecording()

        // Calculate maximum byte counts for different durations
        val bytesForOneSecond = sampleRateInHz * bytesPerSample * channels
        val bytesForThreeSeconds = bytesForOneSecond * 3
        val bytesForThirtySeconds = bytesForOneSecond * 30
        val bytesForSixtySeconds = bytesForOneSecond * RECORDING_DURATION

        val outputBuffer = ByteArrayOutputStream() // Buffer for saving data in wave file
        val realtimeBuffer = ByteArrayOutputStream() // Buffer for real-time processing

        val audioData = ByteArray(bufferSize)
        var totalBytesRead = 0

        while (mInProgress.get() && totalBytesRead < bytesForSixtySeconds) {
            val bytesRead = audioRecord.read(audioData, 0, bufferSize)
            if (bytesRead > 0) {
                outputBuffer.write(audioData, 0, bytesRead)  // Save all bytes read up to 60 seconds
                realtimeBuffer.write(audioData, 0, bytesRead) // Accumulate real-time audio data
                totalBytesRead += bytesRead

                // Check if realtimeBuffer has more than 3 seconds of data
                if (realtimeBuffer.size() >= bytesForThreeSeconds) {
                    val samples = convertToFloatArray(ByteBuffer.wrap(realtimeBuffer.toByteArray()))
                    realtimeBuffer.reset() // Clear the buffer for the next accumulation
                    sendData(samples) // Send real-time data for processing
                }
                
                // Calculate audio level for speech detection
                calculateAudioLevel(audioData, bytesRead)
            } else {
                Log.d(TAG, "AudioRecord error, bytes read: $bytesRead")
                break
            }
        }

        audioRecord.stop()
        audioRecord.release()

        // Save recorded audio data to file (up to 60 seconds)
        WaveUtil.createWaveFile(mWavFilePath!!, outputBuffer.toByteArray(), sampleRateInHz, channels, bytesPerSample)
        sendUpdate(MSG_RECORDING_DONE)

        // Notify the waiting thread that recording is complete
        synchronized(fileSavedLock) {
            (fileSavedLock as Object).notify() // Notify that recording is finished
        }
    }

    private fun convertToFloatArray(buffer: ByteBuffer): FloatArray {
        buffer.order(ByteOrder.nativeOrder())
        val samples = FloatArray(buffer.remaining() / 2)
        for (i in samples.indices) {
            samples[i] = buffer.short / 32768.0f
        }
        return samples
    }

    // Move file from internal storage to external storage
    private fun moveFileToSdcard(waveFilePath: String) {
        val sourceFile = File(waveFilePath)
        val externalDir = mContext.getExternalFilesDir(null) ?: return
        val destinationFile = File(externalDir, sourceFile.name)

        try {
            FileInputStream(sourceFile).use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        outputStream.write(buffer, 0, length)
                    }
                }
            }

            if (sourceFile.delete()) {
                Log.d("FileMove", "File moved successfully to ${destinationFile.absolutePath}")
            } else {
                Log.e("FileMove", "Failed to delete the original file.")
            }
        } catch (e: IOException) {
            Log.e("FileMove", "File move failed", e)
        }
    }
    
    // Calculate audio level for speech detection
    private fun calculateAudioLevel(audioData: ByteArray, bytesRead: Int) {
        synchronized(audioLevelLock) {
            var sum = 0.0
            val buffer = ByteBuffer.wrap(audioData, 0, bytesRead)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            
            // Calculate RMS (Root Mean Square) for audio level
            while (buffer.hasRemaining()) {
                val sample = buffer.short.toDouble() / Short.MAX_VALUE
                sum += sample * sample
            }
            
            currentAudioLevel = Math.sqrt(sum / (bytesRead / 2)).toFloat()
        }
    }
    
    // Get current audio level for speech detection
    fun getCurrentAudioLevel(): Float {
        synchronized(audioLevelLock) {
            return currentAudioLevel
        }
    }
}
