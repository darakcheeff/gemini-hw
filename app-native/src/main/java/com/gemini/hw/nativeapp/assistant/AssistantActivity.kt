package com.gemini.hw.nativeapp.assistant

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemini.hw.nativeapp.MainActivity
import com.gemini.hw.nativeapp.audio.VoiceInputManager

class AssistantActivity : ComponentActivity() {

    private lateinit var voiceInputManager: VoiceInputManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceInputManager = VoiceInputManager(this)

        val screenshotBitmap = AssistantOverlayHolder.latestScreenshotBitmap
        val screenshotUri = AssistantOverlayHolder.latestScreenshotUri

        setContent {
            var inputText by remember { mutableStateOf("") }
            var isListening by remember { mutableStateOf(false) }

            // Dismiss when tapping outside the bottom sheet
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { finish() },
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) {} // prevent dismissing when tapping sheet
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Помощник Gemini",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.Default.Close, contentDescription = "Закрыть")
                            }
                        }

                        // Screenshot Preview
                        if (screenshotBitmap != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = screenshotBitmap.asImageBitmap(),
                                    contentDescription = "Скриншот экрана",
                                    modifier = Modifier
                                        .size(width = 60.dp, height = 90.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Экран зафиксирован",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Gemini может проанализировать содержимое",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick Suggestion Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SuggestionChip(
                                onClick = {
                                    dispatchQuery("Что изображено на этом экране?", screenshotUri?.toString())
                                },
                                label = { Text("🔍 Что на экране?", fontSize = 12.sp) }
                            )
                            SuggestionChip(
                                onClick = {
                                    dispatchQuery("Переведи весь текст с этого экрана на русский язык", screenshotUri?.toString())
                                },
                                label = { Text("🌐 Перевести текст", fontSize = 12.sp) }
                            )
                            SuggestionChip(
                                onClick = {
                                    dispatchQuery("Кратко перескажи суть содержимого экрана", screenshotUri?.toString())
                                },
                                label = { Text("💡 Суть вкратце", fontSize = 12.sp) }
                            )
                            SuggestionChip(
                                onClick = {
                                    dispatchQuery("Извлеки весь код или структурированные данные с экрана", screenshotUri?.toString())
                                },
                                label = { Text("📋 Извлечь данные", fontSize = 12.sp) }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Input Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text(if (isListening) "Слушаю..." else "Спросите о содержимом экрана...") },
                                modifier = Modifier.weight(1f),
                                maxLines = 3,
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (isListening) {
                                                voiceInputManager.stopListening()
                                                isListening = false
                                            } else {
                                                voiceInputManager.startListening(
                                                    onPartialResult = { partial ->
                                                        inputText = partial
                                                    },
                                                    onFinalResult = { final ->
                                                        inputText = final
                                                        isListening = false
                                                    },
                                                    onError = {
                                                        isListening = false
                                                    },
                                                    onStateChange = { listening ->
                                                        isListening = listening
                                                    }
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                                            contentDescription = "Голос",
                                            tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        dispatchQuery(inputText, screenshotUri?.toString())
                                    }
                                },
                                enabled = inputText.isNotBlank()
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Отправить",
                                    tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun dispatchQuery(query: String, uriStr: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ASSISTANT_QUERY, query)
            if (uriStr != null) {
                putExtra(EXTRA_ASSISTANT_IMAGE_URI, uriStr)
            }
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceInputManager.stopListening()
    }

    companion object {
        const val EXTRA_ASSISTANT_QUERY = "com.gemini.hw.extra.ASSISTANT_QUERY"
        const val EXTRA_ASSISTANT_IMAGE_URI = "com.gemini.hw.extra.ASSISTANT_IMAGE_URI"
    }
}
