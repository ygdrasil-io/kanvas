package org.graphiks.kanvas.gpu.renderer.stroke

sealed interface PathEffect {
    data class Dash(val dashes: FloatArray, val offset: Float = 0f) : PathEffect
    data class Corner(val cornerRadius: Float) : PathEffect
    data class Unsupported(val name: String) : PathEffect
}

data class PathEffectResult(val isValid: Boolean, val report: String = "")

data class PathEffectChain(private val effects: List<PathEffect>) {
    fun apply(pathLength: Float): PathEffectResult {
        val unsupported = effects.filterIsInstance<PathEffect.Unsupported>()
        if (unsupported.isNotEmpty()) {
            return PathEffectResult(
                isValid = false,
                report = "unsupported:${unsupported.joinToString(",") { it.name }}"
            )
        }
        return PathEffectResult(isValid = true)
    }
}
