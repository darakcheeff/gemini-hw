package com.gemini.hw.core.repository

import com.gemini.hw.core.model.Gem
import com.gemini.hw.core.model.ScheduledItem
import com.gemini.hw.core.model.SparkStep
import com.gemini.hw.core.model.SparkTask
import com.gemini.hw.core.model.SparkTaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GemsAndSparkRepository {

    private val _gems = MutableStateFlow<List<Gem>>(
        listOf(
            Gem(
                id = "gem_coding",
                name = "Кодер",
                description = "Помощник в написании, отладке и рефакторинге кода",
                iconName = "code",
                systemInstructions = "Ты опытный старший разработчик. Пиши лаконичный, надежный и чистый код."
            ),
            Gem(
                id = "gem_writing",
                name = "Редактор текстов",
                description = "Улучшение стиля, проверка грамматики и вычитка",
                iconName = "edit",
                systemInstructions = "Ты профессиональный литературный редактор. Исправляй стилистику и орфографию."
            ),
            Gem(
                id = "gem_brainstorm",
                name = "Мозговой штурм",
                description = "Генерация нестандартных идей и альтернативных взглядов",
                iconName = "lightbulb",
                systemInstructions = "Предлагай неожиданные, креативные и реализуемые решения."
            )
        )
    )
    val gems: StateFlow<List<Gem>> = _gems.asStateFlow()

    private val _scheduledItems = MutableStateFlow<List<ScheduledItem>>(emptyList())
    val scheduledItems: StateFlow<List<ScheduledItem>> = _scheduledItems.asStateFlow()

    private val _sparkTasks = MutableStateFlow<List<SparkTask>>(
        listOf(
            SparkTask(
                id = "spark_1",
                title = "Исследование темы и конспект",
                description = "Автоматический сбор и структурирование данных по вопросу",
                status = SparkTaskStatus.PENDING,
                steps = listOf(
                    SparkStep("s1", "Поиск ключевых источников", "Поиск информации в сети", false),
                    SparkStep("s2", "Фильтрация и анализ", "Выделение фактов", false),
                    SparkStep("s3", "Составление резюме", "Генерация итогового отчета", false)
                )
            )
        )
    )
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
