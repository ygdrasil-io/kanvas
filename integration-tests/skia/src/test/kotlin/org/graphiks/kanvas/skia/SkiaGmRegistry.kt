package org.graphiks.kanvas.skia

import java.io.BufferedReader
import java.io.InputStreamReader

object SkiaGmRegistry {
    fun all(): List<SkiaGm> {
        val gms = mutableListOf<SkiaGm>()
        val resourceName = "META-INF/services/${SkiaGm::class.qualifiedName}"
        val classLoader = SkiaGm::class.java.classLoader
        val stream = classLoader.getResourceAsStream(resourceName)
            ?: throw IllegalStateException("No $resourceName found")
        BufferedReader(InputStreamReader(stream)).use { reader ->
            for (line in reader.lines()) {
                val className = line.trim()
                if (className.isEmpty() || className.startsWith("#")) continue
                try {
                    val clazz = classLoader.loadClass(className)
                    if (!SkiaGm::class.java.isAssignableFrom(clazz)) continue
                    val instance = clazz.getDeclaredConstructor().newInstance() as SkiaGm
                    gms.add(instance)
                } catch (_: Exception) {
                    System.err.println("[SKIP] $className")
                }
            }
        }
        require(gms.isNotEmpty()) { "No SkiaGms registered." }
        return gms
    }
}
