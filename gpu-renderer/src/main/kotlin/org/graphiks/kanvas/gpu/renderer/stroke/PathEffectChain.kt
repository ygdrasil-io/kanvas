package org.graphiks.kanvas.gpu.renderer.stroke

sealed interface PathEffect {
    data class Dash(val dashes: FloatArray, val offset: Float = 0f) : PathEffect {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Dash) return false
            return dashes.contentEquals(other.dashes) && offset == other.offset
        }

        override fun hashCode(): Int {
            var result = dashes.contentHashCode()
            result = 31 * result + offset.hashCode()
            return result
        }
    }
    data class Corner(val cornerRadius: Float) : PathEffect
    data class Unsupported(val name: String) : PathEffect
}

data class GPUPathEffectDescriptor(
    val effectType: String,
    val parameters: Map<String, Float> = emptyMap(),
)

data class PathEffectResult(val isValid: Boolean, val report: String = "")

data class PathEffectChain(val effects: List<PathEffect>) {
    companion object {
        const val DEFAULT_MAX_DEPTH = 3
    }

    fun apply(pathLength: Float, maxDepth: Int = DEFAULT_MAX_DEPTH): PathEffectResult {
        val unsupported = effects.filterIsInstance<PathEffect.Unsupported>()
        if (unsupported.isNotEmpty()) {
            return PathEffectResult(
                isValid = false,
                report = "unsupported:${unsupported.joinToString(",") { it.name }}"
            )
        }
        if (effects.size > maxDepth) {
            return PathEffectResult(
                isValid = false,
                report = "depth_exceeded:${effects.size}>$maxDepth"
            )
        }
        return PathEffectResult(isValid = true)
    }
}

data class GPUPathEffectChainPlan(
    val effects: List<GPUPathEffectDescriptor>,
    val maxDepthReached: Boolean,
) {
    companion object {
        const val DEFAULT_MAX_DEPTH = 3

        fun plan(effects: List<GPUPathEffectDescriptor>, maxDepth: Int = DEFAULT_MAX_DEPTH): GPUPathEffectChainPlan {
            return GPUPathEffectChainPlan(
                effects = effects,
                maxDepthReached = effects.size > maxDepth,
            )
        }
    }
}

enum class GPUStrokeCap {
    Butt,
    Round,
    Square,
}

enum class GPUStrokeJoin {
    Miter,
    Round,
    Bevel,
}
