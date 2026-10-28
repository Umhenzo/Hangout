package com.hangout.app

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import java.io.File

object HangoutAudioData {
    private const val MAX_BASE64_CHARS = 700_000

    fun startRecording(context: Context): Pair<MediaRecorder, File> {
        val file = File(context.cacheDir, "hangout_voice_${System.currentTimeMillis()}.m4a")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioEncodingBitRate(24000)
        recorder.setAudioSamplingRate(22050)
        recorder.setMaxFileSize(350_000)
        recorder.setOutputFile(file.absolutePath)
        recorder.prepare()
        recorder.start()
        return recorder to file
    }

    fun encode(file: File): String? {
        if (!file.exists() || file.length() <= 0L || file.length() > 350_000L) return null
        val value = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        return value.takeIf { it.length <= MAX_BASE64_CHARS }
    }

    fun play(context: Context, data: String, onFinished: () -> Unit = {}) {
        try {
            val bytes = Base64.decode(data, Base64.DEFAULT)
            val file = File(context.cacheDir, "hangout_play_${System.currentTimeMillis()}.m4a")
            file.writeBytes(bytes)
            val player = MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                try { it.release() } catch (_: Exception) { }
                file.delete()
                onFinished()
            }
            player.setOnErrorListener { mp, _, _ ->
                try { mp.release() } catch (_: Exception) { }
                file.delete()
                onFinished()
                true
            }
            player.prepare()
            player.start()
        } catch (_: Exception) {
            onFinished()
        }
    }
}
