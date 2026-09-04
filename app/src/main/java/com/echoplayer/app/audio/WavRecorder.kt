package com.echoplayer.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * 16kHz 单声道 16bit PCM WAV 录音，直接是 speecheval `/assess` 最喜欢的格式，
 * 也让本地按时间区间回放某个词（[ClipPlayer]）变得简单。
 */
class WavRecorder {
    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /** 写标准 44 字节 WAV 头。 */
        fun writeHeader(raf: RandomAccessFile, pcmBytes: Long, sampleRate: Int = SAMPLE_RATE, channels: Int = 1) {
            val byteRate = sampleRate * channels * 2
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt((36 + pcmBytes).toInt())
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1)
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort((channels * 2).toShort())
            header.putShort(16)
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(pcmBytes.toInt())
            raf.seek(0)
            raf.write(header.array())
        }
    }

    private val _level = MutableStateFlow(0f)
    /** 0..1 的音量电平，供录音按钮的动画使用。 */
    val level: StateFlow<Float> = _level

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording

    @Volatile private var thread: Thread? = null
    @Volatile private var stopRequested = false
    private var outFile: File? = null
    private var startedAt = 0L

    @SuppressLint("MissingPermission")
    fun start(file: File) {
        if (_recording.value) return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val bufSize = maxOf(minBuf, SAMPLE_RATE / 5 * 2)
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, CHANNEL, ENCODING, bufSize)
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("无法打开麦克风")
        }
        file.parentFile?.mkdirs()
        outFile = file
        stopRequested = false
        startedAt = System.currentTimeMillis()
        _recording.value = true
        thread = Thread {
            val raf = RandomAccessFile(file, "rw")
            raf.setLength(0)
            raf.write(ByteArray(44))
            val buf = ByteArray(bufSize)
            var total = 0L
            try {
                record.startRecording()
                while (!stopRequested) {
                    val n = record.read(buf, 0, buf.size)
                    if (n > 0) {
                        raf.write(buf, 0, n)
                        total += n
                        _level.value = peak(buf, n)
                    } else if (n < 0) break
                }
            } finally {
                runCatching { record.stop() }
                record.release()
                writeHeader(raf, total)
                raf.close()
                _level.value = 0f
                _recording.value = false
            }
        }.apply { name = "wav-recorder"; start() }
    }

    /** 停止并返回录音时长（秒）。 */
    fun stop(): Double {
        stopRequested = true
        thread?.join(2000)
        thread = null
        val f = outFile ?: return 0.0
        val pcm = (f.length() - 44).coerceAtLeast(0)
        return pcm / 2.0 / SAMPLE_RATE
    }

    private fun peak(buf: ByteArray, n: Int): Float {
        var max = 0
        var i = 0
        while (i + 1 < n) {
            val s = (buf[i].toInt() and 0xff) or (buf[i + 1].toInt() shl 8)
            val v = abs(s.toShort().toInt())
            if (v > max) max = v
            i += 2
        }
        return (max / 32768f).coerceIn(0f, 1f)
    }
}
