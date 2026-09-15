package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupEntry
import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep

/** Native W5e sees only the sealed frame, physical cache request, and device generation. */
internal object GPUW5eImageNativeV1 {
    fun validate(frame: GPUFramePlan): Boolean {
        val packets = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        val expected = frame.w5eConstructionV1
        if (expected == null) {
            require(frame.w5ePreparedFrameV1 == null && packets.none { it.w5eImageFrameWitnessV1 != null })
            return false
        }
        val witness = requireNotNull(frame.w5ePreparedFrameV1) { W5eImagePlanDiagnostics.InvalidContract }
        require(witness.bridge === expected && witness.validates(frame)) {
            W5eImagePlanDiagnostics.InvalidContract
        }
        expected.imageDraws().forEach { image ->
            require(image.authenticates(expected.materialTable)) { W5eImagePlanDiagnostics.InvalidContract }
            if (image.materialAuthority is PlanDrawMaterialAuthority.MaterialV5) {
                expected.constructionGraph.packedMaterialSourceV4(image.materialAuthority)
                requireNotNull(image.composedOrigin) { W5eImagePlanDiagnostics.InvalidContract }
                return@forEach
            }
            (image.materialAuthority as? PlanDrawMaterialAuthority.MaterialV4)?.let {
                expected.constructionGraph.packedMaterialSourceV4(it)
            }
            // The native path never synthesizes an addressing rule or hardware sampler: its only
            // image resource is the sealed textureLoad source authenticated by the plan table.
            require(image.execution.upload.widthI32 > 0 && image.execution.upload.heightI32 > 0 &&
                image.execution.numericAuthority.graph.sampling == image.execution.sampling &&
                image.execution.numericAuthority.graph.tileModes == image.execution.tileModes) {
                W5eImagePlanDiagnostics.InvalidContract
            }
            val cubic = image.execution.sampling as? ImageSamplingPlanV1.Cubic
            require(image.execution.atlasBlend?.let { blend ->
                image.originalDraw.geometry is org.graphiks.kanvas.render.ir.GeometryNode.Atlas &&
                    blend.color == image.constructionEntry.atlasEntryColor && blend.mode == image.originalDraw.operationBlendMode &&
                    blend.authenticates(image.execution.upload, image.execution.colorAlpha, image.execution.childSourceIdentity,image.execution.numericAuthority)
            } != false) { W5eImagePlanDiagnostics.InvalidContract }
            require(cubic == null || cubic.bF32.isFinite() && cubic.bF32 in 0f..1f &&
                cubic.cF32.isFinite() && cubic.cF32 in 0f..1f) { W5eImagePlanDiagnostics.CubicParameters }
            image.execution.cellSelection?.let { selector ->
                require((image.originalDraw.geometry is org.graphiks.kanvas.render.ir.GeometryNode.ImageNine && !selector.lattice ||
                    image.originalDraw.geometry is org.graphiks.kanvas.render.ir.GeometryNode.ImageLattice && selector.lattice) &&
                    selector.cells.size <= selector.capacityI32 && (selector.lattice || selector.capacityI32 == 9) &&
                    selector.samples.all { sample ->
                        val bounds = sample.cell.copyDestinationF32()
                        sample.numericAuthority.graph.sampling == image.execution.sampling &&
                            sample.numericAuthority.graph.tileModes == ImageTileModePlanV1.ClampClamp &&
                            sample.coordinates.uniformValuesF32().all(Float::isFinite) &&
                            listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).all(Float::isFinite) &&
                            (bounds.right > bounds.left) == (selector.directionX == ImageCellAxisDirectionV1.Increasing) &&
                            (bounds.bottom > bounds.top) == (selector.directionY == ImageCellAxisDirectionV1.Increasing)
                    }) { W5eImagePlanDiagnostics.InvalidContract }
            }
        }
        return expected.imageDraws().isNotEmpty()
    }
    fun acquire(cache: GPUW5eDecodedImageSessionCache, request: PlanCacheResourceRequest.Texture,
        generationI64: Long): GPUW5eDecodedImageSessionCache.Lease {
        require(cache.deviceGenerationI64 == generationI64) { "stale.material.image.device-generation" }
        return cache.acquire(request).also { require(it.generationI64 == generationI64) }
    }
    fun binding(lease: GPUW5eDecodedImageSessionCache.Lease, bindingU32: UInt): BindGroupEntry =
        BindGroupEntry(binding = bindingU32, resource = lease.view)
}
