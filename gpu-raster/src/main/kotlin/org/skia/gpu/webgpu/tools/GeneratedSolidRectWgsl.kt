package org.skia.gpu.webgpu.tools

import io.ygdrasil.wgsl.arena.Arena
import io.ygdrasil.wgsl.arena.rangeOf
import io.ygdrasil.wgsl.ir.BinaryOperator
import io.ygdrasil.wgsl.ir.Binding
import io.ygdrasil.wgsl.ir.BindingAttribute
import io.ygdrasil.wgsl.ir.Block
import io.ygdrasil.wgsl.ir.BuiltinValue
import io.ygdrasil.wgsl.ir.EntryPoint
import io.ygdrasil.wgsl.ir.Expression
import io.ygdrasil.wgsl.ir.ExpressionKind
import io.ygdrasil.wgsl.ir.Function
import io.ygdrasil.wgsl.ir.FunctionParameter
import io.ygdrasil.wgsl.ir.GlobalVariable
import io.ygdrasil.wgsl.ir.LiteralValue
import io.ygdrasil.wgsl.ir.Module
import io.ygdrasil.wgsl.ir.ScalarKind
import io.ygdrasil.wgsl.ir.ScalarValue
import io.ygdrasil.wgsl.ir.ShaderStage
import io.ygdrasil.wgsl.ir.Statement
import io.ygdrasil.wgsl.ir.StorageClass
import io.ygdrasil.wgsl.ir.StructMember
import io.ygdrasil.wgsl.ir.Type
import io.ygdrasil.wgsl.ir.TypeInner
import io.ygdrasil.wgsl.ir.VectorSize
import io.ygdrasil.wgsl.parser.parseWgslResult
import io.ygdrasil.wgsl.wgsl.WgslModule

data class WgslValidationResult(
    val isSuccess: Boolean,
    val diagnostics: List<String>,
)

object GeneratedSolidRectWgsl {
    const val FEATURE_FLAG = "kanvas.gpu.generatedSolidRect.enabled"

    fun generateDeterministic(): String =
        normalizeWgpuCompatibleWgsl(WgslModule.writeString(buildModule()))

    // The current WGSL writer emits legacy-style casts like `(f32)(x)`;
    // it also omits the fragment return location. Normalize until the
    // upstream writer produces WebGPU-native entry-point syntax directly.
    private fun normalizeWgpuCompatibleWgsl(source: String): String =
        source
            .replace("(f32)(", "f32(")
            .replace("fn fs_main() -> vec4<f32>", "fn fs_main() -> @location(0) vec4<f32>")

    private fun buildModule(): Module {
        val module = Module()

        val f32 = module.types.append(Type(TypeInner.Scalar(ScalarKind.F32, 4)))
        val u32 = module.types.append(Type(TypeInner.Scalar(ScalarKind.Uint, 4)))
        val vec4f = module.types.append(Type(TypeInner.Vector(VectorSize.Quad, f32)))

        val vertexOut = module.types.append(
            Type(
                TypeInner.Struct(
                    members = listOf(
                        StructMember(
                            name = "position",
                            type = vec4f,
                            binding = BindingAttribute.Builtin(BuiltinValue.Position),
                            offset = 0,
                        ),
                    ),
                ),
            ),
        )

        val uniforms = module.types.append(
            Type(
                TypeInner.Struct(
                    members = listOf(
                        StructMember(name = "color", type = vec4f, offset = 0),
                    ),
                ),
            ),
        )

        val uniformsHandle = module.globalVariables.append(
            GlobalVariable(
                name = "uniforms",
                storageClass = StorageClass.Uniform,
                type = uniforms,
                binding = Binding(group = 0, index = 0),
            ),
        )

        val vertexHandle = module.functions.append(buildVertexMain(u32, f32, vec4f, vertexOut))
        val fragmentHandle = module.functions.append(buildFragmentMain(vec4f, uniformsHandle))

        module.entryPoints += EntryPoint(
            name = "vs_main",
            function = vertexHandle,
            stage = ShaderStage.Vertex,
        )
        module.entryPoints += EntryPoint(
            name = "fs_main",
            function = fragmentHandle,
            stage = ShaderStage.Fragment,
            bindings = listOf(BindingAttribute.Location(0)),
        )
        // We keep the generated path for fragment-only solid output in this pilot.
        return module
    }

    private fun buildVertexMain(
        u32: io.ygdrasil.wgsl.arena.Handle<Type>,
        f32: io.ygdrasil.wgsl.arena.Handle<Type>,
        vec4f: io.ygdrasil.wgsl.arena.Handle<Type>,
        vertexOut: io.ygdrasil.wgsl.arena.Handle<Type>,
    ): Function {
        val expressions = Arena<Expression>()
        val blocks = Arena<Block>()

        val idx = expressions.append(Expression(ExpressionKind.FunctionArgument(0)))
        val oneU = expressions.append(Expression(ExpressionKind.Literal(LiteralValue.Scalar(ScalarValue.U32(1L)))))
        val twoU = expressions.append(Expression(ExpressionKind.Literal(LiteralValue.Scalar(ScalarValue.U32(2L)))))
        val oneF = expressions.append(Expression(ExpressionKind.Literal(LiteralValue.Scalar(ScalarValue.F32(1f)))))
        val twoF = expressions.append(Expression(ExpressionKind.Literal(LiteralValue.Scalar(ScalarValue.F32(2f)))))
        val zeroF = expressions.append(Expression(ExpressionKind.Literal(LiteralValue.Scalar(ScalarValue.F32(0f)))))

        val shift = expressions.append(Expression(ExpressionKind.Binary(BinaryOperator.ShiftLeft, idx, oneU)))
        val xBits = expressions.append(Expression(ExpressionKind.Binary(BinaryOperator.BitAnd, shift, twoU)))
        val xF = expressions.append(Expression(ExpressionKind.As(xBits, f32)))
        val xScaled = expressions.append(Expression(ExpressionKind.Binary(BinaryOperator.Multiply, xF, twoF)))
        val x = expressions.append(Expression(ExpressionKind.Binary(BinaryOperator.Subtract, xScaled, oneF)))

        val yBits = expressions.append(Expression(ExpressionKind.Binary(BinaryOperator.BitAnd, idx, twoU)))
        val yF = expressions.append(Expression(ExpressionKind.As(yBits, f32)))
        val yScaled = expressions.append(Expression(ExpressionKind.Binary(BinaryOperator.Multiply, yF, twoF)))
        val y = expressions.append(Expression(ExpressionKind.Binary(BinaryOperator.Subtract, yScaled, oneF)))

        val position = expressions.append(Expression(ExpressionKind.TypeConstructor(vec4f, listOf(x, y, zeroF, oneF))))
        val outValue = expressions.append(Expression(ExpressionKind.TypeConstructor(vertexOut, listOf(position))))

        val body = blocks.append(
            Block(
                statements = listOf(
                    Statement.Emit(rangeOf(outValue)),
                    Statement.Return(outValue),
                ),
            ),
        )

        return Function(
            name = "vs_main",
            parameters = listOf(
                FunctionParameter(
                    name = "idx",
                    type = u32,
                    binding = BindingAttribute.Builtin(BuiltinValue.VertexIndex),
                ),
            ),
            returnType = vertexOut,
            localVariables = Arena(),
            expressions = expressions,
            blocks = blocks,
            body = body,
        )
    }

    private fun buildFragmentMain(
        vec4f: io.ygdrasil.wgsl.arena.Handle<Type>,
        uniformsHandle: io.ygdrasil.wgsl.arena.Handle<GlobalVariable>,
    ): Function {
        val expressions = Arena<Expression>()
        val blocks = Arena<Block>()

        val uniforms = expressions.append(Expression(ExpressionKind.GlobalVar(uniformsHandle)))
        val color = expressions.append(Expression(ExpressionKind.AccessIndex(uniforms, 0u)))
        val r = expressions.append(Expression(ExpressionKind.AccessIndex(color, 0u)))
        val g = expressions.append(Expression(ExpressionKind.AccessIndex(color, 1u)))
        val b = expressions.append(Expression(ExpressionKind.AccessIndex(color, 2u)))
        val a = expressions.append(Expression(ExpressionKind.AccessIndex(color, 3u)))
        val pr = expressions.append(Expression(ExpressionKind.Binary(BinaryOperator.Multiply, r, a)))
        val pg = expressions.append(Expression(ExpressionKind.Binary(BinaryOperator.Multiply, g, a)))
        val pb = expressions.append(Expression(ExpressionKind.Binary(BinaryOperator.Multiply, b, a)))
        val out = expressions.append(Expression(ExpressionKind.TypeConstructor(vec4f, listOf(pr, pg, pb, a))))

        val body = blocks.append(
            Block(
                statements = listOf(
                    Statement.Emit(rangeOf(out)),
                    Statement.Return(out),
                ),
            ),
        )

        return Function(
            name = "fs_main",
            parameters = emptyList(),
            returnType = vec4f,
            localVariables = Arena(),
            expressions = expressions,
            blocks = blocks,
            body = body,
        )
    }

    fun validate(source: String): WgslValidationResult {
        val parsed = parseWgslResult(source)
        val diagnostics = parsed.errors.map { "${it.message} span=${it.span}" }
        return WgslValidationResult(isSuccess = parsed.isSuccess, diagnostics = diagnostics)
    }
}
