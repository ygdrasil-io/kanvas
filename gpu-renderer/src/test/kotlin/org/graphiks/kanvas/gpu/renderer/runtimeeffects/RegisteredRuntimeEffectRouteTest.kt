package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceDescriptor
import org.graphiks.kanvas.gpu.renderer.wgsl.REVIEWED_WGSL4K_REFLECTION_SHA
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLUniformFieldLayout
import org.graphiks.kanvas.gpu.renderer.wgsl.Wgsl4kBindingReflection
import org.graphiks.kanvas.gpu.renderer.wgsl.Wgsl4kEntryPointReflection
import org.graphiks.kanvas.gpu.renderer.wgsl.Wgsl4kLayoutMemberReflection
import org.graphiks.kanvas.gpu.renderer.wgsl.Wgsl4kLayoutReflection
import org.graphiks.kanvas.gpu.renderer.wgsl.Wgsl4kReflectionReport
import org.graphiks.kanvas.gpu.renderer.wgsl.Wgsl4kValidationSummary
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslExpectedBinding
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslExpectedEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslExpectedLayout
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslReflectionExpectation
import org.graphiks.kanvas.gpu.renderer.wgsl.consumeWgsl4kReflectionReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class RegisteredRuntimeEffectRouteTest {
    @Test
    fun registeredDescriptorProducesMaterialRouteEvidenceDump() {
        val result = GPURuntimeEffectDescriptorRoutePlanner().plan(routeRequest())

        assertEquals("gpu-renderer.runtime-effect.registered", result.evidenceRow)
        assertEquals("GPUNative", result.routeKind)
        assertEquals("DependencyGated", result.classification)
        assertFalse(result.promoted)
        assertFalse(result.productActivation)
        assertFalse(result.materialized)
        assertFalse(result.materialKeyIncludesUniformValues)
        assertEquals("accepted.runtime_effect.registered_descriptor", result.diagnostics.single().code)

        val route = assertIs<GPURuntimeEffectRoutePlan.Accepted>(result.routePlan)
        assertIs<GPUMaterialSourceDescriptor.RuntimeEffect>(route.materialSource)
        assertEquals(GPURuntimeEffectRoutePlacement.MaterialSource, route.lookupPlan.requestedPlacement)
        assertEquals("runtime.simple.color", route.lookupPlan.descriptorId?.value)
        assertEquals("sha256:runtime-simple", route.descriptor.wgslPlan.moduleHash)
        assertEquals(RUNTIME_ORACLE_EVIDENCE_HASH, route.cpuOracle.evidenceHash)

        assertEquals(
            listOf(
                "runtime-effect:registered row=gpu-renderer.runtime-effect.registered routeKind=GPUNative classification=DependencyGated promoted=false productActivation=false materialized=false descriptor=runtime.simple.color version=1 requestKind=MaterialSource route=MaterialSource",
                "runtime-effect:registry version=runtime-registry-v1 generation=17 descriptors=runtime.simple.color@1 provenance=test-fixture",
                "runtime-effect:uniform schema=sha256:schema-simple-color fields=color:vec4<f32>@0:16 packing=std140 blockBytes=16 dynamicOffsets=false",
                "runtime-effect:wgsl module=runtime.simple.color source=runtime/runtime_simple_rt.wgsl hash=sha256:runtime-simple entry=fs_main wgsl4k=72a35b58758f241756d984a84768ae77308730da comparison=accepted reflection=sha256:reflection-simple-color",
                "runtime-effect:oracle id=runtime.simple.color evidence=$RUNTIME_ORACLE_EVIDENCE_HASH diagnostics=none fallback=false",
                "runtime-effect:material snippet=runtime.simple.color@1 payload=${result.payloadPlanHash} materialKey=${result.materialKeyBoundaryHash} uniformValuesInKey=false route=registered-descriptor",
                "runtime-effect:diagnostic code=accepted.runtime_effect.registered_descriptor terminal=false",
                "runtime-effect:nonclaim nativeRuntimeEffect=false adapterBacked=false dynamicSkSL=false arbitraryWGSL=false children=false blender=false filter=false productActivation=false",
            ),
            result.dumpLines(),
        )
    }

    @Test
    fun unsupportedRuntimeEffectInputsRefuseWithStableDiagnostics() {
        val cases = listOf(
            refusalCase(
                label = "unregistered",
                effectId = GPURuntimeEffectID("runtime.unknown"),
                reason = "unsupported.runtime_effect.unregistered_descriptor",
            ),
            refusalCase(
                label = "descriptor-collision",
                registrySnapshot = registrySnapshot(
                    listOf(
                        runtimeDescriptor(),
                        runtimeDescriptor(version = GPURuntimeEffectDescriptorVersion(2)),
                    ),
                ),
                reason = "unsupported.runtime_effect.descriptor_collision",
            ),
            refusalCase(
                label = "dynamic-sksl",
                dynamicSkSLSourceProvided = true,
                reason = "unsupported.runtime_effect.dynamic_sksl_forbidden",
            ),
            refusalCase(
                label = "kind-mismatch",
                requestedPlacement = GPURuntimeEffectRoutePlacement.FilterRenderNode,
                reason = "unsupported.runtime_effect.kind_mismatch",
            ),
            refusalCase(
                label = "implicit-placement",
                registrySnapshot = registrySnapshot(
                    listOf(
                        runtimeDescriptor(
                            routeContract = GPURuntimeEffectRouteContract(
                                nativeSupported = true,
                                cpuOracleOnly = false,
                            ),
                        ),
                    ),
                ),
                reason = "unsupported.runtime_effect.kind_mismatch",
            ),
            refusalCase(
                label = "wgsl-uniform-mismatch",
                wgslEvidence = mismatchedRuntimeWgslUniformEvidence(),
                reason = "unsupported.runtime_effect.wgsl_reflection",
            ),
            refusalCase(
                label = "cpu-oracle-hash",
                cpuOracle = runtimeOracle(evidenceHash = "sha256:nothex"),
                reason = "unsupported.runtime_effect.cpu_oracle_missing",
            ),
            refusalCase(
                label = "wgsl-reflection",
                wgslEvidence = acceptedRuntimeWgslEvidence("rejected"),
                reason = "unsupported.runtime_effect.wgsl_reflection",
            ),
            refusalCase(
                label = "wgsl-descriptor-mismatch",
                wgslEvidence = mismatchedRuntimeWgslEvidence(),
                reason = "unsupported.runtime_effect.wgsl_reflection",
            ),
            refusalCase(
                label = "cpu-oracle",
                cpuOracle = null,
                reason = "unsupported.runtime_effect.cpu_oracle_missing",
            ),
        )

        cases.forEach { case ->
            val result = GPURuntimeEffectDescriptorRoutePlanner().plan(case.request)

            assertIs<GPURuntimeEffectRoutePlan.Refused>(result.routePlan)
            assertEquals(case.reason, result.diagnostics.single().code)
            assertEquals(
                listOf(
                    "runtime-effect:registered.refused row=gpu-renderer.runtime-effect.registered routeKind=RefuseDiagnostic classification=DependencyGated promoted=false productActivation=false materialized=false descriptor=${case.effectId.value} reason=${case.reason} label=${case.label}",
                    "runtime-effect:nonclaim nativeRuntimeEffect=false adapterBacked=false dynamicSkSL=false arbitraryWGSL=false children=false blender=false filter=false productActivation=false",
                ),
                result.dumpLines(),
            )
        }
    }
}

private data class RefusalCase(
    val label: String,
    val effectId: GPURuntimeEffectID,
    val request: GPURuntimeEffectDescriptorRouteRequest,
    val reason: String,
)

private fun refusalCase(
    label: String,
    effectId: GPURuntimeEffectID = GPURuntimeEffectID("runtime.simple.color"),
    requestedPlacement: GPURuntimeEffectRoutePlacement = GPURuntimeEffectRoutePlacement.MaterialSource,
    registrySnapshot: GPURuntimeEffectRegistrySnapshot = registrySnapshot(),
    dynamicSkSLSourceProvided: Boolean = false,
    wgslEvidence: GPURuntimeEffectWGSLEvidence = acceptedRuntimeWgslEvidence(),
    cpuOracle: GPURuntimeEffectOracleResult? = runtimeOracle(),
    reason: String,
): RefusalCase = RefusalCase(
    label = label,
    effectId = effectId,
    reason = reason,
    request = routeRequest(
        label = label,
        effectId = effectId,
        requestedPlacement = requestedPlacement,
        registrySnapshot = registrySnapshot,
        dynamicSkSLSourceProvided = dynamicSkSLSourceProvided,
        wgslEvidence = wgslEvidence,
        cpuOracle = cpuOracle,
    ),
)

private fun routeRequest(
    label: String = "accepted",
    effectId: GPURuntimeEffectID = GPURuntimeEffectID("runtime.simple.color"),
    requestedPlacement: GPURuntimeEffectRoutePlacement = GPURuntimeEffectRoutePlacement.MaterialSource,
    registrySnapshot: GPURuntimeEffectRegistrySnapshot = registrySnapshot(),
    dynamicSkSLSourceProvided: Boolean = false,
    wgslEvidence: GPURuntimeEffectWGSLEvidence = acceptedRuntimeWgslEvidence(),
    cpuOracle: GPURuntimeEffectOracleResult? = runtimeOracle(),
): GPURuntimeEffectDescriptorRouteRequest =
    GPURuntimeEffectDescriptorRouteRequest(
        label = label,
        effectId = effectId,
        requestedPlacement = requestedPlacement,
        registrySnapshot = registrySnapshot,
        wgslEvidence = wgslEvidence,
        cpuOracle = cpuOracle,
        dynamicSkSLSourceProvided = dynamicSkSLSourceProvided,
    )

private fun registrySnapshot(
    descriptors: List<GPURuntimeEffectDescriptor> = listOf(runtimeDescriptor()),
): GPURuntimeEffectRegistrySnapshot =
    GPURuntimeEffectRegistrySnapshot(
        registryVersion = "runtime-registry-v1",
        generation = 17,
        descriptors = descriptors,
        provenance = "test-fixture",
    )

private fun runtimeDescriptor(
    version: GPURuntimeEffectDescriptorVersion = GPURuntimeEffectDescriptorVersion(1),
    routeContract: GPURuntimeEffectRouteContract = GPURuntimeEffectRouteContract(
        nativeSupported = true,
        cpuOracleOnly = false,
        acceptedPlacements = setOf(GPURuntimeEffectRoutePlacement.MaterialSource),
    ),
): GPURuntimeEffectDescriptor =
    GPURuntimeEffectDescriptor(
        id = GPURuntimeEffectID("runtime.simple.color"),
        version = version,
        uniformSchema = GPURuntimeEffectUniformSchema(
            schemaHash = "sha256:schema-simple-color",
            fields = listOf("color:vec4<f32>@0:16"),
            packingPolicy = "std140",
        ),
        uniformBlockPlan = GPURuntimeEffectUniformBlockPlan(
            schema = GPURuntimeEffectUniformSchema(
                schemaHash = "sha256:schema-simple-color",
                fields = listOf("color:vec4<f32>@0:16"),
                packingPolicy = "std140",
            ),
            blockSizeBytes = 16,
            dynamicOffsets = false,
        ),
        childSlots = emptyList(),
        resources = GPURuntimeEffectResourcePlan(
            resourceLabels = listOf("group1.binding0.uniformBuffer"),
            bindingPlanHash = "sha256:binding-simple-color",
        ),
        wgslPlan = GPURuntimeEffectWGSLPlan(
            moduleHash = "sha256:runtime-simple",
            entryPoint = "fs_main",
            reflectionHash = "sha256:reflection-simple-color",
        ),
        routeContract = routeContract,
        liveEditPlan = GPURuntimeEffectLiveEditPlan(
            enabled = false,
            descriptorVersion = version,
            validationPolicy = "static",
        ),
    )

private const val RUNTIME_ORACLE_EVIDENCE_HASH =
    "sha256:4c284b52db68f20a6f9f30998de4d063d99940dc3aa65b6e5126f8c49f49d90d"

private fun runtimeOracle(evidenceHash: String = RUNTIME_ORACLE_EVIDENCE_HASH): GPURuntimeEffectOracleResult =
    GPURuntimeEffectOracleResult(
        effectId = GPURuntimeEffectID("runtime.simple.color"),
        evidenceHash = evidenceHash,
    )

private fun acceptedRuntimeWgslEvidence(status: String = "accepted"): GPURuntimeEffectWGSLEvidence =
    GPURuntimeEffectWGSLEvidence(
        report = consumeWgsl4kReflectionReport(
            report = Wgsl4kReflectionReport(
                sourceId = "runtime/runtime_simple_rt.wgsl",
                moduleHash = "sha256:runtime-simple",
                wgsl4kSha = REVIEWED_WGSL4K_REFLECTION_SHA,
                validation = Wgsl4kValidationSummary(success = true),
                entryPoints = listOf(Wgsl4kEntryPointReflection("fs_main", "fragment")),
                bindings = listOf(
                    Wgsl4kBindingReflection(1, 0, "runtimeUniforms", "uniformBuffer", minBindingSize = 16),
                ),
                layouts = listOf(
                    Wgsl4kLayoutReflection(
                        structName = "RuntimeSimpleUniforms",
                        addressSpace = "uniform",
                        size = 16,
                        alignment = 16,
                        members = listOf(
                            Wgsl4kLayoutMemberReflection("color", "vec4<f32>", offset = 0, size = 16, alignment = 16, stride = null),
                        ),
                    ),
                ),
            ),
            expectation = WgslReflectionExpectation(
                reportKind = "runtime-effect",
                moduleId = "runtime.simple.color",
                allowedSourceIds = setOf("runtime/runtime_simple_rt.wgsl"),
                expectedEntryPoints = listOf(WgslExpectedEntryPoint("fs_main", "fragment")),
                expectedBindings = listOf(WgslExpectedBinding(1, 0, "runtimeUniforms", "uniformBuffer", minBindingSize = 16)),
                expectedLayouts = listOf(
                    WgslExpectedLayout(
                        structName = "RuntimeSimpleUniforms",
                        addressSpace = "uniform",
                        size = 16,
                        alignment = 16,
                        members = listOf(
                            WGSLUniformFieldLayout("color", "vec4<f32>", offset = 0L, sizeBytes = 16L, alignment = 16),
                        ),
                    ),
                ),
                descriptorId = "runtime.simple.color",
                descriptorVersion = 1,
                routePromotion = "not-promoted",
                productActivation = false,
            ),
        ).let { report ->
            if (status == "accepted") {
                report
            } else {
                report.copy(comparison = report.comparison.copy(status = status))
            }
        },
    )

private fun mismatchedRuntimeWgslEvidence(): GPURuntimeEffectWGSLEvidence {
    val accepted = acceptedRuntimeWgslEvidence()
    return accepted.copy(
        report = accepted.report.copy(
            moduleId = "runtime.other.color",
            descriptorId = "runtime.other.color",
        ),
    )
}

private fun mismatchedRuntimeWgslUniformEvidence(): GPURuntimeEffectWGSLEvidence {
    val accepted = acceptedRuntimeWgslEvidence()
    return accepted.copy(
        report = accepted.report.copy(
            layouts = accepted.report.layouts.map { layout ->
                layout.copy(
                    members = layout.members.map { member ->
                        if (member.name == "color") {
                            member.copy(size = 12)
                        } else {
                            member
                        }
                    },
                )
            },
        ),
    )
}
