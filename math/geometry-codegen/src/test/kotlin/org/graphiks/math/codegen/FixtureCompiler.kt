package org.graphiks.math.codegen

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services

internal data class CompilationResult(
    val exitCode: ExitCode,
    val diagnostics: String,
)

internal fun compileFixture(source: Path, classpath: String, destination: Path): CompilationResult {
    val arguments = K2JVMCompilerArguments().apply {
        freeArgs = listOf(source.toAbsolutePath().toString())
        this.destination = destination.toAbsolutePath().toString()
        this.classpath = classpath
        noStdlib = true
        noReflect = true
    }
    val output = ByteArrayOutputStream()
    val exitCode = PrintStream(output, true, StandardCharsets.UTF_8).use { printStream ->
        K2JVMCompiler().exec(
            PrintingMessageCollector(
                printStream,
                MessageRenderer.PLAIN_RELATIVE_PATHS,
                false,
            ),
            Services.EMPTY,
            arguments,
        )
    }
    return CompilationResult(
        exitCode = exitCode,
        diagnostics = output.toString(StandardCharsets.UTF_8),
    )
}
