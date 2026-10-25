# Спецификация 05: Загрузка вложений по протоколу Google Scotty

Спецификация загрузки пользовательских медиафайлов через протокол Google Scotty Push Uploader.

---

## 1. Архитектура Google Scotty
- **Хост**: `https://push.clients6.google.com/upload/`
- **Протокол**: Resumable HTTP/2 POST.
- **Аутентификация**: На основе активных куки сессии (`__Secure-1PSID`, `__Secure-1PSIDTS`) и токенов из `WIZ_global_data`.

## 2. Пошаговый цикл загрузки файла

### Шаг 1: Инициализация сессии (Start)
- **Метод**: `POST https://push.clients6.google.com/upload/`
- **Заголовки**:
  - `Origin: https://gemini.google.com`
  - `Referer: https://gemini.google.com/`
  - `X-Tenant-Id: bard-storage`
  - `Push-ID: <qKIAYe>` (извлекается из HTML)
  - `X-Client-Pctx: <Ylro7b>` (извлекается из HTML)
  - `X-Goog-Upload-Command: start`
  - `X-Goog-Upload-Protocol: resumable`
  - `X-Goog-Upload-Header-Content-Length: <размер файла в байтах>`
- **Тело**: `File name: <имя_файла>`
- **Результат**: Заголовок ответа `x-goog-upload-url` (персональный URL для загрузки содержимого).

### Шаг 2: Передача данных и финализация (Upload & Finalize)
- **Метод**: `POST <x-goog-upload-url>`
- **Заголовки**:
  - `X-Goog-Upload-Command: upload, finalize`
  - `X-Goog-Upload-Offset: 0`
  - `Content-Type: <mime-тип>`
- **Тело**: Сырые байты файла (`ByteArray`).
- **Результат**: Строка серверного пути к файлу вида `/contrib_service/ttl_1d/...`.

## 3. Интеграция в запрос к Gemini (`StreamGenerate`)
Серверный путь к загруженному файлу передается в слоте 3 структуры `promptArr`:
```json
[
  [
    ["/contrib_service/ttl_1d/...", <KIND>, null, "<mimeType>"],
    "<fileName>",
    null, null, null, null, null, null,
    [0]
  ]
]
```
Где `<KIND>`:
- `1` — для изображений (`image/jpeg`, `image/png`, `image/webp`).
- `2` — для аудиозаписей (`audio/mp4`, `audio/aac`, `audio/wav`).
- `3` — для текстовых файлов и документов (`application/pdf`, `text/plain`).
- `4` — для видеозаписей (`video/mp4`).
