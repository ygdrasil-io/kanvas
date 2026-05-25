package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.core.SkMesh
import org.skia.core.SkMeshSpecification
import org.skia.core.SkMeshes
import org.skia.core.SkPictureRecorder
import org.skia.core.SkRTreeFactory
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mesh GM coverage split by currently-supported CPU subset.
 * `custommesh` now runs through SkCanvas.drawMesh; the remaining GMs stay
 * blocked on named mesh features that the CPU subset deliberately excludes.
 */
class MeshTest {

    @Test
    fun `CustomMeshGM runs cpu subset without STUB_MESH`() {
        val gm = CustomMeshGM()
        val rendered = TestUtils.runGmTest(gm)
        assertTrue(rendered.width > 0)
        assertTrue(rendered.height > 0)
        var hasNonWhitePixel = false
        for (y in 0 until rendered.height) {
            for (x in 0 until rendered.width) {
                if (rendered.getPixel(x, y) != 0xFFFFFFFF.toInt()) {
                    hasNonWhitePixel = true
                    break
                }
            }
            if (hasNonWhitePixel) break
        }
        assertTrue(
            hasNonWhitePixel,
            "CustomMeshGM should draw visible CPU mesh content",
        )
    }

    @Test
    @Disabled(
        "STUB.MESH.VISUAL_PARITY: custommesh CPU subset runs, but upstream reference still " +
            "depends on mesh SkSL fragment outputs, UV-return shaders, and GPU buffer variants.",
    )
    fun `CustomMeshGM matches custommesh_png within tolerance`() {
        val gm = CustomMeshGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("CustomMeshGM", comparison)
        if (comparison.similarity < 75.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("CustomMeshGM", comparison.similarity)
        assertTrue(accepted, "CustomMeshGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 75.0,
            "CustomMeshGM similarity ${"%.2f".format(comparison.similarity)}% < 75.0% floor",
        )
    }

    @Test
    fun `CustomMeshCsGM runs cpu color-space subset`() {
        val rendered = TestUtils.runGmTest(CustomMeshCsGM())
        assertTrue(rendered.width > 0)
        assertTrue(rendered.height > 0)
        val srgbPremul = rendered.getPixel(40, 40)
        val spinPremul = rendered.getPixel(40, 170)
        val srgbUnpremul = rendered.getPixel(150, 40)
        val shaderSrgbPremul = rendered.getPixel(260, 40)
        assertTrue(srgbPremul != 0xFFFFFFFF.toInt(), "sRGB premul mesh cell should draw")
        assertTrue(spinPremul != 0xFFFFFFFF.toInt(), "color-spun premul mesh cell should draw")
        assertTrue(srgbUnpremul != 0xFFFFFFFF.toInt(), "sRGB unpremul mesh cell should draw")
        assertTrue(
            srgbPremul != spinPremul,
            "color-spun mesh specification should transform vertex colors differently from sRGB",
        )
        assertTrue(
            srgbPremul != srgbUnpremul,
            "premul and unpremul mesh specifications should not collapse to identical color output",
        )
        assertTrue(
            srgbPremul != shaderSrgbPremul,
            "mesh color-space cell with paint shader should exercise shader/color blending path",
        )
    }

    @Test
    fun `CustomMeshUniformsGM runs cpu uniforms subset`() {
        val rendered = TestUtils.runGmTest(CustomMeshUniformsGM())
        assertTrue(rendered.width > 0)
        assertTrue(rendered.height > 0)
        val top = rendered.getPixel(30, 40)
        val bottom = rendered.getPixel(30, 200)
        assertTrue(top != 0xFFFFFFFF.toInt())
        assertTrue(bottom != 0xFFFFFFFF.toInt())
        assertTrue(top != bottom, "uniform*meshColor subset should preserve varying color differences")
    }

    @Test
    fun `MeshUpdateGM runs cpu buffer update subset`() {
        val rendered = TestUtils.runGmTest(MeshUpdateGM())
        assertTrue(rendered.width > 0)
        assertTrue(rendered.height > 0)
        assertTrue(rendered.getPixel(70, 70) != 0xFFFFFFFF.toInt(), "non-indexed strip should render in updated region")
        assertTrue(rendered.getPixel(70, 220) != 0xFFFFFFFF.toInt(), "indexed strip should render in updated region")
        assertTrue(rendered.getPixel(70, 370) != 0xFFFFFFFF.toInt(), "indexed strip after index update should render")
        assertTrue(rendered.getPixel(20, 70) == 0xFFFFFFFF.toInt(), "pre-update left area should remain background")
    }

    @Test
    @Disabled("STUB.MESH.GPU_ZERO_INIT: upstream verifies GPU buffer zero-initialisation")
    fun `MeshZeroInitGM placeholder`() {
        TestUtils.runGmTest(MeshZeroInitGM())
    }

    @Test
    fun `PictureMeshGM records and replays drawMesh through SkPicture`() {
        val rendered = TestUtils.runGmTest(PictureMeshGM())
        assertTrue(rendered.width > 0)
        assertTrue(rendered.height > 0)
        for (mode in 0 until 4) {
            val x = 20 + mode * 50
            val direct = rendered.getPixel(x, 20)
            val picture = rendered.getPixel(x, 70)
            assertTrue(direct != 0xFFFFFFFF.toInt(), "direct drawMesh mode $mode should render")
            assertTrue(picture != 0xFFFFFFFF.toInt(), "picture playback drawMesh mode $mode should render")
            assertEquals(direct, picture, "picture playback mode $mode should match direct drawMesh output")
        }
    }

    @Test
    fun `SkPicture drawMesh records a buffer snapshot`() {
        val spec = SkMeshSpecification.Make(
            attributes = listOf(
                SkMeshSpecification.Attribute(
                    SkMeshSpecification.Attribute.Type.kFloat2,
                    offset = 0,
                    name = "position",
                ),
            ),
            vertexStride = 8,
            vs = "Varyings main(const Attributes a) { Varyings v; v.position = a.position; return v; }",
            fs = "float4 main(const Varyings v) { return float4(1); }",
        ).specification ?: error("mesh spec failed")
        val vertexBuffer = SkMeshes.MakeVertexBuffer(quadBytes(10f, 10f, 40f, 40f), 32)
        val mesh = SkMesh.Make(
            specification = spec,
            mode = SkMesh.Mode.kTriangleStrip,
            vertexBuffer = vertexBuffer,
            vertexCount = 4,
            vertexOffset = 0,
            bounds = SkRect.MakeLTRB(10f, 10f, 40f, 40f),
        ).mesh

        val recorder = SkPictureRecorder()
        recorder.beginRecording(100f, 60f).drawMesh(mesh, SkPaint(0xFFFF0000.toInt()))
        val picture = recorder.finishRecordingAsPicture()
        assertTrue(vertexBuffer.update(quadBytes(60f, 10f, 90f, 40f)))

        val bitmap = SkBitmap(100, 60)
        val canvas = SkCanvas(bitmap)
        canvas.clear(0xFFFFFFFF.toInt())
        picture.playback(canvas)

        assertTrue(bitmap.getPixel(20, 20) != 0xFFFFFFFF.toInt(), "recorded mesh should stay at original bounds")
        assertTrue(bitmap.getPixel(70, 20) == 0xFFFFFFFF.toInt(), "post-record vertex updates must not affect playback")
    }

    @Test
    fun `SkPicture drawMesh records an index buffer snapshot`() {
        val spec = SkMeshSpecification.Make(
            attributes = listOf(
                SkMeshSpecification.Attribute(
                    SkMeshSpecification.Attribute.Type.kFloat2,
                    offset = 0,
                    name = "position",
                ),
            ),
            vertexStride = 8,
            vs = "Varyings main(const Attributes a) { Varyings v; v.position = a.position; return v; }",
            fs = "float4 main(const Varyings v) { return float4(1); }",
        ).specification ?: error("mesh spec failed")
        val vertexBuffer = SkMeshes.MakeVertexBuffer(
            quadBytes(10f, 10f, 40f, 40f) + quadBytes(60f, 10f, 90f, 40f),
            64,
        )
        val indexBuffer = SkMeshes.MakeIndexBuffer(shortBytes(intArrayOf(0, 1, 2, 1, 3, 2)), 12)
        val mesh = SkMesh.MakeIndexed(
            specification = spec,
            mode = SkMesh.Mode.kTriangles,
            vertexBuffer = vertexBuffer,
            vertexCount = 8,
            vertexOffset = 0,
            indexBuffer = indexBuffer,
            indexCount = 6,
            indexOffset = 0,
            bounds = SkRect.MakeLTRB(10f, 10f, 90f, 40f),
        ).mesh

        val recorder = SkPictureRecorder()
        recorder.beginRecording(100f, 60f).drawMesh(mesh, SkPaint(0xFFFF0000.toInt()))
        val picture = recorder.finishRecordingAsPicture()
        assertTrue(indexBuffer.update(shortBytes(intArrayOf(4, 5, 6, 5, 7, 6))))

        val bitmap = SkBitmap(100, 60)
        val canvas = SkCanvas(bitmap)
        canvas.clear(0xFFFFFFFF.toInt())
        picture.playback(canvas)

        assertTrue(bitmap.getPixel(20, 20) != 0xFFFFFFFF.toInt(), "recorded index buffer should keep original quad")
        assertTrue(bitmap.getPixel(70, 20) == 0xFFFFFFFF.toInt(), "post-record index updates must not affect playback")
    }

    @Test
    fun `SkPicture drawMesh bounds participate in RTree culling`() {
        val spec = SkMeshSpecification.Make(
            attributes = listOf(
                SkMeshSpecification.Attribute(
                    SkMeshSpecification.Attribute.Type.kFloat2,
                    offset = 0,
                    name = "position",
                ),
            ),
            vertexStride = 8,
            vs = "Varyings main(const Attributes a) { Varyings v; v.position = a.position; return v; }",
            fs = "float4 main(const Varyings v) { return float4(1); }",
        ).specification ?: error("mesh spec failed")
        val leftMesh = meshForQuad(spec, 10f, 10f, 40f, 40f)
        val rightMesh = meshForQuad(spec, 60f, 10f, 90f, 40f)

        val recorder = SkPictureRecorder()
        recorder.beginRecording(100f, 60f, SkRTreeFactory).apply {
            drawMesh(leftMesh, SkPaint(0xFFFF0000.toInt()))
            drawMesh(rightMesh, SkPaint(0xFF0000FF.toInt()))
        }
        val picture = recorder.finishRecordingAsPicture()

        val bitmap = SkBitmap(100, 60)
        val canvas = SkCanvas(bitmap)
        canvas.clear(0xFFFFFFFF.toInt())
        canvas.clipRect(SkRect.MakeLTRB(55f, 0f, 100f, 60f))
        picture.playback(canvas)

        assertTrue(bitmap.getPixel(70, 20) != 0xFFFFFFFF.toInt(), "RTree playback should keep mesh intersecting clip")
        assertTrue(bitmap.getPixel(20, 20) == 0xFFFFFFFF.toInt(), "clip should exclude mesh outside the sub-rect")
    }

    @Test
    @Disabled("STUB.MESH.CHILD_SHADERS: mesh fragment shader child slots are not implemented")
    fun `MeshWithImageGM placeholder`() {
        TestUtils.runGmTest(MeshWithImageGM())
    }

    @Test
    @Disabled("STUB.MESH.CHILD_SHADERS: mesh paint-color child configuration is not implemented")
    fun `MeshWithPaintColorGM placeholder`() {
        TestUtils.runGmTest(MeshWithPaintColorGM())
    }

    @Test
    @Disabled("STUB.MESH.CHILD_SHADERS: mesh paint-image child configuration is not implemented")
    fun `MeshWithPaintImageGM placeholder`() {
        TestUtils.runGmTest(MeshWithPaintImageGM())
    }

    @Test
    @Disabled("STUB.MESH.CHILD_SHADERS: mesh shader/colorFilter/blender children are not implemented")
    fun `MeshWithEffectsGM placeholder`() {
        TestUtils.runGmTest(MeshWithEffectsGM())
    }

    @Test
    fun `CustomMeshCsUniformsGM runs cpu color-managed uniforms subset`() {
        val rendered = TestUtils.runGmTest(CustomMeshCsUniformsGM())
        assertTrue(rendered.width > 0)
        assertTrue(rendered.height > 0)
        for (row in 0 until 7) {
            assertColorsClose(
                actual = rendered.getPixel(50, row * 100 + 50),
                expected = rendered.getPixel(150, row * 100 + 50),
                tolerance = 18,
                message = "managed uniform row $row should match its expected red swatch",
            )
        }
        assertColorsClose(
            actual = rendered.getPixel(50, 750),
            expected = rendered.getPixel(150, 750),
            tolerance = 18,
            message = "raw color-spin uniform row should match its expected green swatch",
        )
        assertColorsClose(
            actual = rendered.getPixel(50, 850),
            expected = rendered.getPixel(150, 850),
            tolerance = 18,
            message = "raw wide-gamut uniform row should match its expected green swatch",
        )
        assertTrue(
            rendered.getPixel(50, 50) != rendered.getPixel(50, 750),
            "managed red and raw spin-green controls should remain distinguishable",
        )
    }

    private fun assertColorsClose(actual: Int, expected: Int, tolerance: Int, message: String) {
        val da = kotlin.math.abs(((actual ushr 24) and 0xFF) - ((expected ushr 24) and 0xFF))
        val dr = kotlin.math.abs(((actual ushr 16) and 0xFF) - ((expected ushr 16) and 0xFF))
        val dg = kotlin.math.abs(((actual ushr 8) and 0xFF) - ((expected ushr 8) and 0xFF))
        val db = kotlin.math.abs((actual and 0xFF) - (expected and 0xFF))
        assertTrue(
            maxOf(da, dr, dg, db) <= tolerance,
            "$message: actual=${actual.toUInt().toString(16)}, expected=${expected.toUInt().toString(16)}",
        )
    }

    private fun hasNonWhitePixel(rendered: org.skia.foundation.SkBitmap): Boolean {
        for (y in 0 until rendered.height) {
            for (x in 0 until rendered.width) {
                if (rendered.getPixel(x, y) != 0xFFFFFFFF.toInt()) {
                    return true
                }
            }
        }
        return false
    }

    private fun quadBytes(left: Float, top: Float, right: Float, bottom: Float): ByteArray =
        ByteBuffer.allocate(32)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putFloat(left)
                putFloat(top)
                putFloat(right)
                putFloat(top)
                putFloat(left)
                putFloat(bottom)
                putFloat(right)
                putFloat(bottom)
            }
            .array()

    private fun meshForQuad(
        spec: SkMeshSpecification,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): SkMesh {
        val vertexBuffer = SkMeshes.MakeVertexBuffer(quadBytes(left, top, right, bottom), 32)
        return SkMesh.Make(
            specification = spec,
            mode = SkMesh.Mode.kTriangleStrip,
            vertexBuffer = vertexBuffer,
            vertexCount = 4,
            vertexOffset = 0,
            bounds = SkRect.MakeLTRB(left, top, right, bottom),
        ).mesh
    }

    private fun shortBytes(values: IntArray): ByteArray =
        ByteBuffer.allocate(values.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { values.forEach { putShort(it.toShort()) } }
            .array()
}
