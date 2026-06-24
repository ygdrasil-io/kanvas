package org.graphiks.kanvas.gpu.renderer.wgsl

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
}
