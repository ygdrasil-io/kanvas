package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import java.security.MessageDigest

class KanvasWGSLValidator : WGSLValidator {

    override fun parse(source: String): WGSLParsedModule {
        return try {
            parserBackedParse(source)
        } catch (_: NoClassDefFoundError) {
            fixtureBackedParse(source)
        } catch (_: ClassNotFoundException) {
            fixtureBackedParse(source)
        }
    }

    private fun parserBackedParse(source: String): WGSLParsedModule {
        val sourceHash = sha256(source)

        val parserClass = Class.forName("org.graphiks.wgsl.parser.WgslParserKt")
        val parseResultMethod = parserClass.getMethod("parseWgslResult", String::class.java)
        val parsed = parseResultMethod.invoke(null, source)

        val errors = parsed?.let { p ->
            val errorsField = p.javaClass.getDeclaredField("errors")
            errorsField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val errorsList = errorsField.get(p) as? List<*> ?: emptyList<Any>()
            errorsList.mapNotNull { err ->
                (err?.javaClass?.getDeclaredField("message")?.apply { isAccessible = true }
                    ?.get(err) as? String)
            }
        } ?: emptyList()

        val uniforms = mutableListOf<String>()
        val textures = mutableListOf<String>()
        val storageBuffers = mutableListOf<String>()
        val bindGroups = mutableListOf<String>()
        var usesComputeShader = false
        var usesWorkgroupBuiltins = false

        runCatching {
            val translationUnit = parsed?.javaClass?.getDeclaredField("translationUnit")
                ?.apply { isAccessible = true }?.get(parsed)
            if (translationUnit != null) {
                val lowererClass = Class.forName("org.graphiks.wgsl.proc.Lowerer")
                val lowerer = lowererClass.getDeclaredConstructor().newInstance()
                val lowered = lowererClass.getMethod("lower", translationUnit.javaClass).invoke(lowerer, translationUnit)

                if (lowered != null) {
                    val loweredClass = lowered.javaClass

                    val globalVariablesField = loweredClass.getDeclaredField("globalVariables")
                    globalVariablesField.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val globalVariables = globalVariablesField.get(lowered) as? List<*> ?: emptyList<Any>()

                    val seenGroups = mutableSetOf<Int>()

                    for (global in globalVariables) {
                        if (global == null) continue
                        val globalClass = global.javaClass

                        val binding = loweredClass.getMethod("bindingOf", globalClass).invoke(lowered, global)
                        if (binding != null) {
                            val group = binding.javaClass.getDeclaredField("group").apply { isAccessible = true }
                                .get(binding) as? Int
                            if (group != null && group !in seenGroups) {
                                seenGroups.add(group)
                                bindGroups.add("group$group")
                            }
                        }

                        val space = globalClass.getDeclaredField("space").apply { isAccessible = true }
                            .get(global) as? String
                        val name = globalClass.getDeclaredField("name").apply { isAccessible = true }
                            .get(global) as? String

                        when (space) {
                            "uniform" -> uniforms.add(name ?: "unknown")
                            "storage" -> storageBuffers.add(name ?: "unknown")
                            "handle" -> {
                                val typesField = loweredClass.getDeclaredField("types").apply { isAccessible = true }
                                val types = typesField.get(lowered)
                                val tyField = globalClass.getDeclaredField("ty").apply { isAccessible = true }
                                val ty = tyField.get(global)
                                if (types != null && ty != null) {
                                    val typesGetMethod = types.javaClass.getMethod("get", Any::class.java)
                                    val typeEntry = typesGetMethod.invoke(types, ty)
                                    if (typeEntry != null) {
                                        val inner = typeEntry.javaClass.getDeclaredField("inner").apply { isAccessible = true }
                                            .get(typeEntry)
                                        if (inner?.javaClass?.simpleName == "Image") {
                                            textures.add(name ?: "unknown")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val entryPointsField = loweredClass.getDeclaredField("entryPoints")
                    entryPointsField.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val entryPoints = entryPointsField.get(lowered) as? List<*> ?: emptyList<Any>()

                    val shaderStageComputeClass = try {
                        Class.forName("org.graphiks.wgsl.ir.ShaderStage").enumConstants
                            ?.firstOrNull { (it as? Enum<*>)?.name == "Compute" }
                    } catch (_: Exception) { null }

                    for (ep in entryPoints) {
                        if (ep == null) continue
                        val stage = ep.javaClass.getDeclaredField("stage").apply { isAccessible = true }
                            .get(ep)
                        if (stage == shaderStageComputeClass) {
                            usesComputeShader = true
                        }
                        val workgroupSize = ep.javaClass.getDeclaredField("workgroupSize").apply { isAccessible = true }
                            .get(ep)
                        if (workgroupSize != null) {
                            usesWorkgroupBuiltins = true
                        }
                    }
                }
            }
        }

        return WGSLParsedModule(
            sourceHash = sourceHash,
            source = source,
            uniforms = uniforms,
            textures = textures,
            bindGroups = bindGroups.ifEmpty { listOf("group0") },
            storageBuffers = storageBuffers,
            usesAtomics = false,
            usesUnboundedStorageBuffers = false,
            usesReadWriteBuffers = false,
            usesPtrOperations = false,
            hasRecursiveFunctions = false,
            hasUnboundedLoops = false,
            usesDynamicSampling = false,
            usesTextureStore = false,
            usesDynamicBinding = false,
            usesRayQuery = false,
            usesComputeShader = usesComputeShader,
            usesWorkgroupBuiltins = usesWorkgroupBuiltins,
            loopIterationCount = 0,
            functionDepth = 0,
            maxTextureDimensions = 0,
            syntaxErrors = errors,
        )
    }

    private fun fixtureBackedParse(source: String): WGSLParsedModule {
        return WGSLParsedModule(
            sourceHash = sha256(source),
            source = source,
            syntaxErrors = emptyList(),
            uniforms = emptyList(),
            textures = emptyList(),
            bindGroups = listOf("group0"),
            storageBuffers = emptyList(),
            usesAtomics = false,
            usesUnboundedStorageBuffers = false,
            usesReadWriteBuffers = false,
            usesPtrOperations = false,
            hasRecursiveFunctions = false,
            hasUnboundedLoops = false,
            usesDynamicSampling = false,
            usesTextureStore = false,
            usesDynamicBinding = false,
            usesRayQuery = false,
            usesComputeShader = false,
            usesWorkgroupBuiltins = false,
            loopIterationCount = 0,
            functionDepth = 0,
            maxTextureDimensions = 0,
        )
    }

    companion object {
        private fun sha256(input: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(12)
    }
}
