package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.color.ColorInterpolationProgramV1
import org.graphiks.kanvas.render.ir.ColorInterpolation
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.GradientStop
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.PaintNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32

internal sealed interface SourceConstructionResultV4<out T> {
    data class Built<T>(val value: T) : SourceConstructionResultV4<T>
    data class Refused(val failure: RenderPlanResult<Nothing>,val diagnosticCode: String) : SourceConstructionResultV4<Nothing>
}

internal fun sourceConstructionRefusalV4(code: String): SourceConstructionResultV4.Refused {
    val resource = code.startsWith("resource.") || code.startsWith("resource-limit.")
    val invalid = code.startsWith("invalid.")
    val diagnostics = listOf(RenderDiagnostic(RenderDiagnosticCode(code),
        if (resource) RenderDiagnosticDomain.RESOURCE else if (invalid) RenderDiagnosticDomain.SCENE
        else RenderDiagnosticDomain.CAPABILITY, RenderDiagnosticSeverity.ERROR, code))
    return SourceConstructionResultV4.Refused(when {
        resource -> RenderPlanResult.ResourceLimitExceeded(diagnostics)
        invalid -> RenderPlanResult.InvalidScene(diagnostics)
        else -> RenderPlanResult.GapOnPromotedScope(diagnostics)
    },code)
}

internal sealed interface SourceUnaryMetadataV4 {
    data class Opacity(val alphaF32: Float) : SourceUnaryMetadataV4
    data class Filter(val execution: ColorFilterExecutionPlanV1) : SourceUnaryMetadataV4
}

/** Metadata-only scan of the immutable recorded stops, never a converted stop snapshot. */
internal class GradientStopCursorV4 private constructor(
    private val input: List<GradientStop>,
    private val effectiveTileMode: GradientTileModeV2,
    val solidColor: ColorARGB?,
) {
    data class Stop(val positionF32: Float, val color: ColorARGB)

    fun values(): Sequence<Stop> = sequence {
        if (solidColor != null) return@sequence
        if (input.size == 1) {
            yield(Stop(0f, input.single().color)); yield(Stop(1f, input.single().color))
            return@sequence
        }
        var previous = 0f
        var position = maxOf(0f, input.first().position.coerceIn(0f, 1f))
        var first = input.first().color
        var last = first
        var runCount = 0
        var firstRun = true
        suspend fun SequenceScope<Stop>.emitRun() {
            if (firstRun && position > 0f) yield(Stop(0f, first))
            val exteriorStart = effectiveTileMode != GradientTileModeV2.CLAMP && position == 0f
            val exteriorEnd = effectiveTileMode != GradientTileModeV2.CLAMP && position == 1f
            if (runCount == 1 || !exteriorStart) yield(Stop(position, first))
            if (runCount > 1 && !exteriorEnd) yield(Stop(position, last))
            firstRun = false
        }
        for (stop in input) {
            val next = maxOf(previous, stop.position.coerceIn(0f, 1f)).let { if (it == 0f) 0f else it }
            if (runCount != 0 && next != position) {
                emitRun(); runCount = 0; first = stop.color
            }
            position = next; last = stop.color; runCount++; previous = next
        }
        emitRun()
        if (position < 1f) yield(Stop(1f, last))
    }

    val countI32: Int = values().count()
    val byteSizeI64: Long = Math.multiplyExact(countI32.toLong(), 32L)

    fun sameSequence(other: GradientStopCursorV4): Boolean {
        if (countI32 != other.countI32 || solidColor != other.solidColor) return false
        val left = values().iterator(); val right = other.values().iterator()
        while (left.hasNext()) if (left.next() != right.next()) return false
        return !right.hasNext()
    }

    /** Hash accelerator only; range allocation must also call sameSequence on a collision. */
    val sequenceIdentity: String by lazy {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        fun word(bits: Int) { repeat(4) { byte -> digest.update((bits ushr (byte * 8)).toByte()) } }
        word(countI32)
        for (stop in values()) { word(stop.positionF32.toRawBits()); word(stop.color.value.toInt()) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun of(input: List<GradientStop>, effectiveTileMode: GradientTileModeV2,
            preserveValidityMask: Boolean): GradientStopCursorV4 {
            require(input.isNotEmpty()) { W5cPlanDiagnostics.EmptyStops }
            require(input.all { it.position.isFinite() }) { W5cPlanDiagnostics.NonFinite }
            require(input.size.toLong() + 2L <= 65_538L) { W5cPlanDiagnostics.StopBudget }
            val cursor = GradientStopCursorV4(input, effectiveTileMode,
                input.singleOrNull()?.color?.takeUnless { preserveValidityMask })
            var previous: Float? = null
            for (stop in cursor.values()) {
                previous?.let { first -> require(stop.positionF32 == first ||
                    stop.positionF32 - first >= java.lang.Float.MIN_NORMAL) { W5cPlanDiagnostics.NumericDomainUnbounded } }
                previous = stop.positionF32
            }
            require(cursor.countI32 == 0 || cursor.countI32 in 2..65_538) { W5cPlanDiagnostics.StopBudget }
            return cursor
        }
    }
}

/** Original source metadata; pending sources have no table, tuple, proof or execution witness. */
internal class MaterialSourceConstructionV4 private constructor(
    val original: MaterialNode,
    val paint: PaintNode?,
    val coordinates: SourceCoordinatesV4,
    bounds: RectF32,
    val blend: BlendPlan,
    val canonicalIdentity: String,
    val resolvedSource: EffectiveMaterialPlanner.Result.Ready?,
    val gradient: GradientMetadata?,
) {
    private val bounds = bounds.copy()
    val deviceBoundsF32: RectF32 get() = bounds.copy()
    val pending: Boolean get() = resolvedSource == null
    val hasGradientStorage: Boolean get() = gradient?.stops?.countI32?.let { it > 0 }
        ?: (resolvedSource?.table?.gradientStopSlab != null)

    class GradientMetadata internal constructor(
        val leaf: MaterialNode,
        val interpolation: ColorInterpolation,
        val family: GradientFamilyV2,
        val tile: GradientTileOperationGraphV2,
        val degeneracy: GradientDegeneracyV1,
        val stops: GradientStopCursorV4,
        wrappers: List<SourceUnaryMetadataV4>,
    ) {
        val wrappers: List<SourceUnaryMetadataV4> = immutableList(wrappers)
        val recipeIdentity: String? = when (interpolation) {
            ColorInterpolation.SRGB -> null
            ColorInterpolation.LINEAR -> "linear-srgb-eotf-ordered-f32-v1"
            ColorInterpolation.OKLAB -> ColorInterpolationProgramV1.OKLAB_RECIPE_VERSION
            ColorInterpolation.HSL -> ColorInterpolationProgramV1.recipe(ColorInterpolationProgramV1.RecipeKind.SRGB_TO_HSL_STOP).identity
            ColorInterpolation.OKLCH -> ColorInterpolationProgramV1.recipe(ColorInterpolationProgramV1.RecipeKind.SRGB_TO_OKLCH_STOP).identity
        }
        val rangeIdentity: String = "stop-domain-v4:$interpolation:$recipeIdentity:${stops.sequenceIdentity}"
    }

    companion object {
        fun retain(draw: DrawNode, resolved: EffectiveMaterialPlanner.Result.Ready, bounds: RectF32): MaterialSourceConstructionV4 =
            MaterialSourceConstructionV4(draw.material, draw.paint, when (val authority = resolved.materialAuthority) {
                is PlanDrawMaterialAuthority.MaterialV4 -> authority.coordinates
                is PlanDrawMaterialAuthority.MaterialV3 -> SourceCoordinatesV4.V3(authority.imageCoordinates)
                is PlanDrawMaterialAuthority.MaterialV2 -> SourceCoordinatesV4.V2(authority.coordinates)
                is PlanDrawMaterialAuthority.MaterialV1 ->
                    (authority.coordinates ?: MaterialCoordinatePlanV1.fromCtm(draw.transform))
                        ?.let(SourceCoordinatesV4::V1) ?: SourceCoordinatesV4.None
                is PlanDrawMaterialAuthority.LegacyColorV1 -> error(W5fPlanDiagnostics.Schema)
            }, bounds, resolved.blend, resolved.table.sourceIdentity(resolved.root), resolved, null)

        fun capture(draw: DrawNode, coordinates: SourceCoordinatesV4, bounds: RectF32,
            blend: BlendPlan): SourceConstructionResultV4<MaterialSourceConstructionV4> = try {
            require(bounds.isFinite() && bounds.isSorted()) { W5fPlanDiagnostics.Schema }
            require(draw.origin in setOf(DrawOrigin.RECT, DrawOrigin.RRECT, DrawOrigin.PATH) &&
                draw.resource == null && draw.operationBlendMode == null) { W5fPlanDiagnostics.Unpromoted }
            require(colorFilterEffectsMatchPaint(draw)) { W5fPlanDiagnostics.Schema }
            val wrappers = mutableListOf<SourceUnaryMetadataV4>()
            val coordinateNodes = mutableListOf<CoordinateNodeV2>()
            var leaf = draw.material
            var depth = 0
            var selectedDomain: ColorInterpolation? = null
            while (true) {
                require(++depth <= 64) { W5fPlanDiagnostics.Schema }
                when (val node = leaf) {
                    is MaterialNode.WithWorkingColorSpace -> {
                        if (selectedDomain == null) selectedDomain = node.interpolation
                        leaf = node.material
                    }
                    is MaterialNode.Opacity -> {
                        require(node.alpha.isFinite() && node.alpha in 0f..1f) { W5aPlanDiagnostics.InvalidOpacity }
                        if (node.alpha != 1f) wrappers += SourceUnaryMetadataV4.Opacity(node.alpha)
                        leaf = node.material
                    }
                    is MaterialNode.WithColorFilter -> {
                        wrappers += SourceUnaryMetadataV4.Filter(compileFilter(node.filter)); leaf = node.material
                    }
                    is MaterialNode.WithLocalMatrix -> {
                        coordinateNodes += CoordinateNodeV2.LocalMatrix(node.matrix); leaf = node.material
                    }
                    is MaterialNode.CoordClamp -> {
                        coordinateNodes += CoordinateNodeV2.CoordClamp(node.copySubset()); leaf = node.material
                    }
                    else -> break
                }
            }
            var interpolation: ColorInterpolation
            val family: GradientFamilyV2
            val requested: GradientTileModeV2
            val stops: List<GradientStop>
            val degeneracy: GradientDegeneracyV1
            when (val source = leaf) {
                is MaterialNode.LinearGradient -> {
                    require(listOf(source.start.x, source.start.y, source.end.x, source.end.y).all(Float::isFinite)) { W5cPlanDiagnostics.NonFinite }
                    interpolation = source.interpolation; family = GradientFamilyV2.LINEAR
                    requested = GradientTileModeV2.valueOf(source.tileMode.name); stops = source.stops()
                    degeneracy = LinearGradientDegeneracyV1.of(source.start, source.end)
                }
                is MaterialNode.RadialGradient -> {
                    require(listOf(source.center.x, source.center.y, source.radius).all(Float::isFinite)) { W5cPlanDiagnostics.NonFinite }
                    require(source.radius >= 0f) { W5cPlanDiagnostics.NegativeRadius }
                    interpolation = source.interpolation; family = GradientFamilyV2.RADIAL
                    requested = GradientTileModeV2.valueOf(source.tileMode.name); stops = source.stops()
                    degeneracy = RadialGradientDegeneracyV1(source.radius, source.radius <= 0.000030517578125f)
                }
                is MaterialNode.SweepGradient -> {
                    require(listOf(source.center.x, source.center.y, source.startAngle, source.endAngle).all(Float::isFinite)) { W5cPlanDiagnostics.NonFinite }
                    interpolation = source.interpolation; family = GradientFamilyV2.SWEEP
                    requested = GradientTileModeV2.valueOf(source.tileMode.name); stops = source.stops()
                    degeneracy = SweepGradientDegeneracyV1.of(source.startAngle, source.endAngle)
                    require(!degeneracy.sweepOrderingInvalid) { W5cPlanDiagnostics.SweepOrdering }
                }
                is MaterialNode.ConicalGradient -> {
                    require(listOf(source.start.x, source.start.y, source.end.x, source.end.y,
                        source.startRadius, source.endRadius).all(Float::isFinite)) { W5cPlanDiagnostics.NonFinite }
                    require(source.startRadius >= 0f && source.endRadius >= 0f) { W5cPlanDiagnostics.NegativeRadius }
                    interpolation = source.interpolation; family = GradientFamilyV2.CONICAL
                    requested = GradientTileModeV2.valueOf(source.tileMode.name); stops = source.stops()
                    degeneracy = ConicalGradientDegeneracyV1.of(source.start, source.startRadius, source.end, source.endRadius)
                }
                else -> error(W5fPlanDiagnostics.Unpromoted)
            }
            interpolation = selectedDomain ?: interpolation
            val scalars = when (degeneracy) {
                is LinearGradientDegeneracyV1 -> degeneracy.copyScalarsF32()
                is RadialGradientDegeneracyV1 -> listOf(degeneracy.radialRadiusF32)
                is SweepGradientDegeneracyV1 -> listOf(degeneracy.startAngleDegreesF32, degeneracy.endAngleDegreesF32, degeneracy.sweepSpanDegreesF32)
                is ConicalGradientDegeneracyV1 -> degeneracy.copyScalarsF32()
            }
            require(scalars.all(Float::isFinite)) { W5cPlanDiagnostics.NumericDomainUnbounded }
            val tile = requested.operationGraph((degeneracy as? SweepGradientDegeneracyV1)?.sweepFullCoverage == true)
            val cursor = GradientStopCursorV4.of(stops, tile.effectiveMode, family == GradientFamilyV2.CONICAL)
            if (cursor.solidColor == null) {
                require(coordinates is SourceCoordinatesV4.V2) { W5fPlanDiagnostics.Schema }
                when (val expected = MaterialCoordinatePlanV2.fromCtmAndNodes(draw.transform,coordinateNodes)) {
                    is MaterialCoordinatePlanV2.Build.Ready -> require(coordinates.plan == expected.coordinates) { W5fPlanDiagnostics.Schema }
                    is MaterialCoordinatePlanV2.Build.Refused -> throw IllegalArgumentException(expected.code)
                }
            } else require(coordinates == SourceCoordinatesV4.None) { W5fPlanDiagnostics.Schema }
            val orderedWrappers = wrappers.asReversed().toMutableList()
            val paintAlpha = draw.paint?.takeIf { it.shader != null }?.color?.alphaNormalized ?: 1f
            if (paintAlpha != 1f) orderedWrappers += SourceUnaryMetadataV4.Opacity(paintAlpha)
            draw.paint?.colorFilter?.let { orderedWrappers += SourceUnaryMetadataV4.Filter(compileFilter(it)) }
            if (orderedWrappers.any { it is SourceUnaryMetadataV4.Filter }) require(
                draw.origin in setOf(DrawOrigin.RECT, DrawOrigin.PATH) && draw.paint?.style == PaintStyleNode.FILL
            ) { W5fPlanDiagnostics.Unpromoted }
            val metadata = GradientMetadata(leaf, interpolation, family, tile, degeneracy, cursor, orderedWrappers)
            val identity = "captured-source-v4:${draw.material.canonicalId.value}:${draw.paint?.canonicalId?.value}:" +
                "${coordinates.identityV4()}:${bounds.left.toRawBits()}:${bounds.top.toRawBits()}:" +
                "${bounds.right.toRawBits()}:${bounds.bottom.toRawBits()}:$blend:${metadata.rangeIdentity}"
            SourceConstructionResultV4.Built(MaterialSourceConstructionV4(draw.material, draw.paint,
                coordinates, bounds, blend, identity, null, metadata))
        } catch (failure: IllegalArgumentException) {
            sourceConstructionRefusalV4(failure.message ?: W5fPlanDiagnostics.Schema)
        } catch (failure: IllegalStateException) {
            sourceConstructionRefusalV4(failure.message ?: W5fPlanDiagnostics.Schema)
        }

        private fun compileFilter(node: org.graphiks.kanvas.render.ir.ColorFilterNode): ColorFilterExecutionPlanV1 =
            when (val result = ColorFilterPlanCompilerV1.compile(node)) {
                is ColorFilterCompileResultV1.Ready -> result.execution
                is ColorFilterCompileResultV1.Refused -> throw IllegalArgumentException(result.diagnosticCode)
            }
    }
}

/** Source occurrence order only: actual draw refs are checked by the deferred lane factory. */
internal class MaterialSourceConstructionTableV4 private constructor(sources: List<MaterialSourceConstructionV4>) {
    private val stored = immutableList(sources)
    fun sources(): List<MaterialSourceConstructionV4> = stored
    fun source(symbolicRoot: MaterialPlanRef): MaterialSourceConstructionV4 = stored.getOrElse(symbolicRoot.indexI32) {
        throw IllegalArgumentException(W5fPlanDiagnostics.Schema)
    }
    companion object {
        fun of(sources: List<MaterialSourceConstructionV4>): SourceConstructionResultV4<MaterialSourceConstructionTableV4> =
            SourceConstructionResultV4.Built(MaterialSourceConstructionTableV4(sources))
    }
}
