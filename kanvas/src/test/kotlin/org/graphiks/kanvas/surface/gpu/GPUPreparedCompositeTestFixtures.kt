package org.graphiks.kanvas.surface.gpu

/**
 * Deterministic immutable RGBA8 and A8 test fixtures.
 * Every access returns an isolated copy so mutation of a returned array
 * does not affect later accesses.
 */
object GPUPreparedCompositeTestFixtures {

    fun rgbaChecker(width: Int = 4, height: Int = 4): ByteArray {
        val bytes = ByteArray(width * height * 4)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val white = ((x + y) and 1) == 0
                bytes[i++] = if (white) 255.toByte() else 0.toByte()
                bytes[i++] = if (white) 255.toByte() else 0.toByte()
                bytes[i++] = if (white) 255.toByte() else 0.toByte()
                bytes[i++] = 255.toByte()
            }
        }
        return bytes
    }

    fun rgbaChecker(): ByteArray = rgbaChecker(4, 4)

    fun rgbaSolid(width: Int = 4, height: Int = 4, r: Int = 128, g: Int = 128, b: Int = 128, a: Int = 255): ByteArray {
        val bytes = ByteArray(width * height * 4)
        var i = 0
        repeat(width * height) {
            bytes[i++] = r.toByte()
            bytes[i++] = g.toByte()
            bytes[i++] = b.toByte()
            bytes[i++] = a.toByte()
        }
        return bytes
    }

    fun rgbaGradient(width: Int = 4, height: Int = 4): ByteArray {
        val bytes = ByteArray(width * height * 4)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                bytes[i++] = ((x * 255) / (width - 1).coerceAtLeast(1)).toByte()
                bytes[i++] = ((y * 255) / (height - 1).coerceAtLeast(1)).toByte()
                bytes[i++] = 0.toByte()
                bytes[i++] = 255.toByte()
            }
        }
        return bytes
    }

    fun a8Checker(width: Int = 4, height: Int = 4): ByteArray {
        val bytes = ByteArray(width * height)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                bytes[i++] = if (((x + y) and 1) == 0) 255.toByte() else 0.toByte()
            }
        }
        return bytes
    }

    fun a8Solid(width: Int = 4, height: Int = 4, a: Int = 128): ByteArray {
        val bytes = ByteArray(width * height)
        for (i in bytes.indices) {
            bytes[i] = a.toByte()
        }
        return bytes
    }
}
