package dev.picall.android.network

import dev.picall.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL

data class Session(val piCallId: String, val displayName: String, val token: String)
data class Participant(val piCallId: String, val displayName: String, val online: Boolean)
data class TurnCredentials(val urls: List<String>, val username: String, val credential: String)

class PiCallApi(private val session: Session? = null) {
    private val http = OkHttpClient()
    private var signaling: WebSocket? = null
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var closedByUser = false
    private var presenceCallback: ((String, Boolean) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null
    private var readyCallback: (() -> Unit)? = null
    private var signalCallback: ((JSONObject) -> Unit)? = null

    suspend fun register(name: String, password: String): Session = withContext(Dispatchers.IO) {
        val json = request("/v1/register", "POST", JSONObject().put("displayName", name).put("password", password))
        Session(json.getString("piCallId"), json.getString("displayName"), json.getString("token"))
    }

    suspend fun participants(): List<Participant> = withContext(Dispatchers.IO) {
        val items: JSONArray = request("/v1/participants", "GET").getJSONArray("participants")
        List(items.length()) { i -> items.getJSONObject(i).run { Participant(getString("piCallId"), getString("displayName"), getBoolean("online")) } }
    }

    suspend fun turnCredentials(): TurnCredentials = withContext(Dispatchers.IO) {
        val json = request("/v1/turn-credentials", "GET")
        val array = json.getJSONArray("urls")
        TurnCredentials(List(array.length()) { array.getString(it) }, json.getString("username"), json.getString("credential"))
    }

    fun connect(onPresence: (String, Boolean) -> Unit, onError: (String) -> Unit, onReady: () -> Unit = {}, onSignal: (JSONObject) -> Unit = {}) {
        closedByUser = false
        presenceCallback = onPresence
        errorCallback = onError
        readyCallback = onReady
        signalCallback = onSignal
        openSignaling()
    }

    private fun openSignaling() {
        val active = requireNotNull(session)
        val url = BuildConfig.API_BASE_URL.replaceFirst("https://", "wss://") + "/v1/signaling?token=${active.token}"
        signaling = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = JSONObject(text)
                if (json.optString("type") == "ready") readyCallback?.invoke()
                if (json.optString("type") == "presence") presenceCallback?.invoke(json.getString("piCallId"), json.getBoolean("online"))
                if (json.optString("type") in setOf("call", "accept", "reject", "offer", "answer", "ice", "hangup", "unavailable")) signalCallback?.invoke(json)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                errorCallback?.invoke(t.message ?: "Нет соединения")
                scheduleReconnect()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = scheduleReconnect()
        })
    }

    private fun scheduleReconnect() {
        if (closedByUser) return
        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectHandler.postDelayed({ if (!closedByUser) openSignaling() }, 3_000)
    }

    fun signal(type: String, id: String, configure: JSONObject.() -> Unit = {}): Boolean = signaling?.send(
        JSONObject().put("type", type).put("to", id).apply(configure).toString(),
    ) == true
    fun call(id: String) = signal("call", id)
    fun close() {
        closedByUser = true
        reconnectHandler.removeCallbacksAndMessages(null)
        signaling?.close(1000, "screen closed")
        http.dispatcher.executorService.shutdown()
    }

    private fun request(path: String, method: String, body: JSONObject? = null): JSONObject {
        val connection = URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Accept", "application/json")
        session?.let { connection.setRequestProperty("Authorization", "Bearer ${it.token}") }
        body?.let {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { output -> output.write(it.toString().toByteArray()) }
        }
        val status = connection.responseCode
        val text = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) throw IllegalStateException(JSONObject(text).optString("error", "HTTP $status"))
        return JSONObject(text)
    }
}
