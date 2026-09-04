package com.echoplayer.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.PlaybackParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 回放：
 *  - [playWavRange] 播放自己录音里的一小段（点某个词 → 听自己怎么读的），用 AudioTrack 精确切片；
 *  - [playFile] 播放完整文件（自己的录音 / 服务器合成的句子语音），支持倍速。
 */
class ClipPlayer {
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    private var track: AudioTrack? = null
    private var player: MediaPlayer? = null
    private var onDone: (() -> Unit)? = null

    fun playWavRange(file: File, startSec: Double, endSec: Double, padSec: Double = 0.04, onComplete: (() -> Unit)? = null) {
        stop()
        val wav = readWav(file) ?: return
        val from = ((startSec - padSec).coerceAtLeast(0.0) * wav.sampleRate).toInt().coerceIn(0, wav.samples.size)
        val to = ((endSec + padSec) * wav.sampleRate).toInt().coerceIn(from, wav.samples.size)
        if (to - from < wav.sampleRate / 50) return
        val slice = wav.samples.copyOfRange(from, to)
        playPcm(slice, wav.sampleRate, onComplete)
    }

    fun playFile(file: File, rate: Float = 1f, onComplete: (() -> Unit)? = null) {
        stop()
        onDone = onComplete
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                _playing.value = false
                release()
                onDone?.invoke()
            }
            mp.setOnErrorListener { _, _, _ ->
                _playing.value = false
                release(); true
            }
            mp.prepare()
            if (rate != 1f) {
                mp.playbackParams = PlaybackParams().setSpeed(rate.coerceIn(0.5f, 2f))
            }
            _playing.value = true
            mp.start()
        } catch (e: Exception) {
            _playing.value = false
            release()
        }
    }

    private fun playPcm(samples: ShortArray, sampleRate: Int, onComplete: (() -> Unit)?) {
        val bytes = samples.size * 2
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track = t
        t.write(samples, 0, samples.size)
        t.setNotificationMarkerPosition(samples.size)
        t.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack?) {
                _playing.value = false
                onComplete?.invoke()
            }

            override fun onPeriodicNotification(track: AudioTrack?) {}
        })
        _playing.value = true
        t.play()
    }

    fun stop() {
        track?.let { runCatching { it.stop(); it.release() } }
        track = null
        release()
        _playing.value = false
    }

    private fun release() {
        player?.let { runCatching { it.reset(); it.release() } }
        player = null
    }

    data class Wav(val sampleRate: Int, val samples: ShortArray)

    companion object {
        /** 读 16bit PCM WAV（单声道；多声道只取第一声道）。 */
        fun readWav(file: File): Wav? = runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(12)
                raf.readFully(header)
                if (String(header, 0, 4, Charsets.US_ASCII) != "RIFF") return null
                var sampleRate = 16_000
                var channels = 1
                var bits = 16
                while (true) {
                    val chunk = ByteArray(8)
                    if (raf.read(chunk) < 8) return null
                    val id = String(chunk, 0, 4, Charsets.US_ASCII)
                    val size = ByteBuffer.wrap(chunk, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    if (id == "fmt ") {
                        val fmt = ByteArray(size)
                        raf.readFully(fmt)
                        val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                        bb.short // format
                        channels = bb.short.toInt()
                        sampleRate = bb.int
                        bb.int; bb.short
                        bits = bb.short.toInt()
                    } else if (id == "data") {
                        val dataSize = if (size <= 0 || size > raf.length() - raf.filePointer) (raf.length() - raf.filePointer).toInt() else size
                        val data = ByteArray(dataSize)
                        raf.readFully(data)
                        if (bits != 16) return null
                        val frames = dataSize / 2 / channels
                        val out = ShortArray(frames)
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until frames) {
                            out[i] = bb.short
                            for (c in 1 until channels) bb.short
                        }
                        return Wav(sampleRate, out)
                    } else {
                        raf.seek(raf.filePointer + size + (size and 1))
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                null
            }
        }.getOrNull()
    }
}
