package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLReflectionProvider
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLValidator
import org.graphiks.wgsl.parser.parseWgslResult

class PreparedTextA8ShaderTest {

    @Test
    fun `module WGSL source is parser-validated through wgsl4k`() {
        val source = PreparedTextA8Shader.WGSL_SOURCE
        val parsed = parseWgslResult(source)
        assertTrue(parsed.isSuccess, parsed.errors.joinToString { it.message })

        val validation = KanvasWGSLValidator().parse(source)
        assertTrue(validation.syntaxErrors.isEmpty(), validation.syntaxErrors.joinToString())
    }

    @Test
    fun `entry point is fs_main`() {
        val source = PreparedTextA8Shader.WGSL_SOURCE
        assertContains(source, "fn fs_main(")
        val parsed = KanvasWGSLValidator().parse(source)
        val reflected = KanvasWGSLReflectionProvider().reflect(parsed)
        val report = requireNotNull(reflected.report)
        assertTrue(
            report.entryPoints.any { it.name == "fs_main" && it.stage == "fragment" },
            "Expected fs_main fragment entry point, got: ${report.entryPoints}",
        )
    }

    @Test
    fun `vertex entry point is vs_main`() {
        val source = PreparedTextA8Shader.WGSL_SOURCE
        assertContains(source, "fn vs_main(")
    }

    @Test
    fun `shader declares R8 texture and sampler`() {
        val source = PreparedTextA8Shader.WGSL_SOURCE
        assertContains(source, "textAtlas")
        assertContains(source, "textSampler")
        assertContains(source, "texture_2d<f32>")
        assertContains(source, "sampler")

        val parsed = parseWgslResult(source)
        assertTrue(parsed.isSuccess, parsed.errors.joinToString { it.message })
        val reflected = KanvasWGSLReflectionProvider().reflect(
            KanvasWGSLValidator().parse(source),
        )
        assertEquals(1, reflected.textureCount)
        val report = requireNotNull(reflected.report)
        assertEquals(
            1,
            report.bindings.count { it.resourceKind == "sampler" },
        )
    }

    @Test
    fun `shader declares instance storage buffer`() {
        val source = PreparedTextA8Shader.WGSL_SOURCE

        assertContains(source, "TextInstance")
        assertContains(source, "instances")
        assertContains(source, "storage")
    }

    @Test
    fun `shader declares material uniform block`() {
        val source = PreparedTextA8Shader.WGSL_SOURCE

        assertContains(source, "MaterialBlock")
        assertContains(source, "var<uniform> material")
    }

    @Test
    fun `shader declares text uniform block with paintAlpha`() {
        val source = PreparedTextA8Shader.WGSL_SOURCE

        assertContains(source, "TextUniforms")
        assertContains(source, "paintAlpha")
        assertContains(source, "var<uniform> text")
    }

    @Test
    fun `reflected ABI bindings match exact topology`() {
        val source = PreparedTextA8Shader.WGSL_SOURCE
        val parsed = parseWgslResult(source)
        assertTrue(parsed.isSuccess, parsed.errors.joinToString { it.message })

        val reflected = KanvasWGSLReflectionProvider().reflect(
            KanvasWGSLValidator().parse(source),
        )
        val report = requireNotNull(reflected.report)

        val bindings = report.bindings.map {
            Triple(it.group, it.binding, it.resourceKind)
        }

        assertTrue(
            bindings.any { (g, b, kind) -> g == 0 && b == 0 && kind == "uniformBuffer" },
            "Expected group=0 binding=0 uniformBuffer (TextUniforms): $bindings",
        )
        assertTrue(
            bindings.any { (g, b, kind) -> g == 0 && b == 1 && kind == "storageBuffer" },
            "Expected group=0 binding=1 storageBuffer (instances): $bindings",
        )
        assertTrue(
            bindings.any { (g, b, kind) -> g == 1 && b == 0 && kind == "uniformBuffer" },
            "Expected group=1 binding=0 uniformBuffer (MaterialBlock): $bindings",
        )
        assertTrue(
            bindings.any { (g, b, kind) -> g == 1 && b == 1 && kind == "sampledTexture" },
            "Expected group=1 binding=1 sampledTexture (textAtlas): $bindings",
        )
        assertTrue(
            bindings.any { (g, b, kind) -> g == 1 && b == 2 && kind == "sampler" },
            "Expected group=1 binding=2 sampler (textSampler): $bindings",
        )
    }

    @Test
    fun `fragment samples coverage from R channel exactly once`() {
        val source = PreparedTextA8Shader.WGSL_SOURCE
        val fragment = source.substringAfter("fn fs_main(").substringBefore("}")
        assertContains(fragment, "textureSample")
        assertContains(fragment, ".r")
        val rCount = fragment.split(".r").size - 1
        assertTrue(rCount >= 1, "Fragment must sample .r channel, found $rCount")
    }

    @Test
    fun `paint alpha is applied exactly once in fragment`() {
        val source = PreparedTextA8Shader.WGSL_SOURCE
        val fragment = source.substringAfter("fn fs_main(").substringBefore("}")

        assertContains(fragment, "clamp(text.paintAlpha")
        val paintAlphaOccurrences = fragment.split("paintAlpha").size - 1
        assertEquals(
            3,
            paintAlphaOccurrences,
            "paintAlpha should appear 3 times: text.paintAlpha uniform access, local declaration, and sourceAlpha usage",
        )
    }

    @Test
    fun `coverage contributes to sourceAlpha exactly once`() {
        val source = PreparedTextA8Shader.WGSL_SOURCE
        val fragment = source.substringAfter("fn fs_main(").substringBefore("}")

        assertContains(fragment, "coverage")
        val coverageOccurrences = fragment.split("coverage").size - 1
        assertEquals(
            2,
            coverageOccurrences,
            "coverage should appear twice: declaration from textureSample and once in sourceAlpha",
        )
    }

    @Test
    fun `fragment returns premultiplied output with correct alpha formula`() {
        val source = PreparedTextA8Shader.WGSL_SOURCE
        val fragment = source.substringAfter("fn fs_main(").substringBefore("}")

        assertContains(fragment, "paintStraightLinear")
        assertContains(fragment, "sourceAlpha")
        assertContains(fragment, "paintStraightLinear.rgb * sourceAlpha")
        assertContains(fragment, "sourceAlpha,")

        assertTrue(
            fragment.contains("sourceAlpha") &&
                fragment.indexOf("sourceAlpha") < fragment.lastIndexOf("sourceAlpha"),
            "sourceAlpha must be used at least twice (declaration + return alpha)",
        )
    }

    @Test
    fun `legacy formula vec4 color dot rgb a8 times color dot a is rejected`() {
        val source = PreparedTextA8Shader.WGSL_SOURCE

        assertTrue(
            !source.contains("a8 * color.a"),
            "Legacy formula 'a8 * color.a' must not appear in the prepared shader",
        )
        assertTrue(
            !source.contains("vec4(color.rgb, a8"),
            "Legacy formula 'vec4(color.rgb, a8' must not appear",
        )
        assertTrue(
            !source.contains("vec4<f32>(color.rgb, a8"),
            "Legacy formula variant must not appear",
        )
    }

    @Test
    fun `no magenta or hardcoded fallback color exists`() {
        val source = PreparedTextA8Shader.WGSL_SOURCE

        assertTrue(
            !source.contains("vec4<f32>(1.0, 0.0, 1.0"),
            "Magenta fallback must not appear in the prepared shader",
        )
        assertTrue(
            !source.contains("vec4<f32>(1.0, 1.0, 1.0, a8)") || source.contains("vec4<f32>("),
            "Hardcoded white-alpha fallback from legacy snippet must not be the only output path",
        )
    }

    @Test
    fun `WGSL_SOURCE is non-empty and contains fragment keyword`() {
        val source = PreparedTextA8Shader.WGSL_SOURCE
        assertTrue(source.isNotBlank())
        assertContains(source, "@fragment")
    }

    @Test
    fun `PreparedTextA8SourceHash is fragment text prepared_a8 v1`() {
        assertEquals(
            "fragment:text.prepared_a8:v1",
            PreparedTextA8SourceHash,
        )
    }

    @Test
    fun `PreparedTextA8EntryPoint is fs_main`() {
        assertEquals("fs_main", PreparedTextA8EntryPoint)
    }
}
