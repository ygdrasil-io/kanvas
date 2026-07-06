package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult
import org.graphiks.kanvas.gpu.renderer.wgsl.reflectWgslModule

/** Reflects on parsed WGSL through parser-backed reflection with fixture fallback. */
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

        val report = lowered.reflectWgslModule(
            sourceId = module.sourceHash.ifBlank { "custom-module" },
        )

        val uniformCount = report.bindings.count { it.resourceKind == "uniformBuffer" }
        val textureCount = report.bindings.count { it.resourceKind in TEXTURE_RESOURCE_KINDS }
        val bindGroupCount = report.bindings.map { it.group }.distinct().size
        val entryPointName = report.entryPoints.firstOrNull()?.name ?: "main"
        val moduleHash = WGSLHashUtils.sha256("custom-module:$uniformCount:$textureCount:$bindGroupCount")
        val reflectionHash = WGSLHashUtils.sha256("$moduleHash:$entryPointName:reflection-v1")

        return WGSLReflectionResult(
            moduleHash = moduleHash,
            entryPoint = entryPointName,
            uniformCount = uniformCount,
            textureCount = textureCount,
            bindGroupCount = bindGroupCount,
            reflectionHash = reflectionHash,
            report = report,
        )
    }

    private fun fixtureBackedReflect(module: WGSLParsedModule): WGSLReflectionResult {
        val moduleHash = "fixture:${module.sourceHash}"
        return WGSLReflectionResult(
            moduleHash = moduleHash,
            entryPoint = "main",
            uniformCount = module.uniforms.size,
            textureCount = module.textures.size,
            bindGroupCount = module.bindGroups.size,
            reflectionHash = WGSLHashUtils.sha256("$moduleHash:fixture-reflection"),
        )
    }

    private companion object {
        val TEXTURE_RESOURCE_KINDS = setOf("sampledTexture", "multisampledTexture", "storageTexture")
    }
}
