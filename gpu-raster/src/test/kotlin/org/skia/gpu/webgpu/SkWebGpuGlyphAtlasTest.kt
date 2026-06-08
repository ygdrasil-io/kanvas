package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkCpuGlyphCache
import org.skia.foundation.SkCpuGlyphMask
import org.skia.tools.ToolUtils
import java.io.File

class SkWebGpuGlyphAtlasTest {
    @Test
    fun `simple latin cache builds deterministic bounded webgpu glyph atlas`() {
        val cache = simpleLatinCache()
        val atlas = SkWebGpuGlyphAtlas.build(
            cache = cache,
            generation = 1,
            maxTextureWidth = 128,
        )

        assertEquals(SimpleLatinScopeId, atlas.scopeId)
        assertEquals("R8Unorm", atlas.textureFormat)
        assertEquals("A8", atlas.maskFormat)
        assertEquals("webgpu.text.glyph-atlas.simple-latin", atlas.routeIdentifier)
        assertEquals(null, atlas.fallbackReason)
        assertEquals("TextureBinding|CopyDst", atlas.textureUsage)
        assertEquals(atlas.width * atlas.height, atlas.uploadBytes.size)
        assertEquals(atlas.uploadBytes.size, atlas.uploadByteCount)
        assertTrue(atlas.entries.size < cache.inventory.size, "atlas entries should stay deduped by glyph key")
        assertTrue(atlas.entries.all { it.atlasGeneration == 1 })
        assertTrue(atlas.entries.any { it.maskWidth == 0 && it.maskHeight == 0 }, "space glyph keeps an empty entry")

        val firstInventory = cache.inventory.first()
        val firstMask = cache.glyphs.first { it.key == firstInventory.key }.mask
        val firstEntry = atlas.entryForKey(firstInventory.key)

        assertEquals(firstMask.width, firstEntry.maskWidth)
        assertEquals(firstMask.height, firstEntry.maskHeight)
        assertEquals(firstMask.sha256, firstEntry.maskSha256)
        assertTrue(firstEntry.u0 >= 0f && firstEntry.u1 <= 1f)
        assertTrue(firstEntry.v0 >= 0f && firstEntry.v1 <= 1f)
        assertTrue(firstEntry.u1 > firstEntry.u0)
        assertTrue(firstEntry.v1 > firstEntry.v0)

        val firstNonZeroIndex = firstMask.pixels.indexOfFirst { (it.toInt() and 0xFF) != 0 }
        assertTrue(firstNonZeroIndex >= 0, "first glyph mask must contain alpha coverage")
        val localX = firstNonZeroIndex % firstMask.width
        val localY = firstNonZeroIndex / firstMask.width
        assertEquals(
            firstMask.pixels[firstNonZeroIndex].toInt() and 0xFF,
            atlas.sample(firstInventory.key, localX, localY),
            "atlas coordinate sampling should match the CPU glyph mask alpha",
        )

        val spaceKey = cache.glyphs.first { it.codePoints.contains(' '.code) }.key
        assertEquals(0, atlas.sample(spaceKey, 0, 0))

        val rebuilt = SkWebGpuGlyphAtlas.build(
            cache = cache,
            generation = 1,
            maxTextureWidth = 128,
        )
        assertEquals(atlas, rebuilt)
        assertEquals(atlas.dumpSha256, rebuilt.dumpSha256)
        assertEquals(atlas.toJson(), rebuilt.toJson())
        assertNotEquals(SkCpuGlyphMask.EmptyHash, atlas.uploadSha256)

        val dump = atlas.toJson()
        assertTrue(dump.contains("\"routeIdentifier\": \"webgpu.text.glyph-atlas.simple-latin\""))
        assertTrue(dump.contains("\"textureFormat\": \"R8Unorm\""))
        assertTrue(dump.contains("\"nonClaims\""))
        assertTrue(dump.contains("no-line-text-render-claim"))
        assertFalse(dump.contains("fallbackReason\": \"none\""), "fallbackReason should be null, not a broad support token")

        val out = File("build/reports/kan-011-glyph-atlas/simple-latin-webgpu-glyph-atlas.json")
        out.parentFile.mkdirs()
        out.writeText(dump)
        assertTrue(out.readText().contains("\"uploadByteCount\": ${atlas.uploadByteCount}"))
    }

    private fun simpleLatinCache(): SkCpuGlyphCache =
        SkCpuGlyphCache.build(
            scopeId = SimpleLatinScopeId,
            fontSourceId = LiberationSansRegularSourceId,
            font = ToolUtils.DefaultPortableFont(32f),
            text = "Kanvas Latin 0123456789 ABC xyz.",
        )

    private companion object {
        const val SimpleLatinScopeId = "text.simple-latin.liberation-sans-regular.v1"
        const val LiberationSansRegularSourceId =
            "kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Regular.ttf#sha256=76d04c18ea243f426b7de1f3ad208e927008f961dc5945e5aad352d0dfde8ee8"
    }
}
