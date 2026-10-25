package com.gemini.hw.nativeapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.gemini.hw.core.model.*
import com.gemini.hw.nativeapp.assistant.AssistantActivity
import com.gemini.hw.nativeapp.audio.TtsManager
import com.gemini.hw.nativeapp.audio.VoiceInputManager
import com.gemini.hw.nativeapp.auth.LoginActivity
import java.io.File
import com.gemini.hw.nativeapp.ui.components.*
import com.gemini.hw.nativeapp.ui.theme.GeminiTheme
import com.gemini.hw.nativeapp.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GeminiTheme {
                MainScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val currentConvoId by viewModel.currentConversationId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val models by viewModel.models.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val thinkingLevel by viewModel.thinkingLevel.collectAsState()
    val isRecordingVoice by viewModel.isRecordingVoice.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val activeCanvas by viewModel.activeCanvas.collectAsState()

    val gems by viewModel.gems.collectAsState()
    val notebooks by viewModel.notebooks.collectAsState()
    val sparkTasks by viewModel.sparkTasks.collectAsState()
    val scheduledItems by viewModel.scheduledItems.collectAsState()
    val currentNotebook by viewModel.currentNotebook.collectAsState()

    var showGemsDialog by remember { mutableStateOf(false) }
    var showNotebooksDialog by remember { mutableStateOf(false) }
    var showCreateNotebookDialog by remember { mutableStateOf(false) }
    var showSparkDialog by remember { mutableStateOf(false) }
    var showScheduledDialog by remember { mutableStateOf(false) }
    var showAddSourceDialog by remember { mutableStateOf(false) }

    var selectedConvoForMenu by remember { mutableStateOf<Conversation?>(null) }
    var selectedNotebookChatForMenu by remember { mutableStateOf<Conversation?>(null) }
    var convoToRename by remember { mutableStateOf<Conversation?>(null) }
    var convoToMoveToNotebook by remember { mutableStateOf<Conversation?>(null) }

    androidx.activity.compose.BackHandler(enabled = currentNotebook != null) {
        if (currentConvoId != null || messages.isNotEmpty()) {
            viewModel.closeNotebookChat()
        } else {
            viewModel.closeNotebook()
        }
    }

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen && isLoggedIn) {
            viewModel.refreshConversations()
            viewModel.refreshGems()
            viewModel.refreshNotebooks()
        }
    }

    val voiceInputManager = remember { com.gemini.hw.nativeapp.audio.VoiceInputManager(context) }
    var isVoiceListening by remember { mutableStateOf(false) }

    val ttsManager = remember { TtsManager(context) }
    val speakingMsgId by ttsManager.speakingMessageId.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            voiceInputManager.stopListening()
            ttsManager.destroy()
        }
    }

    val isCanvasMode by viewModel.isCanvasMode.collectAsState()
    val pendingAttachments by viewModel.pendingAttachments.collectAsState()
    var showAttachmentMenu by remember { mutableStateOf(false) }

    // Camera launcher
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            viewModel.addAttachment(
                Attachment(
                    id = "att_${System.currentTimeMillis()}",
                    name = "Камера_${System.currentTimeMillis()}.jpg",
                    mimeType = "image/jpeg",
                    localUri = tempCameraUri.toString()
                )
            )
            Toast.makeText(context, "Фото прикреплено", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery / Photos launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            val name = "Фото_${System.currentTimeMillis()}.jpg"
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            viewModel.addAttachment(
                Attachment(
                    id = "att_${System.currentTimeMillis()}_${uri.hashCode()}",
                    name = name,
                    mimeType = mime,
                    localUri = uri.toString()
                )
            )
        }
    }

    // Documents / Google Drive launcher
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            var fileName = "Документ"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            } catch (_: Exception) {}
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            viewModel.addAttachment(
                Attachment(
                    id = "att_${System.currentTimeMillis()}_${uri.hashCode()}",
                    name = fileName,
                    mimeType = mime,
                    localUri = uri.toString()
                )
            )
        }
    }

    fun pickLatestScreenshot() {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)
                    val uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    viewModel.addAttachment(
                        Attachment(
                            id = "att_${System.currentTimeMillis()}",
                            name = name,
                            mimeType = mime,
                            localUri = uri.toString()
                        )
                    )
                    Toast.makeText(context, "Скриншот прикреплен: $name", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Скриншоты не найдены", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка доступа к скриншотам: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val activity = context as? Activity
    LaunchedEffect(activity?.intent) {
        val actIntent = activity?.intent ?: return@LaunchedEffect
        val assistQuery = actIntent.getStringExtra(AssistantActivity.EXTRA_ASSISTANT_QUERY)
        val assistImageUri = actIntent.getStringExtra(AssistantActivity.EXTRA_ASSISTANT_IMAGE_URI)
        if (!assistQuery.isNullOrBlank()) {
            val atts = if (!assistImageUri.isNullOrBlank()) {
                listOf(
                    Attachment(
                        id = "att_assist_${System.currentTimeMillis()}",
                        name = "Скриншот экрана.jpg",
                        mimeType = "image/jpeg",
                        localUri = assistImageUri
                    )
                )
            } else emptyList()
            viewModel.sendMessage(assistQuery, atts)
            actIntent.removeExtra(AssistantActivity.EXTRA_ASSISTANT_QUERY)
            actIntent.removeExtra(AssistantActivity.EXTRA_ASSISTANT_IMAGE_URI)
        } else if (actIntent.action == Intent.ACTION_SEND) {
            val text = actIntent.getStringExtra(Intent.EXTRA_TEXT)
            val uri = actIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            val atts = if (uri != null) {
                listOf(
                    Attachment(
                        id = "att_share_${System.currentTimeMillis()}",
                        name = "Вложение",
                        mimeType = actIntent.type ?: "application/octet-stream",
                        localUri = uri.toString()
                    )
                )
            } else emptyList()
            if (!text.isNullOrBlank() || atts.isNotEmpty()) {
                viewModel.sendMessage(text ?: "Посмотри прикрепленный файл", atts)
            }
            actIntent.action = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceInputManager.startListening(
                onPartialResult = { partial ->
                    inputText = partial
                },
                onFinalResult = { final ->
                    inputText = final
                    isVoiceListening = false
                },
                onError = { err ->
                    isVoiceListening = false
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                },
                onStateChange = { listening ->
                    isVoiceListening = listening
                }
            )
        } else {
            Toast.makeText(context, "Требуется разрешение на запись звука", Toast.LENGTH_SHORT).show()
        }
    }

    val loginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.loadInitialData()
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            loginLauncher.launch(Intent(context, LoginActivity::class.java))
        } else {
            viewModel.loadInitialData()
        }
    }

    if (!isLoggedIn) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Google Gemini",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Для синхронизации ваших чатов, блокнотов и загрузки актуальных моделей (Pro, Argon, Flash) требуется вход в аккаунт Google.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { loginLauncher.launch(Intent(context, LoginActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Войти через Google", fontSize = 16.sp)
                }
            }
        }
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Gemini", style = MaterialTheme.typography.titleLarge)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.startNewChat()
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Новый чат")
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()

                NavigationDrawerItem(
                    label = { Text("Боты (Gems)") },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showGemsDialog = true
                    }
                )

                NavigationDrawerItem(
                    label = { Text("Запланированное") },
                    icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showScheduledDialog = true
                    }
                )

                NavigationDrawerItem(
                    label = { Text("Задачи (Spark)") },
                    icon = { Icon(Icons.Default.Work, contentDescription = null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showSparkDialog = true
                    }
                )

                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    // --- Блокноты Section (as in 2.png) ---
                    item {
                        Text(
                            "Блокноты",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    item {
                        NavigationDrawerItem(
                            label = { Text("Новый блокнот") },
                            icon = { Icon(Icons.Default.Add, contentDescription = null) },
                            selected = false,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                showCreateNotebookDialog = true
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                    if (notebooks.isNotEmpty()) {
                        items(notebooks.take(4)) { nb ->
                            NavigationDrawerItem(
                                label = { Text(nb.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                                selected = currentNotebook?.id == nb.id,
                                onClick = {
                                    coroutineScope.launch { drawerState.close() }
                                    viewModel.openNotebook(nb)
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                    }
                    item {
                        NavigationDrawerItem(
                            label = { Text("Все блокноты") },
                            icon = { Icon(Icons.Default.MoreVert, contentDescription = null) },
                            selected = false,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                showNotebooksDialog = true
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }

                    // --- Недавние Section (as in 2.png) ---
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            if (isLoggedIn) "Недавние" else "История чатов (требуется вход)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    if (conversations.isEmpty()) {
                        item {
                            Text(
                                if (isLoggedIn) "Чатов пока нет" else "Войдите в аккаунт для загрузки истории",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        items(conversations) { convo ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        if (convo.id == currentConvoId && currentNotebook == null) MaterialTheme.colorScheme.secondaryContainer
                                        else Color.Transparent
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            viewModel.selectConversation(convo.id)
                                            coroutineScope.launch { drawerState.close() }
                                        },
                                        onLongClick = {
                                            selectedConvoForMenu = convo
                                        }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    convo.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (convo.id == currentConvoId && currentNotebook == null) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                if (isLoggedIn) {
                    NavigationDrawerItem(
                        label = { Text("Выйти из аккаунта") },
                        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                        selected = false,
                        onClick = {
                            viewModel.logout()
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                } else {
                    NavigationDrawerItem(
                        label = { Text("Войти через Google") },
                        icon = { Icon(Icons.Default.Login, contentDescription = null) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            loginLauncher.launch(Intent(context, LoginActivity::class.java))
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (currentNotebook != null) {
                            val activeChatTitle = conversations.firstOrNull { it.id == currentConvoId }?.title
                            Text(
                                if (currentConvoId != null && !activeChatTitle.isNullOrBlank()) activeChatTitle else currentNotebook!!.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            ModelSelectorDropdown(
                                models = models,
                                selectedModel = selectedModel,
                                onModelSelected = { viewModel.selectModel(it) },
                                thinkingLevel = thinkingLevel,
                                onThinkingLevelSelected = { viewModel.setThinkingLevel(it) }
                            )
                        }
                    },
                    navigationIcon = {
                        if (currentNotebook != null) {
                            IconButton(onClick = {
                                if (currentConvoId != null || messages.isNotEmpty()) {
                                    viewModel.closeNotebookChat()
                                } else {
                                    viewModel.closeNotebook()
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                            }
                        } else {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Меню")
                            }
                        }
                    },
                    actions = {
                        val activeConvo = conversations.firstOrNull { it.id == currentConvoId }
                            ?: (if (messages.isNotEmpty() || currentConvoId != null) Conversation(
                                id = currentConvoId ?: "",
                                title = messages.firstOrNull { it.role == MessageRole.USER }?.text?.take(30) ?: "Диалог"
                            ) else null)

                        if (activeConvo != null && (currentConvoId != null || messages.isNotEmpty())) {
                            IconButton(onClick = {
                                val title = activeConvo.title
                                val convoId = currentConvoId ?: activeConvo.id
                                val url = if (convoId.isNotBlank() && !convoId.startsWith("new")) "\nhttps://gemini.google.com/app/$convoId" else ""
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, "Диалог: $title$url")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Поделиться диалогом"))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Поделиться")
                            }

                            IconButton(onClick = {
                                convoToRename = activeConvo
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Переименовать")
                            }

                            IconButton(onClick = {
                                val convoId = currentConvoId ?: activeConvo.id
                                if (convoId.isNotBlank() && !convoId.startsWith("new")) {
                                    viewModel.deleteConversation(convoId)
                                } else {
                                    if (currentNotebook != null) {
                                        viewModel.closeNotebookChat()
                                    } else {
                                        viewModel.startNewChat()
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить")
                            }
                        }

                        IconButton(onClick = { viewModel.toggleCanvasMode() }) {
                            Icon(
                                Icons.Default.SpaceDashboard,
                                contentDescription = "Режим Canvas",
                                tint = if (isCanvasMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (currentNotebook == null) {
                            IconButton(onClick = { viewModel.startNewChat() }) {
                                Icon(Icons.Default.Add, contentDescription = "Новый чат")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (currentNotebook != null && currentConvoId == null && messages.isEmpty()) {
                    val notebookConvos = remember(currentNotebook, conversations) {
                        viewModel.getNotebookConversations(currentNotebook!!.id)
                    }
                    NotebookView(
                        notebook = currentNotebook!!,
                        conversations = notebookConvos,
                        models = models,
                        selectedModel = selectedModel,
                        thinkingLevel = thinkingLevel,
                        isLoading = isLoading,
                        onModelSelected = { viewModel.selectModel(it) },
                        onThinkingLevelSelected = { viewModel.setThinkingLevel(it) },
                        onSelectConversation = { convoId ->
                            viewModel.openNotebookChat(currentNotebook!!, convoId)
                        },
                        onConversationLongClick = { convo ->
                            selectedNotebookChatForMenu = convo
                        },
                        onSendMessage = { prompt ->
                            viewModel.startChatInNotebook(currentNotebook!!, prompt)
                        },
                        onAddSourcesClick = {
                            showAddSourceDialog = true
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {


                // Messages list (ALWAYS accessible)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "С чего начнем?",
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    } else {
                        items(messages, key = { it.id }) { msg ->
                            MessageBubble(
                                message = msg,
                                onEdit = { newText -> viewModel.editMessage(msg.id, newText) },
                                onBranch = { viewModel.createBranchInNewChat(msg) },
                                onOpenCanvas = { doc -> viewModel.openCanvas(doc) },
                                speakingMessageId = speakingMsgId,
                                onToggleTts = { m -> ttsManager.speak(m.id, m.text) }
                            )
                        }
                    }
                }

                // Bottom Input Bar (ALWAYS active)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column {
                        if (isRecordingVoice) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "🔴 Запись голоса...",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Button(
                                    onClick = { viewModel.stopVoiceRecording() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Остановить и отправить")
                                }
                            }
                        }

                        // Pending attachments preview bar
                        if (pendingAttachments.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pendingAttachments.forEach { att ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                if (att.mimeType.startsWith("image/")) Icons.Default.Image else Icons.Default.InsertDriveFile,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = att.name.take(18),
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1
                                            )
                                            IconButton(
                                                onClick = { viewModel.removeAttachment(att.id) },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Удалить",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    showAttachmentMenu = true
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Прикрепить файл",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text("Спросить Gemini", fontSize = 14.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(24.dp)),
                                maxLines = 4,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        if (isVoiceListening) {
                                            voiceInputManager.stopListening()
                                            isVoiceListening = false
                                        } else {
                                            voiceInputManager.startListening(
                                                onPartialResult = { partial ->
                                                    inputText = partial
                                                },
                                                onFinalResult = { final ->
                                                    inputText = final
                                                    isVoiceListening = false
                                                },
                                                onError = { err ->
                                                    isVoiceListening = false
                                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                                },
                                                onStateChange = { listening ->
                                                    isVoiceListening = listening
                                                }
                                            )
                                        }
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isVoiceListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    if (isVoiceListening) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = "Голосовой ввод",
                                    tint = if (isVoiceListening) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(
                                onClick = {
                                    try {
                                        val liveIntent = Intent().apply {
                                            setClassName(
                                                "com.google.android.googlequicksearchbox",
                                                "com.google.android.apps.search.assistant.surfaces.voice.robin.main.MainActivity"
                                            )
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(liveIntent)
                                    } catch (e: Exception) {
                                        try {
                                            val bardIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.bard")
                                            if (bardIntent != null) {
                                                context.startActivity(bardIntent)
                                            } else {
                                                Toast.makeText(context, "Gemini Live недоступен на этом устройстве", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Не удалось запустить Gemini Live", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = "Gemini Live",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = {
                                    if (inputText.isNotBlank() || pendingAttachments.isNotEmpty()) {
                                        val textToSend = inputText
                                        val attsToSend = pendingAttachments
                                        inputText = ""
                                        viewModel.clearAttachments()
                                        viewModel.sendMessage(textToSend, attsToSend)
                                    }
                                },
                                enabled = (inputText.isNotBlank() || pendingAttachments.isNotEmpty()) && !isLoading,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if ((inputText.isNotBlank() || pendingAttachments.isNotEmpty()) && !isLoading) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Отправить",
                                    tint = if ((inputText.isNotBlank() || pendingAttachments.isNotEmpty()) && !isLoading) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
        }
    }

    if (showAttachmentMenu) {
        AlertDialog(
            onDismissRequest = { showAttachmentMenu = false },
            title = { Text("Прикрепить к сообщению") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("Галерея и Google Фото") },
                        leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            showAttachmentMenu = false
                            galleryLauncher.launch("image/*")
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Живая камера") },
                        leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            showAttachmentMenu = false
                            try {
                                val photoFile = File(context.cacheDir, "camera/photo_${System.currentTimeMillis()}.jpg").apply {
                                    parentFile?.mkdirs()
                                }
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    photoFile
                                )
                                tempCameraUri = uri
                                cameraLauncher.launch(uri)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Ошибка запуска камеры: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Файлы и Google Диск") },
                        leadingContent = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            showAttachmentMenu = false
                            documentLauncher.launch(arrayOf("*/*"))
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Скриншот экрана") },
                        leadingContent = { Icon(Icons.Default.Screenshot, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            showAttachmentMenu = false
                            pickLatestScreenshot()
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAttachmentMenu = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    activeCanvas?.let { doc ->
        CanvasDialog(
            document = doc,
            onDismiss = { viewModel.closeCanvas() },
            onSave = { updated ->
                viewModel.sendMessage("Обнови документ: $updated")
            },
            onRevisionPrompt = { prompt ->
                viewModel.sendMessage(prompt)
            }
        )
    }

    if (showGemsDialog) {
        GemsDialog(
            gems = gems,
            onSelectGem = { gem ->
                viewModel.selectGem(gem)
            },
            onDismiss = { showGemsDialog = false }
        )
    }

    if (showNotebooksDialog) {
        NotebooksDialog(
            notebooks = notebooks,
            onAddNotebook = { title, content -> viewModel.addNotebook(title, content) },
            onRemoveNotebook = { id -> viewModel.removeNotebook(id) },
            onSelectNotebook = { nb ->
                viewModel.openNotebook(nb)
                showNotebooksDialog = false
            },
            onDismiss = { showNotebooksDialog = false }
        )
    }

    if (showCreateNotebookDialog) {
        var createTitle by remember { mutableStateOf("") }
        var createContent by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateNotebookDialog = false },
            title = { Text("Новый блокнот") },
            text = {
                Column {
                    OutlinedTextField(
                        value = createTitle,
                        onValueChange = { createTitle = it },
                        label = { Text("Название блокнота") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = createContent,
                        onValueChange = { createContent = it },
                        label = { Text("Заметки (необязательно)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (createTitle.isNotBlank()) {
                            viewModel.addNotebook(createTitle.trim(), createContent.trim())
                            showCreateNotebookDialog = false
                        }
                    }
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateNotebookDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showSparkDialog) {
        SparkDialog(
            tasks = sparkTasks,
            onDismiss = { showSparkDialog = false }
        )
    }

    if (showScheduledDialog) {
        ScheduledDialog(
            items = scheduledItems,
            onDismiss = { showScheduledDialog = false }
        )
    }

    selectedConvoForMenu?.let { convo ->
        ChatActionMenuDialog(
            conversation = convo,
            onDismiss = { selectedConvoForMenu = null },
            onShare = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, "Диалог: ${convo.title}\nhttps://gemini.google.com/app/${convo.id}")
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, "Поделиться диалогом")
                context.startActivity(shareIntent)
            },
            onRename = {
                convoToRename = convo
            },
            onMoveToNotebook = {
                convoToMoveToNotebook = convo
            },
            onDelete = {
                viewModel.deleteConversation(convo.id)
            }
        )
    }

    selectedNotebookChatForMenu?.let { convo ->
        val nbId = currentNotebook?.id ?: convo.notebookId ?: ""
        NotebookChatActionMenuDialog(
            conversation = convo,
            onDismiss = { selectedNotebookChatForMenu = null },
            onMoveToOtherNotebook = {
                convoToMoveToNotebook = convo
            },
            onRemoveFromNotebook = {
                viewModel.removeConversationFromNotebook(convo.id, nbId)
            },
            onRename = {
                convoToRename = convo
            },
            onDelete = {
                viewModel.deleteConversation(convo.id)
            }
        )
    }

    convoToRename?.let { convo ->
        RenameDialog(
            initialTitle = convo.title,
            onDismiss = { convoToRename = null },
            onConfirm = { newTitle ->
                viewModel.renameConversation(convo.id, newTitle)
            }
        )
    }

    convoToMoveToNotebook?.let { convo ->
        SelectNotebookDialog(
            notebooks = notebooks,
            currentNotebookId = convo.notebookId,
            onDismiss = { convoToMoveToNotebook = null },
            onNotebookSelected = { targetNb ->
                viewModel.moveConversationToNotebook(convo.id, targetNb.id)
            }
        )
    }

    if (showAddSourceDialog && currentNotebook != null) {
        AddSourceDialog(
            notebookTitle = currentNotebook!!.title,
            onDismiss = { showAddSourceDialog = false },
            onAddSource = { text ->
                viewModel.addNotebook(currentNotebook!!.title, text)
                Toast.makeText(context, "Источник добавлен в блокнот", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotebookView(
    notebook: Notebook,
    conversations: List<Conversation>,
    models: List<GeminiModel>,
    selectedModel: GeminiModel?,
    thinkingLevel: String,
    isLoading: Boolean,
    onModelSelected: (GeminiModel) -> Unit,
    onThinkingLevelSelected: (String) -> Unit,
    onSelectConversation: (String) -> Unit,
    onConversationLongClick: (Conversation) -> Unit,
    onSendMessage: (String) -> Unit,
    onAddSourcesClick: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var showModelMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val nbVoiceManager = remember { com.gemini.hw.nativeapp.audio.VoiceInputManager(context) }
    var isNbVoiceListening by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            nbVoiceManager.stopListening()
        }
    }

    val emoji = remember(notebook.title) {
        val lower = notebook.title.lowercase()
        when {
            lower.contains("китай") || lower.contains("тур") || lower.contains("поезд") || lower.contains("путеш") -> "🗺️"
            lower.contains("вайб") || lower.contains("код") || lower.contains("dev") -> "💻"
            lower.contains("идеи") || lower.contains("taobao") || lower.contains("шоп") -> "🛍️"
            lower.contains("контент") || lower.contains("медиа") -> "📝"
            else -> "📓"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // --- Top Row: Emoji, Title and "Добавить источники" Button ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = emoji,
                    fontSize = 28.sp,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Text(
                    text = notebook.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                onClick = onAddSourcesClick,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Добавить источники",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- Search / Input Pill Container (matching 3.png) ---
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onAddSourcesClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Прикрепить",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    decorationBox = { innerTextField ->
                        if (inputText.isEmpty()) {
                            Text(
                                "Спросить Gemini",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Model selector badge inside pill
                Box {
                    Surface(
                        onClick = { showModelMenu = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = selectedModel?.displayName?.replace("Gemini ", "") ?: "Flash",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showModelMenu,
                        onDismissRequest = { showModelMenu = false }
                    ) {
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.displayName) },
                                onClick = {
                                    onModelSelected(model)
                                    showModelMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = {
                        if (isNbVoiceListening) {
                            nbVoiceManager.stopListening()
                            isNbVoiceListening = false
                        } else {
                            nbVoiceManager.startListening(
                                onPartialResult = { partial ->
                                    inputText = partial
                                },
                                onFinalResult = { final ->
                                    inputText = final
                                    isNbVoiceListening = false
                                },
                                onError = { err ->
                                    isNbVoiceListening = false
                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                },
                                onStateChange = { listening ->
                                    isNbVoiceListening = listening
                                }
                            )
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (isNbVoiceListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Голос",
                        tint = if (isNbVoiceListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        try {
                            val liveIntent = Intent().apply {
                                setClassName(
                                    "com.google.android.googlequicksearchbox",
                                    "com.google.android.apps.search.assistant.surfaces.voice.robin.main.MainActivity"
                                )
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(liveIntent)
                        } catch (e: Exception) {
                            try {
                                val bardIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.bard")
                                if (bardIntent != null) {
                                    context.startActivity(bardIntent)
                                } else {
                                    Toast.makeText(context, "Gemini Live недоступен на этом устройстве", Toast.LENGTH_SHORT).show()
                                }
                            } catch (_: Exception) {
                                Toast.makeText(context, "Не удалось запустить Gemini Live", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = "Gemini Live",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            val text = inputText
                            inputText = ""
                            onSendMessage(text)
                        }
                    },
                    enabled = inputText.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Отправить",
                        tint = if (inputText.isNotBlank() && !isLoading) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- "Предыдущие чаты" Section ---
        Text(
            text = "Предыдущие чаты",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "В этом блокноте еще нет чатов.\nЗадайте вопрос выше, чтобы начать диалог.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(conversations) { convo ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { onSelectConversation(convo.id) },
                                onLongClick = { onConversationLongClick(convo) }
                            )
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = convo.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        val dt = convo.dateText
                        if (!dt.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = dt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
fun ChatActionMenuDialog(
    conversation: Conversation,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMoveToNotebook: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                conversation.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Поделиться") },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onShare()
                    }
                )
                ListItem(
                    headlineContent = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onDelete()
                    }
                )
                ListItem(
                    headlineContent = { Text("Переименовать") },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onRename()
                    }
                )
                ListItem(
                    headlineContent = { Text("Переместить в блокнот") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onMoveToNotebook()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun NotebookChatActionMenuDialog(
    conversation: Conversation,
    onDismiss: () -> Unit,
    onMoveToOtherNotebook: () -> Unit,
    onRemoveFromNotebook: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                conversation.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Перенести в другой блокнот") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onMoveToOtherNotebook()
                    }
                )
                ListItem(
                    headlineContent = { Text("Удалить из блокнота") },
                    leadingContent = { Icon(Icons.Default.Close, contentDescription = null) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onRemoveFromNotebook()
                    }
                )
                ListItem(
                    headlineContent = { Text("Переименовать") },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onRename()
                    }
                )
                ListItem(
                    headlineContent = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onDelete()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun SelectNotebookDialog(
    notebooks: List<Notebook>,
    currentNotebookId: String? = null,
    onDismiss: () -> Unit,
    onNotebookSelected: (Notebook) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите блокнот") },
        text = {
            if (notebooks.isEmpty()) {
                Text("Нет доступных блокнотов")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(notebooks) { nb ->
                        ListItem(
                            headlineContent = { Text(nb.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                            modifier = Modifier.clickable {
                                onDismiss()
                                onNotebookSelected(nb)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun RenameDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переименовать") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                if (title.isNotBlank()) {
                    onConfirm(title.trim())
                    onDismiss()
                }
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun AddSourceDialog(
    notebookTitle: String,
    onDismiss: () -> Unit,
    onAddSource: (String) -> Unit
) {
    var sourceText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить источники") },
        text = {
            Column {
                Text(
                    "Блокнот: $notebookTitle",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Поддерживаются файлы Google Диска, документы, ссылки и заметки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = sourceText,
                    onValueChange = { sourceText = it },
                    label = { Text("Текст заметки или ссылка") },
                    placeholder = { Text("Введите содержание источника...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (sourceText.isNotBlank()) {
                    onAddSource(sourceText.trim())
                    onDismiss()
                }
            }) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
