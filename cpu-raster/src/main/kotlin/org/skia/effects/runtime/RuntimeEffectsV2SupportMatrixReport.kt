package org.skia.effects.runtime

import org.skia.effects.runtime.effects.SkBuiltinColorFilterEffects
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsChildren
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsColorCube
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsCommon
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsExponential
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsGeometric
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsMatrix
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsRelational
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsTrig
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsRtifImageFilters
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsSimple
import org.skia.effects.runtime.effects.SkBuiltinSpecialisedEffects
import java.nio.file.Files
import java.nio.file.Path

public fun registerRuntimeEffectsV2SupportMatrixBuiltins() {
    SkBuiltinColorFilterEffects.registerAll()
    SkBuiltinShaderEffectsSimple.registerAll()
    SkBuiltinShaderEffectsChildren.registerAll()
    SkBuiltinShaderEffectsColorCube.registerAll()
    SkBuiltinShaderEffectsIntrinsicsTrig.registerAll()
    SkBuiltinShaderEffectsIntrinsicsExponential.registerAll()
    SkBuiltinShaderEffectsIntrinsicsCommon.registerAll()
    SkBuiltinShaderEffectsIntrinsicsGeometric.registerAll()
    SkBuiltinShaderEffectsIntrinsicsMatrix.registerAll()
    SkBuiltinShaderEffectsIntrinsicsRelational.registerAll()
    SkBuiltinSpecialisedEffects.registerAll()
    SkBuiltinShaderEffectsRtifImageFilters.registerAll()
}

public fun writeRuntimeEffectsV2SupportMatrix(outputRoot: Path) {
    registerRuntimeEffectsV2SupportMatrixBuiltins()
    Files.createDirectories(outputRoot)
    Files.writeString(
        outputRoot.resolve("support-matrix.json"),
        SkRuntimeEffectDescriptorRegistry.exportSupportMatrixV2Json() + "\n",
    )
    Files.writeString(
        outputRoot.resolve("support-matrix.md"),
        SkRuntimeEffectDescriptorRegistry.exportSupportMatrixV2Markdown(),
    )
}

public fun main(args: Array<String>) {
    val outputRoot = args.firstOrNull()
        ?.let(Path::of)
        ?: Path.of("reports/wgsl-pipeline/runtime-effects-v2")
    writeRuntimeEffectsV2SupportMatrix(outputRoot)
}
