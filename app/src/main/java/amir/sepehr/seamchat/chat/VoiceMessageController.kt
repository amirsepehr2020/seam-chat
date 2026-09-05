package amir.sepehr.seamchat.chat

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class VoiceMessageController(context: Context) {
    private val recorder = VoiceMessageRecorder(context.applicationContext)
    private var currentFile: File? = null

    fun start(): Boolean = runCatching {
        currentFile = recorder.start()
    }.isSuccess

    fun stop(): Uri? = recorder.stop()?.let { file ->
        currentFile = null
        Uri.fromFile(file)
    }

    fun cancel() {
        recorder.cancel()
        currentFile = null
    }
}
