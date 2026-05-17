package com.joncas.jontracker.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Thin wrapper over [MediaRecorder] that records to a single cache-dir file
 * (overwritten on each start). Output is AAC/M4A — small files, accepted by
 * Whisper. The file lives only long enough to be uploaded.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start() {
        stop() // ensure idle
        val file = File(context.cacheDir, "idea-$timestamp.m4a")
        outputFile = file
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        rec.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(64_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = rec
    }

    /** Stop recording and return the captured audio file, or null if not recording. */
    fun stop(): File? {
        val rec = recorder ?: return null
        recorder = null
        return try {
            rec.stop()
            rec.release()
            outputFile
        } catch (_: Exception) {
            rec.release()
            outputFile?.delete()
            null
        }
    }

    private val timestamp: Long get() = System.currentTimeMillis()
}
