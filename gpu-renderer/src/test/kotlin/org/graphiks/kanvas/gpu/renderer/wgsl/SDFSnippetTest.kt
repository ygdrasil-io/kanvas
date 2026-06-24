package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlin.test.Test
import kotlin.test.assertTrue

class SDFSnippetTest {
    @Test
    fun `sdfSamplingWgsl contains sdf_sample function`() {
        assertTrue(SDFSamplingWgsl.contains("fn sdf_sample"))
    }

    @Test
    fun `sdfSamplingWgsl contains sdf texture and sampler bindings`() {
        assertTrue(SDFSamplingWgsl.contains("sdf_atlas_texture"))
        assertTrue(SDFSamplingWgsl.contains("sdf_atlas_sampler"))
    }

    @Test
    fun `sdfSamplingWgsl contains smoothstep`() {
        assertTrue(SDFSamplingWgsl.contains("smoothstep"))
    }

    @Test
    fun `source hash is non-empty`() {
        assertTrue(SDFSnippetSourceHash.isNotEmpty())
    }

    @Test
    fun `source hash contains fragment prefix`() {
        assertTrue(SDFSnippetSourceHash.startsWith("fragment:"))
    }

    @Test
    fun `entry point is non-empty`() {
        assertTrue(SDFSamplingEntryPoint.isNotEmpty())
    }

    @Test
    fun `entry point matches function name`() {
        assertTrue(SDFSamplingWgsl.contains("fn ${SDFSamplingEntryPoint}"))
    }
}
