package org.graphiks.kanvas.gpu.plan

private fun imageScalarScheduleIdentity(roots: List<ImageNumericOperationGraphV1.Node>): String {
    val indices = linkedMapOf<ImageNumericOperationGraphV1.Node, Int>()
    fun visit(node: ImageNumericOperationGraphV1.Node) {
        if (node in indices) return
        node.inputs.forEach(::visit)
        indices[node] = indices.size
    }
    roots.forEach(::visit)
    return indices.entries.joinToString(";") { (node, indexI32) ->
        "$indexI32:${node.operation}:${node.uniformIndexI32}:${node.constantBitsI32}:" +
            node.inputs.joinToString(",") { indices.getValue(it).toString() }
    } + ":roots=" + roots.joinToString(",") { indices.getValue(it).toString() }
}

/** Executed scalar schedule and selected tap/address topology. */
public class ImageNumericOperationGraphV1 private constructor(public val colorAlpha: ImageColorAlphaPlanV1,
    public val sampling: ImageSamplingPlanV1, public val tileModes: ImageTileModePlanV1) {
    public enum class Operation { DEVICE_X_F32, DEVICE_Y_F32, UNIFORM_F32, CONSTANT_HALF_F32, CONSTANT_ONE_F32,
        ADD_F32, SUB_F32, MUL_F32, DIV_F32, FLOOR_F32, CONSTANT_F32, ABS_F32,
        KERNEL_DISTANCE_F32, TAP_INDEX_F32, CUBIC_KERNEL_F32, TEXEL_COMPONENT_F32 }
    public class Node internal constructor(public val operation: Operation, public val uniformIndexI32: Int = -1,
        inputs: List<Node> = emptyList(), public val constantBitsI32: Int = 0) {
        public val inputs: List<Node> = immutableList(inputs)
    }
    /** Branch domains are exact comparisons of abs(distance), not weight clamping. */
    public class CubicKernelGraph internal constructor() {
        public val distance: Node = Node(Operation.KERNEL_DISTANCE_F32)
        public val absoluteDistance: Node = Node(Operation.ABS_F32, inputs = listOf(distance))
        public val innerLimitF32: Float = 1f
        public val outerLimitF32: Float = 2f
        public val innerResult: Node
        public val outerResult: Node
        public val outsideResult: Node = Node(Operation.CONSTANT_F32, constantBitsI32 = 0f.toRawBits())
        public val scheduleIdentity: String
        init {
            fun constant(value: Float) = Node(Operation.CONSTANT_F32, constantBitsI32 = value.toRawBits())
            fun binary(operation: Operation, a: Node, b: Node) = Node(operation, inputs = listOf(a, b))
            fun add(a: Node, b: Node) = binary(Operation.ADD_F32, a, b)
            fun subtract(a: Node, b: Node) = binary(Operation.SUB_F32, a, b)
            fun multiply(a: Node, b: Node) = binary(Operation.MUL_F32, a, b)
            val b = Node(Operation.UNIFORM_F32, 24)
            val c = Node(Operation.UNIFORM_F32, 25)
            fun scaled(value: Float, parameter: Node) = multiply(constant(value), parameter)
            fun polynomial(outer: Boolean): Node {
                val coefficient3 = if (outer) subtract(subtract(constant(0f), b), scaled(6f, c))
                    else subtract(subtract(constant(12f), scaled(9f, b)), scaled(6f, c))
                val coefficient2 = if (outer) add(scaled(6f, b), scaled(30f, c))
                    else add(add(constant(-18f), scaled(12f, b)), scaled(6f, c))
                val x = absoluteDistance
                val cubicTerm = multiply(multiply(multiply(coefficient3, x), x), x)
                val squareTerm = multiply(multiply(coefficient2, x), x)
                var numerator = add(cubicTerm, squareTerm)
                if (outer) numerator = add(numerator, multiply(subtract(scaled(-12f, b), scaled(48f, c)), x))
                val coefficient0 = if (outer) add(scaled(8f, b), scaled(24f, c))
                    else subtract(constant(6f), scaled(2f, b))
                return binary(Operation.DIV_F32, add(numerator, coefficient0), constant(6f))
            }
            innerResult = polynomial(false)
            outerResult = polynomial(true)
            scheduleIdentity = "ordered-abs-lt:${innerLimitF32.toRawBits()}:${outerLimitF32.toRawBits()}:otherwise:" +
                imageScalarScheduleIdentity(listOf(innerResult, outerResult, outsideResult))
        }
    }
    public enum class TexelOperation { PROJECTIVE_VALIDITY_MASK, PIXEL_CENTER_NEAREST, PIXEL_CENTER_LINEAR, PIXEL_CENTER_CUBIC,
        FLOOR_F32, CONVERT_I32, MINUS_ONE_TAP_OFFSET_I32, ZERO_TAP_OFFSET_I32, PLUS_ONE_TAP_OFFSET_I32, PLUS_TWO_TAP_OFFSET_I32,
        MITCHELL_NETRAVALI_KERNEL_F32, CLAMP_X_I32, CLAMP_Y_I32,
        REPEAT_X_I32, REPEAT_Y_I32, MIRROR_X_I32, MIRROR_Y_I32, DECAL_X_I32, DECAL_Y_I32, LOAD_UNORM8,
        SWIZZLE_BGRA, ALPHA_OPAQUE, ALPHA_STORED, ZERO_ALPHA_GUARD,
        UNIT_ALPHA_GUARDED_UNPREMULTIPLY_SOURCE, SRGB_TO_LINEAR, DISPLAY_P3_TO_LINEAR_SRGB,
        PREMULTIPLY_LINEAR, RETURN_LINEAR_PREMULTIPLIED_COLOR, RETURN_SCALAR_MASK, ACCUMULATE_NEAREST, ACCUMULATE_LINEAR, ACCUMULATE_CUBIC_ROW_MAJOR, PAINT_OPACITY }
    public val contractId: String = "WgslFloatEnvelopeV1"
    public val topologyIdentity: String = "w5e-image-numeric-v1:inverse-project-divide-map:${sampling.topologyId}:${tileModes.topologyId}:$colorAlpha:" +
        texelOperations().joinToString(",") { it.name } +
        (if (sampling is ImageSamplingPlanV1.Cubic) ":cubic-scalar-schedule-v1" else "")
    public val denominator: Node
    public val localXF32: Node
    public val localYF32: Node
    public val sourceX: Node
    public val sourceY: Node
    public val unitXF32: Node
    public val unitYF32: Node
    /** These nodes are emitted verbatim by the sampler; sealing evaluates this same graph. */
    public val tapXF32: Node
    public val tapYF32: Node
    public val baseXF32: Node
    public val baseYF32: Node
    public val fractionXF32: Node?
    public val fractionYF32: Node?
    public val weight00F32: Node?
    public val weight10F32: Node?
    public val weight01F32: Node?
    public val weight11F32: Node?
    public val cubicKernel: CubicKernelGraph?
    public val cubicWeightsF32: List<Node>
    public val cubicAccumulationF32: Node?
    public val cubicScheduleIdentity: String?
    /** Actual separable sampler region; no independent bounds or replacement evaluator. */
    public class SampledRegion private constructor(
        public val topologyIdentity: String,
        public val colorAlpha: ImageColorAlphaPlanV1,
        public val isLinear: Boolean,
        taps: List<TexelRead>, outputs: List<ColorOperationGraphV1.Scalar>,
        weightsX: List<ColorOperationGraphV1.Scalar>, weightsY: List<ColorOperationGraphV1.Scalar>,
        distancesX: List<ColorOperationGraphV1.Scalar>, distancesY: List<ColorOperationGraphV1.Scalar>,
    ) {
        public val taps: List<TexelRead> = immutableList(taps)
        public val outputs: List<ColorOperationGraphV1.Scalar> = immutableList(outputs)
        public val weightsX: List<ColorOperationGraphV1.Scalar> = immutableList(weightsX)
        public val weightsY: List<ColorOperationGraphV1.Scalar> = immutableList(weightsY)
        public val distancesX: List<ColorOperationGraphV1.Scalar> = immutableList(distancesX)
        public val distancesY: List<ColorOperationGraphV1.Scalar> = immutableList(distancesY)
        internal fun rebase(bind: (ColorOperationGraphV1.Scalar) -> ColorOperationGraphV1.Scalar,
            read: (TexelRead) -> TexelRead): SampledRegion = SampledRegion(topologyIdentity,colorAlpha,isLinear,taps.map(read),outputs.map(bind),
                weightsX.map(bind),weightsY.map(bind),distancesX.map(bind),distancesY.map(bind))
        internal companion object {
            fun bind(graph: ImageNumericOperationGraphV1,taps: List<TexelRead>,outputs: List<ColorOperationGraphV1.Scalar>,
                scalar: (Node) -> ColorOperationGraphV1.Scalar): SampledRegion {
                val weights = if (graph.sampling == ImageSamplingPlanV1.Linear)
                    listOf(requireNotNull(graph.weight00F32),requireNotNull(graph.weight10F32),
                        requireNotNull(graph.weight01F32),requireNotNull(graph.weight11F32)) else graph.cubicWeightsF32
                val size = if (graph.sampling == ImageSamplingPlanV1.Linear) 2 else 4
                val x = (0 until size).map { weights[it].inputs[0] }
                val y = (0 until size).map { weights[it*size].inputs[1] }
                fun distances(axis: List<Node>,fraction: Node?): List<ColorOperationGraphV1.Scalar> =
                    if (fraction != null) listOf(scalar(fraction)) else axis.map { scalar(it.inputs.single()) }
                return SampledRegion(graph.topologyIdentity,graph.colorAlpha,graph.sampling == ImageSamplingPlanV1.Linear,
                    taps,outputs,x.map(scalar),y.map(scalar),
                    distances(x,graph.fractionXF32),distances(y,graph.fractionYF32))
            }
        }
    }
    /** Legacy reads retain the upload; V5 reads retain only a declared logical resource. */
    public sealed interface TexelResource {
        public class Legacy internal constructor(public val upload: ImageUploadPlanV1) : TexelResource
        public class Logical internal constructor(public val ownerNodeIndexI32: Int,
            public val logicalSlotI32: Int) : TexelResource {
            init { require(ownerNodeIndexI32 >= 0 && logicalSlotI32 >= 0) { W5gPlanDiagnostics.Schema } }
        }
    }
    public class TexelRead private constructor(
        public val topologyIdentity: String,
        public val colorAlpha: ImageColorAlphaPlanV1,
        public val tileModes: ImageTileModePlanV1,
        private val decoder: ColorOperationGraphV1,
        public val resource: TexelResource,
        public val baseX: ColorOperationGraphV1.Scalar,
        public val baseY: ColorOperationGraphV1.Scalar,
        public val offsetXI32: Int,
        public val offsetYI32: Int,
        public val width: ColorOperationGraphV1.Scalar,
        public val height: ColorOperationGraphV1.Scalar,
    ) {
        internal constructor(graph: ImageNumericOperationGraphV1,upload: ImageUploadPlanV1,
            baseX: ColorOperationGraphV1.Scalar,baseY: ColorOperationGraphV1.Scalar,
            offsetXI32: Int,offsetYI32: Int,width: ColorOperationGraphV1.Scalar,height: ColorOperationGraphV1.Scalar) :
            this(graph.topologyIdentity,graph.colorAlpha,graph.tileModes,graph.decodedTexelGraph(),TexelResource.Legacy(upload),
                baseX,baseY,offsetXI32,offsetYI32,width,height)
        init { require(offsetXI32 in -1..2 && offsetYI32 in -1..2) }
        public val identity: String = "image-raw-tap:$topologyIdentity:${when(resource) {
            is TexelResource.Legacy -> resource.upload.contentIdentity
            is TexelResource.Logical -> "logical:${resource.ownerNodeIndexI32}:${resource.logicalSlotI32}"
        }}:$offsetXI32:$offsetYI32"
        internal fun rebase(x: ColorOperationGraphV1.Scalar,y: ColorOperationGraphV1.Scalar,
            width: ColorOperationGraphV1.Scalar,height: ColorOperationGraphV1.Scalar,
            resource: TexelResource = this.resource): TexelRead =
            TexelRead(topologyIdentity,colorAlpha,tileModes,decoder,resource,x,y,offsetXI32,offsetYI32,width,height)
        public val encoded: List<ColorOperationGraphV1.Scalar> = immutableList(List(4) {
            ColorOperationGraphV1.Scalar.ImageEncodedComponent(this,it) })
        public val decoded: List<ColorOperationGraphV1.Scalar> = run {
            val zero = ColorOperationGraphV1.constant(0f)
            val body = decoder.bindInput(ColorOperationGraphV1(List(4) { zero }),0L,imageEncodedInputs=encoded).outputs
            if (tileModes.x != ImageTileAxisModePlanV1.DECAL && tileModes.y != ImageTileAxisModePlanV1.DECAL) body
            else {
                val branch = ColorOperationGraphV1.BranchVector(ColorOperationGraphV1.Predicate.Equal(
                    ColorOperationGraphV1.Scalar.ImageTexelValid(this),ColorOperationGraphV1.constant(1f)),body,List(4) { zero })
                immutableList(List(4) { ColorOperationGraphV1.Scalar.BranchComponent(branch,it) })
            }
        }
    }
    /** Original decoder, expressed once for both the texel emitter and source proof. */
    public fun decodedTexelGraph(encoded: List<ColorOperationGraphV1.Scalar> =
        List(4) { ColorOperationGraphV1.Scalar.ImageEncodedInput(it) }): ColorOperationGraphV1 {
        require(encoded.size == 4)
        val operations = texelOperations()
        val zero = ColorOperationGraphV1.constant(0f)
        val one = ColorOperationGraphV1.constant(1f)
        val mask = TexelOperation.RETURN_SCALAR_MASK in operations
        val alpha = if (TexelOperation.ALPHA_OPAQUE in operations) one else encoded[if (mask) 0 else 3]
        if (mask) return ColorOperationGraphV1(List(4) { alpha })
        val rgb = if (TexelOperation.SWIZZLE_BGRA in operations) listOf(encoded[2],encoded[1],encoded[0]) else encoded.take(3)
        val straight = rgb.map { value -> if (TexelOperation.UNIT_ALPHA_GUARDED_UNPREMULTIPLY_SOURCE in operations)
            ColorOperationGraphV1.Scalar.LazyBranch(ColorOperationGraphV1.Predicate.Equal(alpha,one),value,
                ColorOperationGraphV1.Scalar.Divide(value,alpha)) else value }
        val linear = if (TexelOperation.SRGB_TO_LINEAR in operations) straight.map(ColorOperationGraphV1::eotf) else straight
        val working = if (TexelOperation.DISPLAY_P3_TO_LINEAR_SRGB in operations)
            ColorOperationGraphV1.conversion(linear,
                org.graphiks.kanvas.color.ColorInterpolationProgramV1.RecipeKind.DISPLAY_P3_TO_LINEAR_SRGB) else linear
        val output = List(4) { if (it == 3) alpha else if (TexelOperation.PREMULTIPLY_LINEAR in operations)
            ColorOperationGraphV1.Scalar.Multiply(working[it],alpha) else working[it] }
        val guarded = ColorOperationGraphV1.BranchVector(ColorOperationGraphV1.Predicate.Equal(alpha,zero),List(4) { zero },output)
        return ColorOperationGraphV1(List(4) { ColorOperationGraphV1.Scalar.BranchComponent(guarded,it) })
    }

    private inner class ColorScalarBinding(val uniformWordOffsetI64: Long,val cellWordOffsetI64: Long?) {
        fun c(value: Float) = ColorOperationGraphV1.constant(value)
        fun word(index: Int): ColorOperationGraphV1.Scalar = ColorOperationGraphV1.Scalar.DynamicF32(
            if (cellWordOffsetI64 != null && index in 12..19) Math.addExact(cellWordOffsetI64,(index-12).toLong())
            else Math.addExact(uniformWordOffsetI64,index.toLong()))
        val memo = java.util.IdentityHashMap<Node,ColorOperationGraphV1.Scalar>()
        fun scalar(node: Node, cache: MutableMap<Node,ColorOperationGraphV1.Scalar> = memo): ColorOperationGraphV1.Scalar =
            cache[node] ?: run {
                fun a() = scalar(node.inputs[0],cache)
                fun b() = scalar(node.inputs[1],cache)
                when (node.operation) {
                    Operation.DEVICE_X_F32 -> ColorOperationGraphV1.Scalar.DevicePositionF32(0)
                    Operation.DEVICE_Y_F32 -> ColorOperationGraphV1.Scalar.DevicePositionF32(1)
                    Operation.UNIFORM_F32 -> word(node.uniformIndexI32)
                    Operation.CONSTANT_HALF_F32 -> c(.5f)
                    Operation.CONSTANT_ONE_F32 -> c(1f)
                    Operation.CONSTANT_F32 -> ColorOperationGraphV1.Scalar.ConstantF32(node.constantBitsI32)
                    Operation.ADD_F32 -> ColorOperationGraphV1.Scalar.Add(a(),b())
                    Operation.SUB_F32 -> ColorOperationGraphV1.Scalar.Subtract(a(),b())
                    Operation.MUL_F32 -> ColorOperationGraphV1.Scalar.Multiply(a(),b())
                    Operation.DIV_F32 -> ColorOperationGraphV1.Scalar.Divide(a(),b())
                    Operation.FLOOR_F32 -> ColorOperationGraphV1.Scalar.Floor(a())
                    Operation.ABS_F32 -> ColorOperationGraphV1.Scalar.Abs(a())
                    Operation.TAP_INDEX_F32 -> ColorOperationGraphV1.Scalar.ImageIntegerOffset(a(),node.uniformIndexI32)
                    Operation.CUBIC_KERNEL_F32 -> {
                        val kernel = requireNotNull(cubicKernel)
                        val region = java.util.IdentityHashMap(cache)
                        region[kernel.distance] = a()
                        val distance = scalar(kernel.absoluteDistance,region)
                        ColorOperationGraphV1.Scalar.LazyBranch(ColorOperationGraphV1.Predicate.Not(
                            ColorOperationGraphV1.Predicate.LessEqual(c(kernel.innerLimitF32),distance)),
                            scalar(kernel.innerResult,region),ColorOperationGraphV1.Scalar.LazyBranch(
                                ColorOperationGraphV1.Predicate.Not(ColorOperationGraphV1.Predicate.LessEqual(c(kernel.outerLimitF32),distance)),
                                scalar(kernel.outerResult,region),scalar(kernel.outsideResult,region)))
                    }
                    Operation.KERNEL_DISTANCE_F32,Operation.TEXEL_COMPONENT_F32 -> error("Unbound original sampler operand")
                }
            }.also { cache[node] = it }
    }

    internal fun localCoordinateExpressions(): List<ColorOperationGraphV1.Scalar> = ColorScalarBinding(0L,null).let {
        listOf(it.scalar(localXF32),it.scalar(localYF32),it.scalar(denominator))
    }

    /** Binds the original coordinate, tap, kernel and decoder nodes to real raw operands. */
    internal fun sampledTexelGraph(upload: ImageUploadPlanV1, uniformWordOffsetI64: Long = 0L,
        cellWordOffsetI64: Long? = null): ColorOperationGraphV1 {
        val binding = ColorScalarBinding(uniformWordOffsetI64,cellWordOffsetI64)
        val memo = binding.memo
        fun c(value: Float) = ColorOperationGraphV1.constant(value)
        fun word(index: Int) = binding.word(index)
        fun scalar(node: Node,cache: MutableMap<Node,ColorOperationGraphV1.Scalar> = memo) = binding.scalar(node,cache)
        val x = scalar(baseXF32); val y = scalar(baseYF32)
        val offsets = when (sampling) {
            ImageSamplingPlanV1.Nearest -> listOf(0 to 0)
            ImageSamplingPlanV1.Linear -> listOf(0 to 0,1 to 0,0 to 1,1 to 1)
            is ImageSamplingPlanV1.Cubic -> (-1..2).flatMap { row -> (-1..2).map { column -> column to row } }
        }
        val taps = offsets.map { (column,row) -> TexelRead(this,upload,x,y,column,row,word(20),word(21)) }
        // All four decoded components and the proof metadata share these exact
        // materialized weights, not separately rebound copies of the kernel DAG.
        cubicWeightsF32.forEach { scalar(it) }
        val output = when (sampling) {
            ImageSamplingPlanV1.Nearest -> taps.single().decoded
            ImageSamplingPlanV1.Linear -> {
                val weights = listOf(weight00F32,weight10F32,weight01F32,weight11F32).map { scalar(requireNotNull(it)) }
                List(4) { channel ->
                    val terms = taps.mapIndexed { tap,read -> ColorOperationGraphV1.Scalar.Multiply(read.decoded[channel],weights[tap]) }
                    terms.drop(1).fold(terms.first() as ColorOperationGraphV1.Scalar) { sum,term -> ColorOperationGraphV1.Scalar.Add(sum,term) }
                }
            }
            is ImageSamplingPlanV1.Cubic -> List(4) { channel ->
                val region = java.util.IdentityHashMap(memo)
                fun bindTexels(node: Node) {
                    if (node.operation == Operation.TEXEL_COMPONENT_F32) region[node] = taps[node.uniformIndexI32].decoded[channel]
                    else node.inputs.forEach(::bindTexels)
                }
                val root = requireNotNull(cubicAccumulationF32)
                bindTexels(root)
                scalar(root,region)
            }
        }
        val sampledOutput = if (sampling == ImageSamplingPlanV1.Nearest) output else {
            val region = SampledRegion.bind(this,taps,output) { scalar(it) }
            List(4) { ColorOperationGraphV1.Scalar.ImageSampleComponent(region,it) }
        }
        val denominator = scalar(denominator)
        val valid = ColorOperationGraphV1.Predicate.And(ColorOperationGraphV1.Predicate.Finite(denominator),
            ColorOperationGraphV1.Predicate.LessEqual(c(java.lang.Float.MIN_NORMAL),ColorOperationGraphV1.Scalar.Abs(denominator)))
        val low = if (sampling is ImageSamplingPlanV1.Cubic) -2147483647f else -2147483648f
        val high = when (sampling) {
            ImageSamplingPlanV1.Nearest -> 2147483648f
            ImageSamplingPlanV1.Linear -> 2147483647f
            is ImageSamplingPlanV1.Cubic -> 2147483646f
        }
        fun validTap(tap: ColorOperationGraphV1.Scalar) = ColorOperationGraphV1.Predicate.And(
            ColorOperationGraphV1.Predicate.Finite(tap),ColorOperationGraphV1.Predicate.And(
                ColorOperationGraphV1.Predicate.LessEqual(c(low),tap),
                ColorOperationGraphV1.Predicate.Not(ColorOperationGraphV1.Predicate.LessEqual(c(high),tap))))
        val tapGuard = ColorOperationGraphV1.BranchVector(ColorOperationGraphV1.Predicate.And(
            validTap(scalar(tapXF32)),validTap(scalar(tapYF32))),sampledOutput,List(4) { c(0f) })
        // Keep the denominator guard outside source-coordinate division and the
        // tap guard outside every floor/I32 conversion, exactly as the sampler.
        val guarded = ColorOperationGraphV1.BranchVector(valid,List(4) {
            ColorOperationGraphV1.Scalar.BranchComponent(tapGuard,it) },List(4) { c(0f) })
        return ColorOperationGraphV1(List(4) { ColorOperationGraphV1.Scalar.BranchComponent(guarded,it) })
    }
    public fun texelOperations(): List<TexelOperation> = buildList {
        add(TexelOperation.PROJECTIVE_VALIDITY_MASK)
        add(when (sampling) {
            ImageSamplingPlanV1.Nearest -> TexelOperation.PIXEL_CENTER_NEAREST
            ImageSamplingPlanV1.Linear -> TexelOperation.PIXEL_CENTER_LINEAR
            is ImageSamplingPlanV1.Cubic -> TexelOperation.PIXEL_CENTER_CUBIC
        })
        addAll(listOf(TexelOperation.FLOOR_F32, TexelOperation.CONVERT_I32, TexelOperation.ZERO_TAP_OFFSET_I32))
        when (sampling) {
            ImageSamplingPlanV1.Linear -> add(TexelOperation.PLUS_ONE_TAP_OFFSET_I32)
            is ImageSamplingPlanV1.Cubic -> addAll(listOf(TexelOperation.MINUS_ONE_TAP_OFFSET_I32,
                TexelOperation.PLUS_ONE_TAP_OFFSET_I32, TexelOperation.PLUS_TWO_TAP_OFFSET_I32, TexelOperation.MITCHELL_NETRAVALI_KERNEL_F32))
            ImageSamplingPlanV1.Nearest -> Unit
        }
        fun axis(mode: ImageTileAxisModePlanV1, x: Boolean) = when (mode) {
            ImageTileAxisModePlanV1.CLAMP -> if (x) TexelOperation.CLAMP_X_I32 else TexelOperation.CLAMP_Y_I32
            ImageTileAxisModePlanV1.REPEAT -> if (x) TexelOperation.REPEAT_X_I32 else TexelOperation.REPEAT_Y_I32
            ImageTileAxisModePlanV1.MIRROR -> if (x) TexelOperation.MIRROR_X_I32 else TexelOperation.MIRROR_Y_I32
            ImageTileAxisModePlanV1.DECAL -> if (x) TexelOperation.DECAL_X_I32 else TexelOperation.DECAL_Y_I32
        }
        add(axis(tileModes.x, true)); add(axis(tileModes.y, false)); add(TexelOperation.LOAD_UNORM8)
        if (colorAlpha.channelOrder == ImageChannelOrderV1.BGRA) add(TexelOperation.SWIZZLE_BGRA)
        add(if (colorAlpha.alphaType == org.graphiks.kanvas.render.ir.ImageAlphaType.OPAQUE)
            TexelOperation.ALPHA_OPAQUE else TexelOperation.ALPHA_STORED)
        if (colorAlpha.channelOrder == ImageChannelOrderV1.ALPHA) add(TexelOperation.RETURN_SCALAR_MASK)
        else {
            add(TexelOperation.ZERO_ALPHA_GUARD)
            colorAlpha.unpremultiplyOperation?.let(::add)
            if (colorAlpha.transfer == ImageTransferPlanV1.SRGB) add(TexelOperation.SRGB_TO_LINEAR)
            if (colorAlpha.gamut == ImageGamutPlanV1.DISPLAY_P3) add(TexelOperation.DISPLAY_P3_TO_LINEAR_SRGB)
            add(if (colorAlpha.premultiplication == org.graphiks.kanvas.render.ir.ImagePremultiplicationV1.TRANSFER_ENCODED_LINEAR_PREMUL)
                TexelOperation.RETURN_LINEAR_PREMULTIPLIED_COLOR else TexelOperation.PREMULTIPLY_LINEAR)
            add(when (sampling) {
                ImageSamplingPlanV1.Nearest -> TexelOperation.ACCUMULATE_NEAREST
                ImageSamplingPlanV1.Linear -> TexelOperation.ACCUMULATE_LINEAR
                is ImageSamplingPlanV1.Cubic -> TexelOperation.ACCUMULATE_CUBIC_ROW_MAJOR
            })
            add(TexelOperation.PAINT_OPACITY)
        }
    }
    init {
        fun uniform(indexI32: Int) = Node(Operation.UNIFORM_F32, indexI32)
        fun binary(op: Operation, a: Node, b: Node) = Node(op, inputs = listOf(a, b))
        val x = Node(Operation.DEVICE_X_F32)
        val y = Node(Operation.DEVICE_Y_F32)
        fun row(indexI32: Int) = binary(Operation.ADD_F32,
            binary(Operation.ADD_F32, binary(Operation.MUL_F32, uniform(indexI32), x),
                binary(Operation.MUL_F32, uniform(indexI32 + 1), y)), uniform(indexI32 + 2))
        denominator = row(8)
        localXF32 = binary(Operation.DIV_F32, row(0), denominator)
        localYF32 = binary(Operation.DIV_F32, row(4), denominator)
        fun unit(local: Node, axisI32: Int): Node {
            val relative = binary(Operation.SUB_F32, local, uniform(16 + axisI32))
            return binary(Operation.DIV_F32, relative, uniform(18 + axisI32))
        }
        unitXF32 = unit(localXF32, 0)
        unitYF32 = unit(localYF32, 1)
        fun source(unit: Node, axisI32: Int): Node {
            return binary(Operation.ADD_F32, uniform(12 + axisI32),
                binary(Operation.MUL_F32, unit, uniform(14 + axisI32)))
        }
        sourceX = source(unitXF32, 0)
        sourceY = source(unitYF32, 1)
        val half = Node(Operation.CONSTANT_HALF_F32)
        val one = Node(Operation.CONSTANT_ONE_F32)
        tapXF32 = if (sampling == ImageSamplingPlanV1.Nearest) sourceX else binary(Operation.SUB_F32, sourceX, half)
        tapYF32 = if (sampling == ImageSamplingPlanV1.Nearest) sourceY else binary(Operation.SUB_F32, sourceY, half)
        baseXF32 = Node(Operation.FLOOR_F32, inputs = listOf(tapXF32))
        baseYF32 = Node(Operation.FLOOR_F32, inputs = listOf(tapYF32))
        if (sampling == ImageSamplingPlanV1.Linear) {
            fractionXF32 = binary(Operation.SUB_F32, tapXF32, baseXF32)
            fractionYF32 = binary(Operation.SUB_F32, tapYF32, baseYF32)
            val inverseX = binary(Operation.SUB_F32, one, fractionXF32)
            val inverseY = binary(Operation.SUB_F32, one, fractionYF32)
            weight00F32 = binary(Operation.MUL_F32, inverseX, inverseY)
            weight10F32 = binary(Operation.MUL_F32, fractionXF32, inverseY)
            weight01F32 = binary(Operation.MUL_F32, inverseX, fractionYF32)
            weight11F32 = binary(Operation.MUL_F32, fractionXF32, fractionYF32)
        } else {
            fractionXF32 = null
            fractionYF32 = null
            weight00F32 = null
            weight10F32 = null
            weight01F32 = null
            weight11F32 = null
        }
        if (sampling is ImageSamplingPlanV1.Cubic) {
            cubicKernel = CubicKernelGraph()
            fun weights(tap: Node, base: Node): List<Node> = (-1..2).map { offsetI32 ->
                val index = Node(Operation.TAP_INDEX_F32, offsetI32, listOf(base))
                Node(Operation.CUBIC_KERNEL_F32, inputs = listOf(binary(Operation.SUB_F32, tap, index)))
            }
            val weightsX = weights(tapXF32, baseXF32)
            val weightsY = weights(tapYF32, baseYF32)
            cubicWeightsF32 = immutableList((0 until 4).flatMap { rowI32 -> (0 until 4).map { columnI32 ->
                binary(Operation.MUL_F32, weightsX[columnI32], weightsY[rowI32])
            } })
            val terms = cubicWeightsF32.mapIndexed { tapI32, weight ->
                binary(Operation.MUL_F32, Node(Operation.TEXEL_COMPONENT_F32, tapI32), weight)
            }
            cubicAccumulationF32 = terms.drop(1).fold(terms.first()) { sum, term -> binary(Operation.ADD_F32, sum, term) }
            cubicScheduleIdentity = cubicKernel.scheduleIdentity + ":row-major:" + imageScalarScheduleIdentity(listOf(cubicAccumulationF32))
        } else {
            cubicKernel = null
            cubicWeightsF32 = emptyList()
            cubicAccumulationF32 = null
            cubicScheduleIdentity = null
        }
    }
    internal companion object {
        fun of(colorAlpha: ImageColorAlphaPlanV1, sampling: ImageSamplingPlanV1,
            tileModes: ImageTileModePlanV1): ImageNumericOperationGraphV1 = ImageNumericOperationGraphV1(colorAlpha, sampling, tileModes)
    }
}
