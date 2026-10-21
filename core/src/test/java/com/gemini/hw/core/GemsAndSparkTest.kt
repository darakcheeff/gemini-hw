package com.gemini.hw.core

import com.gemini.hw.core.model.ScheduledItem
import com.gemini.hw.core.model.SparkStep
import com.gemini.hw.core.model.SparkTaskStatus
import com.gemini.hw.core.repository.GemsAndSparkRepository
import org.junit.Assert.*
import org.junit.Test

class GemsAndSparkTest {

    @Test
    fun testDefaultGemsLoaded() {
        val repo = GemsAndSparkRepository()
        val gems = repo.gems.value

        assertTrue(gems.isNotEmpty())
        assertTrue(gems.any { it.name == "Кодер" })
        assertTrue(gems.any { it.name == "Редактор текстов" })
        assertTrue(gems.any { it.name == "Мозговой штурм" })
    }

    @Test
    fun testAddAndRemoveScheduledItem() {
        val repo = GemsAndSparkRepository()

        val item = ScheduledItem(
            id = "sched_1",
            title = "Напомнить о релизе",
            prompt = "Подготовь чеклист к релизу",
            scheduledTimeEpochMillis = System.currentTimeMillis() + 3600000
        )

        repo.addScheduledItem(item)
        assertEquals(1, repo.scheduledItems.value.size)
        assertEquals("sched_1", repo.scheduledItems.value[0].id)

        repo.removeScheduledItem("sched_1")
        assertEquals(0, repo.scheduledItems.value.size)
    }

    @Test
    fun testAddSparkTask() {
        val repo = GemsAndSparkRepository()
        val initialCount = repo.sparkTasks.value.size

        repo.addSparkTask(
            title = "Анализ логов",
            description = "Пошаговый поиск аномалий",
            steps = listOf(
                SparkStep("s1", "Загрузить логи", "Парсинг файлов", false),
                SparkStep("s2", "Построить график ошибок", "Визуализация", false)
            )
        )

        val updated = repo.sparkTasks.value
        assertEquals(initialCount + 1, updated.size)
        assertEquals("Анализ логов", updated[0].title)
        assertEquals(SparkTaskStatus.PENDING, updated[0].status)
        assertEquals(2, updated[0].steps.size)
    }
}
