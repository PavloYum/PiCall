package dev.picall.android

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("picall", MODE_PRIVATE)
        val saved = prefs.getString("token", null)?.let {
            Session(prefs.getString("id", "")!!, prefs.getString("name", "")!!, it)
        }
        setContent { MaterialTheme {
            var session by remember { mutableStateOf(saved) }
            Surface(Modifier.fillMaxSize()) {
                if (session == null) RegistrationScreen { created ->
                    prefs.edit().putString("id", created.piCallId).putString("name", created.displayName)
                        .putString("token", created.token).apply()
                    session = created
                } else ParticipantsScreen(requireNotNull(session))
            }
        } }
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
    val api = remember(session) { PiCallApi(session) }
    val participants = remember { mutableStateListOf<Participant>() }
    var message by remember { mutableStateOf("Подключение…") }

    LaunchedEffect(api) {
        while (true) {
            runCatching { api.participants() }.onSuccess {
                participants.clear(); participants.addAll(it)
                message = if (it.isEmpty()) "Других участников пока нет" else ""
            }.onFailure { message = it.message ?: "Сервер недоступен" }
            delay(5_000)
        }
    }
    DisposableEffect(api) {
        api.connect({ id, online ->
            val index = participants.indexOfFirst { it.piCallId == id }
            if (index >= 0) participants[index] = participants[index].copy(online = online)
        }, { message = it })
        onDispose(api::close)
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("PiCall", style = MaterialTheme.typography.headlineLarge)
        Text("${session.displayName} · ${session.piCallId}")
        Spacer(Modifier.height(24.dp))
        if (message.isNotEmpty()) Text(message)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(participants.sortedWith(compareByDescending<Participant> { it.online }.thenBy { it.displayName })) { person ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (person.online) "●" else "○", color = if (person.online) Color(0xFF16803A) else Color.Gray)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(person.displayName, style = MaterialTheme.typography.titleMedium)
                        Text("${person.piCallId} · ${if (person.online) "В сети" else "Не в сети"}")
                    }
                    OutlinedButton(onClick = {
                        message = if (api.call(person.piCallId)) "Вызов: ${person.displayName}" else "Нет связи с сервером"
                    }, enabled = person.online) { Text("Позвонить") }
                }
            }
        }
    }
}
