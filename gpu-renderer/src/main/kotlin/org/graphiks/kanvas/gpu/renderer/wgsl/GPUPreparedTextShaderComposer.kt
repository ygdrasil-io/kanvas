package org.graphiks.kanvas.gpu.renderer.materials

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.collections.CanonicalIdentityDigestEncoder
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialColorContract
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialCoordinateContract
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialFragment
import org.graphiks.kanvas.gpu.renderer.wgsl.GPUPreparedTextVertexLayout
import org.graphiks.kanvas.gpu.renderer.wgsl.GPUPreparedTextClipVariant
import org.graphiks.kanvas.gpu.renderer.wgsl.PreparedTextA8Shader
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslBindingReflection
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslLayoutReflection
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslReflectionReport
import org.graphiks.kanvas.gpu.renderer.wgsl.reflectWgslModule
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
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
    val coverageMaskTextureGroup: Int?,
    val coverageMaskTextureBinding: Int?,
)

internal sealed interface GPUPreparedTextCompositeAdmissionToken

private class IssuedGPUPreparedTextCompositeAdmissionToken(
    val admission: GPUPreparedTextCompositeAdmission,
) : GPUPreparedTextCompositeAdmissionToken

internal sealed interface GPUPreparedTextAuthenticatedComposite {
    val wgslSource: String
    val vertexEntryPoint: String
    val fragmentEntryPoint: String
    val bindingPlan: GPUPreparedTextCompositeBindingPlan
    val vertexLayout: GPUPreparedTextVertexLayout
    val sourceHash: String
    val abiHash: String
    val targetFormatClass: String
    val blendPlanIdentity: String
    val fixedFunctionBlendState: GPUFixedFunctionBlendState?
    val sourceCoverageEncoding: GPUSourceCoverageEncoding
    val clipVariant: GPUPreparedTextClipVariant
    val pipelineKey: String
}

private class GPUPreparedTextCompositeAdmission(
    material: GPUPreparedMaterialProgram,
    val targetFormatClass: String,
    val blendPlanIdentity: String,
    val fixedFunctionBlendState: GPUFixedFunctionBlendState?,
    val sourceCoverageEncoding: GPUSourceCoverageEncoding,
    val clipVariant: GPUPreparedTextClipVariant,
    observer: GPUPreparedTextCompositionObserver,
) {
    val wgslSource: String
    val vertexEntryPoint: String = VERTEX_ENTRY_POINT
    val fragmentEntryPoint: String = FRAGMENT_ENTRY_POINT
    val bindingPlan: GPUPreparedTextCompositeBindingPlan
    val vertexLayout: GPUPreparedTextVertexLayout
    val sourceHash: String
    val abiHash: String
    val pipelineKey: String

    init {
        observer.onCompose()
        if (targetFormatClass.isBlank()) {
            preparedTextCompositeAdmissionRefused(
                "Prepared text target format class must not be blank",
            )
        }
        if (blendPlanIdentity.isBlank()) {
            preparedTextCompositeAdmissionRefused(
                "Prepared text blend-plan identity must not be blank",
            )
        }

        val authenticatedMaterial = runCatching { material.authenticatedSnapshot() }
            .getOrElse { failure ->
                preparedTextCompositeAdmissionRefused(
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
            preparedTextCompositeAdmissionRefused(
                "Prepared material fragment contracts are not composable with text",
            )
        }
        preparedTextReservedIdentifierCollision(fragment)?.let { identifier ->
            preparedTextCompositeAdmissionRefused(
                "Prepared material fragment collides with reserved identifier $identifier",
            )
        }

        val source = runCatching {
            preparedTextCompositeSourceForFragment(
                fragment,
                sourceCoverageEncoding,
                clipVariant,
            )
        }.getOrElse { failure ->
            preparedTextCompositeAdmissionRefused(
                failure.message ?: "Prepared text source coverage encoding is unsupported",
            )
        }
        observer.onParse()
        val parsed = runCatching { parseWgslResult(source) }
            .getOrElse { failure ->
                preparedTextCompositeAdmissionRefused(
                    "wgsl4k parser failed: ${failure::class.simpleName.orEmpty()}",
                )
            }
        if (!parsed.isSuccess) {
            preparedTextCompositeAdmissionRefused(
                "wgsl4k parser diagnostics: ${parsed.errors.joinToString { it.message }}",
            )
        }
        observer.onLower()
        val lowered = runCatching { Lowerer().lower(parsed.translationUnit) }
            .getOrElse { failure ->
                preparedTextCompositeAdmissionRefused(
                    "wgsl4k lowering failed: ${failure::class.simpleName.orEmpty()}",
                )
            }
        val exactSourceHash = sha256Hex(source.encodeToByteArray())
        observer.onReflect()
        val report = runCatching {
            lowered.reflectWgslModule(sourceId = exactSourceHash)
        }.getOrElse { failure ->
            preparedTextCompositeAdmissionRefused(
                "wgsl4k reflection failed: ${failure::class.simpleName.orEmpty()}",
            )
        }
        preparedTextFinalModuleRefusal(fragment, report)?.let { refusal ->
            preparedTextCompositeAdmissionRefused(refusal.message)
        }

        val exactVertexLayout = PreparedTextA8Shader.VertexLayout
        val exactAbiHash = CanonicalIdentityDigestEncoder(
            "prepared-text-composite-abi-v1",
        )
            .texts(
                "facts",
                preparedTextCompositeAbiFacts(
                    vertexLayout = exactVertexLayout,
                    fragment = fragment,
                    report = report,
                ),
            )
            .digestHex()
        wgslSource = source
        bindingPlan = GPUPreparedTextCompositeBindingPlan(
            drawUniformGroup = DRAW_UNIFORM_GROUP,
            drawUniformBinding = DRAW_UNIFORM_BINDING,
            materialFragment = fragment,
            atlasTextureGroup = ATLAS_GROUP,
            atlasTextureBinding = ATLAS_TEXTURE_BINDING,
            atlasSamplerGroup = ATLAS_GROUP,
            atlasSamplerBinding = ATLAS_SAMPLER_BINDING,
            coverageMaskTextureGroup =
                COVERAGE_MASK_GROUP.takeIf {
                    clipVariant == GPUPreparedTextClipVariant.CoverageMask
                },
            coverageMaskTextureBinding =
                COVERAGE_MASK_TEXTURE_BINDING.takeIf {
                    clipVariant == GPUPreparedTextClipVariant.CoverageMask
                },
        )
        vertexLayout = exactVertexLayout
        sourceHash = exactSourceHash
        abiHash = exactAbiHash
        pipelineKey = GPUPreparedTextShaderComposer.pipelineKey(
            sourceHash = exactSourceHash,
            abiHash = exactAbiHash,
            targetFormatClass = targetFormatClass,
            blendPlanIdentity = blendPlanIdentity,
            sourceCoverageEncoding = sourceCoverageEncoding,
            clipVariant = clipVariant,
        )
    }

    val token: GPUPreparedTextCompositeAdmissionToken =
        IssuedGPUPreparedTextCompositeAdmissionToken(this)
    val authenticatedSnapshot: GPUPreparedTextAuthenticatedComposite =
        IssuedGPUPreparedTextAuthenticatedComposite(this)
}

private class IssuedGPUPreparedTextAuthenticatedComposite(
    private val admission: GPUPreparedTextCompositeAdmission,
) : GPUPreparedTextAuthenticatedComposite {
    override val wgslSource: String get() = admission.wgslSource
    override val vertexEntryPoint: String get() = admission.vertexEntryPoint
    override val fragmentEntryPoint: String get() = admission.fragmentEntryPoint
    override val bindingPlan: GPUPreparedTextCompositeBindingPlan get() = admission.bindingPlan
    override val vertexLayout: GPUPreparedTextVertexLayout get() = admission.vertexLayout
    override val sourceHash: String get() = admission.sourceHash
    override val abiHash: String get() = admission.abiHash
    override val targetFormatClass: String get() = admission.targetFormatClass
    override val blendPlanIdentity: String get() = admission.blendPlanIdentity
    override val fixedFunctionBlendState: GPUFixedFunctionBlendState?
        get() = admission.fixedFunctionBlendState
    override val sourceCoverageEncoding: GPUSourceCoverageEncoding
        get() = admission.sourceCoverageEncoding
    override val clipVariant: GPUPreparedTextClipVariant
        get() = admission.clipVariant
    override val pipelineKey: String get() = admission.pipelineKey
}

class GPUPreparedTextCompositeProgram private constructor(
    private val admission: GPUPreparedTextCompositeAdmission,
) {
    val wgslSource: String = admission.wgslSource
    val vertexEntryPoint: String = admission.vertexEntryPoint
    val fragmentEntryPoint: String = admission.fragmentEntryPoint
    val bindingPlan: GPUPreparedTextCompositeBindingPlan = admission.bindingPlan
    val vertexLayout: GPUPreparedTextVertexLayout = admission.vertexLayout
    val sourceHash: String = admission.sourceHash
    val abiHash: String = admission.abiHash
    val targetFormatClass: String = admission.targetFormatClass
    val blendPlanIdentity: String = admission.blendPlanIdentity
    val fixedFunctionBlendState: GPUFixedFunctionBlendState? =
        admission.fixedFunctionBlendState
    val sourceCoverageEncoding: GPUSourceCoverageEncoding =
        admission.sourceCoverageEncoding
    val clipVariant: GPUPreparedTextClipVariant = admission.clipVariant
    val pipelineKey: String = admission.pipelineKey

    internal val admissionToken: GPUPreparedTextCompositeAdmissionToken
        get() = admission.token

    internal fun authenticatedSnapshot(
        token: GPUPreparedTextCompositeAdmissionToken,
    ): GPUPreparedTextAuthenticatedComposite? {
        val issuedToken = token as? IssuedGPUPreparedTextCompositeAdmissionToken
            ?: return null
        if (issuedToken.admission !== admission || !matchesAdmission()) return null
        return admission.authenticatedSnapshot
    }

    private fun matchesAdmission(): Boolean =
        wgslSource == admission.wgslSource &&
            vertexEntryPoint == admission.vertexEntryPoint &&
            fragmentEntryPoint == admission.fragmentEntryPoint &&
            bindingPlan == admission.bindingPlan &&
            vertexLayout == admission.vertexLayout &&
            sourceHash == admission.sourceHash &&
            abiHash == admission.abiHash &&
            targetFormatClass == admission.targetFormatClass &&
            blendPlanIdentity == admission.blendPlanIdentity &&
            fixedFunctionBlendState == admission.fixedFunctionBlendState &&
            sourceCoverageEncoding == admission.sourceCoverageEncoding &&
            clipVariant == admission.clipVariant &&
            pipelineKey == admission.pipelineKey

    companion object {
        @JvmSynthetic
        internal fun composeValidated(
            material: GPUPreparedMaterialProgram,
            targetFormatClass: String,
            blendPlanIdentity: String,
            fixedFunctionBlendState: GPUFixedFunctionBlendState?,
            sourceCoverageEncoding: GPUSourceCoverageEncoding,
            clipVariant: GPUPreparedTextClipVariant,
            observer: GPUPreparedTextCompositionObserver,
        ): GPUPreparedTextCompositeProgramResult =
            try {
                GPUPreparedTextCompositeProgramResult.Ready(
                    GPUPreparedTextCompositeProgram(
                        GPUPreparedTextCompositeAdmission(
                            material = material,
                            targetFormatClass = targetFormatClass,
                            blendPlanIdentity = blendPlanIdentity,
                            fixedFunctionBlendState = fixedFunctionBlendState,
                            sourceCoverageEncoding = sourceCoverageEncoding,
                            clipVariant = clipVariant,
                            observer = observer,
                        ),
                    ),
                )
            } catch (refused: GPUPreparedTextCompositeAdmissionRefused) {
                GPUPreparedTextCompositeProgramResult.Refused(
                    code = REFUSAL_CODE,
                    message = refused.refusalMessage,
                )
            }
    }
}

sealed interface GPUPreparedTextCompositeProgramResult {
    data class Ready(
        val program: GPUPreparedTextCompositeProgram,
    ) : GPUPreparedTextCompositeProgramResult

    data class Refused(
        val code: String,
        val message: String,
    ) : GPUPreparedTextCompositeProgramResult
}

internal interface GPUPreparedTextCompositionObserver {
    fun onCompose()
    fun onParse()
    fun onLower()
    fun onReflect()
}

private object NoOpGPUPreparedTextCompositionObserver : GPUPreparedTextCompositionObserver {
    override fun onCompose() = Unit
    override fun onParse() = Unit
    override fun onLower() = Unit
    override fun onReflect() = Unit
}

object GPUPreparedTextShaderComposer {
    fun compose(
        material: GPUPreparedMaterialProgram,
        targetFormatClass: String,
        blendPlanIdentity: String,
        fixedFunctionBlendState: GPUFixedFunctionBlendState? = null,
        sourceCoverageEncoding: GPUSourceCoverageEncoding =
            GPUSourceCoverageEncoding.ModulateRGBA,
        clipVariant: GPUPreparedTextClipVariant = GPUPreparedTextClipVariant.None,
    ): GPUPreparedTextCompositeProgramResult = composeObserved(
        material = material,
        targetFormatClass = targetFormatClass,
        blendPlanIdentity = blendPlanIdentity,
        fixedFunctionBlendState = fixedFunctionBlendState,
        sourceCoverageEncoding = sourceCoverageEncoding,
        clipVariant = clipVariant,
        observer = NoOpGPUPreparedTextCompositionObserver,
    )

    internal fun composeObserved(
        material: GPUPreparedMaterialProgram,
        targetFormatClass: String,
        blendPlanIdentity: String,
        fixedFunctionBlendState: GPUFixedFunctionBlendState?,
        sourceCoverageEncoding: GPUSourceCoverageEncoding,
        clipVariant: GPUPreparedTextClipVariant,
        observer: GPUPreparedTextCompositionObserver,
    ): GPUPreparedTextCompositeProgramResult =
        GPUPreparedTextCompositeProgram.composeValidated(
            material = material,
            targetFormatClass = targetFormatClass,
            blendPlanIdentity = blendPlanIdentity,
            fixedFunctionBlendState = fixedFunctionBlendState,
            sourceCoverageEncoding = sourceCoverageEncoding,
            clipVariant = clipVariant,
            observer = observer,
        )

    internal fun pipelineKey(
        sourceHash: String,
        abiHash: String,
        targetFormatClass: String,
        blendPlanIdentity: String,
        sourceCoverageEncoding: GPUSourceCoverageEncoding,
        clipVariant: GPUPreparedTextClipVariant,
    ): String = CanonicalIdentityDigestEncoder(
        "prepared-text-composite-pipeline-v1",
    )
        .text("sourceHash", sourceHash)
        .text("abiHash", abiHash)
        .text("targetFormatClass", targetFormatClass)
        .text("blendPlanIdentity", blendPlanIdentity)
        .text("sourceCoverageEncoding", sourceCoverageEncoding.name)
        .text("clipVariant", clipVariant.name)
        .digestHex()
}

private class GPUPreparedTextCompositeAdmissionRefused(
    val refusalMessage: String,
) : RuntimeException(refusalMessage)

private fun preparedTextCompositeAdmissionRefused(message: String): Nothing =
    throw GPUPreparedTextCompositeAdmissionRefused(message)

private fun preparedTextCompositeSourceForFragment(
    fragment: GPUPreparedMaterialFragment,
    sourceCoverageEncoding: GPUSourceCoverageEncoding,
    clipVariant: GPUPreparedTextClipVariant,
): String = listOf(
    fragment.declarationsWgsl,
    fragment.evaluationFunctionWgsl,
    PreparedTextA8Shader.vertexWgsl,
    PreparedTextA8Shader.fragmentWgsl(sourceCoverageEncoding, clipVariant),
).joinToString("\n\n")

private fun preparedTextReservedIdentifierCollision(
    fragment: GPUPreparedMaterialFragment,
): String? {
    val source = fragment.declarationsWgsl + "\n\n" + fragment.evaluationFunctionWgsl
    return RESERVED_IDENTIFIERS.firstOrNull { identifier ->
        Regex("""\b${Regex.escape(identifier)}\b""").containsMatchIn(source)
    }
}

private fun preparedTextCompositeAbiFacts(
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

internal fun preparedTextFinalModuleRefusal(
    fragment: GPUPreparedMaterialFragment,
    report: WgslReflectionReport,
): GPUPreparedTextCompositeProgramResult.Refused? {
    val mismatch = preparedTextFinalModuleMismatch(fragment, report) ?: return null
    return GPUPreparedTextCompositeProgramResult.Refused(
        code = REFUSAL_CODE,
        message = mismatch,
    )
}

private fun preparedTextFinalModuleMismatch(
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
    preparedTextTaskBindingMismatch(report.bindings)?.let {
        return it
    }
    if (!report.bindings.all { it.group in DRAW_UNIFORM_GROUP..COVERAGE_MASK_GROUP }) {
        return "Prepared text final bindings must occupy only deterministic groups 0 through 3"
    }
    val drawUniformLayout = report.layouts
        .filter { layout -> layout.structName == DRAW_UNIFORM_STRUCT_NAME }
        .singleOrNull()
        ?.preparedTextAbi()
    if (drawUniformLayout != EXPECTED_DRAW_UNIFORM_LAYOUT) {
        return "Prepared text draw-uniform layout was not reflected exactly"
    }
    if (!report.entryPoints.any { it.name == VERTEX_ENTRY_POINT } ||
        !report.entryPoints.any { it.name == FRAGMENT_ENTRY_POINT }
    ) {
        return "Prepared text final entry points were not reflected"
    }
    return null
}

private fun preparedTextMaterialBindingMismatch(
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

private fun preparedTextTaskBindingMismatch(
    reflectedBindings: List<WgslBindingReflection>,
): String? {
    val actualBindings = reflectedBindings
        .filter { binding ->
            binding.group == DRAW_UNIFORM_GROUP || binding.group == ATLAS_GROUP
                || binding.group == COVERAGE_MASK_GROUP
        }
        .map(WgslBindingReflection::preparedTextAbi)
        .sortedWith(compareBy({ it.group }, { it.binding }))
    return if (actualBindings == EXPECTED_TASK_BINDINGS ||
        actualBindings == EXPECTED_TASK_BINDINGS_WITH_COVERAGE_MASK
    ) {
        null
    } else {
        "Prepared text final draw and atlas bindings do not match the exact Task 2 ABI"
    }
}

private data class PreparedTextBindingAbi(
    val group: Int,
    val binding: Int,
    val resourceKind: String,
    val access: String?,
    val sampleType: String?,
    val viewDimension: String?,
    val storageFormat: String?,
    val minBindingSize: Int?,
)

private fun WgslBindingReflection.preparedTextAbi(): PreparedTextBindingAbi =
    PreparedTextBindingAbi(
        group = group,
        binding = binding,
        resourceKind = resourceKind,
        access = access,
        sampleType = sampleType,
        viewDimension = viewDimension,
        storageFormat = storageFormat,
        minBindingSize = minBindingSize,
    )

private data class PreparedTextDrawUniformLayoutAbi(
    val structName: String,
    val addressSpace: String,
    val size: Int,
    val alignment: Int,
    val members: List<PreparedTextDrawUniformMemberAbi>,
)

private data class PreparedTextDrawUniformMemberAbi(
    val name: String,
    val type: String,
    val offset: Int,
    val size: Int,
    val alignment: Int,
    val stride: Int?,
)

private fun WgslLayoutReflection.preparedTextAbi(): PreparedTextDrawUniformLayoutAbi =
    PreparedTextDrawUniformLayoutAbi(
        structName = structName,
        addressSpace = addressSpace,
        size = size,
        alignment = alignment,
        members = immutableList(
            members.map { member ->
                PreparedTextDrawUniformMemberAbi(
                    name = member.name,
                    type = member.type,
                    offset = member.offset,
                    size = member.size,
                    alignment = member.alignment,
                    stride = member.stride,
                )
            },
        ),
    )

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

private const val DRAW_UNIFORM_GROUP = 0
private const val DRAW_UNIFORM_BINDING = 0
private const val DRAW_UNIFORM_SIZE_BYTES = 80
private const val DRAW_UNIFORM_STRUCT_NAME = "PreparedTextDrawUniforms"
private const val ATLAS_GROUP = 2
private const val ATLAS_TEXTURE_BINDING = 0
private const val ATLAS_SAMPLER_BINDING = 1
private const val COVERAGE_MASK_GROUP = 3
private const val COVERAGE_MASK_TEXTURE_BINDING = 0
private const val VERTEX_ENTRY_POINT = "vs_main"
private const val FRAGMENT_ENTRY_POINT = "fs_main"
private const val REFUSAL_CODE = "unsupported.material.composition"
private val EXPECTED_TASK_BINDINGS = immutableList(
    listOf(
        PreparedTextBindingAbi(
            group = DRAW_UNIFORM_GROUP,
            binding = DRAW_UNIFORM_BINDING,
            resourceKind = "uniformBuffer",
            access = "read",
            sampleType = null,
            viewDimension = null,
            storageFormat = null,
            minBindingSize = DRAW_UNIFORM_SIZE_BYTES,
        ),
        PreparedTextBindingAbi(
            group = ATLAS_GROUP,
            binding = ATLAS_TEXTURE_BINDING,
            resourceKind = "sampledTexture",
            access = "read",
            sampleType = "float",
            viewDimension = "2d",
            storageFormat = null,
            minBindingSize = null,
        ),
        PreparedTextBindingAbi(
            group = ATLAS_GROUP,
            binding = ATLAS_SAMPLER_BINDING,
            resourceKind = "sampler",
            access = "read",
            sampleType = null,
            viewDimension = null,
            storageFormat = null,
            minBindingSize = null,
        ),
    ),
)
private val EXPECTED_TASK_BINDINGS_WITH_COVERAGE_MASK = immutableList(
    EXPECTED_TASK_BINDINGS + PreparedTextBindingAbi(
        group = COVERAGE_MASK_GROUP,
        binding = COVERAGE_MASK_TEXTURE_BINDING,
        resourceKind = "sampledTexture",
        access = "read",
        sampleType = "float",
        viewDimension = "2d",
        storageFormat = null,
        minBindingSize = null,
    ),
)
private val EXPECTED_DRAW_UNIFORM_LAYOUT = PreparedTextDrawUniformLayoutAbi(
    structName = DRAW_UNIFORM_STRUCT_NAME,
    addressSpace = "uniform",
    size = DRAW_UNIFORM_SIZE_BYTES,
    alignment = 16,
    members = immutableList(
        listOf(
            PreparedTextDrawUniformMemberAbi(
                name = "targetSizeAndPaintAlpha",
                type = "vec4<f32>",
                offset = 0,
                size = 16,
                alignment = 16,
                stride = null,
            ),
            PreparedTextDrawUniformMemberAbi(
                name = "deviceToLocalRow0",
                type = "vec4<f32>",
                offset = 16,
                size = 16,
                alignment = 16,
                stride = null,
            ),
            PreparedTextDrawUniformMemberAbi(
                name = "deviceToLocalRow1",
                type = "vec4<f32>",
                offset = 32,
                size = 16,
                alignment = 16,
                stride = null,
            ),
            PreparedTextDrawUniformMemberAbi(
                name = "clipBounds",
                type = "vec4<f32>",
                offset = 48,
                size = 16,
                alignment = 16,
                stride = null,
            ),
            PreparedTextDrawUniformMemberAbi(
                name = "clipRadiiAndPadding",
                type = "vec4<f32>",
                offset = 64,
                size = 16,
                alignment = 16,
                stride = null,
            ),
        ),
    ),
)
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
