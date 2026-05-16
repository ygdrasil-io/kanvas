package org.skia.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase G1 — smoke tests for the [ToolUtils] resource-loading helpers.
 * These cover the load → decode → image pipeline against the upstream
 * Skia reference asset `images/mandrill_512.png`, shared with the
 * legacy `:kanvas` module via the `:kanvas-skia` build script's
 * `resources.srcDir(...)`.
 */
class ToolUtilsResourceTest {

    @Test
    fun `GetResourceAsImage loads mandrill at native resolution`() {
        val image = ToolUtils.GetResourceAsImage("images/mandrill_512.png")
        assertNotNull(image, "mandrill_512.png should load from the classpath")
        assertEquals(512, image!!.width)
        assertEquals(512, image.height)
    }

    @Test
    fun `GetResourceAsData returns non-empty payload for a real asset`() {
        val data = ToolUtils.GetResourceAsData("images/mandrill_512.png")
        assertNotNull(data, "mandrill_512.png should resolve via the classloader")
        assertTrue(data!!.size > 0, "decoded SkData should carry the PNG bytes")
    }

    @Test
    fun `missing resource returns null across all loaders`() {
        assertNull(ToolUtils.GetResourceAsData("images/does_not_exist.png"))
        assertNull(ToolUtils.GetResourceAsImage("images/does_not_exist.png"))
    }

    @Test
    fun `MakeTextureImage on raster backend is the identity`() {
        val image = ToolUtils.GetResourceAsImage("images/mandrill_512.png")
        assertNotNull(image)
        // No canvas (no GPU recording context in raster) — upstream's
        // GPU-only branches short-circuit ; we mirror by returning `orig`.
        assertEquals(image, ToolUtils.MakeTextureImage(null, image))
    }
}
