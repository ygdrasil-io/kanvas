package org.graphiks.kanvas.text

object EmojiTypeface {
    enum class Format { Sbix, CBDT, COLRv0, SVG }

    fun create(format: Format, fontData: ByteArray): Typeface {
        return FontTypeface(fontData, "emoji-${format.name.lowercase()}")
    }

    fun createOrFallback(format: Format, fontData: ByteArray): Typeface {
        if (fontData.size >= 12) {
            return try {
                create(format, fontData)
            } catch (_: Exception) {
                loadFallback()
            }
        }
        Typefaces.fromResource("fonts/Noto-COLRv1-noflags.ttf")?.let { return it }
        return loadFallback()
    }

    private fun loadFallback(): Typeface {
        return Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")
            ?: error("No fallback typeface available")
    }
}
