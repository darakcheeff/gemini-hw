# Спецификация 01: Архитектура оригинального приложения и сетевые эндпоинты

На основе декомпиляции официальных APK Google (`com.google.android.apps.bard` и `com.google.android.googlequicksearchbox`).

---

## 1. Структура официального приложения
1. **`com.google.android.apps.bard` (Google Play Stub / Shell)**:
   - Размер: ~5.3 МБ.
   - Главная активность: `BardEntryPointActivity` (`BardEntryPointActivityPeer`).
   - Назначение: Проверяет наличие Google Play Services и перенаправляет вызов в системный пакет Google App (`com.google.android.googlequicksearchbox`).
   - На устройствах без GMS (Huawei / HarmonyOS / AOSP) данный пакет не работает, так как целевой активности в системе нет.

2. **`com.google.android.googlequicksearchbox` (AGSA / Robin)**:
   - Включает движок Robin (`com.google.android.apps.search.assistant.surfaces.voice.robin`).
   - Использует смешанную архитектуру: нативный интерфейс Jetpack Compose + Protobuf gRPC / batchexecute REST RPC поверх HTTP/2.

---

## 2. Сетевые протоколы и ключевые эндпоинты

| Протокол / Эндпоинт | Назначение | Параметры и заголовки |
| :--- | :--- | :--- |
| **`https://gemini.google.com/app`** | Инициализация сессии, получение начальных токенов | Извлечение `SNlM0e` (CSRF), `FdrFJe`, `cfb2h`, `qKIAYe` (Push-ID), `Ylro7b` (Pctx) из разметки `WIZ_global_data`. |
| **`https://gemini.google.com/_/BardChatUi/data/batchexecute`** | Пакетный RPC интерфейс Google | RPC ID `MaZiqc` (список бесед), `hNvQHb` (история сообщений), `otAQ7b` (список моделей), `d228cf` (создание ветви), `Gems` (список персонажей). |
| **`https://gemini.google.com/_/BardChatUi/data/assistant.lamda.BardFrontendService/StreamGenerate`** | Потоковая генерация ответов | Параметры: `bl` (build label), `f.sid` (FdrFJe), `hl=ru`, `rt=c`. Тело: `f.req` (структура промпта, ID беседы, слоты вложений). |
| **`https://push.clients6.google.com/upload/`** | Google Scotty Push Uploader | Resumable протокол загрузки фото, аудио, документов. Заголовки: `X-Tenant-Id: bard-storage`, `Push-ID`, `X-Client-Pctx`. |
