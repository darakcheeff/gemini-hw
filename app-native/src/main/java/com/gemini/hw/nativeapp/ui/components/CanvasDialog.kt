package com.gemini.hw.nativeapp.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gemini.hw.core.model.CanvasDocument

@Composable
fun CanvasDialog(
    document: CanvasDocument,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onRevisionPrompt: ((String) -> Unit)? = null
) {
    var content by remember(document.content) { mutableStateOf(document.content) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Preview, 1: Edit
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = document.title.ifBlank { "Canvas документ" },
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "v${document.version}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (!document.language.isNullOrBlank()) {
                            Text(
                                text = "Язык: ${document.language}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(content))
                            Toast.makeText(context, "Скопировано в буфер", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Копировать")
                        }
                        IconButton(onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, content)
                                putExtra(Intent.EXTRA_TITLE, document.title)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Поделиться документом"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Поделиться")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть")
                        }
                    }
                }

                // Mode Tabs: Preview vs Edit
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.padding(vertical = 8.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Предпросмотр") },
                        icon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Редактор") },
                        icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                // Document Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .padding(12.dp)
                ) {
                    if (selectedTab == 0) {
                        // Rich Markdown / Code Preview
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            MarkdownText(
                                text = content,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        // Editable Text Area
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick AI revision chips
                Text(
                    text = "Действия с Gemini Canvas:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            onDismiss()
                            onRevisionPrompt?.invoke("Найди и исправь возможные ошибки, баги и неточности в этом документе:\n\n```${document.language ?: ""}\n$content\n```")
                        },
                        label = { Text("🔍 Найти ошибки", fontSize = 12.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            onDismiss()
                            onRevisionPrompt?.invoke("Сделай этот документ более лаконичным, кратким и четким:\n\n```${document.language ?: ""}\n$content\n```")
                        },
                        label = { Text("📝 Сделать короче", fontSize = 12.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            onDismiss()
                            onRevisionPrompt?.invoke("Добавь подробные поясняющие комментарии и документацию к этому тексту/коду:\n\n```${document.language ?: ""}\n$content\n```")
                        },
                        label = { Text("💬 Добавить комментарии", fontSize = 12.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            onDismiss()
                            onRevisionPrompt?.invoke("Сделай стиль текста более деловым и формальным:\n\n$content")
                        },
                        label = { Text("👔 Деловой стиль", fontSize = 12.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            onDismiss()
                            onRevisionPrompt?.invoke("Перепиши и улучши архитектуру/структуру этого документа:\n\n```${document.language ?: ""}\n$content\n```")
                        },
                        label = { Text("🔄 Переписать", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Save / Close buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Закрыть")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        onSave(content)
                        onDismiss()
                    }) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}
