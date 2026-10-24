package com.gemini.hw.nativeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemini.hw.core.model.GeminiModel

@Composable
fun ModelSelectorDropdown(
    models: List<GeminiModel>,
    selectedModel: GeminiModel,
    onModelSelected: (GeminiModel) -> Unit,
    thinkingLevel: String = "Авто",
    onThinkingLevelSelected: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    var showThinkingMenu by remember { mutableStateOf(false) }

    val thinkingLevels = listOf("Авто", "Низкий", "Средний", "Высокий", "Выкл")

    Box {
        // Model selector pill button (as seen in 1.png: "Flash ▾")
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedModel.displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "Выбор модели",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        // Popup matching the dark dropdown card from 1.png
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                showThinkingMenu = false
            },
            modifier = Modifier
                .width(280.dp)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (!showThinkingMenu) {
                models.forEach { model ->
                    val isSelected = model.id == selectedModel.id
                    DropdownMenuItem(
                        text = {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (model.badge != null) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = model.badge ?: "",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = model.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Выбрано",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null,
                        onClick = {
                            onModelSelected(model)
                            expanded = false
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // "Уровень рассуждений ▸" row from 1.png
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Уровень рассуждений",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = thinkingLevel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        showThinkingMenu = true
                    }
                )
            } else {
                // Submenu for Thinking level
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "‹ Назад к моделям",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = { showThinkingMenu = false }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                thinkingLevels.forEach { level ->
                    val isLevelSelected = level == thinkingLevel
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = level,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isLevelSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        leadingIcon = if (isLevelSelected) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null,
                        onClick = {
                            onThinkingLevelSelected(level)
                            showThinkingMenu = false
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
