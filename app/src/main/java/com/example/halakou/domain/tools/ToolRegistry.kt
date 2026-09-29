package com.example.halakou.domain.tools

import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Open-source contributors can add a new tool to halakou by creating a class implementing
 * [AgentTool] and adding it to [registeredTools] below.
 */
class ToolRegistry(
    customTools: List<AgentTool> = emptyList()
) {
    val registeredTools: List<AgentTool> = listOf(
        WebSearchTool(),
        WebScraperReaderTool(),
        LocalRagSearchTool(com.example.halakou.domain.rag.LocalVectorDatabase()),
        CodeInterpreterSimulatorTool(),
        CalendarOsIntentsTool(),
        CalculatorTool(),
        DateTimeTool(),
        TextScratchpadTool(),
        UnitConverterTool()
    ) + customTools

    fun getTool(name: String): AgentTool? {
        return registeredTools.find { it.name.equals(name, ignoreCase = true) }
    }

    /**
     * Constructs a system prompt section detailing available tools and protocol.
     */
    fun buildToolSystemPrompt(enabledToolNames: Set<String>): String {
        val active = registeredTools.filter { it.name in enabledToolNames }
        if (active.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("\n\n### AGENTIC TOOLS AVAILABLE\n")
        sb.append("You have access to the following built-in real-time tools. If you need live facts, current time, mathematical verification, or local notes, you MUST call the appropriate tool.\n\n")

        for (tool in active) {
            sb.append("Tool: `${tool.name}` (${tool.displayName})\n")
            sb.append("Description: ${tool.description}\n")
            sb.append("Example invocation: ${tool.usageExample}\n\n")
        }

        sb.append("PROTOCOL FOR CALLING A TOOL:\n")
        sb.append("When you decide to call a tool, you MUST output ONLY the tool call block in this format and nothing else until the tool result is provided back to you:\n")
        sb.append("<tool_call>\n")
        sb.append("{\"tool\": \"tool_name\", \"args\": {\"arg_name\": \"value\"}}\n")
        sb.append("</tool_call>\n\n")
        sb.append("After receiving the tool execution result, you will synthesize the answer clearly for the user.\n")

        return sb.toString()
    }

    /**
     * Parses model output looking for a tool call invocation.
     */
    fun parseToolCall(response: String): ToolInvocation? {
        // Match <tool_call> ... </tool_call>
        val toolCallPattern = Pattern.compile("<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>", Pattern.DOTALL)
        val matcher = toolCallPattern.matcher(response)
        if (matcher.find()) {
            val jsonStr = matcher.group(1) ?: return null
            try {
                val json = JSONObject(jsonStr)
                val toolName = json.optString("tool", "")
                val argsObj = json.optJSONObject("args")
                val argsMap = mutableMapOf<String, String>()
                if (argsObj != null) {
                    val keys = argsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        argsMap[k] = argsObj.optString(k, "")
                    }
                }
                if (toolName.isNotBlank()) {
                    return ToolInvocation(
                        toolName = toolName,
                        arguments = argsMap,
                        rawBlock = matcher.group(0) ?: ""
                    )
                }
            } catch (_: Exception) {
                // fall through
            }
        }

        // Secondary fallback match: ```json {"tool": "...", "args": ...} ```
        val jsonBlockPattern = Pattern.compile("```(?:json)?\\s*(\\{\\s*\"tool\"\\s*:\\s*\"[^\"]+\".*?\\})\\s*```", Pattern.DOTALL)
        val jsonMatcher = jsonBlockPattern.matcher(response)
        if (jsonMatcher.find()) {
            val jsonStr = jsonMatcher.group(1) ?: return null
            try {
                val json = JSONObject(jsonStr)
                val toolName = json.optString("tool", "")
                val argsObj = json.optJSONObject("args")
                val argsMap = mutableMapOf<String, String>()
                if (argsObj != null) {
                    val keys = argsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        argsMap[k] = argsObj.optString(k, "")
                    }
                }
                if (toolName.isNotBlank()) {
                    return ToolInvocation(
                        toolName = toolName,
                        arguments = argsMap,
                        rawBlock = jsonMatcher.group(0) ?: ""
                    )
                }
            } catch (_: Exception) {}
        }

        return null
    }
}
