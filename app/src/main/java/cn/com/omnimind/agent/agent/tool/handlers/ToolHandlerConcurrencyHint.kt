package cn.com.omnimind.agent.agent.tool.handlers

import cn.com.omnimind.agent.agent.tool.ToolConcurrency
import kotlinx.serialization.json.JsonObject

/**
 * Optional mixin a [ToolHandler] can implement to declare its parallel-safety.
 * The central [cn.com.omnimind.agent.agent.tool.AgentToolConcurrencyPolicy] consults
 * this first, then falls back to its static whitelist for unknown tools.
 */
interface ToolHandlerConcurrencyHint {
    fun concurrencyFor(toolName: String, args: JsonObject): ToolConcurrency? = null
}
