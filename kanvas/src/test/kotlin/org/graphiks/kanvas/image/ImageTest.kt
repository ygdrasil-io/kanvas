package org.graphiks.kanvas.image

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ImageTest {
    @Test fun `Image data class`() { val i = Image(320, 240, ColorType.RGBA_8888, "test"); assertEquals(320, i.width); assertEquals("test", i.sourceId) }
    @Test fun `Image decode placeholder`() { val i = Image.decode(ByteArray(8), "image/png"); assertEquals(0, i.width) }
    @Test fun `ColorType enum values`() { assertEquals(4, ColorType.entries.size) }
}
