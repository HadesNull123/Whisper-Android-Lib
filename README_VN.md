# WhisperLib - Thư viện Speech Recognition cho Android

[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?&style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![TensorFlow](https://img.shields.io/badge/TensorFlow-FF6F00?style=for-the-badge&logo=TensorFlow&logoColor=white)](https://tensorflow.org/)

WhisperLib là một thư viện Android mạnh mẽ được xây dựng trên TensorFlow Lite, cho phép bạn tích hợp tính năng nhận dạng giọng nói (Speech Recognition) vào ứng dụng Android một cách dễ dàng và hiệu quả.

## ✨ Tính năng

- 🎤 **Ghi âm real-time** với chất lượng cao
- 🧠 **Nhận dạng giọng nói** sử dụng mô hình Whisper
- 🌍 **Hỗ trợ đa ngôn ngữ** (Multilingual)
- ⚡ **Hiệu suất cao** với native C++ engine
- 🔧 **API đơn giản** và dễ sử dụng
- 📱 **Tương thích** với Android API 29+

## 📋 Yêu cầu hệ thống

- **Android API Level**: 29+ (Android 10+)
- **Architecture**: ARM64-v8a, ARMv7
- **RAM**: Tối thiểu 2GB
- **Storage**: ~50MB cho model và thư viện

## 🚀 Cài đặt

### Bước 1: Thêm AAR vào project

1. Tải file `whisper-lib-release.aar` từ [Releases](../../releases)
2. Copy file vào thư mục `libs` trong project của bạn:
   ```
   app/libs/whisper-lib-release.aar
   ```

### Bước 2: Cấu hình build.gradle

Thêm vào file `app/build.gradle.kts`:

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
    
    // Hoặc sử dụng fileTree
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    
    // Required dependencies
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
}
```

### Bước 3: Thêm permissions

Thêm vào `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

### Bước 4: Yêu cầu permissions runtime

```kotlin
class MainActivity : AppCompatActivity() {
    private val REQUEST_RECORD_AUDIO = 1001
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Yêu cầu quyền ghi âm
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
                // Quyền đã được cấp, có thể sử dụng WhisperLib
            }
        }
    }
}
```

## 📖 Cách sử dụng

### Khởi tạo WhisperLib

```kotlin
import com.hadtun.whisperlib.WhisperLib

class MainActivity : AppCompatActivity() {
    private lateinit var whisperLib: WhisperLib
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Khởi tạo WhisperLib
        whisperLib = WhisperLib.init(this)
        
        // Load model mặc định
        val success = whisperLib.loadDefaultModel()
        if (success) {
            Log.d("WhisperLib", "Model loaded successfully")
        }
    }
}
```

### Ghi âm và nhận dạng giọng nói

```kotlin
class VoiceRecognitionActivity : AppCompatActivity() {
    private lateinit var whisperLib: WhisperLib
    
    private fun startRecording() {
        val success = whisperLib.startRecording()
        if (success) {
            Log.d("VoiceRecognition", "Recording started")
            // Cập nhật UI để hiển thị trạng thái đang ghi âm
        }
    }
    
    private fun stopRecording() {
        val success = whisperLib.stopRecording()
        if (success) {
            Log.d("VoiceRecognition", "Recording stopped")
            
            // Lấy kết quả nhận dạng
            val transcription = whisperLib.getTranscription()
            Log.d("VoiceRecognition", "Transcription: $transcription")
            
            // Hiển thị kết quả lên UI
            updateUI(transcription)
        }
    }
    
    private fun updateUI(text: String) {
        runOnUiThread {
            // Cập nhật TextView với kết quả
            textViewResult.text = text
        }
    }
}
```

### Sử dụng với Button Press-and-Hold

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
                    // Bắt đầu ghi âm khi nhấn và giữ
                    whisperLib.startRecording()
                    chatButton.setBackgroundColor(Color.RED)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Dừng ghi âm và nhận dạng khi thả nút
                    whisperLib.stopRecording()
                    chatButton.setBackgroundColor(Color.BLUE)
                    
                    // Lấy kết quả và hiển thị
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

### Nhận dạng từ file audio

```kotlin
class AudioFileActivity : AppCompatActivity() {
    private lateinit var whisperLib: WhisperLib
    
    private fun transcribeAudioFile(filePath: String) {
        // Nhận dạng từ file audio có sẵn
        val result = whisperLib.transcribeFile(filePath)
        
        if (result.isNotEmpty()) {
            Log.d("AudioFile", "Transcription: $result")
            // Xử lý kết quả
        } else {
            Log.e("AudioFile", "Transcription failed")
        }
    }
}
```

### Kiểm tra trạng thái

```kotlin
class StatusActivity : AppCompatActivity() {
    private lateinit var whisperLib: WhisperLib
    
    private fun checkStatus() {
        // Kiểm tra xem có đang ghi âm không
        val isRecording = whisperLib.isRecording()
        
        // Lấy đường dẫn file audio đã ghi
        val audioPath = whisperLib.getRecordedAudioPath()
        
        Log.d("Status", "Is recording: $isRecording")
        Log.d("Status", "Audio path: $audioPath")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Giải phóng tài nguyên
        whisperLib.release()
    }
}
```

## 🔧 API Reference

### WhisperLib Class

#### Methods chính:

| Method | Mô tả | Return Type |
|--------|-------|-------------|
| `init(context)` | Khởi tạo WhisperLib | `WhisperLib` |
| `loadDefaultModel()` | Load model mặc định | `Boolean` |
| `loadModel(path, isMultilingual)` | Load model tùy chỉnh | `Boolean` |
| `startRecording()` | Bắt đầu ghi âm | `Boolean` |
| `stopRecording()` | Dừng ghi âm | `Boolean` |
| `getTranscription()` | Lấy kết quả nhận dạng | `String` |
| `transcribeFile(filePath)` | Nhận dạng từ file | `String` |
| `isRecording()` | Kiểm tra trạng thái ghi âm | `Boolean` |
| `getRecordedAudioPath()` | Lấy đường dẫn file audio | `String?` |
| `release()` | Giải phóng tài nguyên | `Unit` |

#### Ví dụ sử dụng đầy đủ:

```kotlin
class CompleteExample : AppCompatActivity() {
    private lateinit var whisperLib: WhisperLib
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Khởi tạo
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
                // Dừng ghi âm
                whisperLib.stopRecording()
                val result = whisperLib.getTranscription()
                resultText.text = result
                recordButton.text = "Start Recording"
            } else {
                // Bắt đầu ghi âm
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

## 🎯 Các trường hợp sử dụng

### 1. Voice Notes App
```kotlin
// Ghi âm và chuyển thành text note
whisperLib.startRecording()
// ... sau khi người dùng nói xong
whisperLib.stopRecording()
val note = whisperLib.getTranscription()
saveNoteToDatabase(note)
```

### 2. Voice Search
```kotlin
// Tìm kiếm bằng giọng nói
whisperLib.startRecording()
// ... người dùng nói từ khóa tìm kiếm
whisperLib.stopRecording()
val searchQuery = whisperLib.getTranscription()
performSearch(searchQuery)
```

### 3. Voice Commands
```kotlin
// Điều khiển ứng dụng bằng giọng nói
whisperLib.startRecording()
// ... người dùng nói lệnh
whisperLib.stopRecording()
val command = whisperLib.getTranscription()
executeVoiceCommand(command)
```

### 4. Real-time Transcription
```kotlin
// Chuyển đổi giọng nói thành text real-time
whisperLib.startRecording()
// Có thể sử dụng timer để nhận dạng định kỳ
val timer = Timer()
timer.scheduleAtFixedRate(object : TimerTask() {
    override fun run() {
        if (whisperLib.isRecording()) {
            val partialResult = whisperLib.getTranscription()
            updateRealtimeUI(partialResult)
        }
    }
}, 0, 2000) // Mỗi 2 giây
```

## ⚠️ Lưu ý quan trọng

### Performance
- **Lần đầu load model**: Có thể mất 2-5 giây
- **Thời gian nhận dạng**: 1-3 giây tùy thuộc độ dài audio
- **RAM usage**: ~100-200MB khi hoạt động
- **Battery**: Tiêu tốn pin khi ghi âm liên tục

### Best Practices
1. **Luôn kiểm tra permissions** trước khi sử dụng
2. **Release resources** trong `onDestroy()`
3. **Xử lý lỗi** khi load model hoặc ghi âm
4. **Test trên device thật** để đảm bảo chất lượng audio
5. **Sử dụng background thread** cho các tác vụ nặng

### Troubleshooting

#### Lỗi thường gặp:

**1. "Permission denied"**
```kotlin
// Giải pháp: Kiểm tra và yêu cầu quyền
if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
    != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(this, 
        arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
}
```

**2. "Model not loaded"**
```kotlin
// Giải pháp: Kiểm tra model path và file tồn tại
val success = whisperLib.loadDefaultModel()
if (!success) {
    Log.e("WhisperLib", "Check if model files exist in assets")
}
```

**3. "No audio input"**
```kotlin
// Giải pháp: Kiểm tra microphone và audio settings
if (!whisperLib.isRecording()) {
    Log.e("WhisperLib", "Check microphone permissions and hardware")
}
```

## 📱 Demo App

Xem thêm ví dụ chi tiết trong thư mục `demo/` với các tính năng:
- Basic voice recording
- Press-and-hold chat button
- File audio transcription
- Real-time voice commands

## 🤝 Đóng góp

Chúng tôi hoan nghênh mọi đóng góp! Hãy:

1. Fork repository
2. Tạo feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to branch (`git push origin feature/AmazingFeature`)
5. Tạo Pull Request

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

## 📞 Liên hệ

- **GitHub Issues**: [Tạo issue](../../issues)

## 🙏 Acknowledgments

- [OpenAI Whisper](https://github.com/openai/whisper) - Mô hình nhận dạng giọng nói
- [TensorFlow Lite](https://www.tensorflow.org/lite) - Framework machine learning
- [Android AudioRecord](https://developer.android.com/reference/android/media/AudioRecord) - API ghi âm Android

---

⭐ **Nếu thư viện này hữu ích, hãy cho chúng tôi một star!** ⭐
