package dev.picall.android.call

sealed interface CallState {
    data object Idle : CallState
    data class Dialing(val calleeId: String) : CallState
    data class Incoming(val callerId: String) : CallState
    data object Connecting : CallState
    data object Connected : CallState
    data class Failed(val reason: String) : CallState
}

