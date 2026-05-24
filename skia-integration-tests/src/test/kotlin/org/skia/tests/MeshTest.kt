package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Placeholder tests for all 11 GMs ported from gm/mesh.cpp.
 *
 * All GMs require SkMesh / SkMeshSpecification (custom GPU mesh-shader API)
 * which is not yet implemented in :kanvas-skia.  Each test body calls into
 * the GM's onDraw(), which throws TODO("STUB.MESH").
 *
 * Disabled with STUB.MESH until the API is implemented.
 */
@Disabled("STUB.MESH: SkMesh / SkMeshSpecification not implemented in :kanvas-skia")
class MeshTest {

    @Test
    fun `CustomMeshGM placeholder`() {
        TestUtils.runGmTest(CustomMeshGM())
    }

    @Test
    fun `CustomMeshCsGM placeholder`() {
        TestUtils.runGmTest(CustomMeshCsGM())
    }

    @Test
    fun `CustomMeshUniformsGM placeholder`() {
        TestUtils.runGmTest(CustomMeshUniformsGM())
    }

    @Test
    fun `MeshUpdateGM placeholder`() {
        TestUtils.runGmTest(MeshUpdateGM())
    }

    @Test
    fun `MeshZeroInitGM placeholder`() {
        TestUtils.runGmTest(MeshZeroInitGM())
    }

    @Test
    fun `PictureMeshGM placeholder`() {
        TestUtils.runGmTest(PictureMeshGM())
    }

    @Test
    fun `MeshWithImageGM placeholder`() {
        TestUtils.runGmTest(MeshWithImageGM())
    }

    @Test
    fun `MeshWithPaintColorGM placeholder`() {
        TestUtils.runGmTest(MeshWithPaintColorGM())
    }

    @Test
    fun `MeshWithPaintImageGM placeholder`() {
        TestUtils.runGmTest(MeshWithPaintImageGM())
    }

    @Test
    fun `MeshWithEffectsGM placeholder`() {
        TestUtils.runGmTest(MeshWithEffectsGM())
    }

    @Test
    fun `CustomMeshCsUniformsGM placeholder`() {
        TestUtils.runGmTest(CustomMeshCsUniformsGM())
    }
}
