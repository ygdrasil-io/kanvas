package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlin.test.Test
import kotlin.test.assertTrue

class VerticesSnippetTest {
    @Test
    fun `VerticesWgsl contains vs_main vertex function`() {
        assertTrue(VerticesWgsl.contains("fn vs_main"))
    }

    @Test
    fun `VerticesWgsl contains fs_main fragment function`() {
        assertTrue(VerticesWgsl.contains("fn fs_main"))
    }

    @Test
    fun `VerticesWgsl contains VertexInput struct`() {
        assertTrue(VerticesWgsl.contains("struct VertexInput"))
    }

    @Test
    fun `VerticesWgsl contains VertexOutput struct`() {
        assertTrue(VerticesWgsl.contains("struct VertexOutput"))
    }

    @Test
    fun `VerticesWgsl contains position and color attributes`() {
        assertTrue(VerticesWgsl.contains("@location(0) position"))
        assertTrue(VerticesWgsl.contains("@location(1) color"))
    }

    @Test
    fun `VerticesWgsl passes color from vertex to fragment`() {
        assertTrue(VerticesWgsl.contains("input.color"))
    }

    @Test
    fun `source hash is non-empty`() {
        assertTrue(VerticesSnippetSourceHash.isNotEmpty())
    }

    @Test
    fun `source hash contains vertex prefix`() {
        assertTrue(VerticesSnippetSourceHash.startsWith("vertex:"))
    }

    @Test
    fun `vertex entry point matches vs_main`() {
        assertTrue(VerticesWgsl.contains("fn ${VerticesShaderEntryPoint}"))
    }

    @Test
    fun `fragment entry point matches fs_main`() {
        assertTrue(VerticesWgsl.contains("fn ${VerticesFragmentEntryPoint}"))
    }

    @Test
    fun `vertex entry point is non-empty`() {
        assertTrue(VerticesShaderEntryPoint.isNotEmpty())
    }

    @Test
    fun `fragment entry point is non-empty`() {
        assertTrue(VerticesFragmentEntryPoint.isNotEmpty())
    }
}
