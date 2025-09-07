# WhisperLib - Android Speech Recognition Library

[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?&style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![TensorFlow](https://img.shields.io/badge/TensorFlow-FF6F00?style=for-the-badge&logo=TensorFlow&logoColor=white)](https://tensorflow.org/)

WhisperLib is a powerful Android library built on TensorFlow Lite that enables you to easily integrate speech recognition capabilities into your Android applications with high performance and accuracy.

## ✨ Features

- 🎤 **Real-time audio recording** with high quality
- 🧠 **Speech recognition** using Whisper model
- 🌍 **Multilingual support** (English, Vietnamese, Chinese, etc.)
- ⚡ **High performance** with native C++ engine
- 🔧 **Simple API** and easy to use
- 📱 **Compatible** with Android API 29+

## 📋 System Requirements

- **Android API Level**: 29+ (Android 10+)
- **Architecture**: ARM64-v8a, ARMv7
- **RAM**: Minimum 2GB
- **Storage**: ~50MB for model and library

## 🚀 Installation

### Step 1: Add AAR to your project

1. Download `whisper-lib-release.aar` from [Releases](../../releases)
2. Copy the file to your project's `libs` directory:
   ```
   app/libs/whisper-lib-release.aar
   ```

### Step 2: Configure build.gradle

Add to your `app/build.gradle.kts`:

```kotlin
android {
    // ... existing configuration
    
    packagingOptions {
        pickFirst '**/libc++_shared.so'
        pickFirst '**/libjsc.so'
    }
}

dependencies {
    // WhisperLib AAR
    implementation(files("libs/whisper-lib-release.aar"))
    
    // Or use fileTree
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    
    // Required dependencies
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
}
```

### Step 3: Add permissions

Add to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

### Step 4: Request runtime permissions

```kotlin
class MainActivity : AppCompatActivity() {
    private val REQUEST_RECORD_AUDIO = 1001
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request audio recording permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int, 
        permissions: Array<String>, 
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, can use WhisperLib
            }
        }
    }
}
```

## 📖 Usage

### Initialize WhisperLib

```kotlin
import com.hadtun.whisperlib.WhisperLib

class MainActivity : AppCompatActivity() {
    private lateinit var whisperLib: WhisperLib
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize WhisperLib
        whisperLib = WhisperLib.init(this)
        
        // Load default model
        val success = whisperLib.loadDefaultModel()
        if (success) {
            Log.d("WhisperLib", "Model loaded successfully")
        }
    }
}
```

### Record and recognize speech

```kotlin
class VoiceRecognitionActivity : AppCompatActivity() {
    private lateinit var whisperLib: WhisperLib
    
    private fun startRecording() {
        val success = whisperLib.startRecording()
        if (success) {
            Log.d("VoiceRecognition", "Recording started")
            // Update UI to show recording state
        }
    }
    
    private fun stopRecording() {
        val success = whisperLib.stopRecording()
        if (success) {
            Log.d("VoiceRecognition", "Recording stopped")
            
            // Get recognition result
            val transcription = whisperLib.getTranscription()
            Log.d("VoiceRecognition", "Transcription: $transcription")
            
            // Display result on UI
            updateUI(transcription)
        }
    }
    
    private fun updateUI(text: String) {
        runOnUiThread {
            // Update TextView with result
            textViewResult.text = text
        }
    }
}
```

### Using with Press-and-Hold Button

```kotlin
class ChatActivity : AppCompatActivity() {
    private lateinit var whisperLib: WhisperLib
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        
        whisperLib = WhisperLib.init(this)
        whisperLib.loadDefaultModel()
        
        setupChatButton()
    }
    
    private fun setupChatButton() {
        val chatButton = findViewById<Button>(R.id.btnChat)
        
        chatButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Start recording when press and hold
                    whisperLib.startRecording()
                    chatButton.setBackgroundColor(Color.RED)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Stop recording and recognize when release button
                    whisperLib.stopRecording()
                    chatButton.setBackgroundColor(Color.BLUE)
                    
                    // Get result and display
                    val result = whisperLib.getTranscription()
                    displayResult(result)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun displayResult(text: String) {
        runOnUiThread {
            textViewResult.text = text
        }
    }
}
```

### Transcribe from audio file

```kotlin
class AudioFileActivity : AppCompatActivity() {
    private lateinit var whisperLib: WhisperLib
    
    private fun transcribeAudioFile(filePath: String) {
        // Transcribe from existing audio file
        val result = whisperLib.transcribeFile(filePath)
        
        if (result.isNotEmpty()) {
            Log.d("AudioFile", "Transcription: $result")
            // Process result
        } else {
            Log.e("AudioFile", "Transcription failed")
        }
    }
}
```

### Check status

```kotlin
class StatusActivity : AppCompatActivity() {
    private lateinit var whisperLib: WhisperLib
    
    private fun checkStatus() {
        // Check if currently recording
        val isRecording = whisperLib.isRecording()
        
        // Get recorded audio file path
        val audioPath = whisperLib.getRecordedAudioPath()
        
        Log.d("Status", "Is recording: $isRecording")
        Log.d("Status", "Audio path: $audioPath")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Release resources
        whisperLib.release()
    }
}
```

## 🔧 API Reference

### WhisperLib Class

#### Main Methods:

| Method | Description | Return Type |
|--------|-------------|-------------|
| `init(context)` | Initialize WhisperLib | `WhisperLib` |
| `loadDefaultModel()` | Load default model | `Boolean` |
| `loadModel(path, isMultilingual)` | Load custom model | `Boolean` |
| `startRecording()` | Start recording | `Boolean` |
| `stopRecording()` | Stop recording | `Boolean` |
| `getTranscription()` | Get recognition result | `String` |
| `transcribeFile(filePath)` | Transcribe from file | `String` |
| `isRecording()` | Check recording status | `Boolean` |
| `getRecordedAudioPath()` | Get audio file path | `String?` |
| `release()` | Release resources | `Unit` |

#### Complete usage example:

```kotlin
class CompleteExample : AppCompatActivity() {
    private lateinit var whisperLib: WhisperLib
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Initialize
        whisperLib = WhisperLib.init(this)
        
        // 2. Load model
        val modelLoaded = whisperLib.loadDefaultModel()
        if (!modelLoaded) {
            Log.e("WhisperLib", "Failed to load model")
            return
        }
        
        // 3. Setup UI
        setupUI()
    }
    
    private fun setupUI() {
        val recordButton = findViewById<Button>(R.id.btnRecord)
        val resultText = findViewById<TextView>(R.id.tvResult)
        
        recordButton.setOnClickListener {
            if (whisperLib.isRecording()) {
                // Stop recording
                whisperLib.stopRecording()
                val result = whisperLib.getTranscription()
                resultText.text = result
                recordButton.text = "Start Recording"
            } else {
                // Start recording
                whisperLib.startRecording()
                recordButton.text = "Stop Recording"
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        whisperLib.release()
    }
}
```

## 🎯 Use Cases

### 1. Voice Notes App
```kotlin
// Record and convert to text note
whisperLib.startRecording()
// ... after user finishes speaking
whisperLib.stopRecording()
val note = whisperLib.getTranscription()
saveNoteToDatabase(note)
```

### 2. Voice Search
```kotlin
// Search using voice
whisperLib.startRecording()
// ... user speaks search keywords
whisperLib.stopRecording()
val searchQuery = whisperLib.getTranscription()
performSearch(searchQuery)
```

### 3. Voice Commands
```kotlin
// Control app using voice
whisperLib.startRecording()
// ... user speaks command
whisperLib.stopRecording()
val command = whisperLib.getTranscription()
executeVoiceCommand(command)
```

### 4. Real-time Transcription
```kotlin
// Convert speech to text in real-time
whisperLib.startRecording()
// Can use timer for periodic recognition
val timer = Timer()
timer.scheduleAtFixedRate(object : TimerTask() {
    override fun run() {
        if (whisperLib.isRecording()) {
            val partialResult = whisperLib.getTranscription()
            updateRealtimeUI(partialResult)
        }
    }
}, 0, 2000) // Every 2 seconds
```

## ⚠️ Important Notes

### Performance
- **First model load**: May take 2-5 seconds
- **Recognition time**: 1-3 seconds depending on audio length
- **RAM usage**: ~100-200MB when active
- **Battery**: Consumes battery when recording continuously

### Best Practices
1. **Always check permissions** before using
2. **Release resources** in `onDestroy()`
3. **Handle errors** when loading model or recording
4. **Test on real device** to ensure audio quality
5. **Use background thread** for heavy tasks

### Troubleshooting

#### Common Issues:

**1. "Permission denied"**
```kotlin
// Solution: Check and request permission
if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
    != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(this, 
        arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
}
```

**2. "Model not loaded"**
```kotlin
// Solution: Check model path and file exists
val success = whisperLib.loadDefaultModel()
if (!success) {
    Log.e("WhisperLib", "Check if model files exist in assets")
}
```

**3. "No audio input"**
```kotlin
// Solution: Check microphone and audio settings
if (!whisperLib.isRecording()) {
    Log.e("WhisperLib", "Check microphone permissions and hardware")
}
```

## 📱 Demo App

See detailed examples in the `demo/` folder with features:
- Basic voice recording
- Press-and-hold chat button
- File audio transcription
- Real-time voice commands

## 🤝 Contributing

We welcome contributions! Please:

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

## 📞 Contact

- **GitHub Issues**: [Create an issue](../../issues)

## 🙏 Acknowledgments

- [OpenAI Whisper](https://github.com/openai/whisper) - Speech recognition model
- [TensorFlow Lite](https://www.tensorflow.org/lite) - Machine learning framework
- [Android AudioRecord](https://developer.android.com/reference/android/media/AudioRecord) - Android audio recording API

---

⭐ **If this library is helpful, please give us a star!** ⭐
