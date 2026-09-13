package org.graphiks.kanvas.gpu.plan

/** Fail-closed reader of only [BlendFormulaProgramV1]'s loop-free expression/function grammar.
 * The original text remains the shader; these nodes are read FROM it, never a second formula. */
internal class BlendFormulaOperationGraphV1 private constructor(
    val sourceWgsl: String, private val functions: Map<String, Function>,
) {
    sealed interface Expression {
        data class Literal(val valueF32: Float) : Expression
        data class Name(val name: String) : Expression
        data class Member(val value: Expression, val components: String) : Expression
        data class Binary(val operation: String, val left: Expression, val right: Expression) : Expression
        data class Call(val name: String, val arguments: List<Expression>) : Expression
    }
    sealed interface Statement {
        data class Bind(val name: String, val value: Expression) : Statement
        data class Return(val value: Expression) : Statement
        data class Branch(val condition: Expression, val body: List<Statement>) : Statement
        data class Switch(val selector: Expression, val cases: Map<Float, List<Statement>>, val otherwise: List<Statement>) : Statement
    }
    data class Function(val arguments: List<String>, val body: List<Statement>)

    /** Vector/scalar operations and comparisons are supplied by the numeric envelope. */
    interface Arithmetic<T> {
        fun literal(valueF32: Float): T
        fun binary(operation: String, left: T, right: T): T
        fun builtin(name: String, arguments: List<List<T>>): List<T>
        fun hull(left: T, right: T): T
        /** -1 means both outcomes; 0 false; 1 true. */
        fun truth(value: T): Int
        fun exact(value: T): Float?
    }

    fun <T> evaluate(entry: String, arguments: List<List<T>>, arithmetic: Arithmetic<T>): List<T> {
        var visitsI32 = 0
        fun tick() { require(++visitsI32 <= 100_000) }
        fun merge(a: List<T>, b: List<T>): List<T> {
            require(a.size == b.size)
            return a.indices.map { arithmetic.hull(a[it], b[it]) }
        }
        lateinit var call: (String, List<List<T>>, Int) -> List<T>
        fun expression(node: Expression, values: Map<String, List<T>>, depthI32: Int): List<T> {
            tick()
            return when (node) {
                is Expression.Literal -> listOf(arithmetic.literal(node.valueF32))
                is Expression.Name -> requireNotNull(values[node.name])
                is Expression.Member -> {
                    val source = expression(node.value, values, depthI32)
                    node.components.map { source["rgba".indexOf(it).also { index -> require(index >= 0) }] }
                }
                is Expression.Binary -> {
                    val left = expression(node.left, values, depthI32)
                    val right = expression(node.right, values, depthI32)
                    val sizeI32 = maxOf(left.size, right.size)
                    require(left.size == sizeI32 || left.size == 1)
                    require(right.size == sizeI32 || right.size == 1)
                    List(sizeI32) { arithmetic.binary(node.operation, left[if (left.size == 1) 0 else it], right[if (right.size == 1) 0 else it]) }
                }
                is Expression.Call -> {
                    // All arguments are evaluated before every call, notably WGSL select.
                    val operands = node.arguments.map { expression(it, values, depthI32) }
                    if (node.name in functions) call(node.name, operands, depthI32 + 1)
                    else arithmetic.builtin(node.name, operands)
                }
            }
        }
        fun statements(body: List<Statement>, values: MutableMap<String, List<T>>, depthI32: Int): List<T> {
            for ((indexI32, statement) in body.withIndex()) {
                tick()
                when (statement) {
                    is Statement.Bind -> values[statement.name] = expression(statement.value, values, depthI32)
                    is Statement.Return -> return expression(statement.value, values, depthI32)
                    is Statement.Branch -> {
                        val condition = expression(statement.condition, values, depthI32).single()
                        val remainder = body.drop(indexI32 + 1)
                        when (arithmetic.truth(condition)) {
                            1 -> return statements(statement.body + remainder, values, depthI32)
                            -1 -> return merge(statements(statement.body + remainder, values.toMutableMap(), depthI32),
                                statements(remainder, values.toMutableMap(), depthI32))
                        }
                    }
                    is Statement.Switch -> {
                        val selector = requireNotNull(arithmetic.exact(expression(statement.selector, values, depthI32).single()))
                        return statements((statement.cases[selector] ?: statement.otherwise) + body.drop(indexI32 + 1), values, depthI32)
                    }
                }
            }
            error("Blend function has no return")
        }
        call = { name, operands, depthI32 ->
            require(depthI32 <= 32)
            val function = requireNotNull(functions[name])
            require(function.arguments.size == operands.size)
            statements(function.body, function.arguments.zip(operands).toMap().toMutableMap(), depthI32)
        }
        return call(entry, arguments, 0)
    }

    companion object {
        fun read(sourceWgsl: String): BlendFormulaOperationGraphV1? = try {
            require(sourceWgsl.length <= 32_768)
            val reader = Reader(sourceWgsl)
            BlendFormulaOperationGraphV1(sourceWgsl, reader.functions())
        } catch (_: IllegalArgumentException) { null }
    }

    private class Reader(source: String) {
        private val tokenPattern = Regex("\\s+|[A-Za-z_][A-Za-z_0-9]*|(?:[0-9]+(?:\\.[0-9]*)?)(?:e[+-]?[0-9]+)?u?|==|!=|<=|>=|&&|\\|\\||->|[{}(),:;.+*/<>=-]")
        private val tokens = buildList {
            var offsetI32 = 0
            for (match in tokenPattern.findAll(source)) {
                require(match.range.first == offsetI32)
                offsetI32 = match.range.last + 1
                if (!match.value.first().isWhitespace()) add(match.value)
            }
            require(offsetI32 == source.length && size <= 8192)
        }
        private var indexI32 = 0
        private fun next(): String = tokens.getOrNull(indexI32++) ?: throw IllegalArgumentException("End of blend program")
        private fun accept(token: String): Boolean = if (tokens.getOrNull(indexI32) == token) { indexI32++; true } else false
        private fun expect(token: String) { require(next() == token) }
        private fun name(): String = next().also { require(it.matches(Regex("[A-Za-z_][A-Za-z_0-9]*"))) }
        private fun type() { require(next() in setOf("f32", "u32", "vec3f", "vec4f")) }
        fun functions(): Map<String, Function> = buildMap {
            while (indexI32 < tokens.size) {
                expect("fn")
                val name = name()
                expect("(")
                val arguments = mutableListOf<String>()
                if (!accept(")")) {
                    do { arguments += name(); expect(":"); type() } while (accept(","))
                    expect(")")
                }
                expect("->"); type()
                require(put(name, Function(arguments.toList(), block())) == null)
            }
        }
        private fun block(): List<Statement> {
            expect("{")
            return buildList {
                while (!accept("}")) {
                    when (val token = next()) {
                        "let", "var" -> { val name = name(); expect("="); add(Statement.Bind(name, expression())); expect(";") }
                        "return" -> { add(Statement.Return(expression())); expect(";") }
                        "if" -> { expect("("); val condition = expression(); expect(")"); add(Statement.Branch(condition, block())) }
                        "switch" -> {
                            val selector = expression()
                            expect("{")
                            val cases = linkedMapOf<Float, List<Statement>>()
                            var otherwise: List<Statement>? = null
                            while (!accept("}")) when (next()) {
                                "case" -> { val value = next().removeSuffix("u").toFloat(); expect(":"); require(cases.put(value, block()) == null) }
                                "default" -> { expect(":"); require(otherwise == null); otherwise = block() }
                                else -> throw IllegalArgumentException("Unknown blend switch")
                            }
                            add(Statement.Switch(selector, cases.toMap(), requireNotNull(otherwise)))
                        }
                        else -> { require(token.matches(Regex("[A-Za-z_][A-Za-z_0-9]*"))); expect("="); add(Statement.Bind(token, expression())); expect(";") }
                    }
                }
            }
        }
        private val precedence = mapOf("||" to 1, "&&" to 2, "==" to 3, "!=" to 3, "<" to 4, ">" to 4,
            "<=" to 4, ">=" to 4, "+" to 5, "-" to 5, "*" to 6, "/" to 6)
        private fun expression(minimumI32: Int = 0): Expression {
            var left = atom()
            while (true) {
                val operator = tokens.getOrNull(indexI32) ?: break
                val rankI32 = precedence[operator] ?: break
                if (rankI32 < minimumI32) break
                indexI32++
                left = Expression.Binary(operator, left, expression(rankI32 + 1))
            }
            return left
        }
        private fun atom(): Expression {
            val token = next()
            var value = when {
                token == "(" -> expression().also { expect(")") }
                token == "-" -> Expression.Binary("-", Expression.Literal(0f), atom())
                token.first().isDigit() -> Expression.Literal(token.removeSuffix("u").toFloat())
                token.matches(Regex("[A-Za-z_][A-Za-z_0-9]*")) -> if (accept("(")) {
                    val arguments = mutableListOf<Expression>()
                    if (!accept(")")) {
                        do { arguments += expression() } while (accept(",") && tokens.getOrNull(indexI32) != ")")
                        expect(")")
                    }
                    Expression.Call(token, arguments.toList())
                } else Expression.Name(token)
                else -> throw IllegalArgumentException("Unknown blend expression")
            }
            while (accept(".")) value = Expression.Member(value, name().also { require(it in setOf("r", "g", "b", "a", "rgb")) })
            return value
        }
    }
}
