package com.gemini.hw.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavHeaderTest {

    @Test
    fun testWavHeaderFormat() {
        // Create 1000 dummy PCM bytes
        val dummyPcm = ByteArray(1000) { (it % 128).toByte() }
        val wavBytes = wrapPcmToWav(dummyPcm, sampleRate = 16000, channels = 1, bitDepth = 16)

        // WAV header is 44 bytes
        assertEquals(1044, wavBytes.size)

        // RIFF magic
        assertEquals('R'.code.toByte(), wavBytes[0])
        assertEquals('I'.code.toByte(), wavBytes[1])
        assertEquals('F'.code.toByte(), wavBytes[2])
        assertEquals('F'.code.toByte(), wavBytes[3])

        // WAVE magic
        assertEquals('W'.code.toByte(), wavBytes[8])
        assertEquals('A'.code.toByte(), wavBytes[9])
        assertEquals('V'.code.toByte(), wavBytes[10])
        assertEquals('E'.code.toByte(), wavBytes[11])

        // Check sample rate in header (bytes 24..27 little endian)
        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        val sampleRate = buffer.getInt(24)
        assertEquals(16000, sampleRate)

        // Check channels (bytes 22..23 little endian)
        val channels = buffer.getShort(22).toInt()
        assertEquals(1, channels)

        // Check bits per sample (bytes 34..35)
        val bitsPerSample = buffer.getShort(34).toInt()
        assertEquals(16, bitsPerSample)

        // Check data size (bytes 40..43)
        val dataSize = buffer.getInt(40)
        assertEquals(1000, dataSize)
    }

    private fun wrapPcmToWav(pcmData: ByteArray, sampleRate: Int, channels: Int, bitDepth: Int): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * channels * bitDepth / 8
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitDepth / 8).toByte(); header[33] = 0
        header[34] = bitDepth.toByte(); header[35] = 0
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
