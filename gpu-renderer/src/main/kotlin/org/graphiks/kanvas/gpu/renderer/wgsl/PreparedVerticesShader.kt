package org.graphiks.kanvas.gpu.renderer.wgsl

import org.graphiks.kanvas.gpu.renderer.materials.CanonicalIdentityEncoder
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialFragment
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialSampledBinding
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesLayoutAuthority
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexLayoutPlan
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLReflectionProvider
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLValidator
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.WGSLReflectionProvider
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.WGSLValidator
import org.graphiks.wgsl.ast.FunctionDecl
import org.graphiks.wgsl.ast.IdentExpr
import org.graphiks.wgsl.ast.IntLiteral
import org.graphiks.wgsl.ast.MatrixType
import org.graphiks.wgsl.ast.ScalarKind
import org.graphiks.wgsl.ast.ScalarType
import org.graphiks.wgsl.ast.StructDecl
import org.graphiks.wgsl.ast.StructMember
import org.graphiks.wgsl.ast.TypeDecl
import org.graphiks.wgsl.ast.VectorType
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.ParseResult
import org.graphiks.wgsl.parser.parseWgslResult

data class GPUPreparedVerticesShaderProgram(
    val wgslSource: String,
    val vertexEntryPoint: String,
    val fragmentEntryPoint: String,
    val vertexLayoutHash: String,
    val bindingLayoutHash: String,
    val reflectedAbiHash: String,
    val pipelineKeyHash: String,
)

sealed interface GPUPreparedVerticesShaderResult {
    data class Ready(
        val program: GPUPreparedVerticesShaderProgram,
    ) : GPUPreparedVerticesShaderResult

    data class Refused(
        val code: String,
        val message: String,
    ) : GPUPreparedVerticesShaderResult
}

object PreparedVerticesShaderAssembler {
    fun assemble(
        layout: GPUVertexLayoutPlan,
        topology: GPUVertexMode,
        material: GPUPreparedMaterialProgram,
        hasPrimitiveColor: Boolean,
    ): GPUPreparedVerticesShaderResult = assembleObserved(
        layout = layout,
        topology = topology,
        material = material,
        hasPrimitiveColor = hasPrimitiveColor,
        validator = KanvasWGSLValidator(),
        reflectionProvider = KanvasWGSLReflectionProvider(),
    )

    fun assembleObserved(
        layout: GPUVertexLayoutPlan,
        topology: GPUVertexMode,
        material: GPUPreparedMaterialProgram,
        hasPrimitiveColor: Boolean,
        validator: WGSLValidator,
        reflectionProvider: WGSLReflectionProvider,
    ): GPUPreparedVerticesShaderResult {
        if (!GPUPreparedVerticesLayoutAuthority.isCanonical(layout)) {
            return preparedVerticesRefused(
                GPUPreparedVerticesRefusalCodes.AttributeLayout,
                "Prepared vertices shader requires a canonical vertex layout",
            )
        }
        if (topology != GPUVertexMode.Triangles && topology != GPUVertexMode.TriangleStrip) {
            return preparedVerticesRefused(
                GPUPreparedVerticesRefusalCodes.Topology,
                "Prepared vertices shader supports only triangle and triangle-strip topologies",
            )
        }
        val hasColor = layout.attributes.contains("color")
        if (hasPrimitiveColor != hasColor) {
            return preparedVerticesRefused(
                GPUPreparedVerticesRefusalCodes.AttributeLayout,
                "Prepared vertices primitive color flag must match the vertex color attribute",
            )
        }
        val hasTexCoord = layout.attributes.contains("texcoord")
        val fragment = material.composableFragment
        val source = preparedVerticesShaderSource(hasColor, hasTexCoord, fragment)

        val parsedModule = validator.parse(source)
        if (parsedModule.syntaxErrors.isNotEmpty()) {
            return preparedVerticesRefused(
                GPUPreparedVerticesRefusalCodes.Material,
                "Prepared vertices shader parse failed: " +
                    parsedModule.syntaxErrors.joinToString("; "),
            )
        }
        val reflection = reflectionProvider.reflect(parsedModule)
        val report = reflection.report
            ?: return preparedVerticesRefused(
                GPUPreparedVerticesRefusalCodes.Material,
                "parser reflection unavailable",
            )
        if (!report.validation.success || report.unsupportedFeatures.isNotEmpty()) {
            return preparedVerticesRefused(
                GPUPreparedVerticesRefusalCodes.Material,
                "Prepared vertices reflection did not prove a supported module",
            )
        }
        if (report.entryPoints.map { it.name to it.stage }.sortedBy { it.first } !=
            EXPECTED_ENTRY_POINTS
        ) {
            return preparedVerticesRefused(
                GPUPreparedVerticesRefusalCodes.Material,
                "Prepared vertices reflection did not prove the exact entry-point ABI",
            )
        }
        preparedVerticesBindingMismatch(fragment, report.bindings)?.let { message ->
            return preparedVerticesRefused(GPUPreparedVerticesRefusalCodes.Material, message)
        }
        preparedVerticesLayoutMismatch(fragment, report.layouts)?.let { message ->
            return preparedVerticesRefused(GPUPreparedVerticesRefusalCodes.Material, message)
        }

        val parsed = parseWgslResult(source)
        val interfaceFacts = parsedInterfaceFacts(parsed)
            ?: return preparedVerticesRefused(
                GPUPreparedVerticesRefusalCodes.Material,
                "Prepared vertices vertex/fragment interface could not be proven",
            )
        preparedVerticesInterfaceMismatch(interfaceFacts, hasColor, hasTexCoord)?.let { message ->
            return preparedVerticesRefused(GPUPreparedVerticesRefusalCodes.Material, message)
        }
        val lowered = runCatching { Lowerer().lower(parsed.translationUnit) }
            .getOrElse {
                return preparedVerticesRefused(
                    GPUPreparedVerticesRefusalCodes.Material,
                    "Prepared vertices module could not be lowered",
                )
            }
        val materialSignatureProven =
            lowered.hasMaterialColorFunctionSignature(MATERIAL_EVALUATION_FUNCTION)
        if (!materialSignatureProven) {
            return preparedVerticesRefused(
                GPUPreparedVerticesRefusalCodes.Material,
                "Prepared vertices material evaluation signature was not proven",
            )
        }

        val vertexLayoutHash = preparedVerticesVertexLayoutHash(layout)
        val bindingLayoutHash = preparedVerticesBindingLayoutHash(fragment)
        val reflectedAbiHash = preparedVerticesReflectedAbiHash(
            report = report,
            interfaceFacts = interfaceFacts,
            materialSignatureProven = materialSignatureProven,
        )
        val pipelineKeyHash = preparedVerticesPipelineKeyHash(
            vertexLayoutHash = vertexLayoutHash,
            bindingLayoutHash = bindingLayoutHash,
            reflectedAbiHash = reflectedAbiHash,
            topology = topology,
            material = material,
        )
        return GPUPreparedVerticesShaderResult.Ready(
            GPUPreparedVerticesShaderProgram(
                wgslSource = source,
                vertexEntryPoint = VERTEX_ENTRY_POINT,
                fragmentEntryPoint = FRAGMENT_ENTRY_POINT,
                vertexLayoutHash = vertexLayoutHash,
                bindingLayoutHash = bindingLayoutHash,
                reflectedAbiHash = reflectedAbiHash,
                pipelineKeyHash = pipelineKeyHash,
            ),
        )
    }
}

private fun preparedVerticesRefused(
    code: String,
    message: String,
): GPUPreparedVerticesShaderResult.Refused =
    GPUPreparedVerticesShaderResult.Refused(code = code, message = message)

private fun preparedVerticesShaderSource(
    hasColor: Boolean,
    hasTexCoord: Boolean,
    fragment: GPUPreparedMaterialFragment,
): String = listOf(
    """
struct PreparedVerticesDrawUniforms {
    localToDevice: mat3x3<f32>,
    targetSize: vec2<f32>,
    _padding: vec2<f32>,
}
""".trimIndent(),
    buildString {
        append("struct PreparedVerticesVertexInput {\n")
        append("    @location(0) position: vec2<f32>,\n")
        if (hasColor) {
            append("    @location(1) primitiveColor: vec4<f32>,\n")
        }
        if (hasTexCoord) {
            append("    @location(2) texCoord: vec2<f32>,\n")
        }
        append("}")
    },
    buildString {
        append("struct PreparedVerticesVertexOutput {\n")
        append("    @builtin(position) position: vec4<f32>,\n")
        append(
            "    @location(0) @interpolate(perspective, center) " +
                "localPosition: vec2<f32>,\n",
        )
        if (hasColor) {
            append(
                "    @location(1) @interpolate(perspective, center) " +
                    "primitiveColor: vec4<f32>,\n",
            )
        }
        append("}")
    },
    """
@group(0) @binding(0) var<uniform> preparedVerticesDraw: PreparedVerticesDrawUniforms;
""".trimIndent(),
    buildString {
        append("@vertex\n")
        append("fn vs_main(input: PreparedVerticesVertexInput) -> PreparedVerticesVertexOutput {\n")
        append(
            "    let transformed = " +
                "preparedVerticesDraw.localToDevice * vec3<f32>(input.position, 1.0);\n",
        )
        append("    let ndc = vec2<f32>(\n")
        append(
            "        transformed.x / preparedVerticesDraw.targetSize.x * 2.0 - 1.0,\n",
        )
        append(
            "        1.0 - transformed.y / preparedVerticesDraw.targetSize.y * 2.0,\n",
        )
        append("    );\n")
        append("    var output: PreparedVerticesVertexOutput;\n")
        append("    output.position = vec4<f32>(ndc, 0.0, 1.0);\n")
        append("    output.localPosition = input.position;\n")
        if (hasColor) {
            append("    output.primitiveColor = input.primitiveColor;\n")
        }
        append("    return output;\n")
        append("}")
    },
    fragment.declarationsWgsl + "\n\n" + fragment.evaluationFunctionWgsl,
    buildString {
        append("@fragment\n")
        append(
            "fn fs_main(input: PreparedVerticesVertexOutput) -> " +
                "@location(0) vec4<f32> {\n",
        )
        append("    let materialPremul = kanvas_evaluate_material(input.localPosition);\n")
        if (hasColor) {
            append("    return materialPremul * input.primitiveColor;\n")
        } else {
            append("    return materialPremul;\n")
        }
        append("}")
    },
).joinToString("\n\n")

private fun preparedVerticesBindingMismatch(
    fragment: GPUPreparedMaterialFragment,
    bindings: List<WgslBindingReflection>,
): String? {
    val expected = buildList {
        add(
            PreparedBindingFacts(
                group = DRAW_UNIFORMS_GROUP,
                binding = DRAW_UNIFORMS_BINDING,
                resourceKind = "uniformBuffer",
                minBindingSize = DRAW_UNIFORMS_SIZE_BYTES,
                sampleType = null,
                viewDimension = null,
            ),
        )
        fragment.uniformBinding?.let { uniformBinding ->
            add(
                PreparedBindingFacts(
                    group = uniformBinding.group,
                    binding = uniformBinding.binding,
                    resourceKind = "uniformBuffer",
                    minBindingSize = uniformBinding.minBindingSizeBytes,
                    sampleType = null,
                    viewDimension = null,
                ),
            )
        }
        fragment.sampledBindings.forEach { sampledBinding ->
            add(
                PreparedBindingFacts(
                    group = sampledBinding.textureGroup,
                    binding = sampledBinding.textureBinding,
                    resourceKind = "sampledTexture",
                    minBindingSize = null,
                    sampleType = "float",
                    viewDimension = "2d",
                ),
            )
            add(
                PreparedBindingFacts(
                    group = sampledBinding.samplerGroup,
                    binding = sampledBinding.samplerBinding,
                    resourceKind = "sampler",
                    minBindingSize = null,
                    sampleType = null,
                    viewDimension = null,
                ),
            )
        }
    }.sortedWith(compareBy({ it.group }, { it.binding }))
    val actual = bindings
        .map {
            PreparedBindingFacts(
                group = it.group,
                binding = it.binding,
                resourceKind = it.resourceKind,
                minBindingSize = it.minBindingSize,
                sampleType = it.sampleType,
                viewDimension = it.viewDimension,
            )
        }
        .sortedWith(compareBy({ it.group }, { it.binding }))
    return if (actual == expected) {
        null
    } else {
        "Prepared vertices reflected bindings did not match the exact binding ABI"
    }
}

private fun preparedVerticesLayoutMismatch(
    fragment: GPUPreparedMaterialFragment,
    layouts: List<WgslLayoutReflection>,
): String? {
    val drawLayout = layouts.singleOrNull { it.structName == DRAW_UNIFORMS_STRUCT_NAME }
        ?: return "Prepared vertices draw-uniform layout was not reflected"
    val expectedMembers = listOf(
        PreparedLayoutMember("localToDevice", "mat3x3<f32>", 0, 48, 16, 16),
        PreparedLayoutMember("targetSize", "vec2<f32>", 48, 8, 8, null),
        PreparedLayoutMember("_padding", "vec2<f32>", 56, 8, 8, null),
    )
    if (drawLayout.addressSpace != "uniform" ||
        drawLayout.size != DRAW_UNIFORMS_SIZE_BYTES ||
        drawLayout.alignment != 16
    ) {
        return "Prepared vertices draw-uniform layout was not reflected exactly"
    }
    if (drawLayout.members.map {
            PreparedLayoutMember(
                name = it.name,
                type = it.type,
                offset = it.offset,
                size = it.size,
                alignment = it.alignment,
                stride = it.stride,
            )
        } != expectedMembers
    ) {
        return "Prepared vertices draw-uniform layout members were not reflected exactly"
    }
    fragment.uniformBinding?.let { uniformBinding ->
        if (layouts.none { layout ->
                layout.addressSpace == "uniform" &&
                    layout.structName != DRAW_UNIFORMS_STRUCT_NAME &&
                    layout.size == uniformBinding.minBindingSizeBytes
            }
        ) {
            return "Prepared vertices material uniform layout was not reflected"
        }
    }
    return null
}

private fun parsedInterfaceFacts(parsed: ParseResult): ParsedInterfaceFacts? {
    if (!parsed.isSuccess) return null
    val declarations = parsed.translationUnit.declarations
    val input = declarations
        .filterIsInstance<StructDecl>()
        .singleOrNull { it.name == VERTEX_INPUT_STRUCT_NAME }
        ?: return null
    val output = declarations
        .filterIsInstance<StructDecl>()
        .singleOrNull { it.name == VERTEX_OUTPUT_STRUCT_NAME }
        ?: return null
    val fragment = declarations
        .filterIsInstance<FunctionDecl>()
        .singleOrNull { it.name == FRAGMENT_ENTRY_POINT }
        ?: return null
    val inputMembers = input.members.map { member ->
        parsedInterfaceMember(member) ?: return null
    }
    val outputMembers = output.members.map { member ->
        parsedInterfaceMember(member) ?: return null
    }
    val fragmentReturnType = fragment.returnType?.let(::wgslTypeNameOrNull) ?: return null
    val fragmentReturnLocation = fragment.returnAttributes
        .singleOrNull { it.name == "location" }
        ?.args
        ?.singleOrNull()
        ?.let { it as? IntLiteral }
        ?.value
        ?: return null
    return ParsedInterfaceFacts(
        inputMembers = inputMembers,
        outputMembers = outputMembers,
        fragmentReturnType = fragmentReturnType,
        fragmentReturnLocation = fragmentReturnLocation,
    )
}

private fun parsedInterfaceMember(member: StructMember): ParsedInterfaceMember? {
    val type = wgslTypeNameOrNull(member.type) ?: return null
    val location = member.attributes
        .singleOrNull { it.name == "location" }
        ?.args
        ?.singleOrNull()
        ?.let { it as? IntLiteral }
        ?.value
    val builtin = member.attributes
        .singleOrNull { it.name == "builtin" }
        ?.args
        ?.singleOrNull()
        ?.let { it as? IdentExpr }
        ?.name
    val interpolation = member.attributes
        .singleOrNull { it.name == "interpolate" }
        ?.args
        ?.map { argument -> (argument as? IdentExpr)?.name ?: return null }
        ?: emptyList()
    return ParsedInterfaceMember(
        name = member.name,
        type = type,
        location = location,
        builtin = builtin,
        interpolation = interpolation,
    )
}

private fun preparedVerticesInterfaceMismatch(
    facts: ParsedInterfaceFacts,
    hasColor: Boolean,
    hasTexCoord: Boolean,
): String? {
    val expectedInput = buildList {
        add(ParsedInterfaceMember("position", "vec2<f32>", 0, null, emptyList()))
        if (hasColor) {
            add(ParsedInterfaceMember("primitiveColor", "vec4<f32>", 1, null, emptyList()))
        }
        if (hasTexCoord) {
            add(ParsedInterfaceMember("texCoord", "vec2<f32>", 2, null, emptyList()))
        }
    }
    if (facts.inputMembers != expectedInput) {
        return "Prepared vertices vertex-input interface was not proven exactly"
    }
    val expectedOutput = buildList {
        add(ParsedInterfaceMember("position", "vec4<f32>", null, "position", emptyList()))
        add(
            ParsedInterfaceMember(
                "localPosition",
                "vec2<f32>",
                0,
                null,
                listOf("perspective", "center"),
            ),
        )
        if (hasColor) {
            add(
                ParsedInterfaceMember(
                    "primitiveColor",
                    "vec4<f32>",
                    1,
                    null,
                    listOf("perspective", "center"),
                ),
            )
        }
    }
    if (facts.outputMembers != expectedOutput) {
        return "Prepared vertices vertex-output interface was not proven exactly"
    }
    if (facts.fragmentReturnType != "vec4<f32>" || facts.fragmentReturnLocation != 0L) {
        return "Prepared vertices fragment-output interface was not proven exactly"
    }
    return null
}

private fun preparedVerticesVertexLayoutHash(layout: GPUVertexLayoutPlan): String =
    CanonicalIdentityEncoder("prepared-vertices-vertex-layout-v1")
        .texts("attributes", layout.attributes)
        .int("strideBytes", layout.strideBytes)
        .texts(
            "offsets",
            layout.offsets.toSortedMap().map { (name, offset) -> "$name=$offset" },
        )
        .texts(
            "shaderLocations",
            layout.shaderLocations.toSortedMap().map { (name, location) -> "$name=$location" },
        )
        .digestIdentity()

private fun preparedVerticesBindingLayoutHash(
    fragment: GPUPreparedMaterialFragment,
): String =
    CanonicalIdentityEncoder("prepared-vertices-binding-layout-v1")
        .text(
            "drawBinding",
            "group=$DRAW_UNIFORMS_GROUP;binding=$DRAW_UNIFORMS_BINDING;" +
                "kind=uniformBuffer;size=$DRAW_UNIFORMS_SIZE_BYTES",
        )
        .text(
            "materialUniform",
            fragment.uniformBinding?.let { uniformBinding ->
                "group=${uniformBinding.group};binding=${uniformBinding.binding};" +
                    "size=${uniformBinding.minBindingSizeBytes}"
            } ?: "none",
        )
        .texts(
            "sampledBindings",
            fragment.sampledBindings.map { sampledBinding ->
                "texture=${sampledBinding.textureGroup}:${sampledBinding.textureBinding};" +
                    "sampler=${sampledBinding.samplerGroup}:${sampledBinding.samplerBinding}"
            },
        )
        .digestIdentity()

private fun preparedVerticesReflectedAbiHash(
    report: WgslReflectionReport,
    interfaceFacts: ParsedInterfaceFacts,
    materialSignatureProven: Boolean,
): String =
    CanonicalIdentityEncoder("prepared-vertices-reflected-abi-v1")
        .text("reflectionFacts", report.reflectionFactsHash())
        .texts("vertexInputFacts", interfaceFacts.inputMembers.map(ParsedInterfaceMember::fact))
        .texts("vertexOutputFacts", interfaceFacts.outputMembers.map(ParsedInterfaceMember::fact))
        .text(
            "fragmentOutputFacts",
            "type=${interfaceFacts.fragmentReturnType};" +
                "location=${interfaceFacts.fragmentReturnLocation}",
        )
        .text("materialSignature", materialSignatureProven.toString())
        .digestIdentity()

private fun preparedVerticesPipelineKeyHash(
    vertexLayoutHash: String,
    bindingLayoutHash: String,
    reflectedAbiHash: String,
    topology: GPUVertexMode,
    material: GPUPreparedMaterialProgram,
): String =
    CanonicalIdentityEncoder("prepared-vertices-pipeline-key-v1")
        .text("vertexLayoutHash", vertexLayoutHash)
        .text("bindingLayoutHash", bindingLayoutHash)
        .text("reflectedAbiHash", reflectedAbiHash)
        .text("topology", topology.sourceLabel)
        .text("vertexEntryPoint", VERTEX_ENTRY_POINT)
        .text("fragmentEntryPoint", FRAGMENT_ENTRY_POINT)
        .text("materialKey", material.materialKey)
        .text("materialAbiHash", material.abiHash)
        .digestIdentity()

private fun wgslTypeNameOrNull(type: TypeDecl): String? = when (type) {
    is ScalarType -> when (type.kind) {
        ScalarKind.F32 -> "f32"
        else -> null
    }
    is VectorType -> wgslTypeNameOrNull(type.elementType)?.let { "vec${type.size}<$it>" }
    is MatrixType -> wgslTypeNameOrNull(type.elementType)?.let {
        "mat${type.columns}x${type.rows}<$it>"
    }
    else -> null
}

private fun ParsedInterfaceMember.fact(): String = buildString {
    append("name=$name;type=$type")
    location?.let { location -> append(";location=$location") }
    builtin?.let { builtin -> append(";builtin=$builtin") }
    if (interpolation.isNotEmpty()) {
        append(";interpolate=${interpolation.joinToString(":")}")
    }
}

private data class ParsedInterfaceFacts(
    val inputMembers: List<ParsedInterfaceMember>,
    val outputMembers: List<ParsedInterfaceMember>,
    val fragmentReturnType: String,
    val fragmentReturnLocation: Long,
)

private data class ParsedInterfaceMember(
    val name: String,
    val type: String,
    val location: Long?,
    val builtin: String?,
    val interpolation: List<String>,
)

private data class PreparedBindingFacts(
    val group: Int,
    val binding: Int,
    val resourceKind: String,
    val minBindingSize: Int?,
    val sampleType: String?,
    val viewDimension: String?,
)

private data class PreparedLayoutMember(
    val name: String,
    val type: String,
    val offset: Int,
    val size: Int,
    val alignment: Int,
    val stride: Int?,
)

private const val VERTEX_ENTRY_POINT = "vs_main"
private const val FRAGMENT_ENTRY_POINT = "fs_main"
private const val MATERIAL_EVALUATION_FUNCTION = "kanvas_evaluate_material"
private const val VERTEX_INPUT_STRUCT_NAME = "PreparedVerticesVertexInput"
private const val VERTEX_OUTPUT_STRUCT_NAME = "PreparedVerticesVertexOutput"
private const val DRAW_UNIFORMS_STRUCT_NAME = "PreparedVerticesDrawUniforms"
private const val DRAW_UNIFORMS_GROUP = 0
private const val DRAW_UNIFORMS_BINDING = 0
private const val DRAW_UNIFORMS_SIZE_BYTES = 64
private val EXPECTED_ENTRY_POINTS = listOf(
    FRAGMENT_ENTRY_POINT to "fragment",
    VERTEX_ENTRY_POINT to "vertex",
)
