package com.hadtun.whisperlib.engine

import android.content.Context
import android.util.Log
import com.hadtun.whisperlib.utils.WaveUtil
import com.hadtun.whisperlib.utils.WhisperUtil
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class WhisperEngineJava(private val mContext: Context) : WhisperEngine {
    companion object {
        private const val TAG = "WhisperEngineJava"
    }

    private val mWhisperUtil = WhisperUtil()
    private var mIsInitialized = false
    private var mInterpreter: Interpreter? = null

    override val isInitialized: Boolean
        get() = mIsInitialized

    override fun initialize(modelPath: String, vocabPath: String, multilingual: Boolean): Boolean {
        return try {
            // Load model
            loadModel(modelPath)
            Log.d(TAG, "Model is loaded...$modelPath")

            // Load filters and vocab
            val ret = mWhisperUtil.loadFiltersAndVocab(multilingual, vocabPath)
            if (ret) {
                mIsInitialized = true
                Log.d(TAG, "Filters and Vocab are loaded...$vocabPath")
            } else {
                mIsInitialized = false
                Log.d(TAG, "Failed to load Filters and Vocab...")
            }

            mIsInitialized
        } catch (e: IOException) {
            Log.e(TAG, "Error initializing model", e)
            mIsInitialized = false
            false
        }
    }

    // Unload the model by closing the interpreter
    override fun deinitialize() {
        mInterpreter?.let { interpreter ->
            interpreter.close()
            mInterpreter = null // Optional: Set to null to avoid accidental reuse
        }
    }

    override fun transcribeFile(wavePath: String): String {
        // Calculate Mel spectrogram
        Log.d(TAG, "Calculating Mel spectrogram...")
        val melSpectrogram = getMelSpectrogram(wavePath)
        Log.d(TAG, "Mel spectrogram is calculated...!")

        // Perform inference
        val result = runInference(melSpectrogram)
        Log.d(TAG, "Inference is executed...!")

        return result
    }

    override fun transcribeBuffer(samples: FloatArray): String {
        // TODO: Implement buffer transcription
        return ""
    }

    // Load TFLite model
    private fun loadModel(modelPath: String) {
        val fileInputStream = FileInputStream(modelPath)
        val fileChannel = fileInputStream.channel
        val startOffset = 0L
        val declaredLength = fileChannel.size()
        val tfliteModel = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

        // Set the number of threads for inference
        val options = Interpreter.Options()
        options.setNumThreads(Runtime.getRuntime().availableProcessors())

        mInterpreter = Interpreter(tfliteModel, options)
    }

    private fun getMelSpectrogram(wavePath: String): FloatArray {
        // Get samples in PCM_FLOAT format
        val samples = WaveUtil.getSamples(wavePath)

        val fixedInputSize = WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE
        val inputSamples = FloatArray(fixedInputSize)
        val copyLength = minOf(samples.size, fixedInputSize)
        System.arraycopy(samples, 0, inputSamples, 0, copyLength)

        val cores = Runtime.getRuntime().availableProcessors()
        return mWhisperUtil.getMelSpectrogram(inputSamples, inputSamples.size, cores)
    }

    private fun runInference(inputData: FloatArray): String {
        val interpreter = mInterpreter ?: return ""
        
        // Create input tensor
        val inputTensor = interpreter.getInputTensor(0)
        val inputBuffer = TensorBuffer.createFixedSize(inputTensor.shape(), inputTensor.dataType())

        // Create output tensor
        val outputTensor = interpreter.getOutputTensor(0)
        val outputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), DataType.FLOAT32)

        // Load input data
        val inputSize = inputTensor.shape()[0] * inputTensor.shape()[1] * inputTensor.shape()[2] * Float.SIZE_BYTES
        val inputBuf = ByteBuffer.allocateDirect(inputSize)
        inputBuf.order(ByteOrder.nativeOrder())
        for (input in inputData) {
            inputBuf.putFloat(input)
        }

        inputBuffer.loadBuffer(inputBuf)

        // Run inference
        interpreter.run(inputBuffer.buffer, outputBuffer.buffer)

        // Retrieve the results
        val outputLen = outputBuffer.intArray.size
        Log.d(TAG, "output_len: $outputLen")
        val result = StringBuilder()
        for (i in 0 until outputLen) {
            val token = outputBuffer.buffer.int
            if (token == mWhisperUtil.getTokenEOT())
                break

            // Get word for token and Skip additional token
            if (token < mWhisperUtil.getTokenEOT()) {
                val word = mWhisperUtil.getWordFromToken(token)
                result.append(word)
            } else {
                when (token) {
                    mWhisperUtil.getTokenTranscribe() -> Log.d(TAG, "It is Transcription...")
                    mWhisperUtil.getTokenTranslate() -> Log.d(TAG, "It is Translation...")
                }

                val word = mWhisperUtil.getWordFromToken(token)
                Log.d(TAG, "Skipping token: $token, word: $word")
            }
        }

        return result.toString()
    }

    private fun printTensorDump(message: String, tensor: Tensor) {
        Log.d(TAG, "Output Tensor Dump ===>")
        Log.d(TAG, "  shape.length: ${tensor.shape().size}")
        for (i in tensor.shape().indices)
            Log.d(TAG, "    shape[$i]: ${tensor.shape()[i]}")
        Log.d(TAG, "  dataType: ${tensor.dataType()}")
        Log.d(TAG, "  name: ${tensor.name()}")
        Log.d(TAG, "  numBytes: ${tensor.numBytes()}")
        Log.d(TAG, "  index: ${tensor.index()}")
        Log.d(TAG, "  numDimensions: ${tensor.numDimensions()}")
        Log.d(TAG, "  numElements: ${tensor.numElements()}")
        Log.d(TAG, "  shapeSignature.length: ${tensor.shapeSignature().size}")
        Log.d(TAG, "  quantizationParams.getScale: ${tensor.quantizationParams().scale}")
        Log.d(TAG, "  quantizationParams.getZeroPoint: ${tensor.quantizationParams().zeroPoint}")
        Log.d(TAG, "==================================================================")
    }
}
