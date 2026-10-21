package com.gemini.hw.nativeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemini.hw.core.model.GeminiModel

@Composable
fun ModelSelectorDropdown(
    models: List<GeminiModel>,
    selectedModel: GeminiModel,
    onModelSelected: (GeminiModel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedModel.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(model.displayName, style = MaterialTheme.typography.bodyLarge)
                                if (model.badge != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = model.badge ?: "",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = model.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onModelSelected(model)
                        expanded = false
                    },
                    trailingIcon = if (model.id == selectedModel.id) {
                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else null
                )
            }
        }
    }
}
