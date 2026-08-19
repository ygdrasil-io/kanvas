package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesShaderProgram
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesShaderResult
import org.graphiks.kanvas.gpu.renderer.artifacts.PreparedVerticesShaderAssembler
import org.graphiks.kanvas.gpu.renderer.materials.stubPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesLayoutAuthority
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexLayoutPlan
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLReflectionProvider
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLValidator
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.WGSLModuleReflection

class PreparedVerticesShaderAbiTest {
    @Test
    fun `reflection proves exact transform and prepared material binding ABI`() {
        val material = stubPreparedMaterialProgram()
        val ready = assembleReady(
            GPUPreparedVerticesLayoutAuthority.layout(hasColors = true, hasTexCoords = true),
            GPUVertexMode.Triangles,
            materialHasColor = true,
        )
        val parsed = KanvasWGSLValidator().parse(ready.wgslSource)
        assertEquals(emptyList(), parsed.syntaxErrors)
        val report = requireNotNull(KanvasWGSLReflectionProvider().reflect(parsed).report)

        assertEquals(
            listOf("vs_main" to "vertex", "fs_main" to "fragment"),
            report.entryPoints.map { it.name to it.stage },
        )
        assertEquals(
            listOf(
                ReflectedBinding(0, 0, "preparedVerticesDraw", "uniformBuffer", 64),
                ReflectedBinding(1, 0, "solidMaterial", "uniformBuffer", 16),
            ),
            report.bindings.map {
                ReflectedBinding(it.group, it.binding, it.name, it.resourceKind, it.minBindingSize)
            },
        )
        val drawLayout = report.layouts.single { it.structName == "PreparedVerticesDrawUniforms" }
        assertEquals("uniform", drawLayout.addressSpace)
        assertEquals(64, drawLayout.size)
        assertEquals(16, drawLayout.alignment)
        assertEquals(
            listOf(
                ReflectedMember("localToDevice", "mat3x3<f32>", 0, 48, 16, 16),
                ReflectedMember("targetSize", "vec2<f32>", 48, 8, 8, null),
                ReflectedMember("_padding", "vec2<f32>", 56, 8, 8, null),
            ),
            drawLayout.members.map {
                ReflectedMember(it.name, it.type, it.offset, it.size, it.alignment, it.stride)
            },
        )
    }

    @Test
    fun `shader identities are deterministic and separate layout topology ABI and pipeline facts`() {
        val layout = GPUPreparedVerticesLayoutAuthority.layout(hasColors = false, hasTexCoords = false)
        val first = assembleReady(layout, GPUVertexMode.Triangles, materialHasColor = false)
        val same = assembleReady(layout, GPUVertexMode.Triangles, materialHasColor = false)
        val strip = assembleReady(layout, GPUVertexMode.TriangleStrip, materialHasColor = false)
        val uv = assembleReady(
            GPUPreparedVerticesLayoutAuthority.layout(hasColors = false, hasTexCoords = true),
            GPUVertexMode.Triangles,
            materialHasColor = false,
        )

        assertEquals(first, same)
        assertEquals(first.wgslSource, strip.wgslSource)
        assertEquals(first.vertexLayoutHash, strip.vertexLayoutHash)
        assertEquals(first.bindingLayoutHash, strip.bindingLayoutHash)
        assertEquals(first.reflectedAbiHash, strip.reflectedAbiHash)
        assertNotEquals(first.pipelineKeyHash, strip.pipelineKeyHash)
        assertNotEquals(first.vertexLayoutHash, uv.vertexLayoutHash)
        assertNotEquals(first.reflectedAbiHash, uv.reflectedAbiHash)
        listOf(
            first.vertexLayoutHash,
            first.bindingLayoutHash,
            first.reflectedAbiHash,
            first.pipelineKeyHash,
        ).forEach { hash -> assertTrue(hash.matches(Regex("sha256:[0-9a-f]{64}"))) }
    }

    @Test
    fun `assembly preserves exact prepared material payload children resources and fragment`() {
        val material = stubPreparedMaterialProgram()
        val uniformsBefore = material.uniformBytes.toList()
        val resourcesBefore = material.sampledResources.toList()
        val childrenBefore = material.childPrograms.toList()
        val fragmentBefore = material.composableFragment

        assembleReady(
            GPUPreparedVerticesLayoutAuthority.layout(hasColors = false, hasTexCoords = true),
            GPUVertexMode.Triangles,
            materialHasColor = false,
            material = material,
        )

        assertEquals(uniformsBefore, material.uniformBytes)
        assertEquals(resourcesBefore, material.sampledResources)
        assertEquals(childrenBefore, material.childPrograms)
        assertEquals(fragmentBefore, material.composableFragment)
    }

    @Test
    fun `noncanonical topology layout and primitive color facts refuse before shader admission`() {
        val material = stubPreparedMaterialProgram()
        val canonicalColor = GPUPreparedVerticesLayoutAuthority.layout(
            hasColors = true,
            hasTexCoords = false,
        )
        val nonCanonical = GPUVertexLayoutPlan(
            attributes = canonicalColor.attributes,
            strideBytes = canonicalColor.strideBytes + 4,
            offsets = canonicalColor.offsets,
            shaderLocations = canonicalColor.shaderLocations,
        )

        val refusals = listOf(
            PreparedVerticesShaderAssembler.assemble(
                nonCanonical,
                GPUVertexMode.Triangles,
                material,
                hasPrimitiveColor = true,
            ) to GPUPreparedVerticesRefusalCodes.AttributeLayout,
            PreparedVerticesShaderAssembler.assemble(
                canonicalColor,
                GPUVertexMode.TriangleFan,
                material,
                hasPrimitiveColor = true,
            ) to GPUPreparedVerticesRefusalCodes.Topology,
            PreparedVerticesShaderAssembler.assemble(
                canonicalColor,
                GPUVertexMode.Triangles,
                material,
                hasPrimitiveColor = false,
            ) to GPUPreparedVerticesRefusalCodes.AttributeLayout,
        )

        refusals.forEach { (result, code) ->
            assertEquals(code, assertIs<GPUPreparedVerticesShaderResult.Refused>(result).code)
        }
    }

    @Test
    fun `fixture reflection without a live wgsl4k report is refused`() {
        val material = stubPreparedMaterialProgram()
        val result = PreparedVerticesShaderAssembler.assembleObserved(
            layout = GPUPreparedVerticesLayoutAuthority.layout(false, false),
            topology = GPUVertexMode.Triangles,
            material = material,
            hasPrimitiveColor = false,
            validator = KanvasWGSLValidator(),
            reflectionProvider = object : org.graphiks.kanvas.gpu.renderer.wgsl.validation.WGSLReflectionProvider {
                override fun reflect(
                    module: org.graphiks.kanvas.gpu.renderer.wgsl.validation.WGSLParsedModule,
                ): WGSLModuleReflection = KanvasWGSLReflectionProvider().reflect(module).copy(report = null)
            },
        )

        assertEquals(
            GPUPreparedVerticesRefusalCodes.Material,
            assertIs<GPUPreparedVerticesShaderResult.Refused>(result).code,
        )
    }

    private fun assembleReady(
        layout: GPUVertexLayoutPlan,
        topology: GPUVertexMode,
        materialHasColor: Boolean,
        material: org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram =
            stubPreparedMaterialProgram(),
    ): GPUPreparedVerticesShaderProgram = assertIs<GPUPreparedVerticesShaderResult.Ready>(
        PreparedVerticesShaderAssembler.assemble(
            layout = layout,
            topology = topology,
            material = material,
            hasPrimitiveColor = materialHasColor,
        ),
    ).program
}

private data class ReflectedBinding(
    val group: Int,
    val binding: Int,
    val name: String,
    val kind: String,
    val minBindingSize: Int?,
)

private data class ReflectedMember(
    val name: String,
    val type: String,
    val offset: Int,
    val size: Int,
    val alignment: Int,
    val stride: Int?,
)
