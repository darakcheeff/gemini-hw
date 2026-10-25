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

    private val _renamedTitles = mutableMapOf<String, String>()

    fun setGems(list: List<Gem>) {
        _gems.value = list
    }

    fun setNotebooks(list: List<Notebook>) {
        val currentLocal = _notebooks.value.filter { it.id.startsWith("nb_") }
        val serverIds = list.map { it.id }.toSet()
        val keptLocal = currentLocal.filter { it.id !in serverIds }
        val mergedList = (list + keptLocal).map { nb ->
            val renamed = _renamedTitles[nb.id]
            if (renamed != null) nb.copy(title = renamed) else nb
        }
        _notebooks.value = mergedList
    }

    fun addNotebook(title: String, content: String = "", id: String? = null): Notebook {
        val nb = Notebook(
            id = id ?: ("nb_" + System.currentTimeMillis()),
            title = title,
            content = content
        )
        _notebooks.value = listOf(nb) + _notebooks.value
        return nb
    }

    fun removeNotebook(id: String) {
        _renamedTitles.remove(id)
        _notebooks.value = _notebooks.value.filter { it.id != id }
    }

    fun renameNotebook(id: String, newTitle: String) {
        _renamedTitles[id] = newTitle
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
