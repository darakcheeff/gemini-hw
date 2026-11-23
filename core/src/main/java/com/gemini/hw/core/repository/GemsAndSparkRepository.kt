package com.gemini.hw.core.repository

import com.gemini.hw.core.model.Gem
import com.gemini.hw.core.model.Notebook
import com.gemini.hw.core.model.ScheduledItem
import com.gemini.hw.core.model.SparkStep
import com.gemini.hw.core.model.SparkTask
import com.gemini.hw.core.model.SparkTaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GemsAndSparkRepository {

    private val _gems = MutableStateFlow<List<Gem>>(emptyList())
    val gems: StateFlow<List<Gem>> = _gems.asStateFlow()

    private val _notebooks = MutableStateFlow<List<Notebook>>(emptyList())
    val notebooks: StateFlow<List<Notebook>> = _notebooks.asStateFlow()

    private val _notebookSources = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    fun setGems(list: List<Gem>) {
        _gems.value = list
    }

    fun setNotebooks(list: List<Notebook>) {
        _notebooks.value = list.map { nb ->
            val localSources = _notebookSources[nb.id] ?: emptyList()
            if (localSources.isNotEmpty()) {
                nb.copy(sources = (nb.sources + localSources).distinct())
            } else nb
        }
    }

    fun addSourceToNotebook(notebookId: String, source: String) {
        val trimmed = source.trim()
        if (trimmed.isBlank()) return
        val current = _notebookSources[notebookId] ?: emptyList()
        val updated = (current + trimmed).distinct()
        _notebookSources[notebookId] = updated
        _notebooks.value = _notebooks.value.map { nb ->
            if (nb.id == notebookId) {
                val allSources = (nb.sources + updated).distinct()
                nb.copy(
                    sources = allSources,
                    content = "Источников: ${allSources.size}"
                )
            } else nb
        }
    }

    fun getSourcesForNotebook(notebookId: String): List<String> {
        val targetNorm = if (notebookId.startsWith("notebooks/")) notebookId else "notebooks/$notebookId"
        val rawId = targetNorm.removePrefix("notebooks/")
        return (_notebookSources[notebookId]
            ?: _notebookSources[targetNorm]
            ?: _notebookSources[rawId]
            ?: emptyList())
    }

    fun addNotebook(title: String, content: String = "", id: String? = null): Notebook {
        val nbId = id ?: ("nb_" + System.currentTimeMillis())
        if (content.isNotBlank()) {
            _notebookSources[nbId] = listOf(content.trim())
        }
        val nb = Notebook(
            id = nbId,
            title = title,
            content = if (content.isNotBlank()) "Источников: 1" else "",
            sources = if (content.isNotBlank()) listOf(content.trim()) else emptyList()
        )
        _notebooks.value = listOf(nb) + _notebooks.value
        return nb
    }

    fun removeNotebook(id: String) {
        _notebookSources.remove(id)
        _notebooks.value = _notebooks.value.filter { it.id != id }
    }

    fun renameNotebook(id: String, newTitle: String) {
        _notebooks.value = _notebooks.value.map {
            if (it.id == id) it.copy(title = newTitle) else it
        }
    }

    private val _scheduledItems = MutableStateFlow<List<ScheduledItem>>(emptyList())
    val scheduledItems: StateFlow<List<ScheduledItem>> = _scheduledItems.asStateFlow()

    private val _sparkTasks = MutableStateFlow<List<SparkTask>>(emptyList())
    val sparkTasks: StateFlow<List<SparkTask>> = _sparkTasks.asStateFlow()

    fun addScheduledItem(item: ScheduledItem) {
        _scheduledItems.value = _scheduledItems.value + item
    }

    fun removeScheduledItem(id: String) {
        _scheduledItems.value = _scheduledItems.value.filter { it.id != id }
    }

    fun addSparkTask(title: String, description: String, steps: List<SparkStep>) {
        val newTask = SparkTask(
            id = "spark_" + System.currentTimeMillis(),
            title = title,
            description = description,
            status = SparkTaskStatus.PENDING,
            steps = steps
        )
        _sparkTasks.value = listOf(newTask) + _sparkTasks.value
    }
}
