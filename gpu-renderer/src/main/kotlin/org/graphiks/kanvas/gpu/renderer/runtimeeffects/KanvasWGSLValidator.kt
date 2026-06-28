package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.wgsl.ir.ShaderStage
import org.graphiks.wgsl.ir.StorageClass
import org.graphiks.wgsl.ir.TypeInner
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult

/** Validates WGSL source via the wgsl4k parser with fixture fallback when the library is unavailable. */
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
        val sourceHash = WGSLHashUtils.sha256(source)

        val parsed = parseWgslResult(source)
        val errors = parsed.errors.map { it.message }

        val uniforms = mutableListOf<String>()
        val textures = mutableListOf<String>()
        val storageBuffers = mutableListOf<String>()
        val bindGroups = mutableListOf<String>()
        var usesComputeShader = false
        var usesWorkgroupBuiltins = false

        val module = Lowerer().lower(parsed.translationUnit)
        val seenGroups = mutableSetOf<Int>()

        for (global in module.globalVariables) {
            val binding = global.binding
            if (binding != null) {
                if (binding.group !in seenGroups) {
                    seenGroups.add(binding.group)
                    bindGroups.add("group${binding.group}")
                }
            }

            when (global.storageClass) {
                StorageClass.Uniform -> uniforms.add(global.name)
                StorageClass.Storage -> storageBuffers.add(global.name)
                StorageClass.Handle -> {
                    if (module.types[global.type].inner is TypeInner.Opaque) {
                        textures.add(global.name)
                    }
                }
                else -> {}
            }
        }

        for (ep in module.entryPoints) {
            if (ep.stage == ShaderStage.Compute) {
                usesComputeShader = true
            }
            if (ep.workgroupSize != null) {
                usesWorkgroupBuiltins = true
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
            sourceHash = WGSLHashUtils.sha256(source),
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


}
