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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            )) { AirLinkHome() }
        }
    }
}

@Composable
private fun AirLinkHome() {
    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        Column(
            modifier = Modifier.fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("AIRLINK", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("Your devices, connected.", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Rounded.Security, contentDescription = "Security", tint = Mint)
            }

            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("REMOTE CONNECTION", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                    Text("Connect to a device", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Pair with another phone using a one-time code. The other device must approve each remote session.",
                        color = Muted,
                        fontSize = 14.sp
                    )
                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)
                    ) {
                        Icon(Icons.Rounded.Devices, contentDescription = null)
                        Text("  Pair a device", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("REMOTE TOOLS", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
            ToolCard(Icons.Rounded.Monitor, "Screen sharing", "View a screen after the device owner approves.")
            ToolCard(Icons.Rounded.CameraAlt, "Remote camera", "Start a visible, owner-approved camera session.")
            ToolCard(Icons.Rounded.Mic, "Microphone audio", "Use live audio only during an approved session.")

            Spacer(Modifier.height(2.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF102A2A))) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = Mint)
                    Column {
                        Text("Privacy first", color = Mint, fontWeight = FontWeight.SemiBold)
                        Text("Every session is visible, revocable, and time-limited.", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
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
