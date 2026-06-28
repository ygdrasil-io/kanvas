package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.wgsl.ir.StorageClass
import org.graphiks.wgsl.ir.TypeInner
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult
import java.security.MessageDigest

class KanvasWGSLReflectionProvider : WGSLReflectionProvider {

    override fun reflect(module: WGSLParsedModule): WGSLReflectionResult {
        return try {
            parserBackedReflect(module)
        } catch (_: NoClassDefFoundError) {
            fixtureBackedReflect(module)
        } catch (_: ClassNotFoundException) {
            fixtureBackedReflect(module)
        }
    }

    private fun parserBackedReflect(module: WGSLParsedModule): WGSLReflectionResult {
        val source = module.source.ifBlank { return fixtureBackedReflect(module) }
        val parsed = parseWgslResult(source)
        val lowered = Lowerer().lower(parsed.translationUnit)

        val uniformCount = lowered.globalVariables.count {
            it.storageClass == StorageClass.Uniform
        }
        val textureCount = lowered.globalVariables.count {
            val inner = lowered.types[it.type]?.inner
            inner is TypeInner.Opaque
        }
        val seenGroups = mutableSetOf<Int>()
        for (global in lowered.globalVariables) {
            global.binding?.let { binding -> seenGroups.add(binding.group) }
        }
        val bindGroupCount = seenGroups.size
        val entryPointName = lowered.entryPoints.firstOrNull()?.name ?: "main"
        val moduleHash = sha256("custom-module:$uniformCount:$textureCount:$bindGroupCount")
        val reflectionHash = sha256("$moduleHash:$entryPointName:reflection-v1")

        return WGSLReflectionResult(
            moduleHash = moduleHash,
            entryPoint = entryPointName,
            uniformCount = uniformCount,
            textureCount = textureCount,
            bindGroupCount = bindGroupCount,
            reflectionHash = reflectionHash,
        )
    }

    private fun fixtureBackedReflect(module: WGSLParsedModule): WGSLReflectionResult {
        val moduleHash = sha256("fixture:${module.sourceHash}")
        return WGSLReflectionResult(
            moduleHash = moduleHash,
            entryPoint = "main",
            uniformCount = module.uniforms.size,
            textureCount = module.textures.size,
            bindGroupCount = module.bindGroups.size,
            reflectionHash = sha256("$moduleHash:fixture-reflection"),
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
