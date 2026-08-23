package org.graphiks.math.codegen

import java.nio.file.Path

public fun main(args: Array<String>) {
    require(args.size == 2) { "usage: geometry-codegen <generate|verify|verify-identity> <repo-root>" }
    val repoRoot = Path.of(args[1]).toAbsolutePath().normalize()
    when (args[0]) {
        "generate" -> GeneratedSourceSynchronizer.generate(
            repoRoot,
            KotlinEmitter.emit(MathPrimitiveManifest.schema),
        )

        "verify" -> verify(repoRoot)
        "verify-identity" -> verifyIdentityUsage(repoRoot)
        else -> error("unknown geometry-codegen mode: ${args[0]}")
    }
}

private fun verifyIdentityUsage(repoRoot: Path) {
    val violations = IdentityUsageVerifier.verify(repoRoot)
    check(violations.isEmpty()) {
        violations.joinToString(
            prefix = "forbidden math identity usage:\n",
            separator = "\n",
        ) { violation ->
            "${violation.path}:${violation.line}: ${violation.expression}"
        }
    }
}

private fun verify(repoRoot: Path) {
    val first = KotlinEmitter.emit(MathPrimitiveManifest.schema)
    val second = KotlinEmitter.emit(MathPrimitiveManifest.schema)
    val firstPaths = first.files.map { it.relativePath }
    val secondPaths = second.files.map { it.relativePath }
    check(firstPaths == secondPaths) { "code generation is nondeterministic: emitted paths differ" }
    first.files.zip(second.files).forEach { (firstFile, secondFile) ->
        check(firstFile.utf8.contentEquals(secondFile.utf8)) {
            "code generation is nondeterministic: bytes differ for ${firstFile.relativePath}"
        }
    }
    val differences = GeneratedSourceSynchronizer.verify(repoRoot, first)
    check(differences.isEmpty()) {
        differences.joinToString(prefix = "generated sources are out of date:\n", separator = "\n")
    }
}
