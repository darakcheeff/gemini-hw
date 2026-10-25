package com.gemini.hw.nativeapp.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gemini.hw.core.model.CanvasDocument
import com.gemini.hw.core.model.ChatMessage
import com.gemini.hw.core.model.MessageRole

@Composable
fun MessageBubble(
    message: ChatMessage,
    onEdit: (String) -> Unit,
    onBranch: () -> Unit,
    onOpenCanvas: (CanvasDocument) -> Unit,
    speakingMessageId: String? = null,
    onToggleTts: ((ChatMessage) -> Unit)? = null
) {
    val context = LocalContext.current
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(message.text) { mutableStateOf(message.text) }

    val isUser = message.role == MessageRole.USER
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .then(if (isUser) Modifier.widthIn(max = 320.dp) else Modifier.fillMaxWidth())
                .clip(RoundedCornerShape(12.dp))
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            Column {
                if (isEditing) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { isEditing = false }) {
                            Text("Отмена")
                        }
                        Button(onClick = {
                            isEditing = false
                            onEdit(editText)
                        }) {
                            Text("Сохранить")
                        }
                    }
                } else {
                    // Rich formatted markdown text
                    MarkdownText(
                        text = message.text,
                        color = MaterialTheme.colorScheme.onSurface,
                        isUser = isUser,
                        onOpenCanvas = { code, lang ->
                            onOpenCanvas(
                                CanvasDocument(
                                    id = "canvas_${message.id}",
                                    title = "Код (${lang.ifBlank { "snippet" }})",
                                    content = code,
                                    language = lang
                                )
                            )
                        }
                    )

                    // Attachments / Generated Images
                    message.attachments.forEach { attachment ->
                        Spacer(modifier = Modifier.height(8.dp))
                        if (attachment.url != null) {
                            AsyncImage(
                                model = attachment.url,
                                contentDescription = attachment.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }

                    // Canvas button if message contains interactive document
                    val canvasDoc = message.canvas
                    if (canvasDoc != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            onClick = { onOpenCanvas(canvasDoc) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = canvasDoc.title.ifBlank { "Документ Canvas" },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Интерактивный документ Canvas • Нажмите для просмотра",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = "Открыть",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Streaming indicator
                if (message.isStreaming) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Action Toolbar below message: Copy, Edit, Branch
        if (!isEditing && !message.isStreaming) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Gemini Message", message.text))
                        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Копировать",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isUser) {
                    IconButton(
                        onClick = { isEditing = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Редактировать",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isUser && onToggleTts != null) {
                    val isSpeaking = speakingMessageId == message.id
                    IconButton(
                        onClick = { onToggleTts(message) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = if (isSpeaking) "Остановить" else "Озвучить",
                            modifier = Modifier.size(16.dp),
                            tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Branch in new chat button
                IconButton(
                    onClick = onBranch,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.CallSplit,
                        contentDescription = "Создать ветвь в новом чате",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
