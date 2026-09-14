package dev.picall.android.signaling

import kotlinx.coroutines.flow.Flow

interface SignalingClient {
    val events: Flow<SignalingEvent>

    suspend fun connect(accessToken: String)
    suspend fun send(message: SignalingMessage)
    suspend fun disconnect()
}

sealed interface SignalingMessage {
    data class Call(val calleeId: String) : SignalingMessage
    data class Offer(val callId: String, val sdp: String) : SignalingMessage
    data class Answer(val callId: String, val sdp: String) : SignalingMessage
    data class IceCandidate(val callId: String, val candidate: String) : SignalingMessage
    data class HangUp(val callId: String) : SignalingMessage
}

sealed interface SignalingEvent {
    data class IncomingCall(val callId: String, val callerId: String) : SignalingEvent
    data class RemoteOffer(val callId: String, val sdp: String) : SignalingEvent
    data class RemoteAnswer(val callId: String, val sdp: String) : SignalingEvent
    data class RemoteIceCandidate(val callId: String, val candidate: String) : SignalingEvent
    data class CallEnded(val callId: String) : SignalingEvent
}

