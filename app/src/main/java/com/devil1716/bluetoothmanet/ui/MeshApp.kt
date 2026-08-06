package com.devil1716.bluetoothmanet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MeshApp(viewModel: MeshHomeViewModel = viewModel()) {
    val destination by viewModel.destination.collectAsState()
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    MeshDestination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { viewModel.navigate(item) },
                            icon = { Text(item.label.first().toString()) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        ) { padding -> MeshDestinationScreen(destination, padding) }
    }
}

@Composable
private fun MeshDestinationScreen(destination: MeshDestination, padding: PaddingValues) {
    Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(destination.label, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = when (destination) {
                    MeshDestination.CHATS -> "Encrypted conversations and delivery state will appear here."
                    MeshDestination.NEARBY -> "Nearby MANET peers will be discovered automatically."
                    MeshDestination.GROUPS -> "Encrypted group spaces and invitations will appear here."
                    MeshDestination.MESH -> "Routes, peers, packet queue, and mesh health will appear here."
                    MeshDestination.SETTINGS -> "Bluetooth, privacy, security, diagnostics, and identity settings."
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
