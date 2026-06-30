package org.graphiks.kanvas.surface

data class RenderResult(
    val pixels: UByteArray,
    val width: Int,
    val height: Int,
    val diagnostics: Diagnostics,
    val stats: RenderStats,
) {
    val isClean: Boolean get() = diagnostics.isEmpty
    val hasIssues: Boolean get() = !diagnostics.isEmpty

    fun assertClean() { require(isClean) { diagnostics.summary() } }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RenderResult) return false
        return pixels.contentEquals(other.pixels) && width == other.width && height == other.height
            && diagnostics == other.diagnostics && stats == other.stats
    }
    override fun hashCode(): Int = pixels.contentHashCode() * 31 + width + height + diagnostics.hashCode() + stats.hashCode()
}
