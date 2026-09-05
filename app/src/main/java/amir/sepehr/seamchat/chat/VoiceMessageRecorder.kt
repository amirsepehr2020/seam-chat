package amir.sepehr.seamchat.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceMessageRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    fun start(): File {
        check(recorder == null) { "Already recording" }
        val file = File(context.cacheDir, "seam_voice_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(44_100)
        r.setAudioEncodingBitRate(128_000)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        output = file
        return file
    }

    fun stop(): File? {
        val r = recorder ?: return null
        return try {
            r.stop()
            output
        } finally {
            r.release()
            recorder = null
            output = null
        }
    }

    fun cancel() {
        recorder?.runCatching { stop() }
        recorder?.release()
        recorder = null
        output?.delete()
        output = null
    }
}
