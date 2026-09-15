package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.gpu.plan.ColorOperationGraphV1.Scalar as S
import org.graphiks.kanvas.gpu.plan.ColorOperationGraphV1.Predicate as P
import org.graphiks.kanvas.gpu.plan.GradientNumericOperationGraphV1.Node
import org.graphiks.kanvas.gpu.plan.GradientNumericOperationGraphV1.Operation as G
import org.graphiks.kanvas.gpu.plan.GradientNumericOperationGraphV1.Input as I

internal object ColorSourceProofCompilerV1 {

    private class CoordinateExpressions(val x: S,val y: S,val valid: P,val nextWordI64: Long)
    private fun coordinateExpressions(coordinates: MaterialCoordinatePlanV2,startWordI64: Long): CoordinateExpressions {
        val zero=ColorOperationGraphV1.constant(0f); val one=ColorOperationGraphV1.constant(1f)
        fun word(offset: Long): S = S.DynamicF32(offset)
        var x: S = S.DevicePositionF32(0); var y: S = S.DevicePositionF32(1)
        var valid: P = P.Equal(one,one)
        var offset = startWordI64
        coordinates.copyOperations().forEach { operation -> when (operation) {
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
        return CoordinateExpressions(x,y,valid,offset)
    }
    internal class ComposedGraph(val evaluation: MaterialEvaluationDagV5,val graph: ColorOperationGraphV1,
        val words: Map<Long,Int>,val tables: Map<Long,org.graphiks.kanvas.render.ir.ImmutableUBytes>,val integers: Map<Long,UInt>)
    internal fun graphForComposed(metadata: MaterialSourceConstructionV4.ComposedMetadata,
        gradients: Map<MaterialSourceConstructionV4,PreparedSourceDefinitionV4>,
        images: List<PreparedComposedSourceV5.ImageReference>,
        noises: List<PreparedComposedSourceV5.NoiseReference>): ComposedGraph {
        val words = linkedMapOf<Long,Int>()
        val integers = linkedMapOf<Long,UInt>()
        val tables = linkedMapOf<Long,org.graphiks.kanvas.render.ir.ImmutableUBytes>()
        val graphs = mutableListOf<ColorOperationGraphV1>()
        val entries = mutableListOf<MaterialEvaluationDagV5.Entry>()
        metadata.nodes.forEach { node ->
            val offset = node.offsetBytesI32.toLong()/4L
            fun child(index: Int = 0) = graphs[node.children[index].indexI32]
            val graph = when(val original = node.original) {
                org.graphiks.kanvas.render.ir.MaterialNode.Transparent -> ColorOperationGraphV1(List(4) { ColorOperationGraphV1.constant(0f) })
                is org.graphiks.kanvas.render.ir.MaterialNode.Solid -> {
                    val color = original.color
                    listOf(color.redNormalized,color.greenNormalized,color.blueNormalized,color.alphaNormalized)
                        .forEachIndexed { channel,value -> words[offset+channel] = value.toRawBits() }
                    val alpha = ColorOperationGraphV1.Scalar.DynamicF32(offset+3)
                    ColorOperationGraphV1(List(4) { if(it == 3) alpha else ColorOperationGraphV1.Scalar.Multiply(
                        ColorOperationGraphV1.eotf(ColorOperationGraphV1.Scalar.DynamicF32(offset+it)),alpha) })
                }
                is org.graphiks.kanvas.render.ir.MaterialNode.Opacity -> {
                    words[offset] = original.alpha.toRawBits()
                    ColorOperationGraphV1(child().outputs.map { ColorOperationGraphV1.Scalar.Multiply(it,ColorOperationGraphV1.Scalar.DynamicF32(offset)) })
                }
                is org.graphiks.kanvas.render.ir.MaterialNode.RuntimeEffect -> {
                    val numeric = requireNotNull(node.runtime).numericGraph as NumericOperationGraphV1.RuntimeChildOpacity
                    words[offset] = (original.uniforms().getValue("alpha") as org.graphiks.kanvas.render.ir.RuntimeUniformValue.F1).value.toRawBits()
                    numeric.colorGraph.bindInput(child(),offset)
                }
                is org.graphiks.kanvas.render.ir.MaterialNode.WithColorFilter -> {
                    val filter = requireNotNull(node.filter)
                    filter.forEachWord { index,value -> words[Math.addExact(offset,index)] = value }
                    filter.forEachTable { index,value -> tables[Math.addExact(offset,index)] = value }
                    filter.copyOperationGraph().bindInput(child(),offset)
                }
                is org.graphiks.kanvas.render.ir.MaterialNode.WithWorkingColorSpace,
                is org.graphiks.kanvas.render.ir.MaterialNode.WithLocalMatrix,
                is org.graphiks.kanvas.render.ir.MaterialNode.CoordClamp -> child()
                is org.graphiks.kanvas.render.ir.MaterialNode.Blend -> BlendFormulaProgramV1.colorOperations(original.mode.name.lowercase(),child(1).outputs,child(0).outputs)
                is org.graphiks.kanvas.render.ir.MaterialNode.PerlinNoise,
                is org.graphiks.kanvas.render.ir.MaterialNode.FractalNoise -> {
                    val reference=noises.single { it.ownerNodeIndexI32 == node.ownerNodeIndexI32 && it.wordOffsetI64 == offset }
                    val source=reference.metadata
                    val parameters=source.parameters
                    words[offset]=parameters.frequencyXF32.toRawBits(); words[offset+1L]=parameters.frequencyYF32.toRawBits()
                    integers[offset+2L]=parameters.octavesI32.toUInt(); integers[offset+3L]=if(parameters.fractal) 1u else 0u
                    integers[offset+4L]=reference.range.baseWordU32; integers[offset+5L]=if(parameters.stitched) 1u else 0u
                    for(axis in 0..1) for(limb in 0..3) {
                        val period=if(axis == 0) parameters.periodX else parameters.periodY
                        integers[offset+8L+axis*4L+limb]=period.shiftRight(limb*32).and(java.math.BigInteger("ffffffff",16)).toLong().toUInt()
                    }
                    var cursor=offset+NoiseOperationGraphV1.HEADER_BYTES_I64/4L
                    source.coordinates.copyOperations().forEach { operation ->
                        val values=when(operation) {
                            is MaterialCoordinateOperationV2.InverseMatrixF32 -> operation.inverseF32.let { m ->
                                listOf(m.sx,m.kx,m.tx,0f,m.ky,m.sy,m.ty,0f,m.persp0,m.persp1,m.persp2,
                                    if(m.persp0 == 0f && m.persp1 == 0f && m.persp2 == 1f) 1f else 0f)
                            }
                            is MaterialCoordinateOperationV2.ClampRectF32 -> operation.subsetF32.let { r -> listOf(r.left,r.top,r.right,r.bottom) }
                        }
                        values.forEach { words[cursor++]=it.toRawBits() }
                    }
                    val context=coordinateExpressions(source.coordinates,offset+NoiseOperationGraphV1.HEADER_BYTES_I64/4L)
                    require(context.nextWordI64 == cursor && cursor == offset+source.uniformBytesI64/4L) { W5gPlanDiagnostics.Schema }
                    val region=NoiseOperationGraphV1(node.ownerNodeIndexI32,offset,context.x,context.y)
                    val zero=ColorOperationGraphV1.constant(0f)
                    val guarded=ColorOperationGraphV1.BranchVector(context.valid,List(4) { S.NoiseComponent(region,it) },List(4) { zero })
                    ColorOperationGraphV1(List(4) { S.BranchComponent(guarded,it) })
                }
                is org.graphiks.kanvas.render.ir.MaterialNode.ImageSample -> {
                    val reference=images.single { it.ownerNodeIndexI32 == node.ownerNodeIndexI32 && it.wordOffsetI64 == offset }
                    val image=reference.binding
                    val source=requireNotNull(node.imageSource)
                    var cursor=offset
                    RawMaterialRequirementsV2.forEachImageHeaderWord(image.projection,image.upload,1f,image.graph.sampling,null,null) {
                        words[cursor++]=it
                    }
                    require(cursor == offset+source.headerBytesI64/4L) { W5gPlanDiagnostics.Schema }
                    source.coordinates.copyOperations().forEach { operation ->
                        val values=when(operation) {
                            is MaterialCoordinateOperationV2.InverseMatrixF32 -> operation.inverseF32.let { m ->
                                listOf(m.sx,m.kx,m.tx,0f,m.ky,m.sy,m.ty,0f,m.persp0,m.persp1,m.persp2,
                                    if(m.persp0 == 0f && m.persp1 == 0f && m.persp2 == 1f) 1f else 0f)
                            }
                            is MaterialCoordinateOperationV2.ClampRectF32 -> operation.subsetF32.let { r -> listOf(r.left,r.top,r.right,r.bottom) }
                        }
                        values.forEach { words[cursor++]=it.toRawBits() }
                    }
                    val context=coordinateExpressions(source.coordinates,offset+source.headerBytesI64/4L)
                    require(context.nextWordI64 == cursor && cursor == offset+source.uniformBytesI64/4L) { W5gPlanDiagnostics.Schema }
                    val zero=ColorOperationGraphV1.constant(0f)
                    val sampled=image.graph.sampledTexelGraph(image.upload).bindInput(ColorOperationGraphV1(List(4) { zero }),offset,
                        imageResource=ImageNumericOperationGraphV1.TexelResource.Logical(node.ownerNodeIndexI32,image.resource.logicalSlotI32),
                        deviceCoordinates=listOf(context.x,context.y))
                    val guarded=ColorOperationGraphV1.BranchVector(context.valid,sampled.outputs,List(4) { zero })
                    ColorOperationGraphV1(List(4) { S.BranchComponent(guarded,it) })
                }
                is org.graphiks.kanvas.render.ir.MaterialNode.LinearGradient,
                is org.graphiks.kanvas.render.ir.MaterialNode.RadialGradient,
                is org.graphiks.kanvas.render.ir.MaterialNode.SweepGradient,
                is org.graphiks.kanvas.render.ir.MaterialNode.ConicalGradient -> {
                    val source=requireNotNull(node.gradientSource)
                    val solid=source.gradient?.stops?.solidColor
                    if(solid != null) {
                        listOf(solid.redNormalized,solid.greenNormalized,solid.blueNormalized,solid.alphaNormalized)
                            .forEachIndexed { channel,value -> words[offset+channel]=value.toRawBits() }
                        val alpha=S.DynamicF32(offset+3L)
                        ColorOperationGraphV1(List(4) { if(it == 3) alpha else
                            S.Multiply(ColorOperationGraphV1.eotf(S.DynamicF32(offset+it)),alpha) })
                    } else {
                        val definition=gradients.getValue(source)
                        definition.numericWordsF32Bits.forEach { (key,value) -> words[Math.addExact(offset,key)]=value }
                        definition.integerWordsU32.forEach { (key,value) -> integers[Math.addExact(offset,key)]=value }
                        graphForPrepared(definition).bindInput(ColorOperationGraphV1(List(4) { ColorOperationGraphV1.constant(0f) }),offset)
                    }
                }
                else -> error(W5gPlanDiagnostics.Unpromoted)
            }
            graphs += graph
            entries += MaterialEvaluationDagV5.Entry(node.ownerNodeIndexI32,node.children,node.gradientSource?.coordinates ?:
                node.imageSource?.coordinates?.let(SourceCoordinatesV4::V2) ?:
                node.noiseSource?.coordinates?.let(SourceCoordinatesV4::V2) ?: SourceCoordinatesV4.None,
                ComposedMaterialProgramV5(MaterialProgramPlanId(if (node.runtime != null) "runtime-effect-v1"
                    else "composed-evaluation-v5:${node.topologyIdentity}:${graph.canonicalIdentity}"),graph))
        }
        return ComposedGraph(MaterialEvaluationDagV5.of(entries),graphs.last(),words,tables,integers)
    }
    private fun graphForImage(execution: ImageSampleExecutionPlanV1,child: ColorSourceProofV1?): ColorOperationGraphV1 {
        return graphForCapturedImage(execution.numericAuthority,execution.upload,child,execution.atlasBlend?.copyOperationGraph())
    }
    internal fun graphForCapturedImage(numeric: ImageNumericAuthorityV1,upload: ImageUploadPlanV1,
        child: ColorSourceProofV1?,atlas: BlendFormulaOperationGraphV1?): ColorOperationGraphV1 {
        fun c(value: Float): S = ColorOperationGraphV1.constant(value)
        fun word(index: Long): S = S.DynamicF32(index)
        val zero = c(0f); val one = c(1f)
        fun branch(predicate: P,yes: List<S>,no: List<S>): List<S> =
            ColorOperationGraphV1.BranchVector(predicate,yes,no).let { region -> List(4) { S.BranchComponent(region,it) } }
        val layout = ImageSourceLayoutV3(child?.gradientStopSlab != null,numeric.cellSelection != null,
            numeric.cellSelection?.lattice == true,numeric.cellSelection?.capacityI32 ?: 9,atlas != null)
        val childValues = child?.copyOperationGraph()?.bindInput(ColorOperationGraphV1(List(4) { zero }),
            layout.imageUniformByteCountI64/4L)?.outputs
        require((numeric.graph.colorAlpha.channelOrder == ImageChannelOrderV1.ALPHA) == (childValues != null)) { W5fPlanDiagnostics.Schema }
        fun source(sampled: List<S>): List<S> = if (childValues == null) {
            if (atlas == null) sampled.map { S.Multiply(it,word(22L)) } else sampled
        } else List(4) { S.Multiply(childValues[it],sampled[0]) }
        val selection = numeric.cellSelection
        var values = if (selection == null) source(numeric.sampledTexelGraph(upload).outputs) else {
            val local = numeric.graph.localCoordinateExpressions()
            fun or(a: P,b: P): P = P.Not(P.And(P.Not(a),P.Not(b)))
            var selected: List<S> = List(4) { S.DiscardF32 }
            for (indexI32 in selection.cells.indices.reversed()) {
                val offsetI64 = 32L+indexI32.toLong()*(if (selection.lattice) 20L else 16L)
                val cell = selection.cells[indexI32]
                val output = when (cell) {
                    is ImageCellPlanV1.Sampled -> {
                        val sample = selection.samples.single { it.cell === cell }
                        source(sample.numericAuthority.sampledTexelGraph(upload,offsetI64).outputs)
                    }
                    is ImageCellPlanV1.SolidV1 -> {
                        val alpha = S.Multiply(word(offsetI64+19L),word(30L))
                        List(4) { if (it == 3) alpha else S.Multiply(ColorOperationGraphV1.eotf(word(offsetI64+16L+it)),alpha) }
                    }
                    is ImageCellPlanV1.OmittedV1 -> List(4) { S.DiscardF32 }
                }
                fun axis(axisI32: Int): P {
                    val increasing = P.Equal(word(28L+axisI32),one)
                    val start = word(offsetI64+12L+axisI32); val end = word(offsetI64+14L+axisI32)
                    val x = local[axisI32]
                    val starts = or(P.And(increasing,P.LessEqual(start,x)),P.And(P.Not(increasing),P.LessEqual(x,start)))
                    val ends = or(P.And(increasing,P.Not(P.LessEqual(end,x))),P.And(P.Not(increasing),P.Not(P.LessEqual(x,end))))
                    return P.And(or(P.Equal(word(offsetI64+8L+axisI32),one),starts),
                        or(P.Equal(word(offsetI64+10L+axisI32),one),ends))
                }
                val contains = P.And(P.Not(P.LessEqual(word(23L),c(indexI32.toFloat()))),P.And(axis(0),axis(1)))
                selected = branch(contains,output,selected)
            }
            val localValid = P.And(P.Finite(local[0]),P.Finite(local[1]))
            val denominatorValid = P.And(P.Finite(local[2]),P.LessEqual(c(java.lang.Float.MIN_NORMAL),S.Abs(local[2])))
            branch(denominatorValid,branch(localValid,selected,List(4) { zero }),List(4) { zero })
        }
        atlas?.let { schedule ->
            require(selection == null) { W5fPlanDiagnostics.Schema }
            val offsetI64 = layout.imageUniformByteCountI64/4L-4L
            val alpha = word(offsetI64+3L)
            val entry = List(4) { if (it == 3) alpha else S.Multiply(ColorOperationGraphV1.eotf(word(offsetI64+it)),alpha) }
            values = schedule.colorOperations("w5e_atlas_blend",entry,values).outputs.map { S.Multiply(it,word(22L)) }
        }
        return ColorOperationGraphV1(values)
    }
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
        val context=coordinateExpressions(definition.coordinates,GradientInterpolationUniformLayoutV4.HEADER_WORD_COUNT_I32.toLong())
        val x=context.x; val y=context.y; val valid=context.valid
        val offset=context.nextWordI64
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
            table.entry(MaterialPlanRef(firstI32)).bindings !is ImageSampleV3 &&
            coordinates != SourceCoordinatesV4.None) return ColorSourceProofResultV1.Refused(W5fPlanDiagnostics.Unpromoted)
        val words = linkedMapOf<Long,Int>()
        val integers = linkedMapOf<Long,UInt>()
        var stops: GradientStopSlabPlanV1? = null
        val tables = linkedMapOf<Long,org.graphiks.kanvas.render.ir.ImmutableUBytes>()
        var values: List<ColorOperationGraphV1.Scalar>? = null
        var offsetU32 = 0L
        var imageChild: ColorSourceProofV1? = null
        for (indexI32 in firstI32..root.indexI32) {
            val binding = table.entry(MaterialPlanRef(indexI32)).bindings
            owners += binding
            values = when (binding) {
                is ImageSampleV3 -> {
                    val execution = binding.execution
                    if (indexI32 != firstI32 || coordinates != SourceCoordinatesV4.V3(execution.coordinates) ||
                        execution.numericAuthority.copyDeviceBoundsF32() != deviceBoundsF32 ||
                        !table.authenticatesImage(MaterialPlanRef(indexI32),execution))
                        return ColorSourceProofResultV1.Refused(W5fPlanDiagnostics.Schema)
                    val childAuthority = table.imageChildAuthority(MaterialPlanRef(indexI32))
                    imageChild = childAuthority?.let { authority ->
                        val childCoordinates = when (authority) {
                            is PlanDrawMaterialAuthority.MaterialV4 -> authority.coordinates
                            is PlanDrawMaterialAuthority.MaterialV2 -> SourceCoordinatesV4.V2(authority.coordinates)
                            is PlanDrawMaterialAuthority.MaterialV1 -> authority.coordinates?.let(SourceCoordinatesV4::V1) ?: SourceCoordinatesV4.None
                            else -> return ColorSourceProofResultV1.Refused(W5fPlanDiagnostics.Schema)
                        }
                        (seal(table,authority.materialPlanRef(),childCoordinates,deviceBoundsF32) as? ColorSourceProofResultV1.Ready)?.source
                            ?: return ColorSourceProofResultV1.Refused(W5fPlanDiagnostics.NumericDomainUnbounded)
                    }
                    var headerWordI64 = 0L
                    RawMaterialRequirementsV2.forEachImageHeaderWord(execution) { words[headerWordI64++] = it }
                    require(headerWordI64 == binding.colorUniformWordCountV4()) { W5fPlanDiagnostics.Schema }
                    imageChild?.let { child ->
                        child.numericWordBits.forEach { (word,bits) -> words[Math.addExact(headerWordI64,word)] = bits }
                        child.integerWordValuesU32.forEach { (word,bits) -> integers[Math.addExact(headerWordI64,word)] = bits }
                        child.tableRecords.forEach { (word,table) -> tables[Math.addExact(headerWordI64,word)] = table }
                        stops = child.gradientStopSlab
                        offsetU32 = Math.addExact(offsetU32,child.uniformWordCountI64)
                    }
                    val graph = graphForImage(execution,imageChild)
                    require(execution.atlasBlend?.authenticatesSourceGraph(graph,imageChild,words,integers,tables) != false) {
                        W5fPlanDiagnostics.Schema }
                    graph.outputs
                }
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
            ColorOperationGraphV1(requireNotNull(values)),owners,words,tables,integers,stops,imageChild)
            ?: return ColorSourceProofResultV1.Refused(W5fPlanDiagnostics.NumericDomainUnbounded)
        return ColorSourceProofResultV1.Ready(proof)
    }
}
