package com.airlink.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.*
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.ConcurrentHashMap

object AirLinkClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private var socket: WebSocket? = null
    private val approvedPeers = ConcurrentHashMap.newKeySet<String>()

    interface Events {
        fun onStatus(message: String)
        fun onEvent(event: JSONObject)
    }

    fun registerAndConnect(
        context: Context,
        baseUrl: String,
        deviceName: String,
        events: Events
    ) {
        val base = normalizeBaseUrl(baseUrl) ?: run {
            events.onStatus("Enter a valid HTTPS server URL.")
            return
        }
        val body = JSONObject().put("deviceName", deviceName.trim())
        val request = Request.Builder()
            .url("$base/v1/devices/register")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                events.onStatus("Could not reach server: ${e.message ?: "network error"}")
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        events.onStatus("Registration failed (HTTP ${it.code}).")
                        return
                    }
                    val data = JSONObject(it.body?.string().orEmpty())
                    val token = data.optString("deviceToken")
                    val id = data.optString("deviceId")
                    if (token.isBlank() || id.isBlank()) {
                        events.onStatus("Server returned an invalid registration response.")
                        return
                    }
                    DeviceSecretStore.save(context, JSONObject()
                        .put("baseUrl", base).put("deviceToken", token).put("deviceId", id).toString())
                    connectSocket(base, token, events)
                }
            }
        })
    }

    fun connectSaved(context: Context, events: Events) {
        val saved = DeviceSecretStore.load(context)
        if (saved == null) {
            events.onStatus("This phone is not registered yet.")
            return
        }
        connectSocket(saved.optString("baseUrl"), saved.optString("deviceToken"), events)
    }

    private fun connectSocket(base: String, token: String, events: Events) {
        approvedPeers.clear()
        val wsBase = base.replaceFirst("https://", "wss://")
        // Keep long-lived credentials out of URL/query strings, which may be logged.
        val request = Request.Builder()
            .url("$wsBase/ws")
            .header("Authorization", "Bearer $token")
            .build()
        socket?.close(1000, "Reconnect")
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                events.onStatus("Connected securely to AirLink server.")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val event = JSONObject(text)
                    val peerId = event.optString("peerDeviceId")
                    when (event.optString("type")) {
                        "session:approved" -> if (peerId.isNotBlank()) approvedPeers.add(peerId)
                        "session:denied", "session:stopped", "session:expired", "session:cancelled" ->
                            if (peerId.isNotBlank()) approvedPeers.remove(peerId)
                    }
                    events.onEvent(event)
                } catch (_: Exception) {
                    events.onStatus("Received an unreadable server message.")
                }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                events.onStatus("Server connection closing.")
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                events.onStatus("Connection error: ${t.message ?: "unknown error"}")
            }
        })
    }

    fun createPairingCode() = send(JSONObject().put("type", "pair:create"))
    fun joinPairingCode(code: String) = send(JSONObject().put("type", "pair:join").put("code", code))
    fun approvePairing(requestId: String) = send(JSONObject().put("type", "pair:approve").put("requestId", requestId))
    fun denyPairing(requestId: String) = send(JSONObject().put("type", "pair:deny").put("requestId", requestId))

    fun requestSession(peerDeviceId: String) =
        send(JSONObject().put("type", "session:request").put("peerDeviceId", peerDeviceId))

    fun approveSession(requestId: String) =
        send(JSONObject().put("type", "session:approve").put("requestId", requestId))

    fun denySession(requestId: String) =
        send(JSONObject().put("type", "session:deny").put("requestId", requestId))

    fun stopSession(peerDeviceId: String) =
        send(JSONObject().put("type", "session:stop").put("peerDeviceId", peerDeviceId))

    // These methods are ready for a future PeerConnection, but are accepted only
    // after the backend confirms explicit owner approval for this peer.
    fun sendRtcOffer(peerDeviceId: String, payload: JSONObject): Boolean =
        sendRtcSignal("rtc:offer", peerDeviceId, payload)

    fun sendRtcAnswer(peerDeviceId: String, payload: JSONObject): Boolean =
        sendRtcSignal("rtc:answer", peerDeviceId, payload)

    fun sendRtcIce(peerDeviceId: String, payload: JSONObject): Boolean =
        sendRtcSignal("rtc:ice", peerDeviceId, payload)

    private fun sendRtcSignal(type: String, peerDeviceId: String, payload: JSONObject): Boolean {
        if (peerDeviceId.isBlank() || !approvedPeers.contains(peerDeviceId)) return false
        return send(
            JSONObject()
                .put("type", type)
                .put("peerDeviceId", peerDeviceId)
                .put("payload", payload)
        )
    }

    fun stop() {
        approvedPeers.clear()
        socket?.close(1000, "User disconnected")
        socket = null
    }

    private fun send(message: JSONObject): Boolean {
        return socket?.send(message.toString()) == true
    }

    private fun normalizeBaseUrl(value: String): String? {
        val trimmed = value.trim().trimEnd('/')
        if (!trimmed.startsWith("https://")) return null
        return try {
            val url = trimmed.toHttpUrl()
            if (url.host.isBlank() || url.encodedPath != "/") null else trimmed
        } catch (_: Exception) { null }
    }
}

private object DeviceSecretStore {
    private const val PREFS = "airlink_secure_store"
    private const val KEY_ALIAS = "airlink_device_secret_key"
    private const val VALUE = "credential_blob"

    fun save(context: Context, plain: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(VALUE, packed).apply()
    }

    fun load(context: Context): JSONObject? {
        return try {
            val packed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(VALUE, null) ?: return null
            val bytes = Base64.decode(packed, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, 12)
            val encrypted = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
        } catch (_: Exception) { null }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }
}
