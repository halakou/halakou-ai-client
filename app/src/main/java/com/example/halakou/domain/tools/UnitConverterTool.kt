package com.example.halakou.domain.tools

import java.util.Locale

class UnitConverterTool : AgentTool {
    override val name: String = "unit_converter"
    override val displayName: String = "Unit & Data Converter"
    override val description: String = "Converts physical units (temperature, length, mass) and digital storage sizes (bytes, KB, MB, GB, TB)."
    override val usageExample: String = """{"value": "100", "from": "celsius", "to": "fahrenheit"}"""

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val valueStr = arguments["value"] ?: arguments["val"] ?: return ToolResult("Missing 'value'", false)
        val from = (arguments["from"] ?: "").lowercase().trim()
        val to = (arguments["to"] ?: "").lowercase().trim()

        val value = valueStr.toDoubleOrNull() ?: return ToolResult("Invalid numerical value: $valueStr", false)

        return try {
            val converted = convert(value, from, to)
            ToolResult(
                output = String.format(Locale.US, "%.4f %s = %.4f %s", value, from, converted, to),
                summary = "Converted $value $from to $to",
                isSuccess = true
            )
        } catch (e: Exception) {
            ToolResult("Conversion error: ${e.message}", false)
        }
    }

    private fun convert(v: Double, from: String, to: String): Double {
        // Temperature
        if (from in listOf("c", "celsius") && to in listOf("f", "fahrenheit")) return (v * 9 / 5) + 32
        if (from in listOf("f", "fahrenheit") && to in listOf("c", "celsius")) return (v - 32) * 5 / 9
        if (from in listOf("c", "celsius") && to in listOf("k", "kelvin")) return v + 273.15
        if (from in listOf("k", "kelvin") && to in listOf("c", "celsius")) return v - 273.15

        // Length (base meter)
        val lengthToMeters = mapOf(
            "m" to 1.0, "meter" to 1.0, "meters" to 1.0,
            "km" to 1000.0, "kilometer" to 1000.0,
            "cm" to 0.01, "centimeter" to 0.01,
            "mm" to 0.001, "millimeter" to 0.001,
            "mi" to 1609.344, "mile" to 1609.344, "miles" to 1609.344,
            "yd" to 0.9144, "yard" to 0.9144, "yards" to 0.9144,
            "ft" to 0.3048, "foot" to 0.3048, "feet" to 0.3048,
            "in" to 0.0254, "inch" to 0.0254, "inches" to 0.0254
        )
        if (lengthToMeters.containsKey(from) && lengthToMeters.containsKey(to)) {
            val meters = v * lengthToMeters[from]!!
            return meters / lengthToMeters[to]!!
        }

        // Digital storage (base bytes)
        val dataToBytes = mapOf(
            "b" to 1.0, "bytes" to 1.0,
            "kb" to 1024.0, "kib" to 1024.0,
            "mb" to 1024.0 * 1024.0, "mib" to 1024.0 * 1024.0,
            "gb" to 1024.0 * 1024.0 * 1024.0, "gib" to 1024.0 * 1024.0 * 1024.0,
            "tb" to 1024.0 * 1024.0 * 1024.0 * 1024.0
        )
        if (dataToBytes.containsKey(from) && dataToBytes.containsKey(to)) {
            val bytes = v * dataToBytes[from]!!
            return bytes / dataToBytes[to]!!
        }

        throw IllegalArgumentException("Unsupported unit conversion from '$from' to '$to'.")
    }
}
