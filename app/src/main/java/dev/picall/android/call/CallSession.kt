package dev.picall.android.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CallPhase { INCOMING, OUTGOING, CONNECTING, CONNECTED }

data class ActiveCall(
    val remoteId: String,
    val phase: CallPhase,
    val speakerEnabled: Boolean = false,
)

object CallSession {
    private val mutableState = MutableStateFlow<ActiveCall?>(null)
    val state = mutableState.asStateFlow()

    fun show(remoteId: String, phase: CallPhase) {
        mutableState.value = ActiveCall(remoteId, phase, mutableState.value?.speakerEnabled ?: false)
    }

    fun setSpeaker(enabled: Boolean) {
        mutableState.value = mutableState.value?.copy(speakerEnabled = enabled)
    }

    fun clear() {
        mutableState.value = null
    }
}
