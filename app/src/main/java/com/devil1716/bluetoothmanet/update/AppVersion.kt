package com.devil1716.bluetoothmanet.update

object AppVersion {
    fun normalize(value: String): String =
        value.trim().removePrefix("v").removePrefix("V")

    fun compare(left: String, right: String): Int {
        val a = tokenize(normalize(left))
        val b = tokenize(normalize(right))
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val delta = a.getOrElse(index) { 0 } - b.getOrElse(index) { 0 }
            if (delta != 0) return if (delta > 0) 1 else -1
        }
        return 0
    }

    fun isNewer(latest: String, current: String): Boolean = compare(latest, current) > 0

    private fun tokenize(version: String): List<Int> =
        version.split('.', '-', '_')
            .mapNotNull { token -> token.takeWhile { it.isDigit() }.toIntOrNull() }
}
