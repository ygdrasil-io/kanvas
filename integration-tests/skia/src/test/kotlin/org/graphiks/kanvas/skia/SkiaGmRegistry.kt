package org.graphiks.kanvas.skia

object SkiaGmRegistry {
    private val gms = mutableListOf<SkiaGm>()

    fun register(gm: SkiaGm) {
        gms.add(gm)
    }

    fun all(): List<SkiaGm> {
        val list = gms.toList()
        require(list.isNotEmpty()) { "No SkiaGms registered. Ensure GM classes are loaded." }
        return list
    }
}
