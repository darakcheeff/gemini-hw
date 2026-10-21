package com.gemini.hw.nativeapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gemini.hw.core.model.ScheduledItem
import com.gemini.hw.core.model.SparkTask

@Composable
fun SparkDialog(
    tasks: List<SparkTask>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Work, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Задачи (Spark)")
            }
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(tasks) { task ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium)
                            Text(task.description, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            task.steps.forEach { step ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        if (step.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (step.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(step.title, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

@Composable
fun ScheduledDialog(
    items: List<ScheduledItem>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Запланированное")
            }
        },
        text = {
            if (items.isEmpty()) {
                Text("Нет запланированных задач", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                    items(items) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(item.prompt, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
