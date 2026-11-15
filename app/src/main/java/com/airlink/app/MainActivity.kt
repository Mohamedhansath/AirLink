package com.airlink.app

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

private val Ink = Color(0xFF0B1020)
private val Panel = Color(0xFF151D32)
private val Mint = Color(0xFF6EE7C5)
private val Muted = Color(0xFF9AA8C2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Mint,
                background = Ink,
                surface = Panel,
                onPrimary = Ink,
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                AirLinkApp()
            }
        }
    }
}

@Composable
private fun AirLinkApp() {
    var page by remember { mutableStateOf("login") }
    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        when (page) {
            "login" -> LoginScreen(
                onPreview = { page = "home" },
                onCreateAccount = { page = "signup" }
            )
            "signup" -> SignupScreen(
                onBack = { page = "login" },
                onPreview = { page = "home" }
            )
            "pair" -> PairDeviceScreen(onBack = { page = "home" })
            else -> HomeScreen(onPair = { page = "pair" }, onLogout = { page = "login" })
        }
    }
}

@Composable
private fun BrandHeader(subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("AIRLINK", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Text(subtitle, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LoginScreen(onPreview: () -> Unit, onCreateAccount: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        BrandHeader("Welcome back")
        Spacer(Modifier.height(10.dp))
        Text("Sign in to manage your trusted devices.", color = Muted)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; message = "" },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email address") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; message = "" },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                message = if (email.isBlank() || password.isBlank()) {
                    "Enter your email and password."
                } else {
                    "Authentication backend is not connected yet. Use preview to explore the UI."
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)
        ) { Text("Sign in", fontWeight = FontWeight.Bold) }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(message, color = Muted, fontSize = 12.sp)
        }
        TextButton(onClick = onCreateAccount, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Create an account", color = Mint)
        }
        TextButton(onClick = onPreview, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Preview dashboard (demo)", color = Muted)
        }
    }
}

@Composable
private fun SignupScreen(onBack: () -> Unit, onPreview: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        BrandHeader("Create account")
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Email address") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            message = if (name.isBlank() || email.isBlank() || password.length < 8) {
                "Enter all details. Password must be at least 8 characters."
            } else {
                "Account service is not connected yet. No account has been created."
            }
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) {
            Text("Create account", fontWeight = FontWeight.Bold)
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message, color = Muted, fontSize = 12.sp)
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Back to sign in", color = Mint) }
        TextButton(onClick = onPreview, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Preview dashboard (demo)", color = Muted) }
    }
}

@Composable
private fun PairDeviceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var serverUrl by remember { mutableStateOf("https://YOUR_SERVER_HOST") }
    var deviceName by remember { mutableStateOf("My Android phone") }
    var code by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Register this phone or reconnect a previously registered device.") }
    var requestId by remember { mutableStateOf("") }
    var requesterName by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    val mainExecutor = remember(context) { context.mainExecutor }

    val events = remember(context) {
        object : AirLinkClient.Events {
            override fun onStatus(message: String) {
                mainExecutor.execute {
                    status = message
                    if (message.startsWith("Connected securely")) connected = true
                }
            }
            override fun onEvent(event: org.json.JSONObject) {
                mainExecutor.execute {
                    when (event.optString("type")) {
                        "connected" -> { connected = true; status = "Secure server connection is active." }
                        "pair:code" -> {
                            code = event.optString("code")
                            status = "Your one-time code is ready. It expires in ${event.optInt("expiresInSeconds") / 60} min."
                        }
                        "pair:approval_required" -> {
                            requestId = event.optString("requestId")
                            requesterName = event.optString("requester", "Another device")
                            status = "Pairing approval required from $requesterName."
                        }
                        "pair:waiting" -> status = "Code accepted. Waiting for approval on the other phone."
                        "pair:approved" -> {
                            requestId = ""
                            status = "Device pairing approved. Peer: ${event.optString("peerDeviceId")}"
                        }
                        "pair:denied" -> { requestId = ""; status = "Pairing request was declined." }
                        "error" -> status = "Server: ${event.optString("message", "Request failed.")}"
                        else -> status = "Server event: ${event.optString("type")}"
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        TextButton(onClick = onBack) { Text("← Back", color = Mint) }
        BrandHeader("Pair a device")
        Spacer(Modifier.height(8.dp))
        Text("Connect both phones to the same AirLink server. Pairing requires approval on the other phone.", color = Muted)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Backend HTTPS URL") },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it.take(48) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("This device name") },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { AirLinkClient.registerAndConnect(context, serverUrl, deviceName, events) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)
        ) { Text("Register this phone & connect", fontWeight = FontWeight.Bold) }
        TextButton(
            onClick = { AirLinkClient.connectSaved(context, events) },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text("Reconnect registered phone", color = Mint) }
        Spacer(Modifier.height(10.dp))
        Text(if (connected) "● Connected" else "○ Not connected", color = if (connected) Mint else Muted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { AirLinkClient.createPairingCode() },
            enabled = connected,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Mint)
        ) { Text("Generate one-time pairing code") }
        if (code.isNotBlank()) {
            Text("YOUR CODE", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
            Text(code, color = Mint, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = code.takeIf { it.length != 8 } ?: "",
            onValueChange = { code = it.filter(Char::isLetterOrDigit).take(8).uppercase() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Enter another phone's 8-character code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
        )
        Button(
            onClick = { AirLinkClient.joinPairingCode(code) },
            enabled = connected && code.length == 8,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)
        ) { Text("Request pairing", fontWeight = FontWeight.Bold) }
        if (requestId.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Pairing approval", color = Mint, fontWeight = FontWeight.Bold)
                    Text("$requesterName wants to pair with this phone.", color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { AirLinkClient.approvePairing(requestId) }, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) { Text("Approve") }
                        TextButton(onClick = { AirLinkClient.denyPairing(requestId) }) { Text("Decline", color = Muted) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(status, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Text("Use a deployed HTTPS backend URL. Device credentials are encrypted with Android Keystore. Camera, microphone and screen capture are not enabled by pairing.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun HomeScreen(onPair: () -> Unit, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandHeader("Your devices, connected.")
            Icon(Icons.Rounded.Security, contentDescription = "Security", tint = Mint)
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("REMOTE CONNECTION", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                Text("Connect to a device", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("Pair with another phone using a one-time code. The other device must approve each remote session.", color = Muted, fontSize = 14.sp)
                Button(onClick = onPair, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) {
                    Icon(Icons.Rounded.Devices, contentDescription = null)
                    Text("  Pair a device", fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("REMOTE TOOLS", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
        ToolCard(Icons.Rounded.Monitor, "Screen sharing", "View a screen after the device owner approves.")
        ToolCard(Icons.Rounded.CameraAlt, "Remote camera", "Start a visible, owner-approved camera session.")
        ToolCard(Icons.Rounded.Mic, "Microphone audio", "Use live audio only during an approved session.")

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF102A2A))) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Security, contentDescription = null, tint = Mint)
                Column {
                    Text("Privacy first", color = Mint, fontWeight = FontWeight.SemiBold)
                    Text("Every session is visible, revocable, and time-limited.", color = Muted, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onLogout, modifier = Modifier.align(Alignment.End)) { Text("Exit preview", color = Muted) }
    }
}

@Composable
private fun ToolCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Mint)
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, color = Muted, fontSize = 12.sp)
            }
        }
    }
}
