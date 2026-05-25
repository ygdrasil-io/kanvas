package org.skia.foundation.emoji

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.skia.foundation.SkData

class EmojiTypefaceTest {
    @Test
    fun `CBDT and Sbix empty data fall back to portable typeface`() {
        val empty = SkData.MakeWithCopy(ByteArray(0))
        assertNotNull(EmojiTypeface.create(EmojiTypeface.Format.CBDT, empty))
        assertNotNull(EmojiTypeface.create(EmojiTypeface.Format.Sbix, empty))
    }

    @Test
    fun `CBDT and Sbix parse non empty OpenType bytes`() {
        val stream = EmojiTypefaceTest::class.java.getResourceAsStream("/fonts/liberation/LiberationSans-Regular.ttf")
            ?: error("Missing LiberationSans-Regular.ttf test resource")
        val bytes = stream.use { it.readBytes() }
        val data = SkData.MakeWithCopy(bytes)

        assertNotNull(EmojiTypeface.create(EmojiTypeface.Format.CBDT, data))
        assertNotNull(EmojiTypeface.create(EmojiTypeface.Format.Sbix, data))
    }

    @Test
    fun `SVG stays gated`() {
        val empty = SkData.MakeWithCopy(ByteArray(0))
        assertThrows(NotImplementedError::class.java) {
            EmojiTypeface.create(EmojiTypeface.Format.SVG, empty)
        }
    }
}
