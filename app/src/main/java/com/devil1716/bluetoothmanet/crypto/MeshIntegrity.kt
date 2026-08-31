package com.devil1716.bluetoothmanet.crypto

import java.security.MessageDigest

object MeshIntegrity {
    @JvmStatic
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = StringBuilder(digest.size * 2)
        for (value in digest) {
            hex.append(String.format("%02x", value))
        }
        return hex.toString()
    }

    @JvmStatic
    fun messageCanon(type: String, id: String, source: String, destination: String, data: String): String =
        "$type|$id|$source|$destination|$data"

    @JvmStatic
    fun helloCanon(source: String, data: String): String = messageCanon("HELLO", source, source, "*", data)

    @JvmStatic
    fun fileMetaCanon(id: String, source: String, destination: String, fileName: String, total: Int, size: Int, fileSha: String): String =
        "FILEMETA|$id|$source|$destination|$fileName|$total|$size|$fileSha"

    @JvmStatic
    fun fileChunkCanon(id: String, source: String, destination: String, index: Int, total: Int, chunkSha: String): String =
        "FILE|$id|$source|$destination|$index|$total|$chunkSha"
}
