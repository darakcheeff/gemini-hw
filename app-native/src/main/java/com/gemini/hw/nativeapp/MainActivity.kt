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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    val isRecordingVoice by viewModel.isRecordingVoice.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val activeCanvas by viewModel.activeCanvas.collectAsState()

    val gems by viewModel.gems.collectAsState()
    val sparkTasks by viewModel.sparkTasks.collectAsState()
    val scheduledItems by viewModel.scheduledItems.collectAsState()

    var showGemsDialog by remember { mutableStateOf(false) }
    var showSparkDialog by remember { mutableStateOf(false) }
    var showScheduledDialog by remember { mutableStateOf(false) }

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll when new messages arrive
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Permission launcher for microphone
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceRecording()
        } else {
            Toast.makeText(context, "Требуется разрешение на запись звука", Toast.LENGTH_SHORT).show()
        }
    }

    // Login launcher
    val loginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.loadInitialData()
        }
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

                // Special sections
                NavigationDrawerItem(
                    label = { Text("Блокноты (Gems)") },
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showGemsDialog = true
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
                    "История чатов",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
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

                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Выйти из аккаунта") },
                    icon = { Icon(Icons.Default.Logout, contentDescription = null) },
                    selected = false,
                    onClick = {
                        viewModel.logout()
                        coroutineScope.launch { drawerState.close() }
                    }
                )
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
                            onModelSelected = { viewModel.selectModel(it) }
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (!isLoggedIn) {
                    // Not logged in empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Добро пожаловать в Gemini",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Автономный клиент для Huawei без зависимости от сервисов Google (GMS)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                loginLauncher.launch(Intent(context, LoginActivity::class.java))
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Войти через Google")
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Messages list
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
                                            .padding(top = 100.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Чем я могу помочь сегодня?",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
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

                        // Bottom Input Bar
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
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = inputText,
                                        onValueChange = { inputText = it },
                                        placeholder = { Text("Спросите о чем угодно…", fontSize = 14.sp) },
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

                                    // Voice direct button
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

                                    // Send button
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
                                            Icons.Default.Send,
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
        }
    }

    // Canvas Dialog
    activeCanvas?.let { doc ->
        CanvasDialog(
            document = doc,
            onDismiss = { viewModel.closeCanvas() },
            onSave = { updated ->
                viewModel.sendMessage("Обнови документ: $updated")
            }
        )
    }

    // Gems Dialog
    if (showGemsDialog) {
        GemsDialog(
            gems = gems,
            onSelectGem = { gem ->
                viewModel.sendMessage("Активируй блокнот: ${gem.name}. Инструкции: ${gem.systemInstructions}")
            },
            onDismiss = { showGemsDialog = false }
        )
    }

    // Spark Dialog
    if (showSparkDialog) {
        SparkDialog(
            tasks = sparkTasks,
            onDismiss = { showSparkDialog = false }
        )
    }

    // Scheduled Dialog
    if (showScheduledDialog) {
        ScheduledDialog(
            items = scheduledItems,
            onDismiss = { showScheduledDialog = false }
        )
    }
}
