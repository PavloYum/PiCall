package dev.picall.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.picall.android.call.CallState
import dev.picall.android.identity.PiCallId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CallScreen()
                }
            }
        }
    }
}

@Composable
private fun CallScreen() {
    var callee by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<CallState>(CallState.Idle) }
    val calleeId = PiCallId.parseOrNull(callee)

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PiCall", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(state.label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = callee,
            onValueChange = { callee = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("PiCall ID") },
            placeholder = { Text("PC-XXXX-XXXX") },
            supportingText = {
                if (callee.isNotBlank() && calleeId == null) {
                    Text("Формат: PC-XXXX-XXXX")
                }
            },
            isError = callee.isNotBlank() && calleeId == null,
            singleLine = true,
            enabled = state is CallState.Idle,
        )
        Spacer(Modifier.height(16.dp))

        when (state) {
            CallState.Idle -> Button(
                onClick = { state = CallState.Dialing(requireNotNull(calleeId).value) },
                enabled = calleeId != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Позвонить") }

            else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { state = CallState.Connected }) {
                    Text("Соединить (демо)")
                }
                OutlinedButton(onClick = { state = CallState.Idle }) {
                    Text("Завершить")
                }
            }
        }
    }
}

private val CallState.label: String
    get() = when (this) {
        CallState.Idle -> "Готов к звонку"
        is CallState.Dialing -> "Вызов: $calleeId"
        is CallState.Incoming -> "Входящий: $callerId"
        CallState.Connecting -> "Соединение…"
        CallState.Connected -> "Разговор"
        is CallState.Failed -> "Ошибка: $reason"
    }
