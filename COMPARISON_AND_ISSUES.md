# Сравнительный анализ: Оригинальные приложения Google vs Наш клиент и реестр косяков («велосипедов»)

Документ подготовлен на основе анализа исходного кода модулей [core](file:///home/tech/gemini_hw/core), [app-native](file:///home/tech/gemini_hw/app-native), [app-web](file:///home/tech/gemini_hw/app-web), спецификаций в [specs/](file:///home/tech/gemini_hw/specs), дампов сетевых ответов и распакованных оригинальных APK (`com.google.android.apps.bard`, `com.google.android.googlequicksearchbox` / AGSA).

---

## 1. Архитектурное сравнение по подсистемам

| № | Подсистема | Оригинал Google (`gemini.google.com` Web + AGSA/Bard) | Наш клиент (`:app-native`, `:core`, `:app-web`) | Ключевое расхождение |
| :--- | :--- | :--- | :--- | :--- |
| **1** | **Базовая архитектура и GMS** | `bard_base.apk` (~5.3 МБ) — стаб (`BardEntryPointActivityPeer`), перенаправляющий в `googlequicksearchbox` (~285 МБ, движок Robin). Жестко завязан на GMS. | Автономный клиент на Jetpack Compose + OkHttp, работающий напрямую с `StreamGenerate`, `batchexecute` и `GEMINI_WEB_GRAPHQL` без GMS. | Автономность достигнута, но протоколы воссозданы с эвристиками и локальными «костылями». |
| **2** | **Сессия и токены (`WIZ_global_data`)** | Извлекает `SNlM0e` (`at`), `FdrFJe` (`f.sid`), `cfb2h` (`bl`), `qKIAYe` (`Push-ID`), `Ylro7b` (`Pctx`) и автоматически обновляет их при истечении/ошибке. | Извлекает токены один раз при старте (`extractTokensFromHtml`), кэширует в памяти (`cachedSnlm0e`). | Отсутствует перехват протухших токенов (`400 Bad Request`) и автоматический рефреш при запросах. |
| **3** | **Генерация (`StreamGenerate`) и выбор модели** | Передает выбранную модель (`modelId`) и уровень рассуждений (`thinkingLevel`) в заголовках/слотах запроса; контекст диалога всегда передается в `msgStruct[2]` (`[conversationId, responseId, choiceId]`). | `modelId` в `sendMessageStream()` и `thinkingLevel` в `MainViewModel` **не передаются** в HTTP-запрос. При `contextArr == null` слот `msgStruct[2]` пропускается. | Выбор модели и уровня рассуждений в UI не влияет на модель на сервере. |
| **4** | **Блокноты (Notebooks) и привязка чатов** | При старте чата *из блокнота* передает ID блокнота в слотах проекта `StreamGenerate` — сервер сам создает чат внутри блокнота. Перемещение/удаление через `MUAZcd` + перезапрос `MaZiqc`. | Передает блокнот как источник в `promptArr[3]`, досылает `MUAZcd` через 600 мс после стрима, модифицирует промпт пользователя скрытым текстом и держит локальные кэши-костыли. | Скрытый текст портит историю на сервере, а локальные кэши маскируют рассинхрон с ПК. |
| **5** | **Источники блокнота (Sources)** | Добавляет файлы Drive, ссылки и заметки в существующий блокнот через GraphQL/batchexecute RPC добавления источника. | `AddSourceDialog` вызывает `viewModel.addNotebook(currentNotebook!!.title, text)`. | Вместо добавления источника создается **новый дубликат блокнота** с тем же именем. |
| **6** | **Режим Canvas и презентации** | Активирует инструмент Canvas флагом инструмента в `StreamGenerate`; парсит `candidate[30]` и скрывает маркер `immersive_entry_chip/0` из текста. | Флаг Canvas на сервер не передается. При длинном ответе искусственно оборачивает текст в `CanvasDocument`; сырой URL чипа остается в чате; чипы правок шлют весь HTML в чат. | Ложные срабатывания Canvas, мусорные ссылки в пузырях, «простыни» HTML в истории. |
| **7** | **Голосовой ввод (диктовка) и Gemini Live** | Диктовка в поле ввода; в Gemini Live — единая сессия, серверная транскрибация реплик пользователя (`turn[3][1]`) и сохранение контекста между ходами. | 3 класса записи звука. Кнопки микрофона зовут `SpeechToTextManager` (который ищет iFlyTek/GMS) или вставляют хардкод. В Live двойной старт записи и потеря `conversationId` на 1-м чанке. | Диктовка не работает без стороннего софта, а Live может терять контекст и оставлять заглушки. |
| **8** | **Редактирование и ветвление диалога** | Редактирование отправляет `StreamGenerate` с `contextArr` родительского хода; ветвление вызывает `KDNZr` с `turnId` (`r_...`). | `editMessage` игнорирует `parentMessageId` и шлет контекст конца чата; `createBranchInNewChat` при сбое создает фейковый локальный чат `c_branch_...`. | Редактирование на сервере не переписывает ветку, а фейковые ветви пропадают на ПК. |
| **9** | **Markdown, таблицы и размышления (`thoughts`)** | Рендерит таблицы, код, формулы, а также скрытый блок рассуждений модели (`thoughts`). | В `MarkdownText` нет парсера таблиц; поле `thoughts` в `ChatMessage` не парсится и не отображается в `MessageBubble`. | Таблицы выводятся сырым текстом с `|`, размышления модели невидимы. |
| **10** | **Чистота кода и моки** | Только реальные данные с сервера. | Зашиты тестовые чаты `c_china_1`/`c_china_2`, фоллбэк-генерация локальных `nb_...` ID, а в Drawer висят несинхронизированные «Боты (Gems)», «Запланированное» и «Spark». | Пользователь видит «мертвые» разделы и локальные сущности, отсутствующие на ПК. |

---

## 2. Подробный реестр косяков («велосипедов») и методы устранения

### 2.1. Сессия и обновление токенов (`SNlM0e`, `FdrFJe`, `cfb2h`)
- **Локация**: [GeminiWebClient.kt:28-118](file:///home/tech/gemini_hw/core/src/main/java/com/gemini/hw/core/network/GeminiWebClient.kt#L28-L118), [GeminiWebClient.kt:1295-1361](file:///home/tech/gemini_hw/core/src/main/java/com/gemini/hw/core/network/GeminiWebClient.kt#L1295-L1361)
- **Суть проблемы**:
  1. Проверка `if (cachedSnlm0e.isNullOrBlank()) refreshTokensAndFetchModels()` выполняется только когда кэш пуст. При долгой работе приложения `SNlM0e` или `bl` протухают, и сервер начинает отвечать `HTTP 400`.
  2. Дефолтный `bl` захардкожен строкой `"boq_gemini-web-uiserver_20261023.01_p0"` в 6 разных местах (`127`, `303`, `461`, `522`, `736`, `943`, `1303`).
- **Метод устранения**:
  1. Вынести выполнение RPC и `StreamGenerate` в общую обертку с перехватом `HTTP 400/401/403` (или ошибки токена): при первом сбое вызывать `refreshTokensAndFetchModels()` принудительно и повторять запрос 1 раз.
  2. Вынести формирование URL и общих заголовков `batchexecute` в единый приватный метод вместо дублирования кода в `fetchModelsViaRpc`, `getConversations`, `getNotebookConversations`, `getConversationMessages`, `getGems` и `callBatchexecuteRPC`.

---

### 2.2. Бутафорский выбор модели и уровня рассуждений (`modelId`, `thinkingLevel`)
- **Локация**: [GeminiWebClient.kt:196-319](file:///home/tech/gemini_hw/core/src/main/java/com/gemini/hw/core/network/GeminiWebClient.kt#L196-L319), [MainViewModel.kt:53-54](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/viewmodel/MainViewModel.kt#L53-L54), [MainViewModel.kt:412-414](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/viewmodel/MainViewModel.kt#L412-L414), [GeminiModel.kt:18-56](file:///home/tech/gemini_hw/core/src/main/java/com/gemini/hw/core/model/GeminiModel.kt#L18-L56)
- **Суть проблемы**:
  1. Параметр `modelId` передается в `sendMessageStream(...)`, но **вообще не используется** при сборке запроса! В итоге, какую бы модель пользователь ни выбрал в [ModelSelectorDropdown.kt](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/ui/components/ModelSelectorDropdown.kt) (`3.8 Flash`, `3.1 Pro`, `4 Argon`), на сервер уходит запрос без указания модели.
  2. Состояние `_thinkingLevel` хранится в `MainViewModel`, отображается в UI, но никуда не передается.
  3. В `parseModelsFromRpcResponse` ([GeminiWebClient.kt:163-185](file:///home/tech/gemini_hw/core/src/main/java/com/gemini/hw/core/network/GeminiWebClient.kt#L163-L185)) используется `m.optString(0)` как ID модели — нужно сопоставить с реальными заголовками `x-goog-ext-525001261-jspb` из веб-клиента.
- **Метод устранения**:
  1. Сохранять из ответа `otAQ7b` (либо из `GeminiModel`) точный идентификатор/JSPB-заголовок модели для веб-эндпоинта `StreamGenerate` и передавать заголовок `x-goog-ext-525001261-jspb` в `Request.Builder`.
  2. Передавать `thinkingLevel` в `dispatchToGemini` -> `sendMessageStream` и маппить его на соответствующий параметр генерации.

---

### 2.3. Нарушение структуры `msgStruct` в `StreamGenerate` и фейковый `currentConvoId`
- **Локация**: [GeminiWebClient.kt:279-293](file:///home/tech/gemini_hw/core/src/main/java/com/gemini/hw/core/network/GeminiWebClient.kt#L279-L293), [GeminiWebClient.kt:321](file:///home/tech/gemini_hw/core/src/main/java/com/gemini/hw/core/network/GeminiWebClient.kt#L321)
- **Суть проблемы**:
  1. На строке 321 `currentConvoId` инициализируется как `conversationId ?: ("c_" + System.currentTimeMillis())`. Если первый чанк ответа содержит текст раньше, чем распарсится `convoInfo` (или `convoInfo` в первом чанке еще пустой), `emit` уходит с фейковым клиентским ID `"c_179..."`, который `MainViewModel` тут же записывает в `_currentConversationId`. Следующий запрос уйдет на сервер с несуществующим `conversationId`!
  2. В `msgStruct` слоты сдвигаются в зависимости от `contextArr != null`, хотя для корректной позиционной сериализации (Protobuf-like JSON) `msgStruct[2]` должен быть либо `contextArr`, либо `null` (или `["", "", ""]`), чтобы индексы последующих полей не зависели от ветвления.
- **Метод устранения**:
  1. В `sendMessageStream` разделять `realConvoId` (полученный от сервера) и временный ID сообщения для UI; не сохранять сгенерированный `c_${System.currentTimeMillis()}` в `_currentConversationId` до получения валидного ID от сервера (`c_...` из `convoInfo`).
  2. Всегда класть `contextArr ?: JSONObject.NULL` в индекс 2 `msgStruct`, чтобы последующие слоты имели фиксированные позиции.

---

### 2.4. Блокноты: модификация промпта пользователя и локальные «заплатки» вместо серверной синхронизации
- **Локация**: [MainViewModel.kt:35-36](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/viewmodel/MainViewModel.kt#L35-L36), [MainViewModel.kt:231-254](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/viewmodel/MainViewModel.kt#L231-L254), [MainViewModel.kt:475-481](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/viewmodel/MainViewModel.kt#L475-L481), [MainViewModel.kt:500-561](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/viewmodel/MainViewModel.kt#L500-L561), [GemsAndSparkRepository.kt:21-36](file:///home/tech/gemini_hw/core/src/main/java/com/gemini/hw/core/repository/GemsAndSparkRepository.kt#L21-L36)
- **Суть проблемы**:
  1. **Порча промпта**: на строках 476–481 в `MainViewModel.kt` к тексту пользователя доклеивается скрытое примечание `"\n\n(Примечание: На основе запланированного маршрута...)"`. На скриншоте [6.png](file:///home/tech/gemini_hw/6.png) видно, что этот костыльный текст навсегда сохраняется в истории чата на ПК как часть сообщения пользователя!
  2. **Маскировка рассинхрона**: в `loadNotebookConversations` ([MainViewModel.kt:248](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/viewmodel/MainViewModel.kt#L248)) делается `(serverList + combined).distinctBy { it.id }`. Если чат был отвязан или удален на ПК, он продолжает «висеть» в блокноте на телефоне из-за склейки со старым `combined`. То же самое касается `_conversationNotebookMap`, `_customConvoTitles` и `_renamedTitles` в `GemsAndSparkRepository`.
  3. **Оптимистичное создание до ответа сервера**: на строках 506–514 чат добавляется в `_notebookConversations` с заголовком `prompt.take(30)`, а на 552–558 делается `delay(600)` + `MUAZcd` + `loadNotebookConversations`. Как показала проверка через `MaZiqc` в предыдущей сессии, при такой схеме `MUAZcd` возвращает 200, но чат не всегда мгновенно индексируется в списке блокнота на сервере, если сам `StreamGenerate` не создал его с нужными слотами проекта/бота.
- **Метод устранения**:
  1. **Удалить** модификацию промпта на строках 476–481 — корректное заземление уже обеспечивается структурой вложения блокнота в `promptArr[3]` и слотом `projectId` в `StreamGenerate`.
  2. Передавать привязку к блокноту нативно в `StreamGenerate` (как это делает веб-версия при запуске чата из блокнота) и оставлять `MUAZcd` только как проверку/фоллбэк, если сервер не привязал чат сам.
  3. Сделать сервер **единым источником правды** при обновлении (`loadNotebookConversations`, `refreshConversations`, `refreshNotebooks`): `serverList` должен заменять локальный список (а не склеиваться с устаревшим `combined`), сохраняя оптимистичные элементы только на время незавершенного сетевого запроса.

---

### 2.5. Диалог «Добавить источники» создает дубликат блокнота
- **Локация**: [MainActivity.kt:1263-1272](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/MainActivity.kt#L1263-L1272)
- **Суть проблемы**:
  ```kotlin
  onAddSource = { text ->
      viewModel.addNotebook(currentNotebook!!.title, text)
      Toast.makeText(context, "Источник добавлен в блокнот", Toast.LENGTH_SHORT).show()
  }
  ```
  Вместо добавления источника в текущий блокнот код вызывает `createNotebook(currentNotebook!!.title)` и создает на сервере Google **новый пустой блокнот** с тем же названием!
- **Метод устранения**:
  Реализовать в `GeminiWebClient` реальный RPC/GraphQL-метод добавления текстовой заметки/ссылки/файла в существующий `notebookId`, либо (до реализации RPC) не вводить пользователя в заблуждение созданием дубликатов блокнота и отсылать к веб-управлению источниками / прикреплять файл напрямую к чату.

---

### 2.6. Режим Canvas: эвристика вместо протокола и мусор в тексте чата
- **Локация**: [MainViewModel.kt:520-544](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/viewmodel/MainViewModel.kt#L520-L544), [GeminiWebClient.kt:360](file:///home/tech/gemini_hw/core/src/main/java/com/gemini/hw/core/network/GeminiWebClient.kt#L360), [GeminiWebClient.kt:893-902](file:///home/tech/gemini_hw/core/src/main/java/com/gemini/hw/core/network/GeminiWebClient.kt#L893-L902), [CanvasDialog.kt:270-305](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/ui/components/CanvasDialog.kt#L270-L305)
- **Суть проблемы**:
  1. Переключатель `_isCanvasMode` **не отправляет** флаг инструмента Canvas в `StreamGenerate`.
  2. Вместо этого в `MainViewModel.kt:535-543`, если включен `_isCanvasMode` и ответ длиннее 200 символов, любой обычный ответ искусственно оборачивается в `CanvasDocument` и всплывает поверх экрана.
  3. При получении реального Canvas-документа (`cand30`, как в дампе [canvas_convo_dump.json:4](file:///home/tech/gemini_hw/canvas_convo_dump.json#L4)) сервер вставляет в текст ответа ссылку-маркер `http://googleusercontent.com/immersive_entry_chip/0`. Наш парсер не вырезает её, и пользователь видит сырую неработающую ссылку в сообщении.
  4. В `CanvasDialog.kt` при нажатии «Сохранить» или чипов правок (`🔍 Найти ошибки` и др.) в чат пользователя отправляется весь исходный код/HTML целиком (`"Обнови документ: $updated"`), засоряя диалог.
  5. В `CanvasDialog.kt:210-226` `update = { webView -> webView.loadDataWithBaseURL(...) }` перезагружает WebView при каждой рекомпозиции Compose.
- **Метод устранения**:
  1. Передавать флаг режима Canvas в структуру запроса `StreamGenerate`.
  2. Убрать искусственную эвристику `lastResponseText.length > 200` (открывать Canvas только когда от сервера пришел `cand30` или пользователь сам нажал «Открыть в Canvas» на блоке кода).
  3. Вырезать `https?://googleusercontent\.com/immersive_entry_chip/\d+` из `replyChunk` и `modelText` в `GeminiWebClient.kt`, оставляя только нативную карточку `canvasDoc`.
  4. В `CanvasDialog.kt` вынести `preparedHtml` в `remember(document.id, document.content)` и в `update` загружать HTML только при фактическом изменении текста, а правки отправлять с привязкой к `document.id` без дублирования полного HTML в пузыре пользователя.

---

### 2.7. Голосовой ввод и Gemini Live: дублирование аудио-менеджеров, заглушки и потеря контекста
- **Локация**: [SpeechToTextManager.kt:38-43](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/audio/SpeechToTextManager.kt#L38-L43), [VoiceInputManager.kt](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/audio/VoiceInputManager.kt), [AudioRecorder.kt](file:///home/tech/gemini_hw/core/src/main/java/com/gemini/hw/core/audio/AudioRecorder.kt), [GeminiLiveDialog.kt:67-74,114-139](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/ui/components/GeminiLiveDialog.kt#L67-L139), [MainViewModel.kt:581-712](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/viewmodel/MainViewModel.kt#L581-L712), [AssistantActivity.kt:194-200](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/assistant/AssistantActivity.kt#L194-L200)
- **Суть проблемы**:
  1. **Тройное дублирование записи звука**: в проекте одновременно живут `AudioRecorder` (PCM/WAV, используется мертвым кодом `startVoiceRecording`), `VoiceInputManager` (AAC/M4A, используется в Live и Ассистенте) и `SpeechToTextManager` (системный `SpeechRecognizer` с хардкодом `com.iflytek.speechsuite.oversea`).
  2. **Неиспользуемый рабочий код**: в `GeminiWebClient.transcribeAudio()` и `MainViewModel.transcribeVoiceAudio()` уже реализована автономная транскрибация без GMS через Scotty, но кнопки микрофона в `MainActivity.kt` (935, 1668) зовут не их, а `SpeechToTextManager` (который падает на устройствах без GMS/iFlyTek).
  3. **Заглушка в Ассистенте**: в `AssistantActivity.kt:199` микрофон вообще ничего не распознает, а подставляет строку `"Анализ экрана по голосовому запросу"`.
  4. **Сломанный `startVoiceRecording()`**: в `MainViewModel.kt:581-607` создается `Attachment(name = "voice_recording.m4a")` без сохранения в файл и без `localUri`/`url` — `dispatchToGemini` не может его загрузить.
  5. **Двойной старт и потеря контекста в Gemini Live**:
     - В `GeminiLiveDialog.kt` при переходе в `LiveState.LISTENING` срабатывает `LaunchedEffect(liveState)` (`startRecording`), а после окончания TTS `onDone` вызывает `startListeningCycle()`, который тоже вызывает `startRecording` — запись сбрасывается и перезапускается.
     - В `sendLiveAudioMessage` (`MainViewModel.kt:680-682`) при первом чанке может записаться временный `chunk.conversationId`, а история перезапрашивается через `hNvQHb` только после окончания всего ответа — до этого реплика пользователя отображается как `"🎤 [Голосовой разговор]"`, а при сбое `hNvQHb` контекст между 1-й и 2-й репликами теряется (не гарантируется `conversationContextMap`).
- **Метод устранения**:
  1. Оставить единый `VoiceInputManager` (AAC/M4A) и удалить/убрать из UI привязку к GMS/iFlyTek в `SpeechToTextManager` (переключить кнопку микрофона в чате, блокноте и `AssistantActivity` на запись через `VoiceInputManager` -> `viewModel.transcribeVoiceAudio(...)`).
  2. Убрать мертвый метод `startVoiceRecording()` / `AudioRecorder` или перевести его на реальную запись файла с `localUri`.
  3. В `GeminiLiveDialog.kt` убрать вызов `voiceInputManager.startRecording()` из `startListeningCycle()`, оставив единственную точку запуска внутри `LaunchedEffect(liveState, isMicPaused)`.
  4. В `sendLiveAudioMessage`: гарантировать сохранение реального серверного `conversationId` и `(respId, choiceId)` в `conversationContextMap` до следующего хода, а также подставлять расшифрованную сервером реплику пользователя (`turn[3][1]`) сразу в список сообщений (с фоллбэком на `transcribeAudio`, если `hNvQHb` еще не вернул транскрипцию).

---

### 2.8. Редактирование сообщений и ветвление диалога
- **Локация**: [MainViewModel.kt:717-759](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/viewmodel/MainViewModel.kt#L717-L759), [GeminiWebClient.kt:1243-1290](file:///home/tech/gemini_hw/core/src/main/java/com/gemini/hw/core/network/GeminiWebClient.kt#L1243-L1290)
- **Суть проблемы**:
  1. `editMessage` просто обрезает локальный список `_messages` и вызывает `dispatchToGemini(newText)`, отправляя запрос с контекстом *последнего* ответа в `conversationContextMap`, а не родительского сообщения. На сервере Google это выглядит как новое сообщение в конец чата, а не редактирование.
  2. `createBranchInNewChat` при ошибке RPC `KDNZr` (или если `message.id` имеет формат `u_...`, а сервер ждет `r_...` / `rc_...`) молча возвращает `Result.success` с фейковым ID `"c_branch_" + System.currentTimeMillis()`.
- **Метод устранения**:
  1. Хранить в `ChatMessage` серверный `responseId` (`r_...`) и `choiceId` (`rc_...`) предыдущего хода; при `editMessage` передавать их как `parentMessageId` и использовать в `StreamGenerate` `contextArr`.
  2. В `createBranchInNewChat` передавать корректный `r_...` идентификатор хода и при ошибке RPC возвращать `Result.failure`, показывая пользователю сообщение об ошибке вместо создания фантомного чата.

---

### 2.9. Рендеринг Markdown-таблиц и размышлений модели (`thoughts`)
- **Локация**: [MarkdownText.kt:35-43,316-443](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/ui/components/MarkdownText.kt#L35-L43), [ChatEntities.kt:52](file:///home/tech/gemini_hw/core/src/main/java/com/gemini/hw/core/model/ChatEntities.kt#L52), [MessageBubble.kt](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/ui/components/MessageBubble.kt)
- **Суть проблемы**:
  1. `MarkdownText.kt` не распознает таблицы (`| Колонка 1 | Колонка 2 |` и разделитель `| --- | --- |`), хотя в [TEST_PLAN.md:54](file:///home/tech/gemini_hw/TEST_PLAN.md#L54) таблицы заявлены и Gemini регулярно выдает их (особенно в расчетах маршрутов по блокноту).
  2. В `ChatMessage` есть поле `thoughts: String?`, но ни `StreamGenerate`, ни `hNvQHb` его не извлекают, а `MessageBubble` не имеет сворачиваемого блока «Ход рассуждений».
- **Метод устранения**:
  1. Добавить `MarkdownBlock.Table(headers, rows)` в `MarkdownText.kt` и отрисовать его с горизонтальным скроллом, рамками ячеек и выделением шапки.
  2. Добавить парсинг поля рассуждений из `firstCandidate` и сворачиваемый `AnimatedVisibility`-блок в `MessageBubble.kt`.

---

### 2.10. Хардкодные моки и лишние разделы в меню
- **Локация**: [GeminiWebClient.kt:697-728](file:///home/tech/gemini_hw/core/src/main/java/com/gemini/hw/core/network/GeminiWebClient.kt#L697-L728), [GeminiWebClient.kt:1520](file:///home/tech/gemini_hw/core/src/main/java/com/gemini/hw/core/network/GeminiWebClient.kt#L1520), [MainViewModel.kt:152](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/viewmodel/MainViewModel.kt#L152), [MainActivity.kt:428-456](file:///home/tech/gemini_hw/app-native/src/main/java/com/gemini/hw/nativeapp/MainActivity.kt#L428-L456)
- **Суть проблемы**:
  1. В `getConversationMessages` зашиты захардкоженные ответы для `c_china_1` и `c_china_2`.
  2. При ошибке создания блокнота возвращается `Result.success("nb_...")` / `"notebooks/<random_uuid>"`, создавая локальный блокнот-призрак.
  3. В боковом меню (`ModalDrawerSheet`) остаются пункты «Боты (Gems)», «Запланированное» и «Задачи (Spark)» — при том, что функционал ботов пользователь попросил убрать, а «Запланированное»/«Spark» работают только в памяти до перезапуска приложения.
- **Метод устранения**:
  1. Удалить хардкод `c_china_1` и `c_china_2` и фейковую генерацию ID в `createNotebook`.
  2. Убрать из меню неиспользуемые/локальные разделы («Боты (Gems)», а также «Запланированное» и «Задачи (Spark)», пока для них не реализована реальная серверная синхронизация).
