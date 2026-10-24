package com.gemini.hw.nativeapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.gemini.hw.core.model.ChatMessage
import com.gemini.hw.nativeapp.auth.LoginActivity
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

@OptIn(ExperimentalMaterial3Api::class)
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

    var showGemsDialog by remember { mutableStateOf(false) }
    var showNotebooksDialog by remember { mutableStateOf(false) }
    var showSparkDialog by remember { mutableStateOf(false) }
    var showScheduledDialog by remember { mutableStateOf(false) }

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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceRecording()
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
                    label = { Text("Блокноты") },
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showNotebooksDialog = true
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

                NavigationDrawerItem(
                    label = { Text("Запланированное") },
                    icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showScheduledDialog = true
                    }
                )

                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    if (isLoggedIn) "История чатов" else "История чатов (требуется вход)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
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
                            NavigationDrawerItem(
                                label = { Text(convo.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                selected = convo.id == currentConvoId,
                                onClick = {
                                    viewModel.selectConversation(convo.id)
                                    coroutineScope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
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
                        ModelSelectorDropdown(
                            models = models,
                            selectedModel = selectedModel,
                            onModelSelected = { viewModel.selectModel(it) },
                            thinkingLevel = thinkingLevel,
                            onThinkingLevelSelected = { viewModel.setThinkingLevel(it) }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Меню")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.startNewChat() }) {
                            Icon(Icons.Default.Add, contentDescription = "Новый чат")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
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
                                onOpenCanvas = { doc -> viewModel.openCanvas(doc) }
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    Toast.makeText(context, "Прикрепление файлов и изображений", Toast.LENGTH_SHORT).show()
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
                                        if (isRecordingVoice) {
                                            viewModel.stopVoiceRecording()
                                        } else {
                                            viewModel.startVoiceRecording()
                                        }
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isRecordingVoice) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Голосовой ввод",
                                    tint = if (isRecordingVoice) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        val textToSend = inputText
                                        inputText = ""
                                        viewModel.sendMessage(textToSend)
                                    }
                                },
                                enabled = inputText.isNotBlank() && !isLoading,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (inputText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Отправить",
                                    tint = if (inputText.isNotBlank() && !isLoading) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    activeCanvas?.let { doc ->
        CanvasDialog(
            document = doc,
            onDismiss = { viewModel.closeCanvas() },
            onSave = { updated ->
                viewModel.sendMessage("Обнови документ: $updated")
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
                viewModel.startNewChat()
                viewModel.sendMessage("Используй материалы из блокнота '${nb.title}':\n${nb.content}\nЧто ты можешь предложить на основе этих данных?")
            },
            onDismiss = { showNotebooksDialog = false }
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
}
