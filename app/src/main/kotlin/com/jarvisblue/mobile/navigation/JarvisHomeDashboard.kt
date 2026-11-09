package com.jarvisblue.mobile.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvisblue.core.ui.components.JarvisOrb
import com.jarvisblue.core.ui.components.JarvisScaffold
import com.jarvisblue.core.ui.theme.BlueCore
import com.jarvisblue.core.ui.theme.CyanAccent
import com.jarvisblue.mobile.ui.ResponsiveSizePolicy

/** Small, accessible launch dashboard. All shortcuts open existing app screens. */
@Composable
fun JarvisHomeDashboard(
    onVoice: () -> Unit,
    onChat: () -> Unit,
    onGold: () -> Unit,
    onStudio: () -> Unit,
    onControl: () -> Unit,
    onSettings: () -> Unit,
) {
    JarvisScaffold(
        title = "JARVIS BLUE",
        subtitle = "Your intelligent companion · உங்கள் AI உதவியாளர்",
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.22f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(BlueCore.copy(alpha = 0.15f), Color.Transparent),
                            ),
                        )
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    JarvisOrb(size = 148.dp, active = false, accent = CyanAccent)
                    Text(
                        "Hello! How can I help?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Ask a question, speak in Tamil, or open one of your tools.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onVoice, shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Talk to JARVIS")
                    }
                }
            }

            Text(
                "Your workspace",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = ResponsiveSizePolicy.stackHomeShortcuts(maxWidth.value)
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeShortcut("AI Chat", "Ask & create", Icons.Filled.Message, onChat, Modifier.fillMaxWidth())
                        HomeShortcut("Gold intelligence", "XAU/USD research", Icons.Filled.ShowChart, onGold, Modifier.fillMaxWidth())
                        HomeShortcut("Creative studio", "Code & tools", Icons.Filled.Code, onStudio, Modifier.fillMaxWidth())
                        HomeShortcut("Device control", "Phone & Windows", Icons.Filled.Devices, onControl, Modifier.fillMaxWidth())
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HomeShortcut("AI Chat", "Ask & create", Icons.Filled.Message, onChat, Modifier.weight(1f))
                            HomeShortcut("Gold intelligence", "XAU/USD research", Icons.Filled.ShowChart, onGold, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HomeShortcut("Creative studio", "Code & tools", Icons.Filled.Code, onStudio, Modifier.weight(1f))
                            HomeShortcut("Device control", "Phone & Windows", Icons.Filled.Devices, onControl, Modifier.weight(1f))
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSettings),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = CyanAccent)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Set up your AI", style = MaterialTheme.typography.titleSmall)
                        Text("Connect Gemini, OpenAI or MiniMax in Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.Shield, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "Actions and remote access require your approval. Gold signals are research, not guaranteed trades.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HomeShortcut(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.heightIn(min = 144.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(BlueCore.copy(alpha = 0.15f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = CyanAccent)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
