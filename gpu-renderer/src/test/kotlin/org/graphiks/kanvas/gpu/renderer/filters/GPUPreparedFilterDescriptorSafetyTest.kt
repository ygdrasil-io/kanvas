package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GPUPreparedFilterDescriptorSafetyTest {

    @Test
    fun `runtime uniform arrays are copied on every read`() {
        val params = RuntimeEffectParams(
            effectId = "fx",
            effectVersion = 1,
            uniforms = mapOf("u" to floatArrayOf(1f, 2f)),
            children = emptyMap(),
        )

        params.uniforms.getValue("u")[0] = 99f

        assertEquals(1f, params.uniforms.getValue("u")[0])
    }

    @Test
    fun `drop shadow and lighting colors are copied on every read`() {
        val shadow = DropShadowParams(0f, 0f, 1f, 1f, floatArrayOf(0f, 0f, 0f, 1f))
        val light = DistantLitDiffuseParams(
            0f,
            0f,
            1f,
            1f,
            1f,
            floatArrayOf(1f, 1f, 1f),
        )

        shadow.color[3] = 0f
        light.color[0] = 0f

        assertEquals(1f, shadow.color[3])
        assertEquals(1f, light.color[0])
    }

    @Test
    fun `merge and node inputs are immutable snapshots`() {
        val mutableInputs = mutableListOf<GPUPreparedFilterInputRef>(
            GPUPreparedFilterInputRef.ImplicitSource,
        )
        val merge = MergeParams(mutableInputs)
        val node = GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId("merge"),
            kind = GPUPreparedFilterKind.Merge,
            inputs = mutableInputs,
            parameters = merge,
            provenance = "test",
        )

        mutableInputs += GPUPreparedFilterInputRef.TransparentBlack

        assertEquals(1, merge.inputs.size)
        assertEquals(1, node.inputs.size)
    }

    @Test
    fun `runtime children map is an immutable snapshot`() {
        val children = mutableMapOf<String, GPUPreparedFilterInputRef>(
            "src" to GPUPreparedFilterInputRef.ImplicitSource,
        )
        val params = RuntimeEffectParams("fx", 1, emptyMap(), children)

        children["other"] = GPUPreparedFilterInputRef.TransparentBlack

        assertEquals(setOf("src"), params.children.keys)
    }

    @Test
    fun `array identities do not collide through polynomial folding`() {
        val a = FloatArray(20).also {
            it[18] = Float.fromBits(0)
            it[19] = Float.fromBits(31)
        }
        val b = FloatArray(20).also {
            it[18] = Float.fromBits(1)
            it[19] = Float.fromBits(0)
        }

        assertNotEquals(ColorFilterParams(a).canonicalIdentity(), ColorFilterParams(b).canonicalIdentity())
    }

    @Test
    fun `compose identity length-prefixes user-controlled picture identities`() {
        val first = ComposeParams(
            inner = GPUPreparedFilterInputRef.Picture("x:outer=picture:y"),
            outer = GPUPreparedFilterInputRef.ImplicitSource,
        )
        val second = ComposeParams(
            inner = GPUPreparedFilterInputRef.Picture("x"),
            outer = GPUPreparedFilterInputRef.Picture("y:outer=implicit_source"),
        )

        assertNotEquals(first.canonicalIdentity(), second.canonicalIdentity())
    }

    @Test
    fun `negative picture source coordinates remain explicit`() {
        val params = PictureParams(
            pictureIdentity = "picture",
            srcX = -5f,
            srcY = -4f,
            srcW = 10f,
            srcH = 12f,
        )

        assertTrue(params.hasExplicitSrc)
    }

    @Test
    fun `normalization snapshots rewrite and materialization collections`() {
        val rewrites = mutableListOf<GPUPreparedFilterRewriteProof>()
        val materializations = mutableSetOf<GPUPreparedFilterNodeId>()
        val normalization = GPUPreparedFilterNormalization(
            graph = GPUPreparedFilterGraph(
                emptyList(),
                GPUPreparedFilterInputRef.ImplicitSource,
            ),
            rewrites = rewrites,
            materializationNodeIds = materializations,
        )

        rewrites += GPUPreparedFilterRewriteProof(
            rule = "external-mutation",
            sourceNodeIds = emptyList(),
            resultNodeIds = emptyList(),
            removedIntermediateCount = 0,
            inputBoundsIdentity = "in",
            outputBoundsIdentity = "out",
        )
        materializations += GPUPreparedFilterNodeId("external")

        assertTrue(normalization.rewrites.isEmpty())
        assertTrue(normalization.materializationNodeIds.isEmpty())
    }
}
