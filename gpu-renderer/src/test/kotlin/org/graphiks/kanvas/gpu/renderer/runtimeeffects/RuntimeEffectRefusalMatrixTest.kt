package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RuntimeEffectRefusalMatrixTest {
    @Test
    fun runtimeEffectChildAndSourceShapesProduceRefuseRequiredRows() {
        val report = GPURuntimeEffectRefusalMatrix.evaluate(
            GPURuntimeEffectRefusalMatrixInput(
                registrySnapshot = registrySnapshot(),
                descriptorId = GPURuntimeEffectID("runtime.simple.color"),
                shapes = listOf(
                    GPURuntimeEffectRefusalShape.DynamicSource(
                        label = "dynamic-sksl",
                        sourceKind = GPURuntimeEffectDynamicSourceKind.SkSL,
                    ),
                    GPURuntimeEffectRefusalShape.DynamicSource(
                        label = "dynamic-wgsl",
                        sourceKind = GPURuntimeEffectDynamicSourceKind.WGSL,
                    ),
                    GPURuntimeEffectRefusalShape.CompatibilityKey(
                        label = "unknown-compat-key",
                        keyHash = "sha256:unknown",
                    ),
                    GPURuntimeEffectRefusalShape.ChildSlot(
                        label = "child-shader",
                        slot = GPURuntimeEffectChildSlotPlan(
                            slotName = "childShader",
                            acceptedSourceKinds = setOf("shader"),
                            required = true,
                        ),
                        childSourceKind = "shader",
                        sampleUsage = "same-pixel",
                    ),
                    GPURuntimeEffectRefusalShape.ChildSlot(
                        label = "child-missing",
                        slot = GPURuntimeEffectChildSlotPlan(
                            slotName = "requiredChild",
                            acceptedSourceKinds = setOf("shader"),
                            required = true,
                        ),
                        childSourceKind = "missing",
                        sampleUsage = "same-pixel",
                    ),
                    GPURuntimeEffectRefusalShape.ChildSlot(
                        label = "child-kind",
                        slot = GPURuntimeEffectChildSlotPlan(
                            slotName = "childColorFilter",
                            acceptedSourceKinds = setOf("shader"),
                            required = true,
                        ),
                        childSourceKind = "color-filter",
                        sampleUsage = "same-pixel",
                    ),
                    GPURuntimeEffectRefusalShape.ChildSlot(
                        label = "child-sample-radius",
                        slot = GPURuntimeEffectChildSlotPlan(
                            slotName = "childShader",
                            acceptedSourceKinds = setOf("shader"),
                            required = true,
                        ),
                        childSourceKind = "shader",
                        sampleUsage = "outside-main-radius:4",
                    ),
                    GPURuntimeEffectRefusalShape.UnsupportedPlacement(
                        label = "filter-placement",
                        requestedPlacement = GPURuntimeEffectRoutePlacement.FilterRenderNode,
                    ),
                    GPURuntimeEffectRefusalShape.UnsupportedPlacement(
                        label = "blender-placement",
                        requestedPlacement = GPURuntimeEffectRoutePlacement.MaterialBlender,
                    ),
                ),
            ),
        )

        assertEquals("gpu-renderer.runtime-effect-refusals", report.evidenceRow)
        assertEquals("RefuseRequired", report.classification)
        assertFalse(report.promotable)
        assertEquals(
            listOf(
                "unsupported.runtime_effect.dynamic_sksl_forbidden",
                "unsupported.runtime_effect.dynamic_wgsl_forbidden",
                "unsupported.runtime_effect.compatibility_key_unknown",
                "unsupported.runtime_effect.child_count",
                "unsupported.runtime_effect.child_missing",
                "unsupported.runtime_effect.child_kind",
                "unsupported.runtime_effect.child_sample_radius",
                "unsupported.runtime_effect.kind_mismatch",
                "unsupported.runtime_effect.kind_mismatch",
            ),
            report.rows.map { row -> row.diagnostic.code },
        )
        assertEquals(
            listOf(
                "runtime-effect-refusal row=gpu-renderer.runtime-effect-refusals category=source shape=dynamic-sksl descriptor=runtime.simple.color@1 routeKind=RefuseDiagnostic classification=RefuseRequired reason=unsupported.runtime_effect.dynamic_sksl_forbidden productActivation=false facts=descriptorMatches=1,sourceKind=SkSL",
                "runtime-effect-refusal row=gpu-renderer.runtime-effect-refusals category=source shape=dynamic-wgsl descriptor=runtime.simple.color@1 routeKind=RefuseDiagnostic classification=RefuseRequired reason=unsupported.runtime_effect.dynamic_wgsl_forbidden productActivation=false facts=descriptorMatches=1,sourceKind=WGSL",
                "runtime-effect-refusal row=gpu-renderer.runtime-effect-refusals category=source shape=unknown-compat-key descriptor=runtime.simple.color@1 routeKind=RefuseDiagnostic classification=RefuseRequired reason=unsupported.runtime_effect.compatibility_key_unknown productActivation=false facts=descriptorMatches=1,keyHash=sha256:unknown",
                "runtime-effect-refusal row=gpu-renderer.runtime-effect-refusals category=child shape=child-shader descriptor=runtime.simple.color@1 routeKind=RefuseDiagnostic classification=RefuseRequired reason=unsupported.runtime_effect.child_count productActivation=false facts=acceptedSourceKinds=shader,childSourceKind=shader,descriptorMatches=1,required=true,sampleUsage=same-pixel,slotName=childShader",
                "runtime-effect-refusal row=gpu-renderer.runtime-effect-refusals category=child shape=child-missing descriptor=runtime.simple.color@1 routeKind=RefuseDiagnostic classification=RefuseRequired reason=unsupported.runtime_effect.child_missing productActivation=false facts=acceptedSourceKinds=shader,childSourceKind=missing,descriptorMatches=1,required=true,sampleUsage=same-pixel,slotName=requiredChild",
                "runtime-effect-refusal row=gpu-renderer.runtime-effect-refusals category=child shape=child-kind descriptor=runtime.simple.color@1 routeKind=RefuseDiagnostic classification=RefuseRequired reason=unsupported.runtime_effect.child_kind productActivation=false facts=acceptedSourceKinds=shader,childSourceKind=color-filter,descriptorMatches=1,required=true,sampleUsage=same-pixel,slotName=childColorFilter",
                "runtime-effect-refusal row=gpu-renderer.runtime-effect-refusals category=child shape=child-sample-radius descriptor=runtime.simple.color@1 routeKind=RefuseDiagnostic classification=RefuseRequired reason=unsupported.runtime_effect.child_sample_radius productActivation=false facts=acceptedSourceKinds=shader,childSourceKind=shader,descriptorMatches=1,required=true,sampleUsage=outside-main-radius:4,slotName=childShader",
                "runtime-effect-refusal row=gpu-renderer.runtime-effect-refusals category=placement shape=filter-placement descriptor=runtime.simple.color@1 routeKind=RefuseDiagnostic classification=RefuseRequired reason=unsupported.runtime_effect.kind_mismatch productActivation=false facts=acceptedPlacements=MaterialSource,descriptorMatches=1,requestedPlacement=FilterRenderNode",
                "runtime-effect-refusal row=gpu-renderer.runtime-effect-refusals category=placement shape=blender-placement descriptor=runtime.simple.color@1 routeKind=RefuseDiagnostic classification=RefuseRequired reason=unsupported.runtime_effect.kind_mismatch productActivation=false facts=acceptedPlacements=MaterialSource,descriptorMatches=1,requestedPlacement=MaterialBlender",
                "runtime-effect-refusal:summary row=gpu-renderer.runtime-effect-refusals descriptor=runtime.simple.color@1 refused=9 promotable=false",
                "runtime-effect:nonclaim arbitrarySkSL=false arbitraryWGSL=false children=false unsupportedPlacementSupport=false productActivation=false",
            ),
            report.dumpLines(),
        )
    }

    @Test
    fun refusalMatrixDoesNotPromoteUnregisteredDescriptorAnchors() {
        val report = GPURuntimeEffectRefusalMatrix.evaluate(
            GPURuntimeEffectRefusalMatrixInput(
                registrySnapshot = registrySnapshot(descriptors = emptyList()),
                descriptorId = GPURuntimeEffectID("runtime.simple.color"),
                shapes = listOf(
                    GPURuntimeEffectRefusalShape.DynamicSource(
                        label = "dynamic-sksl",
                        sourceKind = GPURuntimeEffectDynamicSourceKind.SkSL,
                    ),
                ),
            ),
        )

        assertFalse(report.promotable)
        assertEquals("unsupported.runtime_effect.unregistered_descriptor", report.rows.single().diagnostic.code)
        assertEquals(
            listOf(
                "runtime-effect-refusal row=gpu-renderer.runtime-effect-refusals category=source shape=dynamic-sksl descriptor=runtime.simple.color@unregistered routeKind=RefuseDiagnostic classification=RefuseRequired reason=unsupported.runtime_effect.unregistered_descriptor productActivation=false facts=descriptorMatches=0,sourceKind=SkSL",
                "runtime-effect-refusal:summary row=gpu-renderer.runtime-effect-refusals descriptor=runtime.simple.color@unregistered refused=1 promotable=false",
                "runtime-effect:nonclaim arbitrarySkSL=false arbitraryWGSL=false children=false unsupportedPlacementSupport=false productActivation=false",
            ),
            report.dumpLines(),
        )
    }

    @Test
    fun refusalMatrixDoesNotPromoteDescriptorCollisions() {
        val report = GPURuntimeEffectRefusalMatrix.evaluate(
            GPURuntimeEffectRefusalMatrixInput(
                registrySnapshot = registrySnapshot(descriptors = listOf(runtimeDescriptor(), runtimeDescriptor())),
                descriptorId = GPURuntimeEffectID("runtime.simple.color"),
                shapes = listOf(
                    GPURuntimeEffectRefusalShape.DynamicSource(
                        label = "dynamic-sksl",
                        sourceKind = GPURuntimeEffectDynamicSourceKind.SkSL,
                    ),
                ),
            ),
        )

        assertFalse(report.promotable)
        assertEquals("unsupported.runtime_effect.descriptor_collision", report.rows.single().diagnostic.code)
        assertEquals(
            listOf(
                "runtime-effect-refusal row=gpu-renderer.runtime-effect-refusals category=source shape=dynamic-sksl descriptor=runtime.simple.color@collision routeKind=RefuseDiagnostic classification=RefuseRequired reason=unsupported.runtime_effect.descriptor_collision productActivation=false facts=descriptorMatches=2,sourceKind=SkSL",
                "runtime-effect-refusal:summary row=gpu-renderer.runtime-effect-refusals descriptor=runtime.simple.color@collision refused=1 promotable=false",
                "runtime-effect:nonclaim arbitrarySkSL=false arbitraryWGSL=false children=false unsupportedPlacementSupport=false productActivation=false",
            ),
            report.dumpLines(),
        )
    }
}

private fun registrySnapshot(
    descriptors: List<GPURuntimeEffectDescriptor> = listOf(runtimeDescriptor()),
): GPURuntimeEffectRegistrySnapshot =
    GPURuntimeEffectRegistrySnapshot(
        registryVersion = "runtime-registry-v1",
        generation = 17,
        descriptors = descriptors,
        provenance = "test-fixture",
    )

private fun runtimeDescriptor(): GPURuntimeEffectDescriptor =
    GPURuntimeEffectDescriptor(
        id = GPURuntimeEffectID("runtime.simple.color"),
        version = GPURuntimeEffectDescriptorVersion(1),
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
        routeContract = GPURuntimeEffectRouteContract(
            nativeSupported = true,
            cpuOracleOnly = false,
            acceptedPlacements = setOf(GPURuntimeEffectRoutePlacement.MaterialSource),
        ),
        liveEditPlan = GPURuntimeEffectLiveEditPlan(
            enabled = false,
            descriptorVersion = GPURuntimeEffectDescriptorVersion(1),
            validationPolicy = "static",
        ),
    )
