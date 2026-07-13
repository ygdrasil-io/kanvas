package org.graphiks.kanvas.gpu.renderer.wgsl

import org.graphiks.wgsl.parser.parseWgslResult
import kotlin.test.Test
import kotlin.test.assertTrue

class TextAtlasSnippetTest {
    @Test
    fun `textAtlasA8Wgsl contains text_atlas_source function`() {
        assertTrue(TextAtlasA8Wgsl.contains("fn text_atlas_source"))
    }

    @Test
    fun `textAtlasA8Wgsl contains a8 texture and sampler bindings`() {
        assertTrue(TextAtlasA8Wgsl.contains("a8_atlas_texture"))
        assertTrue(TextAtlasA8Wgsl.contains("a8_atlas_sampler"))
    }

    @Test
    fun `textAtlasA8Wgsl samples red channel for alpha`() {
        assertTrue(TextAtlasA8Wgsl.contains("textureSample"))
        assertTrue(TextAtlasA8Wgsl.contains(".r"))
    }

    @Test
    fun `source hash is non-empty`() {
        assertTrue(TextAtlasSnippetSourceHash.isNotEmpty())
    }

    @Test
    fun `source hash contains fragment prefix`() {
        assertTrue(TextAtlasSnippetSourceHash.startsWith("fragment:"))
    }

    @Test
    fun `entry point is non-empty`() {
        assertTrue(TextAtlasA8EntryPoint.isNotEmpty())
    }

    @Test
    fun `entry point matches function name`() {
        assertTrue(TextAtlasA8Wgsl.contains("fn ${TextAtlasA8EntryPoint}"))
    }

    @Test
    fun `A8 atlas WGSL sources parse through wgsl4k`() {
        val resourceSource = checkNotNull(javaClass.getResource("/wgsl/text/a8_text_mask.wgsl")) {
            "Missing A8 atlas WGSL resource"
        }.readText()

        mapOf(
            "TextAtlasA8Wgsl" to TextAtlasA8Wgsl,
            "text/a8_text_mask.wgsl" to resourceSource,
        ).forEach { (sourceId, source) ->
            val parsed = parseWgslResult(source)

            assertTrue(
                parsed.isSuccess,
                "wgsl4k rejected $sourceId: ${parsed.errors.joinToString { it.message }}",
            )
        }
    }
}
