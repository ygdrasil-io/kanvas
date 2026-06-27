package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies Kanvas-side consumption of reviewed wgsl4k reflection reports. */
class Wgsl4kReflectionReportConsumerTest {
    @Test
    fun `text report records reviewed wgsl4k sha and accepted comparison without route promotion`() {
        val report = consumeWgsl4kReflectionReport(
            textWgsl4kReport(),
            textExpectation(),
        )

        assertEquals("accepted", report.comparison.status)
        assertEquals(WGSL4K_MERGE_SHA, report.wgsl4kSha)
        assertEquals("not-promoted", report.routePromotion)
        assertTrue(report.productActivation)
        assertEquals(emptyList(), report.diagnostics)

        val json = report.toDeterministicJson()
        assertContains(json, """"reportKind":"text"""")
        assertContains(json, """"moduleId":"text.a8-mask"""")
        assertContains(json, """"wgsl4kSha":"$WGSL4K_MERGE_SHA"""")
        assertContains(json, """"moduleHash":"sha256:text-a8-mask"""")
        assertContains(json, """"sourceId":"text/a8_text_mask.wgsl"""")
        assertContains(json, """"name":"fragmentMain"""")
        assertContains(json, """"name":"glyphAtlas"""")
        assertContains(json, """"structName":"TextParams"""")
        assertContains(json, """"routePromotion":"not-promoted"""")
    }

    @Test
    fun `runtime effect report records descriptor metadata and accepted layout comparison`() {
        val report = consumeWgsl4kReflectionReport(
            runtimeEffectWgsl4kReport(),
            runtimeEffectExpectation(),
        )

        assertEquals("accepted", report.comparison.status)
        assertEquals("runtime-effect", report.reportKind)
        assertEquals("runtime.simple.color", report.descriptorId)
        assertEquals(1, report.descriptorVersion)
        assertEquals(emptyList(), report.diagnostics)

        val json = report.toDeterministicJson()
        assertContains(json, """"descriptorId":"runtime.simple.color"""")
        assertContains(json, """"descriptorVersion":1""")
        assertContains(json, """"structName":"RuntimeSimpleUniforms"""")
        assertContains(json, """"name":"color"""")
    }

    @Test
    fun `consumer emits stable refusal diagnostics for parser binding layout and registration failures`() {
        val syntaxFailure = consumeWgsl4kReflectionReport(
            textWgsl4kReport().copy(
                validation = Wgsl4kValidationSummary(
                    success = false,
                    diagnostics = listOf(Wgsl4kDiagnostic("wgsl4k.validation.syntax_error", "expected ';'")),
                ),
            ),
            textExpectation(),
        )
        assertContains(syntaxFailure.diagnostics.map { it.code }, "wgsl4k.validation.syntax_error")
        assertEquals("rejected", syntaxFailure.comparison.status)

        val bindingMismatch = consumeWgsl4kReflectionReport(
            textWgsl4kReport().copy(bindings = textWgsl4kReport().bindings.drop(1)),
            textExpectation(),
        )
        assertContains(bindingMismatch.diagnostics.map { it.code }, "unsupported.wgsl.binding_reflection_mismatch")
        assertEquals("rejected", bindingMismatch.comparison.status)

        val layoutMismatch = consumeWgsl4kReflectionReport(
            textWgsl4kReport().copy(
                layouts = listOf(
                    textWgsl4kReport().layouts.single().copy(
                        members = listOf(
                            Wgsl4kLayoutMemberReflection(
                                name = "atlasScale",
                                type = "vec2<f32>",
                                offset = 4,
                                size = 8,
                                alignment = 8,
                                stride = null,
                            ),
                        ),
                    ),
                ),
            ),
            textExpectation(),
        )
        assertContains(layoutMismatch.diagnostics.map { it.code }, "unsupported.wgsl.uniform_layout_mismatch")
        assertEquals("rejected", layoutMismatch.comparison.status)

        val unregistered = consumeWgsl4kReflectionReport(
            textWgsl4kReport().copy(sourceId = "text/unregistered.wgsl"),
            textExpectation(),
        )
        assertContains(unregistered.diagnostics.map { it.code }, "unsupported.wgsl.unregistered_module")
        assertEquals("rejected", unregistered.comparison.status)
    }

    @Test
    fun `default evolution fixtures cover text runtime effect and negative report cases`() {
        val fixtures = wgsl4kEvolutionReportFixtures(WGSL4K_MERGE_SHA)

        assertEquals(
            listOf(
                "negative/binding-mismatch.json",
                "negative/layout-mismatch.json",
                "negative/parser-failure.json",
                "negative/unregistered-module.json",
                "runtime-effect-wgsl-reflection.json",
                "runtime-effect-wgsl-validation-report.json",
                "text-wgsl-reflection.json",
                "text-wgsl-validation-report.json",
            ),
            fixtures.map { it.relativePath },
        )
        fixtures.forEach { fixture ->
            assertContains(fixture.contents, """"wgsl4kSha":"$WGSL4K_MERGE_SHA"""")
            assertContains(fixture.contents, """"routePromotion":"not-promoted"""")
        }
        assertContains(fixtures.single { it.relativePath == "negative/parser-failure.json" }.contents, "wgsl4k.validation.syntax_error")
        assertContains(fixtures.single { it.relativePath == "negative/binding-mismatch.json" }.contents, "unsupported.wgsl.binding_reflection_mismatch")
        assertContains(fixtures.single { it.relativePath == "negative/layout-mismatch.json" }.contents, "unsupported.wgsl.uniform_layout_mismatch")
        assertContains(fixtures.single { it.relativePath == "negative/unregistered-module.json" }.contents, "unsupported.wgsl.unregistered_module")
        assertContains(fixtures.single { it.relativePath == "runtime-effect-wgsl-validation-report.json" }.contents, """"descriptorId":"runtime.simple.color"""")
    }

    private fun textExpectation(): WgslReflectionExpectation =
        WgslReflectionExpectation(
            reportKind = "text",
            moduleId = "text.a8-mask",
            allowedSourceIds = setOf("text/a8_text_mask.wgsl"),
            expectedEntryPoints = listOf(WgslExpectedEntryPoint("fragmentMain", "fragment")),
            expectedBindings = listOf(
                WgslExpectedBinding(2, 0, "glyphAtlas", "sampledTexture", minBindingSize = null),
                WgslExpectedBinding(2, 1, "glyphSampler", "sampler", minBindingSize = null),
                WgslExpectedBinding(2, 2, "textParams", "uniformBuffer", minBindingSize = 16),
            ),
            expectedLayouts = listOf(
                WgslExpectedLayout(
                    structName = "TextParams",
                    addressSpace = "uniform",
                    size = 16,
                    alignment = 8,
                    members = listOf(
                        WGSLUniformFieldLayout("atlasScale", "vec2<f32>", offset = 0L, sizeBytes = 8L, alignment = 8),
                        WGSLUniformFieldLayout("maskGamma", "f32", offset = 8L, sizeBytes = 4L, alignment = 4),
                    ),
                ),
            ),
            routePromotion = "not-promoted",
            productActivation = true,
        )

    private fun runtimeEffectExpectation(): WgslReflectionExpectation =
        WgslReflectionExpectation(
            reportKind = "runtime-effect",
            moduleId = "runtime.simple.color",
            allowedSourceIds = setOf("runtime/runtime_simple_rt.wgsl"),
            expectedEntryPoints = listOf(WgslExpectedEntryPoint("fs_main", "fragment")),
            expectedBindings = listOf(
                WgslExpectedBinding(1, 0, "runtimeUniforms", "uniformBuffer", minBindingSize = 16),
            ),
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
            productActivation = true,
        )

    private fun textWgsl4kReport(): Wgsl4kReflectionReport =
        Wgsl4kReflectionReport(
            sourceId = "text/a8_text_mask.wgsl",
            moduleHash = "sha256:text-a8-mask",
            wgsl4kSha = WGSL4K_MERGE_SHA,
            validation = Wgsl4kValidationSummary(success = true),
            entryPoints = listOf(Wgsl4kEntryPointReflection("fragmentMain", "fragment")),
            bindings = listOf(
                Wgsl4kBindingReflection(2, 0, "glyphAtlas", "sampledTexture", minBindingSize = null),
                Wgsl4kBindingReflection(2, 1, "glyphSampler", "sampler", minBindingSize = null),
                Wgsl4kBindingReflection(2, 2, "textParams", "uniformBuffer", minBindingSize = 16),
            ),
            layouts = listOf(
                Wgsl4kLayoutReflection(
                    structName = "TextParams",
                    addressSpace = "uniform",
                    size = 16,
                    alignment = 8,
                    members = listOf(
                        Wgsl4kLayoutMemberReflection("atlasScale", "vec2<f32>", offset = 0, size = 8, alignment = 8, stride = null),
                        Wgsl4kLayoutMemberReflection("maskGamma", "f32", offset = 8, size = 4, alignment = 4, stride = null),
                    ),
                ),
            ),
        )

    private fun runtimeEffectWgsl4kReport(): Wgsl4kReflectionReport =
        Wgsl4kReflectionReport(
            sourceId = "runtime/runtime_simple_rt.wgsl",
            moduleHash = "sha256:runtime-simple",
            wgsl4kSha = WGSL4K_MERGE_SHA,
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
        )

    private companion object {
        const val WGSL4K_MERGE_SHA = "72a35b58758f241756d984a84768ae77308730da"
    }
}
