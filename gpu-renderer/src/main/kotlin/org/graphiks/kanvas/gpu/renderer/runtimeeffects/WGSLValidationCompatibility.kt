@file:Suppress("DEPRECATION")

package org.graphiks.kanvas.gpu.renderer.runtimeeffects

/**
 * Source-compatibility aliases for the WGSL validation contracts moved to the
 * generic low-level package. New code must import `wgsl.validation` directly.
 */
@Deprecated(
    message = "Use org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLValidator",
    replaceWith = ReplaceWith(
        "KanvasWGSLValidator",
        "org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLValidator",
    ),
)
typealias KanvasWGSLValidator =
    org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLValidator

@Deprecated(
    message = "Use org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLReflectionProvider",
    replaceWith = ReplaceWith(
        "KanvasWGSLReflectionProvider",
        "org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLReflectionProvider",
    ),
)
typealias KanvasWGSLReflectionProvider =
    org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLReflectionProvider

@Deprecated(
    message = "Use org.graphiks.kanvas.gpu.renderer.wgsl.validation.WGSLParsedModule",
    replaceWith = ReplaceWith(
        "WGSLParsedModule",
        "org.graphiks.kanvas.gpu.renderer.wgsl.validation.WGSLParsedModule",
    ),
)
typealias WGSLParsedModule =
    org.graphiks.kanvas.gpu.renderer.wgsl.validation.WGSLParsedModule

@Deprecated(
    message = "Use org.graphiks.kanvas.gpu.renderer.wgsl.validation.WGSLValidator",
    replaceWith = ReplaceWith(
        "WGSLValidator",
        "org.graphiks.kanvas.gpu.renderer.wgsl.validation.WGSLValidator",
    ),
)
typealias WGSLValidator =
    org.graphiks.kanvas.gpu.renderer.wgsl.validation.WGSLValidator

@Deprecated(
    message = "Use org.graphiks.kanvas.gpu.renderer.wgsl.validation.WGSLReflectionProvider",
    replaceWith = ReplaceWith(
        "WGSLReflectionProvider",
        "org.graphiks.kanvas.gpu.renderer.wgsl.validation.WGSLReflectionProvider",
    ),
)
typealias WGSLReflectionProvider =
    org.graphiks.kanvas.gpu.renderer.wgsl.validation.WGSLReflectionProvider

@Deprecated(
    message = "Use org.graphiks.kanvas.gpu.renderer.wgsl.validation.WGSLModuleReflection",
    replaceWith = ReplaceWith(
        "WGSLModuleReflection",
        "org.graphiks.kanvas.gpu.renderer.wgsl.validation.WGSLModuleReflection",
    ),
)
typealias WGSLReflectionResult =
    org.graphiks.kanvas.gpu.renderer.wgsl.validation.WGSLModuleReflection
