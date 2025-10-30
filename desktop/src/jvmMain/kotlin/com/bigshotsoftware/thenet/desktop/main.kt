package com.bigshotsoftware.thenet.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.bigshotsoftware.thenet.Greeting
import com.bigshotsoftware.thenet.p2p.IPv8Manager
import com.bigshotsoftware.thenet.p2p.discovery.IPv8ManagerWithDiscovery
import com.bigshotsoftware.thenet.p2p.discovery.createIPv8ManagerWithDiscovery
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Composable
fun App() {
    var showContent by remember { mutableStateOf(false) }
    val baseManager = remember { DesktopIPv8Manager() }
    val manager: IPv8ManagerWithDiscovery = remember { createIPv8ManagerWithDiscovery(baseManager) }
    val peers by manager.discoveredPeers.collectAsState(emptyList())

    LaunchedEffect(Unit) {
        val baseConfig = IPv8Manager.IPv8Config(
            port = 8090,
            bootstrapPeers = emptyList(),
            enableDiscovery = true,
        )
        manager.start(baseConfig)
        manager.getDiscoveryService().start(emptyList())
    }
    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Button(
                    onClick = { showContent = !showContent },
                ) {
                    Text("Click me!")
                }

                if (showContent) {
                    Text(
                        text = Greeting().greet(),
                        style = MaterialTheme.typography.h4,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        // Trigger manual discovery
                        GlobalScope.launch {
                            manager.discoverPeers()
                        }
                    }) { Text("Discover Peers") }
                    Text("Peers: ${peers.size}")
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    peers.take(10).forEach { p ->
                        Text("${p.peerId.take(8)}… @ ${p.address}:${p.port} ${if (p.isConnected) "(connected)" else ""}")
                    }
                }
            }
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "TheNet Desktop",
        state = WindowState(position = WindowPosition.Aligned(Alignment.Center)),
    ) {
        App()
    }
}
