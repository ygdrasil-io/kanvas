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
    @Disabled("STUB.MESH.COLOR_SPACE: mesh specification color-space and alpha-type semantics are not implemented")
    fun `CustomMeshCsGM placeholder`() {
        TestUtils.runGmTest(CustomMeshCsGM())
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
    @Disabled("STUB.MESH.COLOR_MANAGED_UNIFORMS: color-managed mesh uniforms are not implemented")
    fun `CustomMeshCsUniformsGM placeholder`() {
        TestUtils.runGmTest(CustomMeshCsUniformsGM())
    }
}
