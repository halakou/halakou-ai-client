package com.example.halakou.domain.tools

import kotlin.math.*

class CalculatorTool : AgentTool {
    override val name: String = "calculator"
    override val displayName: String = "Math & Logic Calculator"
    override val description: String = "Evaluates mathematical expressions including arithmetic, exponents, roots, and trigonometry."
    override val usageExample: String = """{"expression": "(128 * 4.5) + sqrt(144) / 2"}"""

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val expr = arguments["expression"] ?: arguments["expr"] ?: return ToolResult(
            output = "Error: Missing 'expression' parameter.",
            isSuccess = false
        )

        return try {
            val result = evaluateExpression(expr.trim())
            ToolResult(
                output = "$expr = $result",
                summary = "Calculated $expr",
                isSuccess = true
            )
        } catch (e: Exception) {
            ToolResult(
                output = "Calculation error for '$expr': ${e.message}",
                isSuccess = false
            )
        }
    }

    private fun evaluateExpression(expr: String): Double {
        val sanitized = expr.replace(" ", "")
            .replace("×", "*")
            .replace("÷", "/")
        return SimpleParser(sanitized).parse()
    }

    private class SimpleParser(private val str: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw IllegalArgumentException("Unexpected: " + ch.toChar())
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    eat('%'.code) -> x %= parseFactor()
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return +parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) {
                while (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) nextChar()
                val func = str.substring(startPos, pos).lowercase()
                x = parseFactor()
                x = when (func) {
                    "sqrt" -> sqrt(x)
                    "sin" -> sin(Math.toRadians(x))
                    "cos" -> cos(Math.toRadians(x))
                    "tan" -> tan(Math.toRadians(x))
                    "log" -> log10(x)
                    "ln" -> ln(x)
                    "abs" -> abs(x)
                    "round" -> round(x)
                    else -> throw IllegalArgumentException("Unknown math function: $func")
                }
            } else {
                throw IllegalArgumentException("Unexpected character: " + ch.toChar())
            }

            if (eat('^'.code)) x = x.pow(parseFactor())
            return x
        }
    }
}
