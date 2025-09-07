package com.hadtun.whisperlib.utils

import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.*

class WhisperUtil {
    companion object {
        private const val TAG = "WhisperUtil"

        const val WHISPER_SAMPLE_RATE = 16000
        const val WHISPER_N_FFT = 400
        const val WHISPER_N_MEL = 80
        const val WHISPER_HOP_LENGTH = 160
        const val WHISPER_CHUNK_SIZE = 30
        const val WHISPER_MEL_LEN = 3000
    }

    private val vocab = WhisperVocab()
    private val filters = WhisperFilter()
    private val mel = WhisperMel()

    // Helper functions definitions
    fun getTokenTranslate(): Int = vocab.tokenTRANSLATE

    fun getTokenTranscribe(): Int = vocab.tokenTRANSCRIBE

    fun getTokenEOT(): Int = vocab.tokenEOT

    fun getTokenSOT(): Int = vocab.tokenSOT

    fun getTokenPREV(): Int = vocab.tokenPREV

    fun getTokenSOLM(): Int = vocab.tokenSOLM

    fun getTokenNOT(): Int = vocab.tokenNOT

    fun getTokenBEG(): Int = vocab.tokenBEG

    fun getWordFromToken(token: Int): String? = vocab.tokenToWord[token]

    // Load filters and vocab data from pre-generated filters_vocab_en.bin file
    fun loadFiltersAndVocab(multilingual: Boolean, vocabPath: String): Boolean {
        try {
            // Read vocab file
            val bytes = Files.readAllBytes(Paths.get(vocabPath))
            val vocabBuf = ByteBuffer.wrap(bytes)
            vocabBuf.order(ByteOrder.nativeOrder())
            Log.d(TAG, "Vocab file size: ${vocabBuf.limit()}")

            // @magic:USEN
            val magic = vocabBuf.int
            if (magic == 0x5553454e) {
                Log.d(TAG, "Magic number: $magic")
            } else {
                Log.d(TAG, "Invalid vocab file (bad magic: $magic), $vocabPath")
                return false
            }

            // Load mel filters
            filters.nMel = vocabBuf.int
            filters.nFft = vocabBuf.int
            Log.d(TAG, "n_mel:${filters.nMel}, n_fft:${filters.nFft}")

            val filterData = ByteArray(filters.nMel * filters.nFft * Float.SIZE_BYTES)
            vocabBuf.get(filterData, 0, filterData.size)
            val filterBuf = ByteBuffer.wrap(filterData)
            filterBuf.order(ByteOrder.nativeOrder())

            filters.data = FloatArray(filters.nMel * filters.nFft)
            for (i in filters.data.indices) {
                filters.data[i] = filterBuf.float
            }

            // Load vocabulary
            val nVocab = vocabBuf.int
            Log.d(TAG, "nVocab: $nVocab")
            for (i in 0 until nVocab) {
                val len = vocabBuf.int
                val wordBytes = ByteArray(len)
                vocabBuf.get(wordBytes, 0, len)
                val word = String(wordBytes)
                vocab.tokenToWord[i] = word
            }

            // Add additional vocab ids
            val nVocabAdditional = if (!multilingual) {
                vocab.nVocabEnglish
            } else {
                vocab.nVocabMultilingual.also {
                    vocab.tokenEOT++
                    vocab.tokenSOT++
                    vocab.tokenPREV++
                    vocab.tokenSOLM++
                    vocab.tokenNOT++
                    vocab.tokenBEG++
                }
            }

            for (i in nVocab until nVocabAdditional) {
                val word = when {
                    i > vocab.tokenBEG -> "[_TT_${i - vocab.tokenBEG}]"
                    i == vocab.tokenEOT -> "[_EOT_]"
                    i == vocab.tokenSOT -> "[_SOT_]"
                    i == vocab.tokenPREV -> "[_PREV_]"
                    i == vocab.tokenNOT -> "[_NOT_]"
                    i == vocab.tokenBEG -> "[_BEG_]"
                    else -> "[_extra_token_$i]"
                }

                vocab.tokenToWord[i] = word
            }

            return true
        } catch (e: IOException) {
            Log.e(TAG, "Error loading filters and vocab", e)
            return false
        }
    }

    // nSamples size => WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE => 480000
    fun getMelSpectrogram(samples: FloatArray, nSamples: Int, nThreads: Int): FloatArray {
        val fftSize = WHISPER_N_FFT
        val fftStep = WHISPER_HOP_LENGTH

        mel.nMel = WHISPER_N_MEL
        mel.nLen = nSamples / fftStep
        mel.data = FloatArray(mel.nMel * mel.nLen)

        val hann = FloatArray(fftSize)
        for (i in 0 until fftSize) {
            hann[i] = (0.5 * (1.0 - cos(2.0 * PI * i / fftSize))).toFloat()
        }

        val nFft = 1 + fftSize / 2

        // Calculate mel values using multiple threads
        val workers = mutableListOf<Thread>()
        for (iw in 0 until nThreads) {
            val ith = iw // Capture iw in a final variable for use in the lambda
            val thread = Thread {
                Log.d(TAG, "Thread $ith started.")

                val fftIn = FloatArray(fftSize) { 0.0f }
                val fftOut = FloatArray(fftSize * 2)

                for (i in ith until mel.nLen step nThreads) {
                    val offset = i * fftStep

                    // apply Hanning window
                    for (j in 0 until fftSize) {
                        fftIn[j] = if (offset + j < nSamples) {
                            hann[j] * samples[offset + j]
                        } else {
                            0.0f
                        }
                    }

                    // FFT -> mag^2
                    fft(fftIn, fftOut)
                    for (j in 0 until fftSize) {
                        fftOut[j] = fftOut[2 * j] * fftOut[2 * j] + fftOut[2 * j + 1] * fftOut[2 * j + 1]
                    }

                    for (j in 1 until fftSize / 2) {
                        fftOut[j] += fftOut[fftSize - j]
                    }

                    // mel spectrogram
                    for (j in 0 until mel.nMel) {
                        var sum = 0.0
                        for (k in 0 until nFft) {
                            sum += (fftOut[k] * filters.data[j * nFft + k])
                        }

                        if (sum < 1e-10) {
                            sum = 1e-10
                        }

                        sum = log10(sum)
                        mel.data[j * mel.nLen + i] = sum.toFloat()
                    }
                }
            }
            workers.add(thread)
            thread.start()
        }

        // Wait for all threads to finish
        workers.forEach { worker ->
            try {
                worker.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        // clamping and normalization
        var mmax = -1e20
        for (i in 0 until mel.nMel * mel.nLen) {
            if (mel.data[i] > mmax) {
                mmax = mel.data[i].toDouble()
            }
        }

        mmax -= 8.0
        for (i in 0 until mel.nMel * mel.nLen) {
            if (mel.data[i] < mmax) {
                mel.data[i] = mmax.toFloat()
            }
            mel.data[i] = ((mel.data[i] + 4.0) / 4.0).toFloat()
        }

        return mel.data
    }

    private fun dft(input: FloatArray, output: FloatArray) {
        val inSize = input.size
        for (k in 0 until inSize) {
            var re = 0.0f
            var im = 0.0f
            for (n in 0 until inSize) {
                val angle = (2 * PI * k * n / inSize).toFloat()
                re += input[n] * cos(angle)
                im -= input[n] * sin(angle)
            }
            output[k * 2 + 0] = re
            output[k * 2 + 1] = im
        }
    }

    private fun fft(input: FloatArray, output: FloatArray) {
        val inSize = input.size
        if (inSize == 1) {
            output[0] = input[0]
            output[1] = 0.0f
            return
        }

        if (inSize % 2 == 1) {
            dft(input, output)
            return
        }

        val even = FloatArray(inSize / 2)
        val odd = FloatArray(inSize / 2)

        var indxEven = 0
        var indxOdd = 0
        for (i in 0 until inSize) {
            if (i % 2 == 0) {
                even[indxEven] = input[i]
                indxEven++
            } else {
                odd[indxOdd] = input[i]
                indxOdd++
            }
        }

        val evenFft = FloatArray(inSize)
        val oddFft = FloatArray(inSize)

        fft(even, evenFft)
        fft(odd, oddFft)
        for (k in 0 until inSize / 2) {
            val theta = (2 * PI * k / inSize).toFloat()
            val re = cos(theta)
            val im = -sin(theta)
            val reOdd = oddFft[2 * k + 0]
            val imOdd = oddFft[2 * k + 1]
            output[2 * k + 0] = evenFft[2 * k + 0] + re * reOdd - im * imOdd
            output[2 * k + 1] = evenFft[2 * k + 1] + re * imOdd + im * reOdd
            output[2 * (k + inSize / 2) + 0] = evenFft[2 * k + 0] - re * reOdd + im * imOdd
            output[2 * (k + inSize / 2) + 1] = evenFft[2 * k + 1] - re * imOdd - im * reOdd
        }
    }

    // Helper class definitions
    private class WhisperVocab {
        val golden_generated_ids = intArrayOf(
            50257, 50362, 1770, 13, 2264, 346, 353, 318,
            262, 46329, 286, 262, 3504, 6097, 11, 290, 356, 389, 9675, 284, 7062
        )

        // Token types
        var tokenEOT = 50256 // end of transcript
        var tokenSOT = 50257 // start of transcript
        var tokenPREV = 50360
        var tokenSOLM = 50361 // ??
        var tokenNOT = 50362 // no timestamps
        var tokenBEG = 50363

        // Available tasks
        val tokenTRANSLATE = 50358
        val tokenTRANSCRIBE = 50359

        // Vocab types
        val nVocabEnglish = 51864       // for english only vocab
        val nVocabMultilingual = 51865  // for multilingual vocab
        val tokenToWord = mutableMapOf<Int, String>()
    }

    private class WhisperFilter {
        var nMel = 0
        var nFft = 0
        lateinit var data: FloatArray
    }

    private class WhisperMel {
        var nLen = 0
        var nMel = 0
        lateinit var data: FloatArray
    }

    private class InputLang(
        val name: String,
        val code: String,
        val id: Long
    ) {
        // Initialize the list of input language objects
        fun getLangList(): ArrayList<InputLang> {
            val inputLangList = ArrayList<InputLang>()
            inputLangList.add(InputLang("English", "en", 50259))
            inputLangList.add(InputLang("Spanish", "es", 50262))
            inputLangList.add(InputLang("Hindi", "hi", 50276))
            inputLangList.add(InputLang("Telugu", "te", 50299))
            return inputLangList
        }
    }
}
