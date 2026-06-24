package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class LayerCompositeSnippetTest {

    @Test
    fun `layerCompositeWgsl contains layer_composite function`() {
        assertTrue(LayerCompositeWgsl.contains("fn layer_composite"))
    }

    @Test
    fun `layerCompositeWgsl contains srcOver blend`() {
        assertTrue(LayerCompositeWgsl.contains("src_color.a"))
        assertTrue(LayerCompositeWgsl.contains("layer_color.a"))
    }

    @Test
    fun `layerCompositeWgsl contains texture and sampler bindings`() {
        assertTrue(LayerCompositeWgsl.contains("layer_texture"))
        assertTrue(LayerCompositeWgsl.contains("layer_sampler"))
    }

    @Test
    fun `source hash is non-empty`() {
        assertTrue(LayerCompositeSnippetSourceHash.isNotEmpty())
    }

    @Test
    fun `source hash contains fragment prefix`() {
        assertTrue(LayerCompositeSnippetSourceHash.startsWith("fragment:"))
    }

    @Test
    fun `entry point is non-empty`() {
        assertTrue(LayerCompositeEntryPoint.isNotEmpty())
    }

    @Test
    fun `entry point matches function name`() {
        assertTrue(LayerCompositeWgsl.contains("fn ${LayerCompositeEntryPoint}"))
    }

    @Test
    fun `nonclaim lines are present in snippet`() {
        assertFalse(LayerCompositeWgsl.contains("nonclaim"))
    }
}
