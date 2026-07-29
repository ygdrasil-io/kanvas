package org.graphiks.kanvas.gpu.renderer.materials

import java.util.LinkedHashMap
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialFragment
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.wgsl.PreparedTextA8Shader

/**
 * Small recording-owned LRU for immutable, parser-authenticated TextA8 programs.
 *
 * The cache is deliberately an instance dependency: callers choose its lifetime, so it is neither
 * a global shader authority nor tied to native handles. Uniform values, paint alpha, resource
 * identities and pixel contents never enter its structural key.
 */
class GPUPreparedTextCompositeProgramCache(
    private val maximumEntries: Int = DEFAULT_MAXIMUM_ENTRIES,
) {
    data class Snapshot(
        val residentEntryCount: Int,
        val hitCount: Int,
        val missCount: Int,
        val evictionCount: Int,
        val composeCount: Int,
        val parseCount: Int,
        val lowerCount: Int,
        val reflectCount: Int,
    )

    private data class StructuralKey(
        val fragmentHash: String,
        val fragmentAbiHash: String,
        val vertexAbi: String,
        val targetFormatClass: String,
        val blendPlanIdentity: String,
        val fixedFunctionBlendState: GPUFixedFunctionBlendState?,
    )

    private val entries =
        LinkedHashMap<StructuralKey, GPUPreparedTextCompositeProgram>(
            maximumEntries,
            0.75f,
            true,
        )
    private var hitCount = 0
    private var missCount = 0
    private var evictionCount = 0
    private var composeCount = 0
    private var parseCount = 0
    private var lowerCount = 0
    private var reflectCount = 0

    init {
        require(maximumEntries > 0) {
            "Prepared-text composite program cache must retain at least one entry"
        }
    }

    @Synchronized
    fun getOrCompose(
        material: GPUPreparedMaterialProgram,
        targetFormatClass: String = "rgba8unorm",
        blendPlanIdentity: String = "fixed-function:src-over",
        fixedFunctionBlendState: GPUFixedFunctionBlendState? = null,
    ): GPUPreparedTextCompositeProgramResult {
        val authenticated = runCatching { material.authenticatedSnapshot() }.getOrNull()
            ?: return composeObserved(
                material,
                targetFormatClass,
                blendPlanIdentity,
                fixedFunctionBlendState,
            )
        val key = structuralKey(
            authenticated.composableFragment,
            targetFormatClass,
            blendPlanIdentity,
            fixedFunctionBlendState,
        )
        entries[key]?.let { cached ->
            hitCount += 1
            return GPUPreparedTextCompositeProgramResult.Ready(cached)
        }
        missCount += 1
        return composeObserved(
            authenticated,
            targetFormatClass,
            blendPlanIdentity,
            fixedFunctionBlendState,
        ).also { result ->
            if (result is GPUPreparedTextCompositeProgramResult.Ready) {
                entries[key] = result.program
                if (entries.size > maximumEntries) {
                    val eldest = entries.entries.iterator()
                    eldest.next()
                    eldest.remove()
                    evictionCount += 1
                }
            }
        }
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(
        residentEntryCount = entries.size,
        hitCount = hitCount,
        missCount = missCount,
        evictionCount = evictionCount,
        composeCount = composeCount,
        parseCount = parseCount,
        lowerCount = lowerCount,
        reflectCount = reflectCount,
    )

    private fun composeObserved(
        material: GPUPreparedMaterialProgram,
        targetFormatClass: String,
        blendPlanIdentity: String,
        fixedFunctionBlendState: GPUFixedFunctionBlendState?,
    ): GPUPreparedTextCompositeProgramResult =
        GPUPreparedTextShaderComposer.composeObserved(
            material = material,
            targetFormatClass = targetFormatClass,
            blendPlanIdentity = blendPlanIdentity,
            fixedFunctionBlendState = fixedFunctionBlendState,
            observer = object : GPUPreparedTextCompositionObserver {
                override fun onCompose() {
                    composeCount += 1
                }

                override fun onParse() {
                    parseCount += 1
                }

                override fun onLower() {
                    lowerCount += 1
                }

                override fun onReflect() {
                    reflectCount += 1
                }
            },
        )

    private fun structuralKey(
        fragment: GPUPreparedMaterialFragment,
        targetFormatClass: String,
        blendPlanIdentity: String,
        fixedFunctionBlendState: GPUFixedFunctionBlendState?,
    ): StructuralKey = StructuralKey(
        fragmentHash = fragment.fragmentHash,
        fragmentAbiHash = fragment.abiHash,
        vertexAbi = PreparedTextA8Shader.VertexLayout.canonicalCacheIdentity(),
        targetFormatClass = targetFormatClass,
        blendPlanIdentity = blendPlanIdentity,
        fixedFunctionBlendState = fixedFunctionBlendState,
    )

    private fun org.graphiks.kanvas.gpu.renderer.wgsl.GPUPreparedTextVertexLayout
        .canonicalCacheIdentity(): String =
        buildString {
            append(arrayStrideBytes)
            append(':')
            append(stepMode)
            attributes.forEach { attribute ->
                append('|')
                append(attribute.location)
                append(':')
                append(attribute.offsetBytes)
                append(':')
                append(attribute.format)
            }
        }

    private companion object {
        const val DEFAULT_MAXIMUM_ENTRIES = 64
    }
}
