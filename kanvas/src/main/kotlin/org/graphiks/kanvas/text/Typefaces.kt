package org.graphiks.kanvas.text

object Typefaces {
    fun fromBytes(bytes: ByteArray, name: String = "unknown"): Typeface {
        return FontTypeface(bytes, name)
    }

    fun fromResource(path: String): Typeface? {
        val stream = javaClass.classLoader.getResourceAsStream(path)
            ?: ClassLoader.getSystemResourceAsStream(path)
            ?: return null
        return try {
            fromBytes(stream.readBytes(), path)
        } catch (_: Exception) {
            null
        } finally {
            stream.close()
        }
    }
}
