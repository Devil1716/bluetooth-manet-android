package com.devil1716.bluetoothmanet.ui

import java.util.Locale

private const val NODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
const val LEGACY_DEFAULT_NODE_ID = "A"

fun generateDefaultNodeId(seed: String): String {
    val material = seed.trim().ifEmpty { "mesh" }
    var x = material.hashCode().toLong() and 0x7FFFFFFF
    if (x == 0L) x = 0xA5A5A5A5
    val generated = buildString(4) {
        repeat(4) {
            append(NODE_ALPHABET[(x % NODE_ALPHABET.length).toInt()])
            x = (x * 1_664_525L + 1_013_904_223L) and 0x7FFFFFFF
        }
    }
    return if (generated.equals(LEGACY_DEFAULT_NODE_ID, ignoreCase = true)) {
        generateDefaultNodeId("$material-mesh")
    } else {
        generated
    }
}

fun resolveInitialNodeId(stored: String?): String? {
    val value = stored?.trim()?.uppercase(Locale.US).orEmpty()
    return value.takeIf { it.isNotEmpty() }
}

/**
 * Placeholders a transport emits when it has an address but no identity yet.
 * These are radio-level names, never mesh node IDs, so treating them as
 * identities would show "BLE peer" to the user as if it were a person.
 */
private val PEER_LABEL_PLACEHOLDERS = setOf("BLE PEER", "UNKNOWN", "NODE", "")

/**
 * Extracts mesh node IDs from transport labels of the form `NODE (address)`.
 * Labels whose identity is still unknown are dropped rather than surfaced.
 */
fun parsePeerNodeIds(labels: List<String>): List<String> =
    labels.map { it.substringBefore(" (").trim().uppercase(Locale.US) }
        .filter { it.isNotEmpty() && it !in PEER_LABEL_PLACEHOLDERS }
        .distinct()

fun meshStatusChipLabel(started: Boolean, peerIds: List<String>): String {
    if (!started) return "Offline"
    if (peerIds.isEmpty()) return "Connecting"
    val count = peerIds.size
    return if (count == 1) "Connected · 1 nearby" else "Connected · $count nearby"
}

fun humanConnectionsLabel(started: Boolean, peerIds: List<String>): String {
    if (!started) return "Nearby chat is off"
    if (peerIds.isEmpty()) return "Looking for phones around you…"
    return peerIds.joinToString(" · ")
}

fun shouldShowGetStarted(
    onboardingComplete: Boolean,
    meshStarted: Boolean,
    permissionsReady: Boolean,
    bluetoothOn: Boolean
): Boolean {
    if (onboardingComplete) return false
    return !meshStarted || !permissionsReady || !bluetoothOn
}

data class NearbyEmptyCopy(
    val title: String,
    val body: String,
    val actionLabel: String
)

enum class NearbyPrimaryAction {
    REQUEST_PERMISSION,
    ENABLE_BLUETOOTH,
    START_MESH,
    NONE
}

fun nearbyPrimaryAction(
    meshStarted: Boolean,
    bluetoothOff: Boolean,
    permissionsGranted: Boolean
): NearbyPrimaryAction {
    if (!permissionsGranted) return NearbyPrimaryAction.REQUEST_PERMISSION
    if (bluetoothOff) return NearbyPrimaryAction.ENABLE_BLUETOOTH
    if (!meshStarted) return NearbyPrimaryAction.START_MESH
    return NearbyPrimaryAction.NONE
}

fun nearbyEmptyCopy(
    meshStarted: Boolean,
    bluetoothOff: Boolean,
    permissionsGranted: Boolean
): NearbyEmptyCopy {
    return when (nearbyPrimaryAction(meshStarted, bluetoothOff, permissionsGranted)) {
        NearbyPrimaryAction.ENABLE_BLUETOOTH -> NearbyEmptyCopy(
            title = "Bluetooth is off",
            body = "Turn Bluetooth on so MESH can look for nearby phones. Queued chats stay on this phone.",
            actionLabel = "Turn on Bluetooth"
        )
        NearbyPrimaryAction.REQUEST_PERMISSION -> NearbyEmptyCopy(
            title = "Permission needed",
            body = "MESH uses Bluetooth to find phones next to you. On older Android, Location must also be on for scanning — MESH does not use your map location.",
            actionLabel = "Allow Bluetooth"
        )
        NearbyPrimaryAction.START_MESH -> NearbyEmptyCopy(
            title = "Nearby chat is off",
            body = "Start MESH to search for phones around you. Keep the app open or use the Mesh is on notification.",
            actionLabel = "Start Mesh"
        )
        NearbyPrimaryAction.NONE -> NearbyEmptyCopy(
            title = "Searching nearby…",
            body = "No phones linked yet. This is not a connection failure — stay in range with MESH running.",
            actionLabel = "Start Mesh"
        )
    }
}

fun messageReceiptLabel(outgoing: Boolean, statusName: String): String {
    if (!outgoing) return ""
    return when (statusName.uppercase(Locale.US)) {
        "SENDING" -> "Sending"
        "QUEUED" -> "Waiting for a nearby phone"
        "SENT" -> "Sent · waiting for confirmation"
        "DELIVERED" -> "Delivered"
        "FAILED" -> "Couldn't send"
        else -> ""
    }
}

fun formatByteSize(bytes: Long): String {
    if (bytes < 0L) return ""
    if (bytes < 1024L) return "$bytes B"
    if (bytes < 1024L * 1024L) {
        return String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    }
    return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

fun fileTransferFromProgress(
    fileName: String?,
    completed: Int,
    total: Int,
    receivedPath: String? = null,
    previous: FileTransferUi = FileTransferUi()
): FileTransferUi {
    val name = fileName?.takeIf { it.isNotBlank() } ?: previous.fileName
    val phase = when {
        receivedPath != null -> FileTransferPhase.SUCCESS
        total > 0 && completed >= total -> FileTransferPhase.WAITING_CONFIRMATION
        total > 0 -> FileTransferPhase.SENDING
        else -> FileTransferPhase.SENDING
    }
    return previous.copy(
        phase = phase,
        fileName = name,
        completed = completed,
        total = total,
        error = if (phase == FileTransferPhase.SUCCESS) "" else previous.error
    )
}

fun fileTransferFromLog(message: String?, previous: FileTransferUi): FileTransferUi {
    val text = message?.trim().orEmpty()
    if (text.isEmpty()) return previous
    return when {
        text.startsWith("File send failed", ignoreCase = true) ||
            text.startsWith("File error", ignoreCase = true) ||
            text.startsWith("Could not read queued file", ignoreCase = true) ->
            previous.copy(phase = FileTransferPhase.FAILED, error = text)
        text.startsWith("File transfer failed", ignoreCase = true) ->
            previous.copy(phase = FileTransferPhase.FAILED, error = text)
        text.contains("Signed file transfer started", ignoreCase = true) ||
            text.contains("Signed file transfer queued", ignoreCase = true) ||
            text.startsWith("Waiting for mesh link to send", ignoreCase = true) ->
            previous.copy(
                phase = FileTransferPhase.SENDING,
                error = "",
                fileName = previous.fileName.ifBlank {
                    text.substringAfter("send ", missingDelimiterValue = previous.fileName)
                        .trim().trimEnd('.')
                }
            )
        text.startsWith("Received file ", ignoreCase = true) ||
            text.startsWith("Verified file saved", ignoreCase = true) ||
            text.startsWith("File saved", ignoreCase = true) ->
            previous.copy(phase = FileTransferPhase.SUCCESS, error = "")
        else -> previous
    }
}

fun shouldShowFirstRunChecklist(
    conversationCount: Int,
    meshStarted: Boolean,
    permissionsReady: Boolean,
    bluetoothOn: Boolean
): Boolean = shouldShowGetStarted(
    onboardingComplete = conversationCount > 0,
    meshStarted = meshStarted,
    permissionsReady = permissionsReady,
    bluetoothOn = bluetoothOn
)
