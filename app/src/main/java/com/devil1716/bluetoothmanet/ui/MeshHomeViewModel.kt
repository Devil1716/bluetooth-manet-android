package com.devil1716.bluetoothmanet.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MeshDestination(val label: String) {
    CHATS("Chats"), NEARBY("Nearby"), GROUPS("Groups"), MESH("Mesh"), SETTINGS("Settings")
}

class MeshHomeViewModel : ViewModel() {
    private val _destination = MutableStateFlow(MeshDestination.CHATS)
    val destination: StateFlow<MeshDestination> = _destination.asStateFlow()

    fun navigate(destination: MeshDestination) { _destination.value = destination }
}
