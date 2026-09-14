package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.color.ColorInterpolationProgramV1
import org.graphiks.kanvas.render.ir.ColorInterpolation
import org.graphiks.math.color.ColorF32

/** Checked final allocation metadata. It cannot stand in for the real packing permit. */
internal class FrameSourceLayoutV4 private constructor(
    val lane: SourceDeferredRenderConstructionV4,
    val interner: MaterialTableInterningRecipeV4,
    entries: List<List<SourceEntry>>,
    ranges: List<RangeAllocation>,
    pendingRanges: Map<MaterialSourceConstructionV4,GradientStopRangeV1>,
    legacyRanges: Map<MaterialBindingPlan,GradientStopRangeV1>,
    legacyAllocations: List<RawMaterialRequirementsV2.RelocatedLegacyLayout>,
    val nonUniformBytesI64: Long,
    val stopBytesI64: Long,
    val uniformBytesI64: Long,
    nativeLanes: List<SourceDeferredRenderConstructionV4>,
    nativeOffsetsI32: List<Int>,
    private val nativeGeometry: NativeCompositeGeometryLayoutV4?,
    private val ordinaryLayout: OrdinaryCompositeSourceLayoutV4? = null,
) {
    private val nativeLanes = immutableList(nativeLanes)
    private val nativeOffsetsI32 = immutableList(nativeOffsetsI32)
    val entries: List<List<SourceEntry>> = immutableList(entries.map(::immutableList))
    private val ranges = immutableList(ranges)
    private val pendingRanges = java.util.Collections.unmodifiableMap(java.util.IdentityHashMap(pendingRanges))
    private val legacyRanges = java.util.Collections.unmodifiableMap(java.util.IdentityHashMap(legacyRanges))
    private val legacyAllocations = immutableList(legacyAllocations)
    fun owns(source: MaterialSourceConstructionV4): Boolean = entries.any { it.first().source === source }
    fun range(source: MaterialSourceConstructionV4): GradientStopRangeV1 =
        requireNotNull(pendingRanges[source]) { W5fPlanDiagnostics.Schema }

    /** All pending conversion follows this checked owner; publication still uses the real permit. */
    fun prepareAndConstruct(): SourceConstructionResultV4<RenderGraphConstruction> = prepareAndFinish { table,roots ->
        require(ordinaryLayout == null) { W5fPlanDiagnostics.Schema }
        constructBound(table,roots)
    }

    fun prepareAndPublishOrdinary(): org.graphiks.kanvas.render.ir.RenderPlanResult<RenderGraph> =
        when (val result = prepareAndFinish { table,roots ->
            val layout = requireNotNull(ordinaryLayout) { W5fPlanDiagnostics.Schema }
            val graphs = nativeLanes.mapIndexed { index,source ->
                constructLaneBound(source,nativeOffsetsI32[index],table,roots)
            }
            W5aCompositeConstruction(graphs,table,Math.addExact(nonUniformBytesI64,stopBytesI64),layout.rectScratch).publish()
        }) {
            is SourceConstructionResultV4.Built -> org.graphiks.kanvas.render.ir.RenderPlanResult.Ready(result.value)
            is SourceConstructionResultV4.Refused -> result.failure
        }

    private fun <T> prepareAndFinish(finish: (MaterialPlanTable,List<MaterialPlanRef>)->T): SourceConstructionResultV4<T> = try {
        val prepared = PreparedStops.prepare(this)
        val boundRows = entries.map { row ->
            val source = row.first().source
            source.resolvedSource?.table?.entries() ?: run {
                val definition = PreparedSourceDefinitionV4.fromPrepared(this,source,prepared)
                val leaf = GradientInterpolationBindingV4.seal(definition)
                var table = MaterialPlanTable.of(listOf(MaterialPlanEntry(
                    GradientInterpolationProgramV4(definition.addressing,definition.domain),leaf,definition.slab)))
                requireNotNull(source.gradient).wrappers.forEach { wrapper ->
                    val child = MaterialPlanRef(table.sizeI32-1)
                    val program = table.entry(child).program
                    val parent = when (wrapper) {
                        is SourceUnaryMetadataV4.Opacity -> MaterialPlanEntry(MaterialProgramPlan.OpacityV1(program),
                            MaterialBindingPlan.OpacityF32V1.of(wrapper.alphaF32))
                        is SourceUnaryMetadataV4.Filter -> {
                            val proof = table.colorSourceProofV4(child)
                            val numeric = ColorNumericAuthorityV1.seal(wrapper.execution,proof)
                                ?: throw RawMaterialRequirementsV2.Refusal(W5fPlanDiagnostics.NumericDomainUnbounded)
                            MaterialPlanEntry(ColorFilteredProgramV4(program,wrapper.execution.structuralIdentity),
                                ColorFilterBindingV4.seal(wrapper.execution,proof,numeric))
                        }
                    }
                    table = MaterialPlanTable.of(table.entries()+parent)
                }
                table.entries()
            }
        }
        data class BoundEntry(val entry: MaterialPlanEntry,val actualDescriptor: MaterialInternerDescriptorV4)
        val bound = boundRows.mapIndexed { row,values ->
            require(values.size == entries[row].size) { W5fPlanDiagnostics.Schema }
            val source = entries[row].first().source
            val leaf = if (source.pending) values.first().bindings as? GradientInterpolationBindingV4 else null
            if (source.pending) require(leaf != null && leaf.definition.captured === source &&
                leaf.definition.frameOwner === this && leaf.sourceProof.preparedDefinition === leaf.definition &&
                leaf.authenticates(values.first().program as GradientInterpolationProgramV4,values.first().stopSlab)) {
                W5fPlanDiagnostics.Schema
            }
            values.mapIndexed { index,value ->
                val descriptor = if (!source.pending) value.internerDescriptorV4() else {
                    val unary = when (val wrapper = requireNotNull(source.gradient).wrappers.getOrNull(index-1)) {
                        null -> { require(index == 0 && value.bindings === leaf) { W5fPlanDiagnostics.Schema }; false }
                        is SourceUnaryMetadataV4.Opacity -> {
                            require(value.program is MaterialProgramPlan.OpacityV1 &&
                                (value.bindings as? MaterialBindingPlan.OpacityF32V1)?.alphaF32?.toRawBits() == wrapper.alphaF32.toRawBits()) {
                                W5fPlanDiagnostics.Schema
                            }; true
                        }
                        is SourceUnaryMetadataV4.Filter -> {
                            require(value.program is ColorFilteredProgramV4 &&
                                (value.bindings as? ColorFilterBindingV4)?.execution === wrapper.execution) { W5fPlanDiagnostics.Schema }; true
                        }
                    }
                    MaterialInternerDescriptorV4("pending-source-entry-v4:${requireNotNull(leaf).definition.allocationIdentity}:$index",unary)
                }
                BoundEntry(value,descriptor)
            }
        }
        // Bind the already-recorded placements/root maps, including copied unary
        // chains. A second structural interning pass cannot drop/add an entry.
        val selected = interner.bind(bound) { it.actualDescriptor }
        val table = MaterialPlanTable.of(selected.map { it.entry })
        require(table.sizeI32 == interner.sizeI32 && table.gradientStopSlab?.canonicalIdentity == prepared.slab?.canonicalIdentity &&
            (table.gradientStopSlab?.byteSizeI64 ?: 0L) == stopBytesI64) { W5fPlanDiagnostics.Schema }
        val roots = interner.laneRemaps().mapIndexed { index,refs ->
            refs[entries[index].lastIndex]
        }
        val actualLegacy = mutableListOf<RawMaterialRequirementsV2.LegacyLayout>()
        val actualV4 = linkedMapOf<String,MaterialSourceFootprintV4>()
        val pendingPhysical = linkedMapOf<String,String>()
        entries.map { it.first().source }.forEachIndexed { index,source ->
            val root = roots[index]
            if (source.pending || source.resolvedSource?.materialAuthority is PlanDrawMaterialAuthority.MaterialV4) {
                val footprint = RawMaterialRequirementsV2.measureV4(table,root)
                require(footprint.proof.authenticates(table,root,source.coordinates)) { W5fPlanDiagnostics.Schema }
                actualV4[footprint.canonicalIdentity] = footprint
                if (source.pending) {
                    var leafIndex = root.indexI32
                    while (table.entry(MaterialPlanRef(leafIndex)).bindings.let {
                        it is MaterialBindingPlan.OpacityF32V1 || it is ColorFilterBindingV4 }) leafIndex--
                    val actualLeaf = table.entry(MaterialPlanRef(leafIndex)).bindings as? GradientInterpolationBindingV4
                    require(actualLeaf != null && actualLeaf.definition.frameOwner === this &&
                        actualLeaf.definition.allocationIdentity == source.canonicalIdentity &&
                        actualLeaf.sourceProof.preparedDefinition === actualLeaf.definition) { W5fPlanDiagnostics.Schema }
                    val previous = pendingPhysical.putIfAbsent(source.canonicalIdentity,footprint.canonicalIdentity)
                    require(previous == null || previous == footprint.canonicalIdentity) { W5fPlanDiagnostics.Schema }
                }
            } else {
                val actual = RawMaterialRequirementsV2.measureLegacy(table,root)
                require(legacyAllocations.any { it.authenticatesFinal(actual,table.gradientStopSlab) }) { W5fPlanDiagnostics.Schema }
                if (actualLegacy.none { it.canonicalIdentity == actual.canonicalIdentity }) actualLegacy += actual
            }
        }
        require(pendingPhysical.values.distinct().size == pendingPhysical.size &&
            actualLegacy.size == legacyAllocations.size && legacyAllocations.all { planned ->
                actualLegacy.any { planned.authenticatesFinal(it,table.gradientStopSlab) }
            }) { W5fPlanDiagnostics.Schema }
        val actualUniformBytes = actualLegacy.fold(0L) { bytes,value -> Math.addExact(bytes,value.uniformByteCountI64) }
        require(actualV4.values.fold(actualUniformBytes) { bytes,value ->
            Math.addExact(bytes,value.uniformByteCountI64) } == uniformBytesI64) { W5fPlanDiagnostics.Schema }
        SourceConstructionResultV4.Built(finish(table,roots))
    } catch (failure: RawMaterialRequirementsV2.Refusal) {
        sourceConstructionRefusalV4(failure.code)
    } catch (failure: IllegalArgumentException) {
        sourceConstructionRefusalV4(failure.message ?: W5fPlanDiagnostics.Schema)
    } catch (_: ArithmeticException) {
        sourceConstructionRefusalV4(W5cPlanDiagnostics.StopBudget)
    }

    private fun constructBound(table: MaterialPlanTable,roots: List<MaterialPlanRef>): RenderGraphConstruction {
        val passes = remapSourcePassesV4(lane.passes()) { symbolic -> roots[symbolic.indexI32] }
        var constructed = RenderGraph.construct(lane.id,lane.capabilityId,lane.targetExtent,lane.colorFormat,
            lane.capabilities,lane.budget,lane.visualCommandCount,lane.resources(),passes,lane.dependencies(),
            lane.peakFrameLocalBytesI64,table)
        val nativeWitnessLanes = nativeGeometry?.lanes?.map { metadata ->
            val deferred = nativeLanes[metadata.ordinalI32]
            GeometryLaneConstruction(constructLaneBound(deferred.geometrySource ?: deferred,nativeOffsetsI32[metadata.ordinalI32],table,roots),
                metadata.commandsI32,metadata.data,metadata.depth)
        }.orEmpty()
        if (lane.topology == DeferredLaneTopologyV4.GeneralGeometryAndColor) {
            val source = constructLaneBound(requireNotNull(lane.geometrySource),0,table,roots)
            val commands = lane.geometryCommandsI32()
            val data = lane.drawDataByCommandI32().values.distinct().single()
            val depth = lane.depthStencilByCommandI32().values.distinct().singleOrNull()
            constructed = RenderGraph.issueW5bGeometry(constructed,listOf(GeometryLaneConstruction(source,commands,data,depth)))
        } else if (lane.topology == DeferredLaneTopologyV4.GeometryBridge)
            constructed = RenderGraph.issueW5bGeometry(constructed,nativeWitnessLanes)
        else if (lane.capabilityId == W4dGeneralPathPlanCompiler.W5A_HARD_CAPABILITY_ID)
            constructed = RenderGraph.issueW4dGeneralCompilerWitness(constructed)
        return constructed
    }

    private fun constructLaneBound(source: SourceDeferredRenderConstructionV4,offset: Int,
        table: MaterialPlanTable,roots: List<MaterialPlanRef>): RenderGraphConstruction {
        var geometry = RenderGraph.construct(source.id,source.capabilityId,source.targetExtent,
            source.colorFormat,source.capabilities,source.budget,source.visualCommandCount,
            source.resources(),remapSourcePassesV4(source.passes()) { roots[offset+it.indexI32] },
            source.dependencies(),source.peakFrameLocalBytesI64,table)
        if (source.capabilityId == W4dGeneralPathPlanCompiler.W5A_HARD_CAPABILITY_ID)
            geometry = RenderGraph.issueW4dGeneralCompilerWitness(geometry)
        if (source.capabilityId == W4dPathStrokePlanCompiler.CAPABILITY_ID)
            geometry = RenderGraph.issueW4dCompilerWitness(geometry)
        if (source.topology == DeferredLaneTopologyV4.GeometryBridge) geometry = RenderGraph.issueW5bGeometry(geometry)
        return geometry
    }

    internal class SourceEntry(val source: MaterialSourceConstructionV4, val oldEntry: MaterialPlanEntry?,
        val wrapperOrdinalI32: Int, val descriptor: MaterialInternerDescriptorV4)

    private sealed interface RangeValues {
        val countI32: Int
        fun same(other: RangeValues): Boolean
        class Legacy(stops: List<GradientStopPlanV1>) : RangeValues {
            val stops = immutableList(stops)
            override val countI32: Int get() = stops.size
            override fun same(other: RangeValues): Boolean = when (other) {
                is Legacy -> stops == other.stops
                is Pending -> other.sameLegacy(this)
            }
        }
        class Pending(val metadata: MaterialSourceConstructionV4.GradientMetadata) : RangeValues {
            override val countI32: Int get() = metadata.stops.countI32
            override fun same(other: RangeValues): Boolean = when (other) {
                is Pending -> metadata.interpolation == other.metadata.interpolation &&
                    metadata.recipeIdentity == other.metadata.recipeIdentity && metadata.stops.sameSequence(other.metadata.stops)
                is Legacy -> sameLegacy(other)
            }

            // A selected SRGB wrapper uses the historical no-conversion tuple.
            // Match that SAME eventual range before budgeting, in either first-use
            // order; no prepared tuple is allocated during this metadata scan.
            fun sameLegacy(other: Legacy): Boolean {
                if (metadata.interpolation != ColorInterpolation.SRGB || metadata.recipeIdentity != null ||
                    countI32 != other.countI32) return false
                val pending = metadata.stops.values().iterator()
                return other.stops.all { legacy ->
                    val stop = pending.next()
                    val color = stop.color
                    fun sameColor(value: ColorF32): Boolean =
                        value.red.toRawBits() == color.redNormalized.toRawBits() &&
                        value.green.toRawBits() == color.greenNormalized.toRawBits() &&
                        value.blue.toRawBits() == color.blueNormalized.toRawBits() &&
                        value.alpha.toRawBits() == color.alphaNormalized.toRawBits()
                    legacy.domain == ColorInterpolation.SRGB && legacy.preparationRecipeIdentity == null &&
                        legacy.positionF32.toRawBits() == stop.positionF32.toRawBits() &&
                        sameColor(legacy.straightSrgbF32) && sameColor(legacy.preparedTupleF32)
                } && !pending.hasNext()
            }
        }
    }
    private class RangeAllocation(val range: GradientStopRangeV1,val values: RangeValues)

    /** Only this factory can create prepared frame data; it accepts no caller tuple. */
    internal class PreparedStops private constructor(val owner: FrameSourceLayoutV4,val slab: GradientStopSlabPlanV1?) {
        companion object {
            fun prepare(owner: FrameSourceLayoutV4): PreparedStops {
                val values = ArrayList<GradientStopPlanV1>(Math.toIntExact(owner.stopBytesI64/32L))
                owner.ranges.forEach { allocation ->
                    require(values.size.toUInt() == allocation.range.baseIndexU32) { W5fPlanDiagnostics.Schema }
                    when (val source = allocation.values) {
                        is RangeValues.Legacy -> values.addAll(source.stops)
                        is RangeValues.Pending -> for (stop in source.metadata.stops.values()) {
                            val c = stop.color
                            val original = ColorF32.of(c.redNormalized,c.greenNormalized,c.blueNormalized,c.alphaNormalized)
                            val rgb = listOf(original.red,original.green,original.blue)
                            val domain = source.metadata.interpolation
                            if (domain == ColorInterpolation.SRGB) {
                                values += GradientStopPlanV1(stop.positionF32,original)
                                continue
                            }
                            val prepared = if (domain == ColorInterpolation.HSL || domain == ColorInterpolation.OKLCH)
                                ColorInterpolationProgramV1.evaluateHostF32(if (domain == ColorInterpolation.HSL)
                                    ColorInterpolationProgramV1.RecipeKind.SRGB_TO_HSL_STOP else ColorInterpolationProgramV1.RecipeKind.SRGB_TO_OKLCH_STOP,
                                    rgb,stop.color)
                            else {
                                val linear = rgb.map {
                                    ColorInterpolationProgramV1.evaluateHostF32(ColorInterpolationProgramV1.RecipeKind.EOTF,listOf(it)).single()
                                }
                                if (domain == ColorInterpolation.OKLAB)
                                    ColorInterpolationProgramV1.evaluateHostF32(ColorInterpolationProgramV1.RecipeKind.LINEAR_RGB_TO_OKLAB,linear)
                                else linear
                            }
                            values += GradientStopPlanV1.prepared(stop.positionF32,original,source.metadata.interpolation,
                                ColorF32.of(prepared[0],prepared[1],prepared[2],original.alpha),requireNotNull(source.metadata.recipeIdentity))
                        }
                    }
                    require(values.size.toLong() == allocation.range.baseIndexU32.toLong()+allocation.range.countU32.toLong()) {
                        W5fPlanDiagnostics.Schema
                    }
                }
                require(values.size.toLong()*32L == owner.stopBytesI64) { W5fPlanDiagnostics.Schema }
                return PreparedStops(owner,values.takeIf { it.isNotEmpty() }?.let(GradientStopSlabPlanV1::of))
            }
        }
    }

    companion object {
        fun standalone(lane: SourceDeferredRenderConstructionV4): SourceConstructionResultV4<FrameSourceLayoutV4> =
            checked(lane,emptyList(),emptyList(),null)

        fun ordinaryComposite(lanes: List<SourceDeferredRenderConstructionV4>): SourceConstructionResultV4<FrameSourceLayoutV4> = try {
            require(lanes.size in 2..W5aCompositePlanCompiler.MAX_LANES_I32) { W5fPlanDiagnostics.Schema }
            val first = lanes.first()
            require(lanes.all { it.topology == DeferredLaneTopologyV4.Ordinary &&
                it.capabilityId in setOf(W3SolidRectPlanCompiler.W5A_CAPABILITY_ID,
                    W4bAnalyticRRectPlanCompiler.CAPABILITY_ID,W4cPathFillPlanCompiler.CAPABILITY_ID,
                    W4dPathStrokePlanCompiler.CAPABILITY_ID) && it.targetExtent == first.targetExtent &&
                it.capabilities == first.capabilities && it.budget == first.budget && it.colorFormat == first.colorFormat }) {
                W5fPlanDiagnostics.Schema
            }
            val commands = lanes.flatMap { RenderGraph.visualDraws(it.passes()).map { draw -> draw.commandIndex } }
            require(commands.zipWithNext().all { (a,b) -> a < b }) { W5fPlanDiagnostics.Schema }
            var count = 0
            val offsets = lanes.map { source -> count.also { count = Math.addExact(count,source.sourceTable().sources().size) } }
            val layout = ordinaryCompositeSourceLayoutV4(lanes.map {
                NativeGeometryInputV4(it.capabilityId,it.resources(),it.passes()) },first.capabilities)
            // The first lane remains a real lane, not a fabricated aggregate
            // RenderGraph. The checked owner carries every immutable lane and
            // its exact symbolic offset, and the shared historical inventory.
            checked(first,lanes,offsets,null,layout)
        } catch (failure: IllegalArgumentException) {
            sourceConstructionRefusalV4(failure.message ?: W5fPlanDiagnostics.Schema)
        } catch (_: ArithmeticException) { sourceConstructionRefusalV4(W5cPlanDiagnostics.StopBudget) }

        fun nativeComposite(lanes: List<SourceDeferredRenderConstructionV4>): SourceConstructionResultV4<FrameSourceLayoutV4> {
            try {
                require(lanes.size in 2..W5aCompositePlanCompiler.MAX_LANES_I32) { W5fPlanDiagnostics.Schema }
                val first = lanes.first()
                require(lanes.all { it.targetExtent == first.targetExtent && it.colorFormat == first.colorFormat &&
                    it.capabilities == first.capabilities && it.budget == first.budget }) { W5fPlanDiagnostics.Schema }
                val active = lanes.filter { it.visualCommandCount > 0 }
                val captures = mutableListOf<MaterialSourceConstructionV4>()
                val offsets = active.map { lane -> captures.size.also { captures += lane.sourceTable().sources() } }
                val sources = when (val result = MaterialSourceConstructionTableV4.of(captures)) {
                    is SourceConstructionResultV4.Built -> result.value
                    is SourceConstructionResultV4.Refused -> return result
                }
                val geometry = nativeCompositeGeometryLayoutV4(active.mapIndexed { ordinal,lane ->
                    NativeGeometryInputV4(lane.capabilityId,lane.resources(),remapSourcePassesV4(lane.passes()) {
                        MaterialPlanRef(Math.addExact(offsets[ordinal],it.indexI32)) })
                },first.capabilities)
                val topology = W5bDestinationGraphSealer.describeSources(W5bGeometryLanePlanV3.COMPOSITE_CAPABILITY_ID,
                    first.targetExtent,first.capabilities,first.budget,geometry.colors,
                    first.resources().single { it.role == PlanResourceRole.LogicalTarget }.byteSize,
                    first.resources().single { it.role == PlanResourceRole.ReadbackStaging }.byteSize,
                    (first.passes().last() as PlanPass.ReadbackPass).bytesPerRow,geometry.geometryResources,
                    drawDataByCommandI32=geometry.dataByCommand,depthStencilByCommandI32=geometry.depthByCommand)
                val identity = java.security.MessageDigest.getInstance("SHA-256").digest(
                    lanes.joinToString("|") { it.id.value }.encodeToByteArray()).joinToString("") { "%02x".format(it) }
                val envelope = when (val result = SourceDeferredRenderConstructionV4.of(PlanId("w5b.composite.$identity"),
                    W5bGeometryLanePlanV3.COMPOSITE_CAPABILITY_ID,first.targetExtent,topology.format,
                    first.capabilities,first.budget,geometry.colors.size,topology.resources,topology.passes,topology.dependencies,
                    sources,DeferredLaneTopologyV4.GeometryBridge,null,emptyList(),emptyMap(),emptyMap())) {
                    is SourceConstructionResultV4.Built -> result.value
                    is SourceConstructionResultV4.Refused -> return result
                }
                return checked(envelope,active,offsets,geometry)
            } catch (failure: IllegalArgumentException) {
                return sourceConstructionRefusalV4(failure.message ?: W5fPlanDiagnostics.Schema)
            } catch (_: ArithmeticException) { return sourceConstructionRefusalV4(W5cPlanDiagnostics.StopBudget) }
        }

        private fun checked(lane: SourceDeferredRenderConstructionV4,nativeLanes: List<SourceDeferredRenderConstructionV4>,
            nativeOffsetsI32: List<Int>,nativeGeometry: NativeCompositeGeometryLayoutV4?,
            ordinaryLayout: OrdinaryCompositeSourceLayoutV4? = null): SourceConstructionResultV4<FrameSourceLayoutV4> = try {
            require(lane.resources().none { it.role == PlanResourceRole.GradientStopData }) { W5fPlanDiagnostics.Schema }
            val sources = if (ordinaryLayout == null) lane.sourceTable().sources()
                else nativeLanes.flatMap { it.sourceTable().sources() }
            require(sources.isNotEmpty() && sources.any { it.pending }) { W5fPlanDiagnostics.Schema }
            val rows = sources.map { source ->
                source.resolvedSource?.table?.entries()?.map { SourceEntry(source,it,-1,it.internerDescriptorV4()) }
                    ?: List(requireNotNull(source.gradient).wrappers.size+1) { index ->
                        SourceEntry(source,null,index-1,MaterialInternerDescriptorV4(
                            "pending-source-entry-v4:${source.canonicalIdentity}:$index",index != 0))
                    }
            }
            // This is the existing structural interner, including contiguous unary
            // copies and the exact2048 entry limit, before any pending conversion.
            val interner = MaterialTableInterningRecipeV4.of(rows.map { row -> row.map { it.descriptor } })
            val finalEntries = interner.bind(rows) { it.descriptor }
            val allocations = mutableListOf<RangeAllocation>()
            fun values(entry: SourceEntry): RangeValues? {
                if (entry.oldEntry == null) return if (entry.wrapperOrdinalI32 == -1 &&
                    requireNotNull(entry.source.gradient).stops.countI32 > 0) RangeValues.Pending(entry.source.gradient) else null
                val range = when (val binding = entry.oldEntry.bindings) {
                    is MaterialBindingPlan.GradientV1 -> binding.stopRange
                    is MaterialBindingPlan.GradientV2 -> binding.stopRange
                    else -> return null
                }
                val slab = requireNotNull(entry.oldEntry.stopSlab)
                require(slab.rangeHasDomain(range,ColorInterpolation.SRGB)) { W5fPlanDiagnostics.Schema }
                val stops = slab.copyStops()
                return RangeValues.Legacy(stops.subList(range.baseIndexU32.toInt(),
                    (range.baseIndexU32.toLong()+range.countU32.toLong()).toInt()))
            }
            var stopCountI64 = 0L
            for (entry in finalEntries) values(entry)?.let { current ->
                if (allocations.none { it.values.same(current) }) {
                    val next = Math.addExact(stopCountI64,current.countI32.toLong())
                    require(next <= UInt.MAX_VALUE.toLong() && Math.multiplyExact(next,32L) <= Int.MAX_VALUE.toLong()) {
                        W5cPlanDiagnostics.StopBudget
                    }
                    allocations += RangeAllocation(GradientStopRangeV1(stopCountI64.toUInt(),current.countI32.toUInt()),current)
                    stopCountI64 = next
                }
            }
            val pendingRanges = java.util.IdentityHashMap<MaterialSourceConstructionV4,GradientStopRangeV1>()
            val legacyRanges = java.util.IdentityHashMap<MaterialBindingPlan,GradientStopRangeV1>()
            rows.flatten().forEach { entry -> values(entry)?.let { current ->
                val range = allocations.single { it.values.same(current) }.range
                if (entry.oldEntry == null) pendingRanges[entry.source] = range
                else legacyRanges[entry.oldEntry.bindings] = range
            } }
            val slabOccurrence = Any() // Identity-scoped to this one final frame, never a proof or permit.
            val legacy = sources.mapNotNull { source -> source.resolvedSource?.takeUnless {
                it.materialAuthority is PlanDrawMaterialAuthority.MaterialV4
            }?.let { RawMaterialRequirementsV2.measureLegacy(it.table,it.root).relocated(legacyRanges,slabOccurrence) } }
                .fold(mutableListOf<RawMaterialRequirementsV2.RelocatedLegacyLayout>()) { unique,current ->
                    if (unique.none { it.sameAllocation(current) }) unique += current
                    unique
                }
            val retainedV4 = sources.mapNotNull { source -> source.resolvedSource?.takeIf {
                it.materialAuthority is PlanDrawMaterialAuthority.MaterialV4
            }?.let { RawMaterialRequirementsV2.measureV4(it.table,it.root) } }.distinctBy { it.canonicalIdentity }
            val pending = sources.filter { it.pending }.distinctBy { it.canonicalIdentity }
            val caps = lane.capabilities
            val budget = lane.budget
            var nonUniform = ordinaryLayout?.nonUniformWithoutStopsI64 ?: lane.peakFrameLocalBytesI64
            if (ordinaryLayout == null && lane.capabilityId == W3SolidRectPlanCompiler.W5A_CAPABILITY_ID) {
                val alignment = caps.minUniformBufferOffsetAlignment.toLong()
                require(alignment > 0L) { W5fPlanDiagnostics.FilterBinding }
                val stride = Math.addExact(32L,(alignment-32L%alignment)%alignment)
                listOf(PlanScratchBufferKind.Vertex to 32L,PlanScratchBufferKind.Index to 24L,
                    PlanScratchBufferKind.Uniform to stride).forEach { (kind,perDraw) ->
                    val bytes = requireNotNull(caps.bufferAllocationPolicy.reserve(kind,
                        Math.multiplyExact(lane.visualCommandCount.toLong(),perDraw))) { W5fPlanDiagnostics.FilterBinding }
                    require(bytes <= caps.maxBufferSizeBytes) { W5fPlanDiagnostics.FilterBinding }
                    nonUniform = Math.addExact(nonUniform,bytes)
                }
            }
            var total = nonUniform
            fun add(bytes: Long,code: String) {
                total = Math.addExact(total,bytes)
                require(total <= budget.maxFrameLocalBytes) { code }
            }
            legacy.sortedBy { it.hasCoordinatesV2 }.forEach {
                require(it.original.fitsUniformBinding(caps)) { if (it.hasCoordinatesV2)
                    W5dPlanDiagnostics.CoordinateUniformBudget else W5cPlanDiagnostics.StorageUnavailable }
                add(it.uniformByteCountI64,if (it.hasCoordinatesV2) W5dPlanDiagnostics.CoordinateUniformBudget else W5cPlanDiagnostics.StopBudget)
            }
            retainedV4.forEach { requireColorUniformBindingV4(it.uniformByteCountI64,caps)
                add(it.sourceUniformByteCountI64,"resource-limit.w5b.source-budget") }
            pending.forEach { source ->
                val bytes = GradientInterpolationUniformLayoutV4.uniformByteCountI64(source)
                requireColorUniformBindingV4(bytes,caps,if (source.hasGradientStorage) 2 else 1,
                    W5dPlanDiagnostics.CoordinateUniformBudget)
                add(GradientInterpolationUniformLayoutV4.sourceByteCountI64(source),W5dPlanDiagnostics.CoordinateUniformBudget)
            }
            val stopBytes = Math.multiplyExact(stopCountI64,32L)
            if (stopBytes > 0L) requireGradientStorageCapabilitiesV4(stopBytes,caps)
            add(stopBytes,W5cPlanDiagnostics.StopBudget)
            retainedV4.forEach { add(it.uniformByteCountI64-it.sourceUniformByteCountI64,W5fPlanDiagnostics.FilterUniform) }
            pending.forEach { add(GradientInterpolationUniformLayoutV4.uniformByteCountI64(it)-
                GradientInterpolationUniformLayoutV4.sourceByteCountI64(it),W5fPlanDiagnostics.FilterUniform) }
            SourceConstructionResultV4.Built(FrameSourceLayoutV4(lane,interner,rows,allocations,pendingRanges,legacyRanges,
                legacy,nonUniform,stopBytes,Math.subtractExact(Math.subtractExact(total,nonUniform),stopBytes),
                nativeLanes,nativeOffsetsI32,nativeGeometry,ordinaryLayout))
        } catch (failure: IllegalArgumentException) {
            sourceConstructionRefusalV4(failure.message ?: W5fPlanDiagnostics.Schema)
        } catch (_: ArithmeticException) {
            sourceConstructionRefusalV4(W5cPlanDiagnostics.StopBudget)
        }
    }
}
