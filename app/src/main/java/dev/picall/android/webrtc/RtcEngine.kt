package dev.picall.android.webrtc

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.picall.android.network.TurnCredentials
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule

class RtcEngine(
    context: Context,
    turn: TurnCredentials,
    private val onIce: (String, Int, String) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit,
) {
    private val factory: PeerConnectionFactory
    private val audioDeviceModule: AudioDeviceModule
    private val peer: PeerConnection
    private val audioSource: AudioSource
    private val audioTrack: AudioTrack
    private val pendingIce = mutableListOf<IceCandidate>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var remoteDescriptionReady = false
    private var closed = false

    init {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
        val servers = turn.urls.map { PeerConnection.IceServer.builder(it).setUsername(turn.username).setPassword(turn.credential).createIceServer() }
        peer = requireNotNull(factory.createPeerConnection(PeerConnection.RTCConfiguration(servers), Observer()))
        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("picall-audio", audioSource)
        audioTrack.setEnabled(true)
        peer.addTrack(audioTrack, listOf("picall"))
    }

    fun createOffer(ready: (String) -> Unit) = peer.createOffer(CreateSdp(ready), MediaConstraints())
    fun answer(remote: String, ready: (String) -> Unit) {
        peer.setRemoteDescription(SetSdp(onSuccess = {
            markRemoteDescriptionReady()
            peer.createAnswer(CreateSdp(ready), MediaConstraints())
        }), SessionDescription(SessionDescription.Type.OFFER, remote))
    }
    fun applyAnswer(sdp: String) = peer.setRemoteDescription(SetSdp(::markRemoteDescriptionReady), SessionDescription(SessionDescription.Type.ANSWER, sdp))
    fun addIce(mid: String, line: Int, candidate: String) {
        val ice = IceCandidate(mid, line, candidate)
        synchronized(pendingIce) {
            if (remoteDescriptionReady) peer.addIceCandidate(ice) else pendingIce += ice
        }
    }
    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        peer.dispose()
        audioTrack.dispose()
        audioSource.dispose()
        factory.dispose()
        audioDeviceModule.release()
    }

    private fun markRemoteDescriptionReady() = synchronized(pendingIce) {
        remoteDescriptionReady = true
        pendingIce.forEach(peer::addIceCandidate)
        pendingIce.clear()
    }

    private inner class CreateSdp(private val ready: (String) -> Unit) : SdpObserver {
        override fun onCreateSuccess(value: SessionDescription) {
            peer.setLocalDescription(SetSdp { ready(value.description) }, value)
        }
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = notifyDisconnected()
        override fun onSetFailure(error: String?) = notifyDisconnected()
    }

    private inner class SetSdp(private val onSuccess: () -> Unit = {}) : SdpObserver {
        override fun onCreateSuccess(value: SessionDescription) = Unit
        override fun onSetSuccess() = onSuccess()
        override fun onCreateFailure(error: String?) = notifyDisconnected()
        override fun onSetFailure(error: String?) = notifyDisconnected()
    }

    private inner class Observer : PeerConnection.Observer {
        override fun onIceCandidate(value: IceCandidate) = onIce(value.sdpMid ?: "0", value.sdpMLineIndex, value.sdp)
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                mainHandler.post { if (!closed) onConnected() }
            }
            if (state == PeerConnection.PeerConnectionState.FAILED || state == PeerConnection.PeerConnectionState.CLOSED) {
                notifyDisconnected()
            }
        }
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
        override fun onIceConnectionReceivingChange(value: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidatesRemoved(values: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
            receiver.track()?.setEnabled(true)
        }
    }

    private fun notifyDisconnected() {
        mainHandler.post { if (!closed) onDisconnected() }
    }
}
