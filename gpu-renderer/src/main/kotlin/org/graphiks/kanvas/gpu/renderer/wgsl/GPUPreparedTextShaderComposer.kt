package org.graphiks.kanvas.gpu.renderer.materials

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.collections.CanonicalIdentityDigestEncoder
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialColorContract
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialCoordinateContract
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialFragment
import org.graphiks.kanvas.gpu.renderer.wgsl.GPUPreparedTextVertexLayout
import org.graphiks.kanvas.gpu.renderer.wgsl.PreparedTextA8Shader
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslBindingReflection
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslReflectionReport
import org.graphiks.kanvas.gpu.renderer.wgsl.reflectWgslModule
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult

data class GPUPreparedTextCompositeBindingPlan(
    val drawUniformGroup: Int,
    val drawUniformBinding: Int,
    val materialFragment: GPUPreparedMaterialFragment,
    val atlasTextureGroup: Int,
    val atlasTextureBinding: Int,
    val atlasSamplerGroup: Int,
    val atlasSamplerBinding: Int,
)

data class GPUPreparedTextCompositeProgram(
    val wgslSource: String,
    val vertexEntryPoint: String,
    val fragmentEntryPoint: String,
    val bindingPlan: GPUPreparedTextCompositeBindingPlan,
    val vertexLayout: GPUPreparedTextVertexLayout,
    val sourceHash: String,
    val abiHash: String,
    val pipelineKey: String,
)

sealed interface GPUPreparedTextCompositeProgramResult {
    data class Ready(
        val program: GPUPreparedTextCompositeProgram,
    ) : GPUPreparedTextCompositeProgramResult

    data class Refused(
        val code: String,
        val message: String,
    ) : GPUPreparedTextCompositeProgramResult
}

object GPUPreparedTextShaderComposer {
    fun compose(
        material: GPUPreparedMaterialProgram,
        targetFormatClass: String,
        blendPlanIdentity: String,
    ): GPUPreparedTextCompositeProgramResult {
        if (targetFormatClass.isBlank()) {
            return refused("Prepared text target format class must not be blank")
        }
        if (blendPlanIdentity.isBlank()) {
            return refused("Prepared text blend-plan identity must not be blank")
        }

        val authenticatedMaterial = runCatching { material.authenticatedSnapshot() }
            .getOrElse { failure ->
                return refused(
                    "Prepared material authentication failed: " +
                        failure::class.simpleName.orEmpty(),
                )
            }
        val fragment = authenticatedMaterial.composableFragment
        if (
            fragment.colorContract !=
            GPUPreparedMaterialColorContract.LinearPremultipliedRgba ||
            fragment.coordinateContract !=
            GPUPreparedMaterialCoordinateContract.LocalPosition2D
        ) {
            return refused("Prepared material fragment contracts are not composable with text")
        }
        reservedIdentifierCollision(fragment)?.let { identifier ->
            return refused("Prepared material fragment collides with reserved identifier $identifier")
        }

        val source = listOf(
            fragment.declarationsWgsl,
            fragment.evaluationFunctionWgsl,
            PreparedTextA8Shader.vertexWgsl,
            PreparedTextA8Shader.fragmentWgsl,
        ).joinToString("\n\n")
        val parsed = runCatching { parseWgslResult(source) }
            .getOrElse { failure ->
                return refused(
                    "wgsl4k parser failed: ${failure::class.simpleName.orEmpty()}",
                )
            }
        if (!parsed.isSuccess) {
            return refused(
                "wgsl4k parser diagnostics: ${parsed.errors.joinToString { it.message }}",
            )
        }
        val lowered = runCatching { Lowerer().lower(parsed.translationUnit) }
            .getOrElse { failure ->
                return refused(
                    "wgsl4k lowering failed: ${failure::class.simpleName.orEmpty()}",
                )
            }
        val sourceHash = sha256Hex(source.encodeToByteArray())
        val report = runCatching {
            lowered.reflectWgslModule(sourceId = sourceHash)
        }.getOrElse { failure ->
            return refused(
                "wgsl4k reflection failed: ${failure::class.simpleName.orEmpty()}",
            )
        }
        finalModuleMismatch(fragment, report)?.let { mismatch ->
            return refused(mismatch)
        }

        val vertexLayout = PreparedTextA8Shader.VertexLayout
        val abiHash = CanonicalIdentityDigestEncoder("prepared-text-composite-abi-v1")
            .texts(
                "facts",
                compositeAbiFacts(
                    vertexLayout = vertexLayout,
                    fragment = fragment,
                    report = report,
                ),
            )
            .digestHex()
        val pipelineKey = CanonicalIdentityDigestEncoder(
            "prepared-text-composite-pipeline-v1",
        )
            .text("sourceHash", sourceHash)
            .text("abiHash", abiHash)
            .text("targetFormatClass", targetFormatClass)
            .text("blendPlanIdentity", blendPlanIdentity)
            .digestHex()
        return GPUPreparedTextCompositeProgramResult.Ready(
            GPUPreparedTextCompositeProgram(
                wgslSource = source,
                vertexEntryPoint = VERTEX_ENTRY_POINT,
                fragmentEntryPoint = FRAGMENT_ENTRY_POINT,
                bindingPlan = GPUPreparedTextCompositeBindingPlan(
                    drawUniformGroup = DRAW_UNIFORM_GROUP,
                    drawUniformBinding = DRAW_UNIFORM_BINDING,
                    materialFragment = fragment,
                    atlasTextureGroup = ATLAS_GROUP,
                    atlasTextureBinding = ATLAS_TEXTURE_BINDING,
                    atlasSamplerGroup = ATLAS_GROUP,
                    atlasSamplerBinding = ATLAS_SAMPLER_BINDING,
                ),
                vertexLayout = vertexLayout,
                sourceHash = sourceHash,
                abiHash = abiHash,
                pipelineKey = pipelineKey,
            ),
        )
    }

    private fun finalModuleMismatch(
        fragment: GPUPreparedMaterialFragment,
        report: WgslReflectionReport,
    ): String? {
        if (!report.validation.success || report.unsupportedFeatures.isNotEmpty()) {
            return "Prepared text reflection did not prove a supported final module"
        }
        val entryPoints = report.entryPoints.map { it.name to it.stage }
        if (
            entryPoints != listOf(
                VERTEX_ENTRY_POINT to "vertex",
                FRAGMENT_ENTRY_POINT to "fragment",
            )
        ) {
            return "Prepared text final module must expose exactly vs_main and fs_main"
        }
        val coordinates = report.bindings.map { it.group to it.binding }
        if (coordinates.distinct().size != coordinates.size) {
            return "Prepared text final module contains a binding collision"
        }
        preparedTextMaterialBindingMismatch(fragment, report.bindings)?.let {
            return it
        }
        val expectedBindings = buildList {
            add(
                ExpectedBinding(
                    DRAW_UNIFORM_GROUP,
                    DRAW_UNIFORM_BINDING,
                    "uniformBuffer",
                ),
            )
            fragment.uniformBinding?.let {
                add(ExpectedBinding(it.group, it.binding, "uniformBuffer"))
            }
            fragment.sampledBindings.forEach {
                add(ExpectedBinding(it.textureGroup, it.textureBinding, "sampledTexture"))
                add(ExpectedBinding(it.samplerGroup, it.samplerBinding, "sampler"))
            }
            add(ExpectedBinding(ATLAS_GROUP, ATLAS_TEXTURE_BINDING, "sampledTexture"))
            add(ExpectedBinding(ATLAS_GROUP, ATLAS_SAMPLER_BINDING, "sampler"))
        }
        val actualBindings = report.bindings.map {
            ExpectedBinding(it.group, it.binding, it.resourceKind)
        }
        if (actualBindings != expectedBindings) {
            return "Prepared text final bindings do not match draw, material and atlas plans"
        }
        if (!report.bindings.all { it.group in DRAW_UNIFORM_GROUP..ATLAS_GROUP }) {
            return "Prepared text final bindings must occupy exactly groups 0 through 2"
        }
        if (!report.layouts.any { layout ->
                layout.structName == "PreparedTextDrawUniforms" &&
                    layout.addressSpace == "uniform" &&
                    layout.size == DRAW_UNIFORM_SIZE_BYTES &&
                    layout.members.map { it.name } == listOf(
                        "targetSizeAndPaintAlpha",
                        "deviceToLocalRow0",
                        "deviceToLocalRow1",
                    )
            }
        ) {
            return "Prepared text draw-uniform layout was not reflected exactly"
        }
        if (!report.entryPoints.any { it.name == VERTEX_ENTRY_POINT } ||
            !report.entryPoints.any { it.name == FRAGMENT_ENTRY_POINT }
        ) {
            return "Prepared text final entry points were not reflected"
        }
        return null
    }

    private fun reservedIdentifierCollision(
        fragment: GPUPreparedMaterialFragment,
    ): String? {
        val source = fragment.declarationsWgsl + "\n\n" + fragment.evaluationFunctionWgsl
        return RESERVED_IDENTIFIERS.firstOrNull { identifier ->
            Regex("""\b${Regex.escape(identifier)}\b""").containsMatchIn(source)
        }
    }

    private fun compositeAbiFacts(
        vertexLayout: GPUPreparedTextVertexLayout,
        fragment: GPUPreparedMaterialFragment,
        report: WgslReflectionReport,
    ): List<String> = buildList {
        add("prepared-text-composite-abi:v1")
        add("vertex.arrayStrideBytes=${vertexLayout.arrayStrideBytes}")
        add("vertex.stepMode=${vertexLayout.stepMode}")
        vertexLayout.attributes.forEach { attribute ->
            add(
                "vertex.attribute=${attribute.location}:" +
                    "${attribute.offsetBytes}:${attribute.format}",
            )
        }
        report.bindings
            .sortedWith(compareBy({ it.group }, { it.binding }))
            .forEach { binding ->
                add(
                    "binding=${binding.group}:${binding.binding}:${binding.name}:" +
                        "${binding.resourceKind}:${binding.access.orEmpty()}:" +
                        "${binding.sampleType.orEmpty()}:" +
                        "${binding.viewDimension.orEmpty()}:" +
                        "${binding.storageFormat.orEmpty()}:" +
                        "${binding.minBindingSize ?: -1}",
                )
            }
        report.entryPoints.forEach { entryPoint ->
            add("entry=${entryPoint.name}:${entryPoint.stage}")
        }
        add("material.colorContract=${fragment.colorContract.name}")
        add("material.coordinateContract=${fragment.coordinateContract.name}")
        add("coordinate=device-pixels-to-ndc:y-down-to-y-up")
        add("coordinate=device-to-local-affine-two-row")
        add("coverage=a8-r-sampled-once:premul-modulated-once")
        add("firstInstance=webgpu-instance-attribute-fetch-only")
    }

    private fun refused(message: String): GPUPreparedTextCompositeProgramResult.Refused =
        GPUPreparedTextCompositeProgramResult.Refused(
            code = REFUSAL_CODE,
            message = message,
        )
}

internal fun preparedTextMaterialBindingMismatch(
    fragment: GPUPreparedMaterialFragment,
    reflectedBindings: List<WgslBindingReflection>,
): String? =
    composableBindingMismatch(
        uniformBinding = fragment.uniformBinding,
        sampledBindings = fragment.sampledBindings,
        reflectedBindings = reflectedBindings.filter { binding -> binding.group == 1 },
    )?.let {
        "Prepared text final material bindings do not match the exact Task 1 ABI"
    }

private data class ExpectedBinding(
    val group: Int,
    val binding: Int,
    val resourceKind: String,
)

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

private const val DRAW_UNIFORM_GROUP = 0
private const val DRAW_UNIFORM_BINDING = 0
private const val DRAW_UNIFORM_SIZE_BYTES = 48
private const val ATLAS_GROUP = 2
private const val ATLAS_TEXTURE_BINDING = 0
private const val ATLAS_SAMPLER_BINDING = 1
private const val VERTEX_ENTRY_POINT = "vs_main"
private const val FRAGMENT_ENTRY_POINT = "fs_main"
private const val REFUSAL_CODE = "unsupported.material.composition"
private val RESERVED_IDENTIFIERS = listOf(
    "PreparedTextDrawUniforms",
    "PreparedTextVertexInput",
    "PreparedTextVertexOutput",
    "drawUniforms",
    "textAtlas",
    "textSampler",
    "vs_main",
    "fs_main",
)
