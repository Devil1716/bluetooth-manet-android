package com.devil1716.bluetoothmanet.crypto

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object MeshIntegrity {
    private val HEX = "0123456789abcdef".toCharArray()

    @JvmStatic
    @JvmOverloads
    fun sha256Hex(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes, offset, length)
        return toHex(digest.digest())
    }

    @JvmStatic
    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return toHex(digest.digest())
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

    private fun toHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (index in bytes.indices) {
            val value = bytes[index].toInt() and 0xFF
            out[index * 2] = HEX[value ushr 4]
            out[index * 2 + 1] = HEX[value and 0x0F]
        }
        return String(out)
    }
}
