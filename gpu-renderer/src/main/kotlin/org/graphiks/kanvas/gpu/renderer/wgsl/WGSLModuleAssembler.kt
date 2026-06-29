package org.graphiks.kanvas.gpu.renderer.wgsl

import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult
import org.graphiks.wgsl.proc.reflectWgslModule
import java.security.MessageDigest

/**
 * Deterministic WGSL render module assembler for declared ABI fixtures.
 *
 * The assembler owns source materialization from Kanvas descriptors only; it
 * does not compile shaders, allocate backend objects, or cache modules. Entry
 * point, binding, facade-limit, feature, and packing failures produce
 * [WGSLModuleAssemblyResult.Rejected] with terminal diagnostics before any
 * backend shader creation can be claimed. When [WGSLParserState.parserBacked]
 * is false, reflection is explicitly labeled `fixture-declared` so callers
 * know the ABI came from descriptors rather than a live `wgsl4k` parse.
 */
object WGSLModuleAssembler {
    /**
     * Assembles a complete render module from structured WGSL descriptors.
     *
     * Accepted modules carry deterministic source text, descriptor-owned
     * bindings, uniform/storage layouts, packing plans, parser state, and
     * non-terminal diagnostics. Rejected modules carry the computed module hash
     * and every terminal diagnostic; callers must not reinterpret rejection as
     * CPU fallback success or as a backend-ready shader.
     */
    fun assembleRenderModule(input: WGSLModuleAssemblyInput): WGSLModuleAssemblyResult {
        val source = input.deterministicSource()
        val moduleHash = WGSLModuleHash("wgsl-module:${source.stableHash()}")
        val diagnostics = input.validationDiagnostics(moduleHash)

        if (diagnostics.any { it.terminal }) {
            return WGSLModuleAssemblyResult.Rejected(
                moduleHash = moduleHash,
                diagnostics = diagnostics,
            )
        }

        val (reflection, parserDiagnostic) = tryParserBackedReflection(source, moduleHash, input)

        return WGSLModuleAssemblyResult.Accepted(
            WGSLModule(
                moduleHash = moduleHash,
                entryPoint = input.fragmentEntryPoint,
                fragments = input.fragments.sortedBy { it.fragmentId },
                bindings = reflection.bindings,
                uniformLayouts = reflection.uniforms,
                storageLayouts = reflection.storage,
                reflection = reflection,
                rendererVersionSalt = input.moduleSalt,
                moduleLabel = input.moduleLabel,
                moduleSalt = input.moduleSalt,
                vertexEntryPoint = input.vertexEntryPoint,
                fragmentEntryPoint = input.fragmentEntryPoint,
                packingPlans = input.packingPlans,
                parserState = reflection.parserState,
                source = source,
                diagnostics = listOf(parserDiagnostic),
            ),
        )
    }
}

/** Emits a deterministic ABI dump for module reflection and packing fixtures. */
fun WGSLModule.abiDump(): String {
    val reflectionSource = when (val result = reflection) {
        is WGSLReflectionResult.Accepted -> result.reflectionSource
        is WGSLReflectionResult.Rejected -> "rejected"
    }

    return buildList {
        add("module=$moduleLabel")
        add("moduleHash=${moduleHash.value}")
        add("entryPoints=vertex:$vertexEntryPoint,fragment:$fragmentEntryPoint")
        add("parser=${parserState.status}:${parserState.toolName}")
        bindings.sortedWith(compareBy<WGSLBindingLayout> { it.group }.thenBy { it.binding }.thenBy { it.layoutRole })
            .forEach { binding ->
                add(
                    "binding=${binding.group}/${binding.binding} ${binding.layoutRole} " +
                        "${binding.resourceKind} min=${binding.minBindingSize ?: 0}",
                )
            }
        uniformLayouts.forEach { layout ->
            val fieldDump = layout.fieldLayouts.joinToString(", ") { field ->
                "${field.name}:${field.type}@${field.offset}/${field.sizeBytes}"
            }
            add("uniform=${layout.layoutHash} size=${layout.sizeBytes} align=${layout.alignment} fields=$fieldDump")
        }
        packingPlans.forEach { plan ->
            val fields = plan.fieldOrder.joinToString(",") { field ->
                "$field@${plan.offsets.getValue(field)}"
            }
            add(
                "packing=${plan.planHash} layout=${plan.layoutHash} " +
                    "fields=$fields padding=${plan.paddingBytes}",
            )
        }
        add("reflection=$reflectionSource")
    }.joinToString("\n")
}

/** Attempts wgsl4k parser-backed reflection; falls back to fixture-declared if unavailable. */
private fun tryParserBackedReflection(
    source: String,
    moduleHash: WGSLModuleHash,
    input: WGSLModuleAssemblyInput,
): Pair<WGSLReflectionResult.Accepted, WGSLValidationDiagnostic> {
    return try {
        parserBackedReflection(source, moduleHash, input)
    } catch (_: NoClassDefFoundError) {
        fixtureDeclaredReflection(moduleHash, input)
    } catch (_: ClassNotFoundException) {
        fixtureDeclaredReflection(moduleHash, input)
    }
}

/** Produces parser-backed WGSL reflection using live wgsl4k parse and layout computation. */
private fun parserBackedReflection(
    source: String,
    moduleHash: WGSLModuleHash,
    input: WGSLModuleAssemblyInput,
): Pair<WGSLReflectionResult.Accepted, WGSLValidationDiagnostic> {
    val parsed = parseWgslResult(source)

    if (!parsed.isSuccess) {
        val errorMessages = parsed.errors.joinToString("; ") { "${it.message} span=${it.span}" }
        val diagnostic = WGSLValidationDiagnostic(
            code = "info.wgsl.parser_error",
            moduleHash = moduleHash,
            message = "wgsl4k parse produced diagnostics: $errorMessages",
            terminal = false,
        )
        val (reflection, _) = fixtureDeclaredReflection(moduleHash, input)
        return Pair(reflection, diagnostic)
    }

    // Lower the parsed translation unit and reflect it through the full
    // wgsl4k WgslReflectionReport rather than reflecting structs manually.
    val module = Lowerer().lower(parsed.translationUnit)
    val report = module.reflectWgslModule(
        sourceId = input.moduleLabel,
        moduleHash = moduleHash.value,
    )

    // Reflected layouts are keyed back to descriptor-owned layout hashes by
    // their member-name signature; the report supplies real offsets/types.
    val inputLayoutByFieldNames = input.uniformLayouts.associateBy { layout ->
        layout.fieldLayouts.map { field -> field.name }
    }

    val reflectedUniforms = report.layouts
        .filter { layout -> layout.addressSpace == "uniform" }
        .mapNotNull { layout ->
            val fields = layout.members.map { member ->
                WGSLUniformFieldLayout(
                    name = member.name,
                    type = member.type,
                    offset = member.offset.toLong(),
                    sizeBytes = member.size.toLong(),
                    alignment = member.alignment,
                )
            }
            val layoutHash = inputLayoutByFieldNames[fields.map { it.name }]?.layoutHash
                ?: return@mapNotNull null
            WGSLUniformLayout(
                layoutHash = layoutHash,
                fields = fields.map { it.name },
                fieldLayouts = fields,
                sizeBytes = layout.size.toLong(),
                alignment = layout.alignment,
                numericRepresentation = "f32/u32",
            )
        }

    val parserState = WGSLParserState(
        status = "parser-backed",
        toolName = "wgsl4k",
        message = "reflection produced by live wgsl4k parse",
    )

    val parserDiagnostic = WGSLValidationDiagnostic(
        code = "info.wgsl.parser_backed",
        moduleHash = moduleHash,
        message = "wgsl4k parser-backed reflection active",
        terminal = false,
    )

    val reflection = WGSLReflectionResult.Accepted(
        moduleHash = moduleHash,
        bindings = input.allBindings(),
        uniforms = reflectedUniforms.ifEmpty { input.uniformLayouts },
        storage = input.storageLayouts.sortedBy { it.layoutHash },
        diagnostics = listOf(parserDiagnostic),
        parserState = parserState,
        reflectionSource = "wgsl4k-parsed",
    )

    return Pair(reflection, parserDiagnostic)
}

/** Produces the legacy fixture-declared reflection when wgsl4k parser is unavailable. */
private fun fixtureDeclaredReflection(
    moduleHash: WGSLModuleHash,
    input: WGSLModuleAssemblyInput,
): Pair<WGSLReflectionResult.Accepted, WGSLValidationDiagnostic> {
    val parserDiagnostic = WGSLValidationDiagnostic(
        code = "info.wgsl.parser_unavailable",
        moduleHash = moduleHash,
        fieldOrBinding = input.parserState.toolName,
        message = input.parserState.message,
        terminal = false,
    )
    val reflection = WGSLReflectionResult.Accepted(
        moduleHash = moduleHash,
        bindings = input.allBindings(),
        uniforms = input.uniformLayouts,
        storage = input.storageLayouts.sortedBy { it.layoutHash },
        diagnostics = listOf(parserDiagnostic),
        parserState = input.parserState,
        reflectionSource = "fixture-declared",
    )
    return Pair(reflection, parserDiagnostic)
}

/** Returns all binding layouts contributed to a module in canonical order. */
private fun WGSLModuleAssemblyInput.allBindings(): List<WGSLBindingLayout> =
    (bindings + fragments.flatMap { it.bindingLayouts })
        .sortedWith(compareBy<WGSLBindingLayout> { it.group }.thenBy { it.binding }.thenBy { it.layoutRole })

/** Produces all terminal diagnostics for a render module assembly attempt. */
private fun WGSLModuleAssemblyInput.validationDiagnostics(moduleHash: WGSLModuleHash): List<WGSLValidationDiagnostic> =
    buildList {
        addAll(entryPointDiagnostics(moduleHash))
        addAll(bindingCollisionDiagnostics(moduleHash))
        addAll(capabilityDiagnostics(moduleHash))
        addAll(packingDiagnostics(moduleHash))
    }.sortedWith(compareBy<WGSLValidationDiagnostic> { it.code }.thenBy { it.fieldOrBinding.orEmpty() })

/** Validates complete vertex and fragment entry points. */
private fun WGSLModuleAssemblyInput.entryPointDiagnostics(moduleHash: WGSLModuleHash): List<WGSLValidationDiagnostic> {
    val entryPoints = fragments.flatMap { it.entryPoints }.toSet()
    val missing = listOf(vertexEntryPoint, fragmentEntryPoint)
        .filter { it.isBlank() || it !in entryPoints }

    return missing.map { entryPoint ->
        WGSLValidationDiagnostic(
            code = "unsupported.wgsl.missing_entry_point",
            moduleHash = moduleHash,
            fieldOrBinding = entryPoint.ifBlank { "<blank>" },
            message = "Complete WGSL module entry point is not declared: ${entryPoint.ifBlank { "<blank>" }}",
            terminal = true,
        )
    }
}

/** Validates that bind group and binding pairs are unique. */
private fun WGSLModuleAssemblyInput.bindingCollisionDiagnostics(moduleHash: WGSLModuleHash): List<WGSLValidationDiagnostic> =
    allBindings()
        .groupBy { it.group to it.binding }
        .filterValues { it.size > 1 }
        .map { (slot, bindings) ->
            WGSLValidationDiagnostic(
                code = "unsupported.wgsl.binding_collision",
                moduleHash = moduleHash,
                fieldOrBinding = "group=${slot.first},binding=${slot.second}",
                message = "Binding collision for ${bindings.joinToString { it.layoutRole }}",
                terminal = true,
            )
        }

/** Validates requested WGSL features and facade binding limits. */
private fun WGSLModuleAssemblyInput.capabilityDiagnostics(moduleHash: WGSLModuleHash): List<WGSLValidationDiagnostic> {
    val featureDiagnostics = fragments
        .flatMap { it.requiredFeatures }
        .distinct()
        .filterNot { it in capabilities.supportedFeatures }
        .map { feature ->
            WGSLValidationDiagnostic(
                code = "unsupported.wgsl.feature_unrepresented_by_wgsl4k",
                moduleHash = moduleHash,
                fieldOrBinding = feature,
                message = "WGSL feature is not represented by the current facade fixture: $feature",
                terminal = true,
            )
        }

    val limitDiagnostics = allBindings()
        .filter { it.group > capabilities.maxBindGroup || it.binding > capabilities.maxBinding }
        .map { binding ->
            WGSLValidationDiagnostic(
                code = "unsupported.wgsl.facade_limit",
                moduleHash = moduleHash,
                fieldOrBinding = "group=${binding.group},binding=${binding.binding}",
                message = "WGSL binding exceeds facade fixture limits",
                terminal = true,
            )
        }

    return featureDiagnostics + limitDiagnostics
}

/** Validates Kotlin packing offsets against declared WGSL uniform layouts. */
private fun WGSLModuleAssemblyInput.packingDiagnostics(moduleHash: WGSLModuleHash): List<WGSLValidationDiagnostic> =
    packingPlans.flatMap { packing ->
        val layout = uniformLayouts.singleOrNull { it.layoutHash == packing.layoutHash }
        if (layout == null) {
            listOf(
                WGSLValidationDiagnostic(
                    code = "unsupported.wgsl.packing_plan_missing",
                    moduleHash = moduleHash,
                    fieldOrBinding = packing.layoutHash,
                    message = "Packing plan references missing uniform layout: ${packing.layoutHash}",
                    terminal = true,
                ),
            )
        } else {
            val expectedOrder = layout.fieldLayouts.map { it.name }
            val orderDiagnostic = if (expectedOrder.isNotEmpty() && packing.fieldOrder != expectedOrder) {
                listOf(
                    WGSLValidationDiagnostic(
                        code = "unsupported.wgsl.uniform_alignment_mismatch",
                        moduleHash = moduleHash,
                        fieldOrBinding = packing.layoutHash,
                        message = "Packing field order ${packing.fieldOrder} does not match WGSL layout $expectedOrder",
                        terminal = true,
                    ),
                )
            } else {
                emptyList()
            }

            orderDiagnostic + layout.fieldLayouts.mapNotNull { field ->
                val actualOffset = packing.offsets[field.name]
                if (actualOffset == field.offset) {
                    null
                } else {
                    WGSLValidationDiagnostic(
                        code = "unsupported.wgsl.uniform_alignment_mismatch",
                        moduleHash = moduleHash,
                        fieldOrBinding = "${packing.layoutHash}.${field.name}",
                        message = "Packing offset for ${field.name} was $actualOffset but WGSL layout requires ${field.offset}",
                        terminal = true,
                    )
                }
            }
        }
    }

/** Emits deterministic WGSL source text for hashing and ABI fixtures. */
private fun WGSLModuleAssemblyInput.deterministicSource(): String =
    buildList {
        add("// module=$moduleLabel")
        add("// salt=$moduleSalt")
        val sortedLayouts = uniformLayouts.sortedBy { it.layoutHash }
        sortedLayouts.forEach { layout ->
            add("struct ${layout.structName()} {")
            layout.fieldLayouts.forEach { field ->
                add("  ${field.name}: ${field.type},")
            }
            add("}")
            add("")
        }
        allBindings().forEach { binding ->
            val layout = layoutForBinding(binding)
            if (layout != null && binding.resourceKind == "uniform-buffer") {
                add(
                    "@group(${binding.group}) @binding(${binding.binding}) " +
                        "var<uniform> ${binding.variableName()}: ${layout.structName()};",
                )
            } else {
                add("// binding=${binding.group}/${binding.binding}:${binding.layoutRole}:${binding.resourceKind}")
            }
        }
        fragments.sortedBy { it.fragmentId }.forEach { fragment ->
            add("// fragment=${fragment.fragmentId}:${fragment.sourceHash}:${fragment.entryPoints.sorted().joinToString(",")}")
        }
        add("@vertex fn $vertexEntryPoint() -> @builtin(position) vec4<f32> { return vec4<f32>(0.0, 0.0, 0.0, 1.0); }")
        add(
            if (uniformLayouts.any { layout -> layout.layoutHash.contains("solid-material") }) {
                "@fragment fn $fragmentEntryPoint() -> @location(0) vec4<f32> { return solidMaterial.color; }"
            } else {
                "@fragment fn $fragmentEntryPoint() -> @location(0) vec4<f32> { return vec4<f32>(1.0, 1.0, 1.0, 1.0); }"
            },
        )
    }.joinToString("\n")

/** Returns the uniform layout matched to a binding role for the first assembly fixtures. */
private fun WGSLModuleAssemblyInput.layoutForBinding(binding: WGSLBindingLayout): WGSLUniformLayout? =
    when (binding.layoutRole) {
        "frame" -> uniformLayouts.singleOrNull { it.layoutHash.contains("frame-block") }
        "render-step" -> uniformLayouts.singleOrNull { it.layoutHash.contains("render-step-block") }
        "intrinsic-draw" -> uniformLayouts.singleOrNull { it.layoutHash.contains("intrinsic-draw-block") }
        "material-solid" -> uniformLayouts.singleOrNull { it.layoutHash.contains("solid-material-block") }
        else -> null
    }

/** Returns a stable WGSL struct name for a declared uniform layout. */
private fun WGSLUniformLayout.structName(): String =
    layoutHash
        .substringAfter("layout:")
        .substringBefore(":")
        .split("-")
        .filter { part -> part != "block" && part.isNotBlank() }
        .joinToString("") { part -> part.replaceFirstChar { char -> char.uppercase() } } + "Block"

/** Returns a stable WGSL variable name for a binding role. */
private fun WGSLBindingLayout.variableName(): String =
    when (layoutRole) {
        "frame" -> "frame"
        "render-step" -> "renderStep"
        "intrinsic-draw" -> "intrinsicDraw"
        "material-solid" -> "solidMaterial"
        else -> layoutRole.split("-").mapIndexed { index, part ->
            if (index == 0) part else part.replaceFirstChar { char -> char.uppercase() }
        }.joinToString("")
    }

/** Creates a short stable hash for key and module fixtures. */
private fun String.stableHash(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    return digest.take(16)
}
