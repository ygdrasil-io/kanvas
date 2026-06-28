package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GpuLightingTest {

    private val whiteColor = GPUColor(1f, 1f, 1f, 1f)
    private val blackColor = GPUColor(0f, 0f, 0f, 1f)
    private val grayColor = GPUColor(0.5f, 0.5f, 0.5f, 1f)
    private val directionDown = floatArrayOf(0f, -1f, 0f)

    @Test
    fun `directional light with bump map normal source is accepted`() {
        val filter = GpuLightingFilter()
        val plan = GPULightingPlan(
            type = GPULightType.Directional,
            direction = directionDown,
            position = null,
            surfaceScale = 5f,
            lightColor = whiteColor,
            ambientColor = grayColor,
            specularExponent = 16f,
            attenuation = null,
        )
        val normalMapPlan = GPULightingNormalMapPlan(
            normalSource = GPULightingNormalSource.BumpAlpha,
            sourceBinding = "source:texture",
            normalMapBinding = null,
        )
        val result = filter.execute(plan, normalMapPlan)
        assertTrue(result.accepted, "Directional light with bump alpha should be accepted")
        assertEquals("accepted.filter.lighting_directional", result.diagnosticCode)
    }

    @Test
    fun `specular light type is accepted with specular diagnostic`() {
        val filter = GpuLightingFilter()
        val plan = GPULightingPlan(
            type = GPULightType.Specular,
            direction = directionDown,
            position = null,
            surfaceScale = 3f,
            lightColor = whiteColor,
            ambientColor = blackColor,
            specularExponent = 64f,
            attenuation = null,
        )
        val normalMapPlan = GPULightingNormalMapPlan(
            normalSource = GPULightingNormalSource.BumpAlpha,
            sourceBinding = "source:texture",
            normalMapBinding = null,
        )
        val result = filter.execute(plan, normalMapPlan)
        assertTrue(result.accepted, "Specular light type should be accepted")
        assertEquals("accepted.filter.lighting_specular", result.diagnosticCode)
    }

    @Test
    fun `directional light with normal map texture source is accepted`() {
        val filter = GpuLightingFilter()
        val plan = GPULightingPlan(
            type = GPULightType.Directional,
            direction = floatArrayOf(1f, 0f, 0f),
            position = null,
            surfaceScale = 2f,
            lightColor = whiteColor,
            ambientColor = grayColor,
            specularExponent = 8f,
            attenuation = null,
        )
        val normalMapPlan = GPULightingNormalMapPlan(
            normalSource = GPULightingNormalSource.NormalMap,
            sourceBinding = "source:texture",
            normalMapBinding = "normal:texture",
        )
        val result = filter.execute(plan, normalMapPlan)
        assertTrue(result.accepted, "Directional light with normal map should be accepted")
    }

    @Test
    fun `point light type produces stable refusal`() {
        val filter = GpuLightingFilter()
        val plan = GPULightingPlan(
            type = GPULightType.Point,
            direction = null,
            position = floatArrayOf(100f, 100f, 0f),
            surfaceScale = 5f,
            lightColor = whiteColor,
            ambientColor = grayColor,
            specularExponent = 16f,
            attenuation = GPUAttenuation(1f, 0.01f, 0.001f),
        )
        val normalMapPlan = GPULightingNormalMapPlan(
            normalSource = GPULightingNormalSource.BumpAlpha,
            sourceBinding = "source:texture",
            normalMapBinding = null,
        )
        val result = filter.execute(plan, normalMapPlan)
        assertFalse(result.accepted, "Point light should be refused")
        assertEquals(
            "unsupported.filter.lighting_type_unsupported",
            result.diagnosticCode,
        )
    }

    @Test
    fun `spot light type produces stable refusal`() {
        val filter = GpuLightingFilter()
        val plan = GPULightingPlan(
            type = GPULightType.Spot,
            direction = directionDown,
            position = floatArrayOf(50f, 50f, 0f),
            surfaceScale = 5f,
            lightColor = whiteColor,
            ambientColor = grayColor,
            specularExponent = 16f,
            attenuation = GPUAttenuation(1f, 0.005f, 0.0001f),
        )
        val normalMapPlan = GPULightingNormalMapPlan(
            normalSource = GPULightingNormalSource.BumpAlpha,
            sourceBinding = "source:texture",
            normalMapBinding = null,
        )
        val result = filter.execute(plan, normalMapPlan)
        assertFalse(result.accepted, "Spot light should be refused")
        assertEquals(
            "unsupported.filter.lighting_type_unsupported",
            result.diagnosticCode,
        )
    }

    @Test
    fun `missing source binding produces normal source missing refusal`() {
        val validator = GpuLightingNormalSourceValidator
        val normalMapPlan = GPULightingNormalMapPlan(
            normalSource = GPULightingNormalSource.BumpAlpha,
            sourceBinding = "",
            normalMapBinding = null,
        )
        val result = validator.validate(normalMapPlan)
        assertFalse(result.accepted, "Blank source binding should be refused")
        assertEquals(
            "unsupported.filter.lighting_normal_source_missing",
            result.diagnosticCode,
        )
    }

    @Test
    fun `normal map source with null normal map binding produces refusal`() {
        val validator = GpuLightingNormalSourceValidator
        val normalMapPlan = GPULightingNormalMapPlan(
            normalSource = GPULightingNormalSource.NormalMap,
            sourceBinding = "source:texture",
            normalMapBinding = null,
        )
        val result = validator.validate(normalMapPlan)
        assertFalse(result.accepted, "NormalMap with null binding should be refused")
        assertEquals(
            "unsupported.filter.lighting_normal_source_missing",
            result.diagnosticCode,
        )
    }

    @Test
    fun `bump alpha normal source with valid binding is accepted`() {
        val validator = GpuLightingNormalSourceValidator
        val normalMapPlan = GPULightingNormalMapPlan(
            normalSource = GPULightingNormalSource.BumpAlpha,
            sourceBinding = "source:texture",
            normalMapBinding = null,
        )
        val result = validator.validate(normalMapPlan)
        assertTrue(result.accepted, "BumpAlpha with valid binding should be accepted")
    }

    @Test
    fun `normal map source with valid bindings is accepted`() {
        val validator = GpuLightingNormalSourceValidator
        val normalMapPlan = GPULightingNormalMapPlan(
            normalSource = GPULightingNormalSource.NormalMap,
            sourceBinding = "source:texture",
            normalMapBinding = "normal:texture",
        )
        val result = validator.validate(normalMapPlan)
        assertTrue(result.accepted, "NormalMap with valid bindings should be accepted")
    }

    @Test
    fun `surface scale zero produces flat surface accepted`() {
        val filter = GpuLightingFilter()
        val plan = GPULightingPlan(
            type = GPULightType.Directional,
            direction = directionDown,
            position = null,
            surfaceScale = 0f,
            lightColor = whiteColor,
            ambientColor = grayColor,
            specularExponent = 16f,
            attenuation = null,
        )
        val normalMapPlan = GPULightingNormalMapPlan(
            normalSource = GPULightingNormalSource.BumpAlpha,
            sourceBinding = "source:texture",
            normalMapBinding = null,
        )
        val result = filter.execute(plan, normalMapPlan)
        assertTrue(result.accepted, "Zero surface scale should be accepted (flat surface)")
    }

    @Test
    fun `negative surface scale produces refusal`() {
        val filter = GpuLightingFilter()
        val plan = GPULightingPlan(
            type = GPULightType.Directional,
            direction = directionDown,
            position = null,
            surfaceScale = -1f,
            lightColor = whiteColor,
            ambientColor = grayColor,
            specularExponent = 16f,
            attenuation = null,
        )
        val normalMapPlan = GPULightingNormalMapPlan(
            normalSource = GPULightingNormalSource.BumpAlpha,
            sourceBinding = "source:texture",
            normalMapBinding = null,
        )
        val result = filter.execute(plan, normalMapPlan)
        assertFalse(result.accepted)
        assertEquals(
            "unsupported.filter.lighting_surface_scale_negative",
            result.diagnosticCode,
        )
    }

    @Test
    fun `specular exponent out of range produces refusal`() {
        val filter = GpuLightingFilter()
        val plan = GPULightingPlan(
            type = GPULightType.Directional,
            direction = directionDown,
            position = null,
            surfaceScale = 3f,
            lightColor = whiteColor,
            ambientColor = grayColor,
            specularExponent = 256f,
            attenuation = null,
        )
        val normalMapPlan = GPULightingNormalMapPlan(
            normalSource = GPULightingNormalSource.BumpAlpha,
            sourceBinding = "source:texture",
            normalMapBinding = null,
        )
        val result = filter.execute(plan, normalMapPlan)
        assertFalse(result.accepted)
        assertEquals(
            "unsupported.filter.lighting_specular_exponent_out_of_range",
            result.diagnosticCode,
        )
    }

    @Test
    fun `params roundtrip preserves all lighting plan fields`() {
        val plan = GPULightingPlan(
            type = GPULightType.Specular,
            direction = floatArrayOf(0.5f, -0.866f, 0f),
            position = null,
            surfaceScale = 4f,
            lightColor = GPUColor(0.9f, 0.8f, 0.7f, 1f),
            ambientColor = GPUColor(0.1f, 0.1f, 0.2f, 1f),
            specularExponent = 32f,
            attenuation = GPUAttenuation(1f, 0.01f, 0.0001f),
        )
        assertEquals(GPULightType.Specular, plan.type)
        assertEquals(0.5f, plan.direction!![0])
        assertEquals(-0.866f, plan.direction!![1])
        assertEquals(0f, plan.direction!![2])
        assertEquals(4f, plan.surfaceScale)
        assertEquals(GPUColor(0.9f, 0.8f, 0.7f, 1f), plan.lightColor)
        assertEquals(GPUColor(0.1f, 0.1f, 0.2f, 1f), plan.ambientColor)
        assertEquals(32f, plan.specularExponent)
        assertNotNull(plan.attenuation)
        assertEquals(1f, plan.attenuation!!.constant)
        assertEquals(0.01f, plan.attenuation!!.linear)
        assertEquals(0.0001f, plan.attenuation!!.quadratic)
    }
}
