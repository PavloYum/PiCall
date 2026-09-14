package dev.picall.android.webrtc

interface RtcEngine {
    suspend fun startLocalAudio()
    suspend fun createOffer(): String
    suspend fun createAnswer(remoteOffer: String): String
    suspend fun applyRemoteAnswer(sdp: String)
    suspend fun addRemoteIceCandidate(candidate: String)
    suspend fun setMuted(muted: Boolean)
    suspend fun close()
}

