package com.gemini.hw.nativeapp.assistant

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class GeminiVoiceInteractionSession(private val context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        if (screenshot != null) {
            AssistantOverlayHolder.latestScreenshotBitmap = screenshot
            try {
                val dir = File(context.cacheDir, "screenshots").apply { mkdirs() }
                val file = File(dir, "assist_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    screenshot.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                AssistantOverlayHolder.latestScreenshotUri = uri
            } catch (e: Exception) {
                android.util.Log.e("GeminiVoiceSession", "Failed to save assist screenshot: ${e.message}")
            }
        }

        // Launch the sleek interactive Assistant overlay activity
        val intent = Intent(context, AssistantActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
        hide()
    }
}
