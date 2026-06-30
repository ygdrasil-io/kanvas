package org.graphiks.kanvas.skia

import java.util.ServiceLoader

object SkiaGmRegistry {
    fun all(): List<SkiaGm> {
        val gms = ServiceLoader.load(SkiaGm::class.java).toList()
        require(gms.isNotEmpty()) { "No SkiaGms registered. Ensure META-INF/services/${SkiaGm::class.qualifiedName} lists all GM classes." }
        return gms
    }
}
