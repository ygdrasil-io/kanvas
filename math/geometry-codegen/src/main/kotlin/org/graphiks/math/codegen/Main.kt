package org.graphiks.math.codegen

import java.nio.file.Path

public fun main(args: Array<String>) {
    require(args.size == 2 && args[0] == "generate") {
        "usage: geometry-codegen generate <repo-root>"
    }
    val repoRoot = Path.of(args[1]).toAbsolutePath().normalize()
    GeneratedSourceSynchronizer.generate(
        repoRoot,
        KotlinEmitter.emit(MathPrimitiveManifest.schema),
    )
}
