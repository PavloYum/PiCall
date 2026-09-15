package dev.picall.android

import android.os.Bundle
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.picall.android.network.*
import dev.picall.android.call.CallPhase
import dev.picall.android.call.CallSession
import dev.picall.android.call.ActiveCall
import dev.picall.android.service.PiCallService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("picall", MODE_PRIVATE)
        val saved = prefs.getString("token", null)?.let {
            Session(prefs.getString("id", "")!!, prefs.getString("name", "")!!, it)
        }
        if (saved != null) startForegroundService(Intent(this, PiCallService::class.java))
        setContent { MaterialTheme {
            var session by remember { mutableStateOf(saved) }
            Surface(Modifier.fillMaxSize()) {
                if (session == null) RegistrationScreen { created ->
                    prefs.edit().putString("id", created.piCallId).putString("name", created.displayName)
                        .putString("token", created.token).apply()
                    startForegroundService(Intent(this, PiCallService::class.java))
                    session = created
                } else ParticipantsScreen(requireNotNull(session))
            }
        } }
    }

    override fun onResume() {
        super.onResume()
        if (intent?.action == PiCallService.ACTION_ACCEPT) {
            startService(Intent(this, PiCallService::class.java).setAction(PiCallService.ACTION_ACCEPT))
            intent.action = null
        }
    }
}

@Composable
private fun RegistrationScreen(onRegistered: (Session) -> Unit) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Добро пожаловать в PiCall", style = MaterialTheme.typography.headlineMedium)
        Text("Домашняя телефонная сеть")
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Ваше имя") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Пароль — минимум 10 символов") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(repeat, { repeat = it }, label = { Text("Повторите пароль") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(20.dp))
        Button(onClick = { scope.launch {
            loading = true; error = null
            runCatching { PiCallApi().register(name.trim(), password) }.onSuccess(onRegistered).onFailure { error = it.message }
            loading = false
        } }, enabled = !loading && name.trim().length >= 2 && password.length >= 10 && password == repeat, modifier = Modifier.fillMaxWidth()) {
            if (loading) CircularProgressIndicator(Modifier.size(24.dp)) else Text("Зарегистрироваться")
        }
    }
}

@Composable
private fun ParticipantsScreen(session: Session) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val api = remember(session) { PiCallApi(session) }
    val participants = remember { mutableStateListOf<Participant>() }
    var message by remember { mutableStateOf("Подключение…") }
    val call by CallSession.state.collectAsState()
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    var batteryUnrestricted by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) }

    fun requestBackgroundAccess() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            })
        }.onFailure {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    LaunchedEffect(Unit) {
        val needed = buildList {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) permissions.launch(needed.toTypedArray())
        val prefs = context.getSharedPreferences("picall", android.content.Context.MODE_PRIVATE)
        if (!batteryUnrestricted && !prefs.getBoolean("battery_prompted", false)) {
            prefs.edit().putBoolean("battery_prompted", true).apply()
            requestBackgroundAccess()
        }
    }

    LaunchedEffect(api) {
        while (true) {
            runCatching { api.participants() }.onSuccess {
                participants.clear(); participants.addAll(it)
                message = if (it.isEmpty()) "Других участников пока нет" else ""
            }.onFailure { message = it.message ?: "Сервер недоступен" }
            delay(5_000)
        }
    }
    if (call != null) {
        CallScreen(requireNotNull(call))
        return
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("PiCall", style = MaterialTheme.typography.headlineLarge)
        Text("${session.displayName} · ${session.piCallId}")
        if (!batteryUnrestricted) {
            Spacer(Modifier.height(12.dp))
            AssistChip(
                onClick = {
                    requestBackgroundAccess()
                    batteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                },
                label = { Text("Разрешить постоянную работу в фоне") },
            )
        }
        Spacer(Modifier.height(24.dp))
        if (message.isNotEmpty()) Text(message)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(participants.sortedWith(compareBy<Participant> { if (it.status == "online") 0 else if (it.status == "busy") 1 else 2 }.thenBy { it.displayName })) { person ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when (person.status) {
                        "online" -> Color(0xFF16803A)
                        "busy" -> Color(0xFFD35400)
                        else -> Color.Gray
                    }
                    Text(if (person.online) "●" else "○", color = statusColor)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(person.displayName, style = MaterialTheme.typography.titleMedium)
                        val statusText = when (person.status) { "online" -> "В сети"; "busy" -> "Занят"; else -> "Не в сети" }
                        Text("${person.piCallId} · $statusText", color = statusColor)
                    }
                    OutlinedButton(onClick = {
                        context.startService(Intent(context, PiCallService::class.java).setAction(PiCallService.ACTION_CALL).putExtra(PiCallService.EXTRA_TARGET, person.piCallId))
                        message = "Вызов: ${person.displayName}"
                    }, enabled = person.status == "online") { Text(if (person.busy) "Занят" else "Позвонить") }
                }
            }
        }
    }
}

@Composable
private fun CallScreen(call: ActiveCall) {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun action(value: String, configure: Intent.() -> Unit = {}) {
        context.startService(Intent(context, PiCallService::class.java).setAction(value).apply(configure))
    }
    val title = when (call.phase) {
        CallPhase.INCOMING -> "Входящий звонок"
        CallPhase.OUTGOING -> "Вызов…"
        CallPhase.CONNECTING -> "Соединение…"
        CallPhase.CONNECTED -> "Разговор"
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call.connectedAtMillis) {
        while (call.connectedAtMillis != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        call.connectedAtMillis?.let {
            val seconds = ((now - it) / 1_000).coerceAtLeast(0)
            Text("%02d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text(call.remoteId, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(40.dp))
        if (call.phase == CallPhase.INCOMING) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { action(PiCallService.ACTION_ACCEPT) }) { Text("Ответить") }
                Button(
                    onClick = { action(PiCallService.ACTION_REJECT) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Отклонить") }
            }
        } else {
            FilterChip(
                selected = call.speakerEnabled,
                onClick = {
                    action(PiCallService.ACTION_SPEAKER) {
                        putExtra(PiCallService.EXTRA_SPEAKER, !call.speakerEnabled)
                    }
                },
                label = { Text(if (call.speakerEnabled) "Громкая связь включена" else "Включить громкую связь") },
            )
            Spacer(Modifier.height(12.dp))
            FilterChip(
                selected = call.microphoneMuted,
                onClick = {
                    action(PiCallService.ACTION_MUTE) {
                        putExtra(PiCallService.EXTRA_MUTED, !call.microphoneMuted)
                    }
                },
                label = { Text(if (call.microphoneMuted) "Микрофон выключен" else "Выключить микрофон") },
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { action(PiCallService.ACTION_HANGUP) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Завершить") }
        }
    }
}
