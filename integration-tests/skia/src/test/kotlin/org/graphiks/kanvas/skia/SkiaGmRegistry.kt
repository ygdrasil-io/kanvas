package org.graphiks.kanvas.skia

object SkiaGmRegistry {
    private val gms = mutableListOf<SkiaGm>()

    fun register(gm: SkiaGm) {
        gms.add(gm)
    }

    fun all(): List<SkiaGm> = gms.toList()
}
