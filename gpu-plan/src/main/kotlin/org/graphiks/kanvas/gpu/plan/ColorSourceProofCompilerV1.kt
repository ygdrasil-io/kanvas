package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.gpu.plan.ColorOperationGraphV1.Scalar as S
import org.graphiks.kanvas.gpu.plan.ColorOperationGraphV1.Predicate as P
import org.graphiks.kanvas.gpu.plan.GradientNumericOperationGraphV1.Node
import org.graphiks.kanvas.gpu.plan.GradientNumericOperationGraphV1.Operation as G
import org.graphiks.kanvas.gpu.plan.GradientNumericOperationGraphV1.Input as I

internal object ColorSourceProofCompilerV1 {
    /** Derives the executed prefix from original typed addressing and actual prepared inputs. */
    internal fun graphForPrepared(definition: PreparedSourceDefinitionV4): ColorOperationGraphV1 {
        fun c(value: Float): S = ColorOperationGraphV1.constant(value)
        fun word(offset: Long): S = S.DynamicF32(offset)
        val zero = c(0f); val one = c(1f)
        fun vectorBranch(predicate: P,yes: List<S>,no: List<S>): List<S> {
            val branch = ColorOperationGraphV1.BranchVector(predicate,yes,no)
            return List(4) { S.BranchComponent(branch,it) }
        }
        if (definition.addressing.consumesDegenerateAverage) {
            // §7.2 bypasses requested-domain interpolation: original SRGB integral
            // is converted once, and no coordinate/search operation executes.
            val alpha = word(27L)
            return ColorOperationGraphV1(List(4) { if (it == 3) alpha else
                S.Multiply(ColorOperationGraphV1.eotf(word(24L+it)),alpha) })
        }
        var x: S = S.DevicePositionF32(0); var y: S = S.DevicePositionF32(1)
        var valid: P = P.Equal(one,one)
        var offset = GradientInterpolationUniformLayoutV4.HEADER_WORD_COUNT_I32.toLong()
        definition.coordinates.copyOperations().forEach { operation -> when (operation) {
            is MaterialCoordinateOperationV2.InverseMatrixF32 -> {
                fun row(start: Long): S = S.Add(S.Add(S.Multiply(word(start),x),S.Multiply(word(start+1L),y)),word(start+2L))
                val hx = row(offset); val hy = row(offset+4L); val hw = row(offset+8L)
                val affine = P.Equal(word(offset+11L),one)
                val px = S.ProjectiveDivide(hx,hw); val py = S.ProjectiveDivide(hy,hw)
                val affineValid = S.LazyBranch(P.And(P.Finite(hx),P.Finite(hy)),one,zero)
                val projectiveValid = S.LazyBranch(P.And(P.ProjectiveValid(px),P.ProjectiveValid(py)),one,zero)
                valid = P.And(valid,P.Equal(S.LazyBranch(affine,affineValid,projectiveValid),one))
                // Same cumulative validity and point reset as W5dLocalPointV2;
                // subsequent clamp/matrix operations consume the reset point.
                x = S.EagerSelect(valid,S.LazyBranch(affine,hx,px),zero)
                y = S.EagerSelect(valid,S.LazyBranch(affine,hy,py),zero)
                offset += 12L
            }
            is MaterialCoordinateOperationV2.ClampRectF32 -> {
                x = S.Min(S.Max(x,word(offset)),word(offset+2L))
                y = S.Min(S.Max(y,word(offset+1L)),word(offset+3L))
                offset += 4L
            }
        } }
        require(offset == definition.uniformWordCountI64) { W5fPlanDiagnostics.Schema }
        val schema = when (definition.addressing.family) {
            GradientFamilyV2.LINEAR -> GradientNumericOperationGraphV1.linear(definition.metadata.tile)
            GradientFamilyV2.RADIAL -> GradientNumericOperationGraphV1.radial(definition.metadata.tile)
            GradientFamilyV2.SWEEP -> GradientNumericOperationGraphV1.sweep(definition.metadata.tile)
            GradientFamilyV2.CONICAL -> GradientNumericOperationGraphV1.conical(definition.metadata.tile)
        }
        val scalars = java.util.IdentityHashMap<Node,S>()
        val flags = java.util.IdentityHashMap<Node,P>()
        val selections = java.util.IdentityHashMap<Node,ColorOperationGraphV1.GradientStopSelection>()
        lateinit var scalar: (Node) -> S
        lateinit var flag: (Node) -> P
        fun input(slot: I): S = when (slot) {
            I.X -> x; I.Y -> y
            I.START_X,I.CENTER_X -> word(4L)
            I.START_Y,I.CENTER_Y -> word(5L)
            I.END_X,I.RADIUS,I.START_DEGREES -> word(6L)
            I.END_Y,I.END_DEGREES -> word(7L)
            I.LINEAR_DX -> word(8L); I.LINEAR_DY -> word(9L); I.LINEAR_LEN2 -> word(10L)
            I.SPAN_DEGREES -> word(12L)
            I.CONICAL_DX -> word(16L); I.CONICAL_DY -> word(17L)
            I.CONICAL_START_RADIUS -> word(18L); I.CONICAL_END_RADIUS -> word(19L)
            I.CONICAL_DR -> word(20L); I.CONICAL_A -> word(21L)
            I.MIN_NORMAL -> c(java.lang.Float.MIN_NORMAL); I.TWO_PI -> c(6.2831855f)
            I.QUARTER -> c(.25f); I.HALF -> c(.5f); I.THREE_QUARTERS -> c(.75f)
            I.FULL_TURN_DEGREES -> c(360f); I.ZERO -> zero; I.ONE -> one; I.TWO -> c(2f); I.FOUR -> c(4f)
            else -> error(W5fPlanDiagnostics.Schema)
        }
        flag = { node -> flags.getOrPut(node) {
            when (node.operation) {
                G.INPUT_UNIFORM_FLAG -> when (node.input) {
                    I.DEGENERATE -> P.UniformU32Equal(2L,1u)
                    I.LEADING_SEGMENT -> P.UniformU32Equal(3L,1u)
                    I.CONICAL_FULLY_DEGENERATE -> P.UniformU32Equal(22L,0u)
                    I.CONICAL_CONCENTRIC -> P.UniformU32Equal(22L,1u)
                    I.CONICAL_LINEAR_EQUATION -> P.UniformU32Equal(22L,2u)
                    I.CONICAL_QUADRATIC -> P.UniformU32Equal(22L,3u)
                    I.CONICAL_SHARED_RADIUS_ABOVE_EPSILON -> P.UniformU32Equal(23L,1u)
                    else -> error(W5fPlanDiagnostics.Schema)
                }
                G.COMPARE_F32 -> if (node.lessOrEqual) P.LessEqual(scalar(node.inputs[0]),scalar(node.inputs[1]))
                    else P.Not(P.LessEqual(scalar(node.inputs[1]),scalar(node.inputs[0])))
                G.AND_FLAG -> P.And(flag(node.inputs[0]),flag(node.inputs[1]))
                G.OR_FLAG -> P.Not(P.And(P.Not(flag(node.inputs[0])),P.Not(flag(node.inputs[1]))))
                G.SELECT -> {
                    val condition = flag(node.inputs[2])
                    // Boolean select still validates/emits both predicate inputs.
                    // Its exact truth table is (condition && yes) || (!condition && no).
                    val yes = P.And(condition,flag(node.inputs[1]))
                    val no = P.And(P.Not(condition),flag(node.inputs[0]))
                    P.Not(P.And(P.Not(yes),P.Not(no)))
                }
                G.FINITE_F32 -> P.Finite(scalar(node.inputs.single()))
                G.ROOT_RADIUS_POSITIVE -> P.Not(P.LessEqual(scalar(node.inputs.single()),zero))
                else -> error(W5fPlanDiagnostics.Schema)
            }
        } }
        scalar = { node -> scalars.getOrPut(node) {
            fun a() = scalar(node.inputs[0])
            fun b() = scalar(node.inputs[1])
            when (node.operation) {
                G.INPUT_LOCAL_POINT_F32,G.INPUT_UNIFORM_F32 -> input(requireNotNull(node.input))
                G.ADD_F32,G.ROOT_RADIUS_ADD_F32 -> S.Add(a(),b())
                G.SUB_F32 -> S.Subtract(a(),b())
                G.MUL_F32,G.ROOT_RADIUS_MUL_F32 -> S.Multiply(a(),b())
                G.DIV_F32,G.ROOT_DIV_F32 -> S.Divide(a(),b())
                G.MAX_F32 -> S.Max(a(),b())
                G.ABS_F32 -> S.Abs(a())
                G.FLOOR_F32 -> S.Floor(a())
                G.ATAN2_F32 -> S.Atan2(a(),b())
                G.SQRT_F32 -> a().let { operand -> S.LazyBranch(P.Equal(operand,zero),zero,S.Sqrt(operand)) }
                G.SELECT -> S.EagerSelect(flag(node.inputs[2]),b(),a())
                G.FINITE_ROOT_OR_ZERO_F32 -> S.EagerSelect(flag(node.inputs[1]),a(),zero)
                else -> error(W5fPlanDiagnostics.Schema)
            }
        } }
        val first = ColorOperationGraphV1.GradientStopSelection(zero,one,zero,0L,definition.domain,firstOnly=true)
        fun rgba(node: Node): List<S> = when (node.operation) {
            G.VALIDITY_MASK -> vectorBranch(flag(node.inputs[1]),rgba(node.inputs[0]),List(4) { zero })
            G.SELECT -> {
                val no = rgba(node.inputs[0]); val yes = rgba(node.inputs[1]); val predicate = flag(node.inputs[2])
                List(4) { S.EagerSelect(predicate,yes[it],no[it]) }
            }
            G.LOAD_STOP_COLOR_SRGBA_F32 -> {
                require(node.inputs[1].operation == G.INPUT_STOP_RANGE_U32 && node.inputs[1].input == I.ZERO &&
                    node.relativeIndexI32 == 0) { W5fPlanDiagnostics.Schema }
                List(4) { S.GradientStopComponent(first,it) }
            }
            G.INTERPOLATE_SRGBA_STRAIGHT_F32 -> {
                require(node.clampInterpolationWeightToUnitInterval) { W5fPlanDiagnostics.Schema }
                val left = node.inputs[0]; val right = node.inputs[1]
                val upper = right.inputs[1]
                require(left.operation == G.LOAD_STOP_COLOR_SRGBA_F32 && right.operation == G.LOAD_STOP_COLOR_SRGBA_F32 &&
                    left.relativeIndexI32 == -1 && right.relativeIndexI32 == 0 && left.inputs[1] === upper &&
                    upper.operation == G.UPPER_BOUND_STOPS_V1 && upper.loopBody?.countBoundU32 == 65_538u &&
                    upper.loopBody.comparisonIsLessOrEqual && upper.loopBody.midpointUsesDifference) {
                    W5fPlanDiagnostics.Schema
                }
                val selected = selections.getOrPut(upper) { ColorOperationGraphV1.GradientStopSelection(
                    scalar(upper.inputs[1]),scalar(upper.inputs[2]),scalar(node.inputs[4]),0L,definition.domain) }
                List(4) { S.GradientStopComponent(selected,it) }
            }
            else -> error(W5fPlanDiagnostics.Schema)
        }
        var straight = rgba(schema.root)
        if (definition.addressing.effectiveTileMode == GradientTileModeV2.DECAL) {
            straight = vectorBranch(P.UniformU32Equal(2L,1u),List(4) { zero },straight)
        }
        val linear = when (definition.domain) {
            org.graphiks.kanvas.render.ir.ColorInterpolation.SRGB -> straight.take(3).map(ColorOperationGraphV1::eotf)
            org.graphiks.kanvas.render.ir.ColorInterpolation.LINEAR -> straight.take(3)
            org.graphiks.kanvas.render.ir.ColorInterpolation.HSL -> ColorOperationGraphV1.conversion(straight.take(3),
                org.graphiks.kanvas.color.ColorInterpolationProgramV1.RecipeKind.HSL_TO_RGB).map(ColorOperationGraphV1::eotf)
            org.graphiks.kanvas.render.ir.ColorInterpolation.OKLAB -> ColorOperationGraphV1.conversion(straight.take(3),
                org.graphiks.kanvas.color.ColorInterpolationProgramV1.RecipeKind.OKLAB_TO_LINEAR_RGB)
            org.graphiks.kanvas.render.ir.ColorInterpolation.OKLCH -> ColorOperationGraphV1.conversion(
                ColorOperationGraphV1.conversion(straight.take(3),org.graphiks.kanvas.color.ColorInterpolationProgramV1.RecipeKind.OKLCH_TO_OKLAB),
                org.graphiks.kanvas.color.ColorInterpolationProgramV1.RecipeKind.OKLAB_TO_LINEAR_RGB)
        }
        val output = List(4) { if (it == 3) straight[3] else S.Multiply(linear[it],straight[3]) }
        return ColorOperationGraphV1(vectorBranch(valid,output,List(4) { zero }))
    }

    internal fun sealPrepared(definition: PreparedSourceDefinitionV4): ColorSourceProofV1? =
        ColorSourceProofV1.issuePrepared(definition)

    fun seal(table: MaterialPlanTable, root: MaterialPlanRef, coordinates: SourceCoordinatesV4,
        deviceBoundsF32: RectF32): ColorSourceProofResultV1 {
        if (listOf(deviceBoundsF32.left,deviceBoundsF32.top,
                deviceBoundsF32.right,deviceBoundsF32.bottom).any { !it.isFinite() })
            return ColorSourceProofResultV1.Refused(W5fPlanDiagnostics.Unpromoted)
        val owners = mutableListOf<MaterialBindingPlan>()
        var firstI32 = root.indexI32
        while (table.entry(MaterialPlanRef(firstI32)).bindings.let {
                it is MaterialBindingPlan.OpacityF32V1 || it is ColorFilterBindingV4 }) {
            if (firstI32 == 0) return ColorSourceProofResultV1.Refused(W5fPlanDiagnostics.Schema)
            firstI32--
        }
        if (table.entry(MaterialPlanRef(firstI32)).bindings !is GradientInterpolationBindingV4 &&
            coordinates != SourceCoordinatesV4.None) return ColorSourceProofResultV1.Refused(W5fPlanDiagnostics.Unpromoted)
        val words = linkedMapOf<Long,Int>()
        val integers = linkedMapOf<Long,UInt>()
        var stops: GradientStopSlabPlanV1? = null
        val tables = linkedMapOf<Long,org.graphiks.kanvas.render.ir.ImmutableUBytes>()
        var values: List<ColorOperationGraphV1.Scalar>? = null
        var offsetU32 = 0L
        for (indexI32 in firstI32..root.indexI32) {
            val binding = table.entry(MaterialPlanRef(indexI32)).bindings
            owners += binding
            values = when (binding) {
                is GradientInterpolationBindingV4 -> {
                    val proof = binding.sourceProof
                    if (indexI32 != firstI32 || !proof.authenticates(table,MaterialPlanRef(indexI32),coordinates) ||
                        proof.deviceBoundsF32 != deviceBoundsF32) return ColorSourceProofResultV1.Refused(W5fPlanDiagnostics.Schema)
                    if (indexI32 == root.indexI32) return ColorSourceProofResultV1.Ready(proof)
                    words.putAll(proof.numericWordBits); integers.putAll(proof.integerWordValuesU32)
                    stops = proof.gradientStopSlab
                    proof.copyOperationGraph().outputs
                }
                MaterialBindingPlan.EmptyV1 -> List(4) { ColorOperationGraphV1.constant(0f) }
                is MaterialBindingPlan.SolidRgbaF32V1 -> {
                    val color = binding.copyRgbaF32()
                    val channels = listOf(color.red,color.green,color.blue,color.alpha)
                    if (channels.any { !it.isFinite() || it !in 0f..1f }) return ColorSourceProofResultV1.Refused(W5fPlanDiagnostics.Schema)
                    channels.forEachIndexed { channelI32, valueF32 -> words[offsetU32+channelI32]=valueF32.toRawBits() }
                    val alpha = ColorOperationGraphV1.Scalar.DynamicF32(offsetU32+3)
                    List(4) { if(it==3) alpha else ColorOperationGraphV1.Scalar.Multiply(
                        ColorOperationGraphV1.eotf(ColorOperationGraphV1.Scalar.DynamicF32(offsetU32+it)),alpha) }
                }
                is MaterialBindingPlan.OpacityF32V1 -> {
                    words[offsetU32] = binding.alphaF32.toRawBits()
                    val alpha = ColorOperationGraphV1.Scalar.DynamicF32(offsetU32)
                    requireNotNull(values).map { ColorOperationGraphV1.Scalar.Multiply(it,alpha) }
                }
                is ColorFilterBindingV4 -> {
                    binding.execution.forEachWord { offset, value -> words[Math.addExact(offsetU32, offset)] = value }
                    binding.execution.forEachTable { offset, table -> tables[Math.addExact(offsetU32,offset)] = table }
                    binding.execution.copyOperationGraph().bindInput(
                        ColorOperationGraphV1(requireNotNull(values)), offsetU32).outputs
                }
                else -> return ColorSourceProofResultV1.Refused(W5fPlanDiagnostics.Unpromoted)
            }
            offsetU32 = Math.addExact(offsetU32,binding.colorUniformWordCountV4())
        }
        val proof = ColorSourceProofV1.issue(table,root,coordinates,deviceBoundsF32,
            ColorOperationGraphV1(requireNotNull(values)),owners,words,tables,integers,stops)
            ?: return ColorSourceProofResultV1.Refused(W5fPlanDiagnostics.NumericDomainUnbounded)
        return ColorSourceProofResultV1.Ready(proof)
    }
}
