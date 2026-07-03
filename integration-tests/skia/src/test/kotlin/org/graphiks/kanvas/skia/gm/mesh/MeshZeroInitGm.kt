package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

class MeshZeroInitGm : SkiaGm {
    override val name = "mesh_zero_init"
    override val renderFamily = RenderFamily.MESH
    override val minSimilarity = 0.0
    override val width = 90
    override val height = 30

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        throw NotImplementedError("STUB.MESH.GPU_ZERO_INIT")
    }
}
