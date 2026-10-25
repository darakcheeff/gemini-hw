package com.gemini.hw.nativeapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gemini.hw.nativeapp.audio.TtsManager
import com.gemini.hw.nativeapp.audio.VoiceInputManager
import com.gemini.hw.nativeapp.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class LiveState {
    LISTENING,
    THINKING,
    SPEAKING
}

@Composable
fun GeminiLiveDialog(
    onDismiss: () -> Unit,
    viewModel: MainViewModel,
    ttsManager: TtsManager,
    voiceInputManager: VoiceInputManager
) {
    val context = LocalContext.current
    var liveState by remember { mutableStateOf(LiveState.LISTENING) }
    var userSubtitle by remember { mutableStateOf("Говорите, Gemini слушает...") }
    var geminiResponseText by remember { mutableStateOf("") }
    var currentAmplitude by remember { mutableFloatStateOf(0f) }
    var isMicPaused by remember { mutableStateOf(false) }

    // Pulsing circle animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleScale"
    )

    // Function to start listening for user voice
    fun startListeningCycle() {
        if (isMicPaused) return
        liveState = LiveState.LISTENING
        userSubtitle = "Слушаю вас..."
        voiceInputManager.startRecording { err ->
            userSubtitle = "Ошибка микрофона: $err"
        }
    }

    // Function to finish utterance and send to Gemini
    fun finishAndSend() {
        if (liveState != LiveState.LISTENING) return
        val recordedFile = voiceInputManager.stopRecording()
        if (recordedFile == null || !recordedFile.exists() || recordedFile.length() < 1000) {
            userSubtitle = "Голос не распознан. Попробуйте еще раз."
            startListeningCycle()
            return
        }

        liveState = LiveState.THINKING
        userSubtitle = "Обработка речи..."

        viewModel.sendLiveAudioMessage(
            audioFile = recordedFile,
            prompt = "Ты собеседник в режиме живого разговора. Отвечай лаконично, естественно, разговорным стилем, емко (1-3 предложения), на русском языке.",
            onStreamingChunk = { chunk ->
                liveState = LiveState.SPEAKING
                geminiResponseText = chunk
            },
            onDone = { fullAnswer ->
                liveState = LiveState.SPEAKING
                geminiResponseText = fullAnswer
                // Read aloud via TTS
                ttsManager.speak("live_msg_${System.currentTimeMillis()}", fullAnswer) {
                    // When Gemini finishes speaking, automatically resume listening cycle!
                    startListeningCycle()
                }
            },
            onError = { err ->
                liveState = LiveState.LISTENING
                userSubtitle = "Ошибка: $err"
                startListeningCycle()
            }
        )
    }

    // Monitor amplitude periodically while listening
    LaunchedEffect(liveState, isMicPaused) {
        if (liveState == LiveState.LISTENING && !isMicPaused) {
            voiceInputManager.startRecording()
            var silenceCounter = 0
            var speechDetected = false

            while (isActive && liveState == LiveState.LISTENING) {
                delay(100)
                val amp = voiceInputManager.getMaxAmplitude()
                val normalizedAmp = (amp / 15000f).coerceIn(0f, 1f)
                currentAmplitude = normalizedAmp

                if (amp > 2500) {
                    speechDetected = true
                    silenceCounter = 0
                    userSubtitle = "Слушаю речь..."
                } else if (speechDetected) {
                    silenceCounter++
                    // If silence detected for 1.8 seconds after speech, auto send!
                    if (silenceCounter > 18) {
                        finishAndSend()
                        break
                    }
                }
            }
        }
    }

    // Cleanup when dismissed
    DisposableEffect(Unit) {
        onDispose {
            voiceInputManager.stopRecording()
            ttsManager.stop()
        }
    }

    Dialog(
        onDismissRequest = {
            voiceInputManager.stopRecording()
            ttsManager.stop()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Gemini Live",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Автономный голосовой режим",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            voiceInputManager.stopRecording()
                            ttsManager.stop()
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                // Center Pulsing Orb & Status
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Pulsing animated orb
                    val orbScale = when (liveState) {
                        LiveState.LISTENING -> (1f + currentAmplitude * 0.4f) * idlePulse
                        LiveState.THINKING -> idlePulse * 0.95f
                        LiveState.SPEAKING -> 1.08f * idlePulse
                    }

                    val gradientColors = when (liveState) {
                        LiveState.LISTENING -> listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                        LiveState.THINKING -> listOf(
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.outline
                        )
                        LiveState.SPEAKING -> listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(190.dp)
                            .scale(orbScale)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(gradientColors))
                            .clickable {
                                if (liveState == LiveState.LISTENING) {
                                    finishAndSend()
                                }
                            }
                    ) {
                        Icon(
                            imageVector = when (liveState) {
                                LiveState.LISTENING -> Icons.Default.Mic
                                LiveState.THINKING -> Icons.Default.HourglassEmpty
                                LiveState.SPEAKING -> Icons.Default.VolumeUp
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = when (liveState) {
                            LiveState.LISTENING -> "Слушаю вас..."
                            LiveState.THINKING -> "Gemini думает..."
                            LiveState.SPEAKING -> "Gemini говорит..."
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Text subtitle
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .heightIn(max = 180.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (liveState == LiveState.SPEAKING && geminiResponseText.isNotBlank()) {
                                    geminiResponseText
                                } else {
                                    userSubtitle
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Bottom Action Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Finish utterance button / manual trigger
                    Button(
                        onClick = {
                            if (liveState == LiveState.LISTENING) {
                                finishAndSend()
                            } else if (liveState == LiveState.SPEAKING) {
                                ttsManager.stop()
                                startListeningCycle()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            if (liveState == LiveState.LISTENING) Icons.Default.Check else Icons.Default.SkipNext,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (liveState == LiveState.LISTENING) "Отправить" else "Продолжить")
                    }

                    // End Live Session
                    IconButton(
                        onClick = {
                            voiceInputManager.stopRecording()
                            ttsManager.stop()
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            contentDescription = "Завершить",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}
