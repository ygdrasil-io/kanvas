package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

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
    @Disabled("STUB.MESH.PICTURE_PLAYBACK: drawMesh picture recording/playback coverage is not implemented")
    fun `PictureMeshGM placeholder`() {
        TestUtils.runGmTest(PictureMeshGM())
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
}
