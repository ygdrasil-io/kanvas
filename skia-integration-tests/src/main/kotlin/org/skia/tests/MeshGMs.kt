package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

// ---------------------------------------------------------------------------
// Stub ports of Skia's gm/mesh.cpp  (11 GMs)
//
// All GMs in this file depend on SkMesh / SkMeshSpecification — a custom
// GPU mesh-shader API that has no counterpart in :kanvas-skia yet.
// Every onDraw body calls TODO("STUB.MESH") as the flag-planting marker.
// The companion test file (MeshTest.kt) is @Disabled with the same tag.
//
// Upstream: /Users/chaos/workspace/kanvas-forge/skia-main/gm/mesh.cpp
// ---------------------------------------------------------------------------

/**
 * Stub for Skia's `MeshGM` ("custommesh", 435×1180).
 *
 * Upstream exercises SkMesh::Make / SkMesh::MakeIndexed with two
 * SkMeshSpecifications (one with per-vertex colour, one without), across
 * every combination of alpha / colours / shader / blender. Relies on
 * SkMeshes::MakeVertexBuffer / MakeIndexBuffer + GrDirectContext GPU copy.
 *
 * TODO("STUB.MESH") — SkMesh / SkMeshSpecification not implemented.
 */
public class CustomMeshGM : GM() {
    override fun getName(): String = "custommesh"
    override fun getISize(): SkISize = SkISize.Make(435, 1180)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}

/**
 * Stub for Skia's `MeshColorSpaceGM` ("custommesh_cs", 468×258).
 *
 * Upstream creates four SkMeshSpecifications varying alpha-type (premul /
 * unpremul) and colour-space (sRGB / colour-spun). Forces an intermediate
 * sRGB surface when the canvas is in legacy mode.
 *
 * TODO("STUB.MESH") — SkMesh / SkMeshSpecification not implemented.
 */
public class CustomMeshCsGM : GM() {
    override fun getName(): String = "custommesh_cs"
    override fun getISize(): SkISize = SkISize.Make(468, 258)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}

/**
 * Stub for Skia's `MeshUniformsGM` ("custommesh_uniforms", 140×250).
 *
 * Upstream packs three typed uniforms (float[2] t, half3×3 m, half4 color)
 * into an SkData blob and drives a radial-gradient shader through the mesh
 * fragment stage. Also implements onAnimate (sine-wave colour animation).
 *
 * TODO("STUB.MESH") — SkMesh / SkMeshSpecification not implemented.
 */
public class CustomMeshUniformsGM : GM() {
    override fun getName(): String = "custommesh_uniforms"
    override fun getISize(): SkISize = SkISize.Make(140, 250)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}

/**
 * Stub for Skia's `MeshUpdateGM` ("mesh_updates", 270×490).
 *
 * Upstream tests SkMesh::VertexBuffer::update() and
 * SkMesh::IndexBuffer::update() for both CPU-backed and GPU-backed
 * buffers, with wrapping offsets (kVBRects / kIBRects). Skips on GPU
 * recording contexts (only direct contexts).
 *
 * TODO("STUB.MESH") — SkMesh / SkMeshSpecification not implemented.
 */
public class MeshUpdateGM : GM() {
    override fun getName(): String = "mesh_updates"
    override fun getISize(): SkISize = SkISize.Make(270, 490)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}

/**
 * Stub for Skia's `MeshZeroInitGM` ("mesh_zero_init", 90×30).
 *
 * Upstream verifies that freshly-allocated GPU vertex/index buffers are
 * zero-initialised: uses the zeroed region as the first index, then
 * re-uses (recycles) the buffer to check the recycled block is also zeroed.
 *
 * TODO("STUB.MESH") — SkMesh / SkMeshSpecification not implemented.
 */
public class MeshZeroInitGM : GM() {
    override fun getName(): String = "mesh_zero_init"
    override fun getISize(): SkISize = SkISize.Make(90, 30)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}

/**
 * Stub for Skia's `PictureMesh` ("picture_mesh", 390×90).
 *
 * Upstream records an SkMesh draw into an SkPicture and plays it back,
 * exercising the picture→GPU path. Tests four draw modes (triangles /
 * triangleStrip × non-indexed / indexed) with a sweep-gradient paint.
 *
 * TODO("STUB.MESH") — SkMesh / SkMeshSpecification not implemented.
 */
public class PictureMeshGM : GM() {
    override fun getName(): String = "picture_mesh"
    override fun getISize(): SkISize = SkISize.Make(390, 90)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}

/**
 * Shared stub base for Skia's `MeshWithShadersGM` (four registered GMs).
 *
 * Upstream creates a 16×16 ripple-animated mesh grid and draws it with four
 * shader-child configurations passed to the SkMeshSpecification fragment:
 *  - kMeshWithImage      ("mesh_with_image",       320×320)
 *  - kMeshWithPaintColor ("mesh_with_paint_color",  320×320)
 *  - kMeshWithPaintImage ("mesh_with_paint_image",  320×320)
 *  - kMeshWithEffects    ("mesh_with_effects",      320×320)
 *
 * The fragment shader consumes up to four child slots (shader×2,
 * colorFilter×1, blender×1) and blends them together.
 *
 * TODO("STUB.MESH") — SkMesh / SkMeshSpecification not implemented.
 */
public class MeshWithImageGM : GM() {
    override fun getName(): String = "mesh_with_image"
    override fun getISize(): SkISize = SkISize.Make(320, 320)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}

public class MeshWithPaintColorGM : GM() {
    override fun getName(): String = "mesh_with_paint_color"
    override fun getISize(): SkISize = SkISize.Make(320, 320)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}

public class MeshWithPaintImageGM : GM() {
    override fun getName(): String = "mesh_with_paint_image"
    override fun getISize(): SkISize = SkISize.Make(320, 320)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}

public class MeshWithEffectsGM : GM() {
    override fun getName(): String = "mesh_with_effects"
    override fun getISize(): SkISize = SkISize.Make(320, 320)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}

/**
 * Stub for Skia's `custommesh_cs_uniforms` DEF_SIMPLE_GM_CAN_FAIL
 * (200×900).
 *
 * Upstream tests that `layout(color)` uniforms in the fragment shader are
 * correctly colour-managed vs raw (non-managed) uniforms across several
 * (meshCS, surfaceCS) combinations. Skips on CPU-only / recording contexts.
 *
 * TODO("STUB.MESH") — SkMesh / SkMeshSpecification not implemented.
 */
public class CustomMeshCsUniformsGM : GM() {
    override fun getName(): String = "custommesh_cs_uniforms"
    override fun getISize(): SkISize = SkISize.Make(200, 900)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}
