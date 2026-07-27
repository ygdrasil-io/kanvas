package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPUOrderingFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.LatticeFlags
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.isAffine

data class GPUPreparedImageLoweringContext(
    val provenance: GPUFrameProvenance,
    val target: GPUTargetFacts,
    val config: RenderConfig,
    val capabilities: GPUCapabilities,
)

sealed interface GPUPreparedImageGridLowering {
    data class Ready(val commands: List<GPUFramePathVisualCommand>) :
        GPUPreparedImageGridLowering

    data class Refused(
        val code: String,
        val operationIndex: Int,
        val facts: Map<String, String>,
    ) : GPUPreparedImageGridLowering
}

internal object GPUPreparedImageGridLowerer {
    private const val MAX_LATTICE_CELL_COUNT = 4096L

    fun lowerNine(
        operation: DisplayOp.DrawImageNine,
        firstCommandId: Int,
        firstPaintOrder: Int,
        context: GPUPreparedImageLoweringContext,
    ): GPUPreparedImageGridLowering {
        validateCommon(
            imageWidth = operation.image.width,
            imageHeight = operation.image.height,
            transform = operation.transform,
            dst = operation.dst,
            context = context,
            geometryCode = GPUPreparedImageRefusalCodes.NINE_GEOMETRY,
        )?.let { return it }
        val center = operation.center
        if (!center.isFiniteRect() ||
            center.left < 0f ||
            center.top < 0f ||
            center.right < center.left ||
            center.bottom < center.top ||
            center.right > operation.image.width.toFloat() ||
            center.bottom > operation.image.height.toFloat()
        ) {
            return refused(
                code = GPUPreparedImageRefusalCodes.NINE_GEOMETRY,
                operationIndex = -1,
                reason = "invalid_center",
            )
        }
        val artifact = when (val prepared = GPUPreparedSurfaceImageSource.prepare(operation.image)) {
            is GPUPreparedImageArtifactResult.Ready -> prepared.artifact
            is GPUPreparedImageArtifactResult.Refused -> return GPUPreparedImageGridLowering.Refused(
                code = prepared.code,
                operationIndex = -1,
                facts = prepared.facts + ("sourceId" to operation.image.sourceId),
            )
        }
        val cells = operation.decompose()
        validateIdentityBudget(
            firstCommandId,
            firstPaintOrder,
            cells.size,
            GPUPreparedImageRefusalCodes.NINE_GEOMETRY,
        )?.let { return it }
        cells.firstOrNull { cell -> !cell.hasFiniteTransformedCorners(operation.transform) }?.let { cell ->
            return refused(
                code = GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING,
                operationIndex = cell.sourceIndex,
                reason = "non_finite_transformed_cell",
            )
        }
        val temporary = ArrayList<GPUFramePathVisualCommand>(cells.size)
        for (cell in cells) {
            val draw = DisplayOp.DrawImage(
                image = operation.image,
                src = cell.src,
                dst = cell.dst,
                paint = operation.paint.withImageSampling(operation.image, SamplingOptions.LINEAR),
                transform = operation.transform,
                clip = operation.clip,
            )
            when (
                val lowered = GPUPreparedDrawImageLowerer.lower(
                    operation = draw,
                    commandId = GPUDrawCommandID(0),
                    paintOrder = 0,
                    provenance = context.provenance,
                    target = context.target,
                    config = context.config,
                    capabilities = context.capabilities,
                    preparedArtifact = artifact,
                )
            ) {
                is GPUPreparedDrawImageLowering.Ready -> temporary += lowered.command
                is GPUPreparedDrawImageLowering.Refused -> return GPUPreparedImageGridLowering.Refused(
                    code = lowered.code,
                    operationIndex = cell.sourceIndex,
                    facts = lowered.facts,
                )
            }
        }
        return readyWithFinalIdentities(temporary, firstCommandId, firstPaintOrder)
    }

    fun lowerLattice(
        operation: DisplayOp.DrawImageLattice,
        firstCommandId: Int,
        firstPaintOrder: Int,
        context: GPUPreparedImageLoweringContext,
    ): GPUPreparedImageGridLowering {
        validateCommon(
            imageWidth = operation.image.width,
            imageHeight = operation.image.height,
            transform = operation.transform,
            dst = operation.dst,
            context = context,
            geometryCode = GPUPreparedImageRefusalCodes.LATTICE_GEOMETRY,
        )?.let { return it }
        validateSampling(operation.sampling)?.let { return it }

        val lattice = operation.lattice
        val xValid = lattice.xDivs.isStrictlyIncreasingInside(operation.image.width)
        val yValid = lattice.yDivs.isStrictlyIncreasingInside(operation.image.height)
        val cellCount = (lattice.xDivs.size.toLong() + 1L) *
            (lattice.yDivs.size.toLong() + 1L)
        if (!xValid || !yValid || cellCount <= 0L || cellCount > MAX_LATTICE_CELL_COUNT) {
            return refused(
                code = GPUPreparedImageRefusalCodes.LATTICE_GEOMETRY,
                operationIndex = -1,
                reason = if (cellCount > MAX_LATTICE_CELL_COUNT) "cell_budget" else "invalid_divisions",
                extraFacts = mapOf(
                    "cellCount" to cellCount.toString(),
                    "maxCellCount" to MAX_LATTICE_CELL_COUNT.toString(),
                ),
            )
        }
        val metadataLengths = listOfNotNull(
            lattice.rects?.size?.let { "rects" to it },
            lattice.colors?.size?.let { "colors" to it },
            lattice.flags?.size?.let { "flags" to it },
        )
        metadataLengths.firstOrNull { (_, size) -> size.toLong() != cellCount }?.let { (name, size) ->
            return refused(
                code = GPUPreparedImageRefusalCodes.LATTICE_GEOMETRY,
                operationIndex = -1,
                reason = "metadata_length",
                extraFacts = mapOf(
                    "metadata" to name,
                    "actualCount" to size.toString(),
                    "cellCount" to cellCount.toString(),
                ),
            )
        }
        lattice.rects?.forEachIndexed { index, rect ->
            if (!rect.isFiniteRect() || rect.isEmpty) {
                return refused(
                    code = GPUPreparedImageRefusalCodes.LATTICE_GEOMETRY,
                    operationIndex = index,
                    reason = "invalid_cell_rect",
                )
            }
        }
        lattice.flags?.forEachIndexed { index, flag ->
            if (flag == LatticeFlags.FIXED_COLOR && lattice.colors == null) {
                return refused(
                    code = GPUPreparedImageRefusalCodes.LATTICE_GEOMETRY,
                    operationIndex = index,
                    reason = "fixed_color_missing",
                )
            }
        }

        val artifact = when (val prepared = GPUPreparedSurfaceImageSource.prepare(operation.image)) {
            is GPUPreparedImageArtifactResult.Ready -> prepared.artifact
            is GPUPreparedImageArtifactResult.Refused -> return GPUPreparedImageGridLowering.Refused(
                code = prepared.code,
                operationIndex = -1,
                facts = prepared.facts + ("sourceId" to operation.image.sourceId),
            )
        }
        val cells = operation.decompose()
        validateIdentityBudget(
            firstCommandId,
            firstPaintOrder,
            cells.size,
            GPUPreparedImageRefusalCodes.LATTICE_GEOMETRY,
        )?.let { return it }
        cells.firstOrNull { cell ->
            !cell.src.isFiniteRect() ||
                !cell.dst.isFiniteRect() ||
                cell.src.isEmpty ||
                cell.dst.isEmpty
        }?.let { cell ->
            return refused(
                code = GPUPreparedImageRefusalCodes.LATTICE_GEOMETRY,
                operationIndex = cell.sourceIndex,
                reason = "invalid_expanded_cell",
            )
        }
        cells.firstOrNull { cell -> !cell.hasFiniteTransformedCorners(operation.transform) }?.let { cell ->
            return refused(
                code = GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING,
                operationIndex = cell.sourceIndex,
                reason = "non_finite_transformed_cell",
            )
        }
        val temporary = ArrayList<GPUFramePathVisualCommand>(cells.size)
        for (cell in cells) {
            val lowered = if (cell.color == null) {
                lowerSampledLatticeCell(operation, cell, artifact, context)
            } else {
                lowerFixedLatticeCell(operation, cell, context)
            }
            when (lowered) {
                is GPUPreparedGridCellLowering.Ready -> temporary += lowered.command
                is GPUPreparedGridCellLowering.Refused -> return GPUPreparedImageGridLowering.Refused(
                    code = lowered.code,
                    operationIndex = cell.sourceIndex,
                    facts = lowered.facts,
                )
            }
        }
        return readyWithFinalIdentities(temporary, firstCommandId, firstPaintOrder)
    }

    private fun lowerSampledLatticeCell(
        operation: DisplayOp.DrawImageLattice,
        cell: ImageCell,
        artifact: org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImageUploadArtifact,
        context: GPUPreparedImageLoweringContext,
    ): GPUPreparedGridCellLowering {
        val draw = DisplayOp.DrawImage(
            image = operation.image,
            src = cell.src,
            dst = cell.dst,
            paint = operation.paint.withImageSampling(operation.image, operation.sampling),
            transform = operation.transform,
            clip = operation.clip,
        )
        return when (
            val lowered = GPUPreparedDrawImageLowerer.lower(
                operation = draw,
                commandId = GPUDrawCommandID(0),
                paintOrder = 0,
                provenance = context.provenance,
                target = context.target,
                config = context.config,
                capabilities = context.capabilities,
                preparedArtifact = artifact,
            )
        ) {
            is GPUPreparedDrawImageLowering.Ready -> GPUPreparedGridCellLowering.Ready(lowered.command)
            is GPUPreparedDrawImageLowering.Refused ->
                GPUPreparedGridCellLowering.Refused(lowered.code, lowered.facts)
        }
    }

    private fun lowerFixedLatticeCell(
        operation: DisplayOp.DrawImageLattice,
        cell: ImageCell,
        context: GPUPreparedImageLoweringContext,
    ): GPUPreparedGridCellLowering {
        val fixedPaint = fixedLatticeColorPaint(requireNotNull(cell.color), operation.paint).copy(
            shader = null,
            colorFilter = null,
            maskFilter = null,
            pathEffect = null,
            imageFilter = null,
            blender = null,
            style = PaintStyle.FILL,
            strokeWidth = 0f,
            antiAlias = false,
        )
        val visual = GPUOpMapper.lowerPreparedCoreVisual(
            operation = DisplayOp.DrawRect(
                rect = cell.dst,
                paint = fixedPaint,
                transform = operation.transform,
                clip = operation.clip,
            ),
            commandId = GPUDrawCommandID(0),
            paintOrder = 0,
            context = context,
        ) ?: return GPUPreparedGridCellLowering.Refused(
            code = GPUPreparedImageRefusalCodes.LATTICE_GEOMETRY,
            facts = mapOf("reason" to "fixed_color_core_lowering"),
        )
        visual.geometryRefusal?.let { refusal ->
            return GPUPreparedGridCellLowering.Refused(
                code = refusal.code,
                facts = refusal.refusalFacts,
            )
        }
        return GPUPreparedGridCellLowering.Ready(visual)
    }

    private fun validateCommon(
        imageWidth: Int,
        imageHeight: Int,
        transform: Matrix33,
        dst: Rect,
        context: GPUPreparedImageLoweringContext,
        geometryCode: String,
    ): GPUPreparedImageGridLowering.Refused? {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return refused(
                code = GPUPreparedImageRefusalCodes.DIMENSIONS,
                operationIndex = -1,
                reason = "image_dimensions",
            )
        }
        val maxTextureDimension2D = context.capabilities.limits?.maxTextureDimension2D
        if (maxTextureDimension2D != null &&
            (imageWidth > maxTextureDimension2D || imageHeight > maxTextureDimension2D)
        ) {
            return refused(
                code = GPUPreparedImageRefusalCodes.TEXTURE_LIMIT,
                operationIndex = -1,
                reason = "texture_limit",
                extraFacts = mapOf(
                    "maxTextureDimension2D" to maxTextureDimension2D.toString(),
                ),
            )
        }
        if (!dst.isFiniteRect() || dst.isEmpty) {
            return refused(geometryCode, -1, "invalid_destination")
        }
        if (!transform.isAffine()) {
            return refused(
                GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING,
                -1,
                "perspective_transform",
            )
        }
        if (!transform.scaleX.isFinite() ||
            !transform.skewX.isFinite() ||
            !transform.transX.isFinite() ||
            !transform.skewY.isFinite() ||
            !transform.scaleY.isFinite() ||
            !transform.transY.isFinite()
        ) {
            return refused(
                GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING,
                -1,
                "non_finite_transform",
            )
        }
        val determinant =
            transform.scaleX * transform.scaleY - transform.skewX * transform.skewY
        if (!determinant.isFinite() || determinant == 0f) {
            return refused(
                GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING,
                -1,
                "singular_transform",
            )
        }
        return null
    }

    private fun validateIdentityBudget(
        firstCommandId: Int,
        firstPaintOrder: Int,
        commandCount: Int,
        geometryCode: String,
    ): GPUPreparedImageGridLowering.Refused? {
        val lastCommandId = firstCommandId.toLong() + commandCount.toLong() - 1L
        val lastPaintOrder = firstPaintOrder.toLong() + commandCount.toLong() - 1L
        return if (firstCommandId < 0 ||
            firstPaintOrder < 0 ||
            lastCommandId > Int.MAX_VALUE ||
            lastPaintOrder > Int.MAX_VALUE
        ) {
            refused(
                code = geometryCode,
                operationIndex = -1,
                reason = "command_identity_budget",
            )
        } else {
            null
        }
    }

    private fun validateSampling(
        sampling: SamplingOptions,
    ): GPUPreparedImageGridLowering.Refused? = when (sampling) {
        SamplingOptions.NEAREST,
        SamplingOptions.LINEAR,
        -> null
        is SamplingOptions.Cubic -> refused(
            GPUPreparedImageRefusalCodes.SAMPLING_CUBIC,
            -1,
            "cubic_sampling",
        )
    }

    private fun readyWithFinalIdentities(
        temporary: List<GPUFramePathVisualCommand>,
        firstCommandId: Int,
        firstPaintOrder: Int,
    ): GPUPreparedImageGridLowering.Ready =
        GPUPreparedImageGridLowering.Ready(
            temporary.mapIndexed { index, command ->
                command.withIdentity(
                    commandId = firstCommandId + index,
                    paintOrder = firstPaintOrder + index,
                )
            },
        )

    private fun GPUFramePathVisualCommand.withIdentity(
        commandId: Int,
        paintOrder: Int,
    ): GPUFramePathVisualCommand {
        val ordering = GPUOrderingFacts(
            paintOrder = paintOrder,
            dependsOnDestination = normalized.ordering.dependsOnDestination,
            requiresBarrier = normalized.ordering.requiresBarrier,
        )
        val identified = when (val command = normalized) {
            is NormalizedDrawCommand.DrawImageRect -> command.copy(
                commandId = GPUDrawCommandID(commandId),
                ordering = ordering,
            )
            is NormalizedDrawCommand.FillRect -> command.copy(
                commandId = GPUDrawCommandID(commandId),
                ordering = ordering,
            )
            else -> error("Prepared image grids emit only image or fixed-color rect commands.")
        }
        return copy(normalized = identified)
    }

    private fun Paint?.withImageSampling(
        image: org.graphiks.kanvas.image.Image,
        sampling: SamplingOptions,
    ): Paint {
        val base = this ?: Paint()
        return base.copy(shader = Shader.Image(image, sampling = sampling))
    }

    private fun List<Int>.isStrictlyIncreasingInside(limit: Int): Boolean {
        var previous = 0
        for (value in this) {
            if (value <= previous || value >= limit) return false
            previous = value
        }
        return true
    }

    private fun Rect.isFiniteRect(): Boolean =
        left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()

    private fun ImageCell.hasFiniteTransformedCorners(transform: Matrix33): Boolean =
        listOf(
            Point(dst.left, dst.top),
            Point(dst.right, dst.top),
            Point(dst.right, dst.bottom),
            Point(dst.left, dst.bottom),
        ).all { point ->
            val transformed = transform * point
            transformed.x.isFinite() && transformed.y.isFinite()
        }

    private fun refused(
        code: String,
        operationIndex: Int,
        reason: String,
        extraFacts: Map<String, String> = emptyMap(),
    ) = GPUPreparedImageGridLowering.Refused(
        code = code,
        operationIndex = operationIndex,
        facts = mapOf("reason" to reason) + extraFacts,
    )
}

private sealed interface GPUPreparedGridCellLowering {
    data class Ready(val command: GPUFramePathVisualCommand) : GPUPreparedGridCellLowering
    data class Refused(val code: String, val facts: Map<String, String>) :
        GPUPreparedGridCellLowering
}
