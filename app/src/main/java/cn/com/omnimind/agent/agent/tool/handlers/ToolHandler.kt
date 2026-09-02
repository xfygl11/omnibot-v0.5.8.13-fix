package cn.com.omnimind.agent.agent.tool.handlers

import cn.com.omnimind.agent.agent.AgentCallback
import cn.com.omnimind.agent.agent.AgentToolExecutionHandle
import cn.com.omnimind.agent.agent.AgentToolRegistry
import cn.com.omnimind.agent.agent.ToolExecutionResult
import kotlinx.serialization.json.JsonObject

interface ToolHandler {
    val toolNames: Set<String>

    fun canHandle(toolName: String): Boolean = toolName in toolNames

    suspend fun execute(
        toolCall: cn.com.omnimind.baselib.llm.AssistantToolCall,
        args: JsonObject,
        runtimeDescriptor: AgentToolRegistry.RuntimeToolDescriptor,
        env: cn.com.omnimind.agent.agent.AgentExecutionEnvironment,
        callback: AgentCallback,
        toolHandle: AgentToolExecutionHandle
    ): ToolExecutionResult

    suspend fun dispose() {}
}
