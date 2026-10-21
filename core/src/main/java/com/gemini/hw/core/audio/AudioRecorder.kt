package com.gemini.hw.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class AudioRecorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(2048)

    @SuppressLint("MissingPermission")
    suspend fun recordAudio(onChunk: ((ByteArray) -> Unit)? = null): ByteArray = withContext(Dispatchers.IO) {
        val outputStream = ByteArrayOutputStream()
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        val buffer = ByteArray(bufferSize)
        audioRecord?.startRecording()
        isRecording = true

        try {
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                    onChunk?.invoke(buffer.copyOf(read))
                }
            }
        } finally {
            stop()
        }

        val rawPcm = outputStream.toByteArray()
        wrapPcmToWav(rawPcm, sampleRate, 1, 16)
    }

    fun stop() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    fun isCurrentlyRecording(): Boolean = isRecording

    /**
     * Converts raw 16-bit PCM to a standard RIFF/WAV container so Gemini audio endpoints can play it.
     */
    private fun wrapPcmToWav(pcmData: ByteArray, sampleRate: Int, channels: Int, bitDepth: Int): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * channels * bitDepth / 8
        val header = ByteArray(44)

        // RIFF chunk
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        // WAVE
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        // fmt chunk
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // 16 for PCM
        header[20] = 1; header[21] = 0 // format = 1 (PCM)
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitDepth / 8).toByte(); header[33] = 0 // block align
        header[34] = bitDepth.toByte(); header[35] = 0 // bits per sample
        // data chunk
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        val wav = ByteArray(header.size + pcmData.size)
        System.arraycopy(header, 0, wav, 0, header.size)
        System.arraycopy(pcmData, 0, wav, header.size, pcmData.size)
        return wav
    }
}
