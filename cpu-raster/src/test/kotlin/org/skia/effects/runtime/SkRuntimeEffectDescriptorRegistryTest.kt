package org.skia.effects.runtime

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsChildren
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsSimple
import java.nio.file.Files
import java.nio.file.Path

class SkRuntimeEffectDescriptorRegistryTest {
    @AfterEach
    fun cleanup() {
        SkRuntimeEffectDispatch.clearForTest()
    }

    @Test
    fun `support matrix entries are sorted by stable id then canonical hash`() {
        SkRuntimeEffectDescriptorRegistry.register("half4 main(vec2 p) { return vec4(0); }", descriptor("runtime.z"))
        SkRuntimeEffectDescriptorRegistry.register("half4 main(vec2 p) { return vec4(1); }", descriptor("runtime.a"))

        val entries = SkRuntimeEffectDescriptorRegistry.supportMatrixEntries()

        assertEquals(listOf("runtime.a", "runtime.z"), entries.map { it.stableId })
    }

    @Test
    fun `support matrix contains SimpleRT CPU and GPU status`() {
        SkBuiltinShaderEffectsSimple.registerAll()

        val entry = SkRuntimeEffectDescriptorRegistry.supportMatrixEntries()
            .single { it.stableId == "runtime.simple_rt" }

        assertEquals(3617365546103039931L, entry.canonicalHash)
        assertEquals("runtime.simple_rt", entry.stableId)
        assertEquals("descriptor-backed", entry.descriptorStatus)
        assertEquals("supported:kotlin/simple_rt", entry.cpuSupport)
        assertEquals("supported:wgsl/runtime_simple_rt", entry.gpuSupport)
        assertEquals("none", entry.missingReason)
    }

    @Test
    fun `support matrix reports descriptor-backed builtin runtime effects`() {
        SkBuiltinShaderEffectsSimple.registerAll()

        val entries = SkRuntimeEffectDescriptorRegistry.supportMatrixEntries()
        val counts = SkRuntimeEffectDescriptorRegistry.supportMatrixStatusCounts()

        assertEquals(
            listOf("runtime.linear_gradient_rt", "runtime.simple_rt", "runtime.spiral_rt"),
            entries.map { it.stableId },
        )
        assertEquals(
            SkRuntimeEffectSupportMatrixStatusCounts(
                total = 3,
                descriptorBacked = 3,
                dispatchOnlyMissingDescriptor = 0,
                cpuOnly = 0,
                gpuBacked = 3,
            ),
            counts,
        )
        val linearGradient = entries.single { it.stableId == "runtime.linear_gradient_rt" }
        assertEquals("descriptor-backed", linearGradient.descriptorStatus)
        assertEquals("supported:kotlin/linear_gradient_rt", linearGradient.cpuSupport)
        assertEquals("supported:wgsl/runtime_linear_gradient_rt", linearGradient.gpuSupport)
        assertEquals("none", linearGradient.missingReason)

        val spiral = entries.single { it.stableId == "runtime.spiral_rt" }
        assertEquals("descriptor-backed", spiral.descriptorStatus)
        assertEquals("supported:kotlin/spiral_rt", spiral.cpuSupport)
        assertEquals("supported:wgsl/runtime_spiral_rt", spiral.gpuSupport)
        assertEquals("none", spiral.missingReason)
        assertTrue(spiral.uniforms.map { it.name }.containsAll(listOf("rad_scale", "in_center", "in_colors0", "in_colors1")))
    }

    @Test
    fun `support matrix rows always include descriptor status and missing reason`() {
        SkBuiltinShaderEffectsSimple.registerAll()

        val entries = SkRuntimeEffectDescriptorRegistry.supportMatrixEntries()
        val markdown = SkRuntimeEffectDescriptorRegistry.exportSupportMatrixMarkdown()

        entries.forEach { entry ->
            assertTrue(entry.descriptorStatus.isNotBlank(), "descriptor status missing for ${entry.stableId}")
            assertTrue(entry.missingReason.isNotBlank(), "missing reason missing for ${entry.stableId}")
            if (entry.descriptorStatus == "descriptor-backed") {
                assertEquals("none", entry.missingReason)
            }
            if (entry.descriptorStatus == "dispatch-only; missing descriptor") {
                assertTrue(
                    entry.missingReason.startsWith("Runtime effect descriptor missing for dispatch-only effect: "),
                    "dispatch-only row missing stable descriptor diagnostic for ${entry.stableId}",
                )
            }
        }
        assertTrue(markdown.contains("| Descriptor status | Missing reason |"))
        assertTrue(
            markdown.contains(
            "Status counts: total=3; descriptor-backed=3; " +
                    "dispatch-only/missing-descriptor=0; CPU-only=0; GPU-backed=3.",
            ),
        )
        markdown.lines()
            .filter { it.startsWith("| runtime.") }
            .forEach { row ->
                val cells = row.trim('|').split('|').map { it.trim() }
                assertEquals(10, cells.size)
                assertTrue(cells[8].isNotBlank(), "markdown descriptor status missing in row: $row")
                assertTrue(cells[9].isNotBlank(), "markdown missing reason missing in row: $row")
                if (cells[8] == "descriptor-backed") {
                    assertEquals("none", cells[9])
                }
                if (cells[8] == "dispatch-only; missing descriptor") {
                    assertTrue(
                        cells[9].startsWith("Runtime effect descriptor missing for dispatch-only effect: "),
                        "markdown dispatch-only row missing stable descriptor diagnostic: $row",
                    )
                }
            }
    }

    @Test
    fun `markdown export is deterministic and includes fallback fields`() {
        SkBuiltinShaderEffectsSimple.registerAll()

        val first = SkRuntimeEffectDescriptorRegistry.exportSupportMatrixMarkdown()
        val second = SkRuntimeEffectDescriptorRegistry.exportSupportMatrixMarkdown()

        assertEquals(first, second)
        assertTrue(first.contains("| runtime.simple_rt |"))
        assertTrue(first.contains("gColor:kFloat4"))
        assertTrue(first.contains("supported:kotlin/simple_rt"))
        assertTrue(first.contains("supported:wgsl/runtime_simple_rt"))
        assertTrue(first.contains("supported:wgsl/runtime_linear_gradient_rt"))
        assertTrue(first.contains("supported:wgsl/runtime_spiral_rt"))
        assertTrue(first.contains("descriptor-backed"))
    }

    @Test
    fun `V2 support matrix distinguishes gpu backed effects from stable policy refusals`() {
        registerRuntimeEffectsV2SupportMatrixBuiltins()

        val entries = SkRuntimeEffectDescriptorRegistry.supportMatrixV2Entries()
        val counts = SkRuntimeEffectDescriptorRegistry.supportMatrixV2StatusCounts()

        assertEquals(
            SkRuntimeEffectSupportMatrixV2StatusCounts(
                total = 7,
                descriptorBacked = 5,
                cpuOnly = 1,
                gpuBacked = 4,
                dependencyGated = 0,
                expectedUnsupported = 2,
            ),
            counts,
        )

        val simple = entries.single { it.stableId == "runtime.simple_rt" }
        assertEquals("descriptor-backed", simple.descriptorStatus)
        assertEquals("gpu-backed", simple.supportState)
        assertEquals("kotlin/simple_rt", simple.cpuImplementationId)
        assertEquals("wgsl/runtime_simple_rt", simple.wgslImplementationId)
        assertEquals("none", simple.fallbackReason)
        assertTrue(simple.pmNote.contains("registered Kotlin/CPU and parser-validated WGSL"))

        val arbitrarySkSL = entries.single { it.fallbackReason == "runtime-effect.arbitrary-sksl-unsupported" }
        assertEquals("policy-only", arbitrarySkSL.descriptorStatus)
        assertEquals("expected-unsupported", arbitrarySkSL.supportState)
        assertEquals(null, arbitrarySkSL.cpuImplementationId)
        assertEquals(null, arbitrarySkSL.wgslImplementationId)
        assertTrue(arbitrarySkSL.pmNote.contains("does not dynamically compile SkSL"))

        val missingDescriptor = entries.single { it.stableId == "policy.unregistered_wgsl_descriptor" }
        assertEquals("expected-unsupported", missingDescriptor.supportState)
        assertTrue(missingDescriptor.pmNote.contains("registered WGSL descriptor"))

        val lumaToAlpha = entries.single { it.stableId == "runtime.color_filter_luma_to_alpha" }
        assertEquals("descriptor-backed", lumaToAlpha.descriptorStatus)
        assertEquals("gpu-backed", lumaToAlpha.supportState)
        assertEquals("kotlin/color_filter_luma_to_alpha", lumaToAlpha.cpuImplementationId)
        assertEquals("wgsl/runtime_color_filter_luma_to_alpha", lumaToAlpha.wgslImplementationId)
        assertEquals("none", lumaToAlpha.fallbackReason)
    }

    @Test
    fun `child shader descriptor records named child lane and remains CPU-only`() {
        SkBuiltinShaderEffectsChildren.registerAll()

        val entry = SkRuntimeEffectDescriptorRegistry.supportMatrixV2Entries()
            .single { it.stableId == "runtime.unsharp_rt" }

        assertEquals("descriptor-backed", entry.descriptorStatus)
        assertEquals("cpu-only", entry.supportState)
        assertEquals(listOf("child:kShader"), entry.children)
        assertEquals("kotlin/unsharp_rt", entry.cpuImplementationId)
        assertEquals(null, entry.wgslImplementationId)
        assertEquals("runtime-effect.wgsl-descriptor-missing", entry.fallbackReason)
        assertTrue(entry.pmNote.contains("registered Kotlin/CPU behavior"))
    }

    @Test
    fun `V2 JSON and Markdown exports are deterministic and expose PM non claims`() {
        registerRuntimeEffectsV2SupportMatrixBuiltins()

        val firstJson = SkRuntimeEffectDescriptorRegistry.exportSupportMatrixV2Json()
        val secondJson = SkRuntimeEffectDescriptorRegistry.exportSupportMatrixV2Json()
        val firstMarkdown = SkRuntimeEffectDescriptorRegistry.exportSupportMatrixV2Markdown()
        val secondMarkdown = SkRuntimeEffectDescriptorRegistry.exportSupportMatrixV2Markdown()

        assertEquals(firstJson, secondJson)
        assertEquals(firstMarkdown, secondMarkdown)

        assertTrue(firstJson.contains("\"schemaVersion\":\"kanvas.runtime-effects.v2.support-matrix\""))
        assertTrue(firstJson.contains("\"supportState\":\"gpu-backed\""))
        assertTrue(firstJson.contains("\"supportState\":\"cpu-only\""))
        assertTrue(firstJson.contains("\"fallbackReason\":\"runtime-effect.arbitrary-sksl-unsupported\""))
        assertTrue(firstJson.contains("\"fallbackReason\":\"runtime-effect.wgsl-descriptor-missing\""))
        assertTrue(firstJson.contains("\"descriptorBacked\":5"))
        assertTrue(firstJson.contains("\"cpuOnly\":1"))
        assertTrue(firstJson.contains("\"gpuBacked\":4"))
        assertTrue(firstJson.contains("\"expectedUnsupported\":2"))
        assertTrue(firstJson.contains("No dynamic SkSL compilation"))

        assertTrue(firstMarkdown.contains("# Runtime Effects V2 Support Matrix"))
        assertTrue(firstMarkdown.contains("Status counts: total=7; descriptor-backed=5; CPU-only=1; GPU-backed=4; dependency-gated=0; expected-unsupported=2."))
        assertTrue(firstMarkdown.contains("| Stable id | Kind | Descriptor status | Support state | CPU implementation | WGSL implementation | Fallback reason | PM note |"))
        assertTrue(firstMarkdown.contains("| runtime.color_filter_luma_to_alpha | kColorFilter | descriptor-backed | gpu-backed | kotlin/color_filter_luma_to_alpha | wgsl/runtime_color_filter_luma_to_alpha | none |"))
        assertTrue(firstMarkdown.contains("| runtime.simple_rt | kShader | descriptor-backed | gpu-backed | kotlin/simple_rt | wgsl/runtime_simple_rt | none |"))
        assertTrue(firstMarkdown.contains("| runtime.unsharp_rt | kShader | descriptor-backed | cpu-only | kotlin/unsharp_rt | - | runtime-effect.wgsl-descriptor-missing |"))
        assertTrue(firstMarkdown.contains("runtime-effect.arbitrary-sksl-unsupported"))
        assertTrue(firstMarkdown.contains("No dynamic SkSL compilation"))
    }

    @Test
    fun `V2 support matrix writer materializes JSON and Markdown files`(@TempDir tempDir: Path) {
        writeRuntimeEffectsV2SupportMatrix(tempDir)

        val json = Files.readString(tempDir.resolve("support-matrix.json"))
        val markdown = Files.readString(tempDir.resolve("support-matrix.md"))

        assertTrue(json.contains("\"schemaVersion\":\"kanvas.runtime-effects.v2.support-matrix\""))
        assertTrue(json.contains("\"fallbackReason\":\"runtime-effect.arbitrary-sksl-unsupported\""))
        assertTrue(markdown.contains("# Runtime Effects V2 Support Matrix"))
        assertTrue(markdown.contains("runtime-effect.wgsl-descriptor-missing"))
    }

    @Test
    fun `CPU-only descriptor is valid and exports GPU unsupported`() {
        SkRuntimeEffectDescriptorRegistry.register(
            "half4 main(vec2 p) { return vec4(0); }",
            descriptor("runtime.cpu_only"),
        )

        val entry = SkRuntimeEffectDescriptorRegistry.supportMatrixEntries().single()

        assertEquals("unsupported: WGSL implementation id missing", entry.gpuSupport)
        assertTrue(
            SkRuntimeEffectDescriptorRegistry.exportSupportMatrixMarkdown()
                .contains("unsupported: WGSL implementation id missing"),
        )
    }

    @Test
    fun `blank WGSL implementation id fails registration`() {
        val source = "half4 main(vec2 p) { return vec4(0); }"
        val error = assertThrows(IllegalStateException::class.java) {
            SkRuntimeEffectDescriptorRegistry.register(
                source,
                descriptor("runtime.blank_wgsl", wgslImplementationId = " "),
            )
        }
        assertEquals(
            "Invalid runtime effect descriptor WGSL implementation id: " +
                "canonicalHash=${SkRuntimeEffectDispatch.canonicalHash(source)} " +
                "stableId=runtime.blank_wgsl wgslImplementationId= ",
            error.message,
        )
    }

    @Test
    fun `invalid stable id fails registration`() {
        val source = "half4 main(vec2 p) { return vec4(0); }"
        val error = assertThrows(IllegalStateException::class.java) {
            SkRuntimeEffectDescriptorRegistry.register(source, descriptor("Runtime.Bad"))
        }
        assertEquals(
            "Invalid runtime effect descriptor stableId: " +
                "canonicalHash=${SkRuntimeEffectDispatch.canonicalHash(source)} stableId=Runtime.Bad",
            error.message,
        )
    }

    @Test
    fun `missing CPU implementation id fails registration`() {
        val source = "half4 main(vec2 p) { return vec4(0); }"
        val error = assertThrows(IllegalStateException::class.java) {
            SkRuntimeEffectDescriptorRegistry.register(
                source,
                descriptor("runtime.missing_cpu").copy(cpuImplementationId = ""),
            )
        }
        assertEquals(
            "Invalid runtime effect descriptor CPU implementation id: " +
                "canonicalHash=${SkRuntimeEffectDispatch.canonicalHash(source)} " +
                "stableId=runtime.missing_cpu cpuImplementationId=",
            error.message,
        )
    }

    @Test
    fun `unknown WGSL implementation id fails without parser evidence`() {
        val source = "half4 main(vec2 p) { return vec4(0); }"
        val error = assertThrows(IllegalStateException::class.java) {
            SkRuntimeEffectDescriptorRegistry.register(
                source,
                descriptor("runtime.unknown_wgsl", wgslImplementationId = "wgsl/unknown_rt"),
            )
        }
        assertEquals(
            "Runtime effect descriptor WGSL evidence missing: " +
                "canonicalHash=${SkRuntimeEffectDispatch.canonicalHash(source)} " +
                "stableId=runtime.unknown_wgsl wgslImplementationId=wgsl/unknown_rt",
            error.message,
        )
    }

    private fun descriptor(stableId: String): SkRuntimeEffectDescriptor =
        SkRuntimeEffectDescriptor(
            stableId = stableId,
            kind = SkRuntimeEffect.Kind.kShader,
            uniforms = emptyList(),
            children = emptyList(),
            flags = 0,
            cpuImplementationId = "kotlin/test",
            wgslImplementationId = null,
        )

    private fun descriptor(
        stableId: String,
        wgslImplementationId: String?,
    ): SkRuntimeEffectDescriptor =
        descriptor(stableId).copy(wgslImplementationId = wgslImplementationId)
}
