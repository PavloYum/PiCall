package dev.picall.android.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CallPhase { INCOMING, OUTGOING, CONNECTING, CONNECTED }

data class ActiveCall(
    val remoteId: String,
    val phase: CallPhase,
    val speakerEnabled: Boolean = false,
    val microphoneMuted: Boolean = false,
    val connectedAtMillis: Long? = null,
)

object CallSession {
    private val mutableState = MutableStateFlow<ActiveCall?>(null)
    val state = mutableState.asStateFlow()

    fun show(remoteId: String, phase: CallPhase) {
        val previous = mutableState.value
        mutableState.value = ActiveCall(
            remoteId,
            phase,
            previous?.speakerEnabled ?: false,
            previous?.microphoneMuted ?: false,
            if (phase == CallPhase.CONNECTED) previous?.connectedAtMillis ?: System.currentTimeMillis() else previous?.connectedAtMillis,
        )
    }

    fun setSpeaker(enabled: Boolean) {
        mutableState.value = mutableState.value?.copy(speakerEnabled = enabled)
    }

    fun setMicrophoneMuted(muted: Boolean) {
        mutableState.value = mutableState.value?.copy(microphoneMuted = muted)
    }

    fun clear() {
        mutableState.value = null
    }
}
