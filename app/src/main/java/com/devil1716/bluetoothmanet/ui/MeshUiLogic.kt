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

fun parsePeerNodeIds(labels: List<String>): List<String> =
    labels.map { it.substringBefore(" (").trim() }
        .filter { it.isNotEmpty() }
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

fun messageReceiptLabel(outgoing: Boolean, statusName: String): String {
    if (!outgoing) return ""
    return when (statusName.uppercase(Locale.US)) {
        "SENDING" -> "Sending"
        "SENT" -> "Sent"
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
        total > 0 && completed >= total -> FileTransferPhase.SUCCESS
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
            text.contains("File transfer queued", ignoreCase = true) ||
            text.startsWith("Waiting for mesh link to send", ignoreCase = true) ||
            text.startsWith("Waiting to send", ignoreCase = true) ->
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

fun shouldReloadInboxAfterMeshStatus(peersChanged: Boolean, fileReceived: Boolean): Boolean =
    peersChanged || fileReceived

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
