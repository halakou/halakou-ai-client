package com.example.halakou.domain.tools

data class ToolResult(
    val output: String,
    val isSuccess: Boolean = true,
    val summary: String? = null
)

data class ToolInvocation(
    val toolName: String,
    val arguments: Map<String, String>,
    val rawBlock: String
)

/**
 * Standardized interface for halakou Agent Tools.
 * Contributors can implement this interface and add their tool to [ToolRegistry] in just 1 line.
 */
interface AgentTool {
    val name: String
    val displayName: String
    val description: String
    val usageExample: String

    /**
     * Executes the tool asynchronously.
     * @param arguments Key-value map of parameters passed by the LLM.
     */
    suspend fun execute(arguments: Map<String, String>): ToolResult
}
