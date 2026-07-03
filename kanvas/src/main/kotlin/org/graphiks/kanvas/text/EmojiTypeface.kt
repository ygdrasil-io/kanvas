package org.graphiks.kanvas.text

object EmojiTypeface {
    enum class Format { Sbix, CBDT, COLRv0, SVG }

    fun create(format: Format, fontData: ByteArray): Typeface {
        return FontTypeface(fontData, "emoji-${format.name.lowercase()}")
    }

    fun createOrFallback(format: Format, fontData: ByteArray): Typeface {
        return try {
            create(format, fontData)
        } catch (_: Exception) {
            Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")
                ?: error("No fallback typeface available")
        }
    }
}
