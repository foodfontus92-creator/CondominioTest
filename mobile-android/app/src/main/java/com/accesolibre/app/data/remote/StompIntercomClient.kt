package com.accesolibre.app.data.remote

import com.accesolibre.app.data.local.SecureStorage
import com.accesolibre.app.domain.model.IncomingCall
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.Instant

enum class StompConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * Lightweight STOMP 1.2 client over OkHttp WebSocket for local LAN virtual intercom.
 * Connects to ws://<host>/ws/intercom with JWT in the STOMP CONNECT frame and subscribes
 * to /topic/condo/{condoId}/unit/{unitId}/intercom (or condo-wide feed for admins).
 */
class StompIntercomClient(
    private val okHttpClient: OkHttpClient,
    private val storage: SecureStorage,
    private val gson: Gson = Gson(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(StompConnectionState.DISCONNECTED)
    val connectionState: StateFlow<StompConnectionState> = _connectionState.asStateFlow()

    private val _incomingCall = MutableStateFlow<IncomingCall?>(null)
    val incomingCall: StateFlow<IncomingCall?> = _incomingCall.asStateFlow()

    private val _lastEventAtMs = MutableStateFlow(0L)
    val lastEventAtMs: StateFlow<Long> = _lastEventAtMs.asStateFlow()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var desiredActive = false
    private var reconnectJob: Job? = null

    fun connect() {
        desiredActive = true
        openSocket()
    }

    fun disconnect() {
        desiredActive = false
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.let { ws ->
            ws.send("DISCONNECT\n\n\u0000")
            ws.close(1000, "client_disconnect")
        }
        webSocket = null
        _connectionState.value = StompConnectionState.DISCONNECTED
    }

    fun dismissIncomingCall() {
        _incomingCall.value = null
    }

    fun sendDecision(callId: String, action: String): Boolean {
        val ws = webSocket ?: return false
        if (_connectionState.value != StompConnectionState.CONNECTED) return false
        val body = gson.toJson(mapOf("action" to action))
        val frame = buildString {
            append("SEND\n")
            append("destination:/app/intercom/").append(callId).append("/decision\n")
            append("content-type:application/json\n")
            append("\n")
            append(body)
            append('\u0000')
        }
        return ws.send(frame)
    }

    private fun openSocket() {
        if (!desiredActive) return
        val serverUrl = storage.serverUrl ?: return
        val token = storage.accessToken ?: return
        val session = storage.readSession() ?: return

        val wsUrl = serverUrl.trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + "/ws/intercom"

        _connectionState.value = StompConnectionState.CONNECTING
        webSocket?.cancel()

        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val connectFrame = buildString {
                    append("CONNECT\n")
                    append("accept-version:1.2,1.1\n")
                    append("heart-beat:0,0\n")
                    append("Authorization:Bearer ").append(token).append("\n")
                    append("\n")
                    append('\u0000')
                }
                webSocket.send(connectFrame)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                text.split('\u0000').forEach { rawFrame ->
                    val frame = rawFrame.trimStart('\r', '\n')
                    if (frame.isNotEmpty()) {
                        handleFrame(webSocket, frame, session.condominiumId, session.unitId)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = StompConnectionState.DISCONNECTED
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = StompConnectionState.DISCONNECTED
                scheduleReconnect()
            }
        })
    }

    private fun handleFrame(ws: WebSocket, frame: String, condoId: String, unitId: String?) {
        val headerEnd = frame.indexOf("\n\n").let { if (it >= 0) it else frame.indexOf("\r\n\r\n") }
        val headerBlock = if (headerEnd >= 0) frame.substring(0, headerEnd) else frame
        val body = if (headerEnd >= 0) {
            val sepLen = if (frame.startsWith("\r\n\r\n", headerEnd)) 4 else 2
            frame.substring(headerEnd + sepLen)
        } else ""

        val command = headerBlock.lineSequence().firstOrNull()?.trim().orEmpty()
        when (command) {
            "CONNECTED" -> {
                _connectionState.value = StompConnectionState.CONNECTED
                val topic = if (!unitId.isNullOrBlank()) {
                    "/topic/condo/$condoId/unit/$unitId/intercom"
                } else {
                    "/topic/condo/$condoId/intercom"
                }
                val subFrame = "SUBSCRIBE\nid:sub-intercom\ndestination:$topic\n\n\u0000"
                ws.send(subFrame)
            }
            "MESSAGE" -> {
                val event = runCatching { gson.fromJson(body, IntercomSignalEventDto::class.java) }.getOrNull()
                    ?: return
                _lastEventAtMs.value = System.currentTimeMillis()
                val callId = event.callId ?: return
                val status = event.status?.uppercase().orEmpty()
                if (event.eventType == "INCOMING_CALL" || status == "RINGING") {
                    val ts = event.timestamp?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                        ?: System.currentTimeMillis()
                    _incomingCall.value = IncomingCall(
                        callId = callId,
                        visitorName = event.visitorName?.ifBlank { "Visitante en puerta" } ?: "Visitante en puerta",
                        visitorNote = event.visitorNote,
                        deviceName = event.deviceName,
                        unitNumber = event.unitNumber,
                        receivedAtMs = ts,
                    )
                } else if (status in setOf("UNLOCKED", "REJECTED", "ENDED", "MISSED")) {
                    if (_incomingCall.value?.callId == callId) {
                        _incomingCall.value = null
                    }
                }
            }
            "ERROR" -> {
                _connectionState.value = StompConnectionState.DISCONNECTED
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        if (!desiredActive) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(4000)
            if (desiredActive && _connectionState.value == StompConnectionState.DISCONNECTED) {
                openSocket()
            }
        }
    }
}
