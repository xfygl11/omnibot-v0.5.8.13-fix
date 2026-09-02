# 独占工具阻塞同轮兄弟工具 — 问题分析与修复方案

日期: 2026-09-02
状态: 已实现（主源码改动 + 单测补充 + 镜像验证；端到端设备验证待 APK 构建）
范围: `AgentOrchestrator.kt` / `AgentToolConcurrencyPolicy.kt`（App Agent 运行时）

## 1. 问题描述

小万 Agent（Agnes）的《独占工具调用机制测试报告》确认了一个真实存在的低效行为：

> 当同一轮（同一条 assistant 消息）里既有独占工具（turn-boundary，如 `terminal_execute` / `bash`），又有其他工具时，独占工具执行完毕后，同轮**剩余工具会被全部丢弃**，模型在下一轮必须重新发起这些调用。

报告中的典型例子（测试组 A / B / F）：

- `terminal_execute + memory_write_daily + file_list + context_time_now`
  → `terminal_execute` 成功，其余 3 个工具全部被阻塞；
- `terminal_execute + terminal_session_start`
  → 即使 `terminal_session_start` 本身非独占，也被阻塞。

每个这样的轮次代价是：

1. 该轮只真正执行了 1 个工具，其余 N-1 个调用被 `appendSyntheticToolResultMessages`
   回填为“本轮未执行该工具…请由模型在下一轮重新发起”；
2. 模型下一轮要**重新生成**这些调用，浪费一次完整 LLM 调用与 token；
3. 对话轮次预算（`DEFAULT_AGENT_MAX_MODEL_ROUNDS = 16`）被大量浪费，
   与用户之前遇到的“Agent 已达到 16 轮模型调用上限”直接相关。

## 2. 根因分析

当前执行流程（`AgentOrchestrator.kt` Phase B）**严格按模型发射顺序**执行批次：

1. `AgentToolConcurrencyPolicy.partitionToolCalls()` 贪心分桶，**保留消息原始顺序**；
2. 串行遍历批次，一旦遇到 turn-boundary 工具，设置 `hitTurnBoundary = true`
   并 `advanceToNextRound = true`；
3. 此后再遇到**非并行批次**就 `break@batchLoop`，这些批次的工具调用被丢弃、
   回填合成错误消息（`AgentOrchestrator.kt:788-931`、`1225-1289`）。

也就是说：只要独占工具在消息里排得靠前，其后的兄弟调用（无论是否安全）
都会被这一轮“吃掉”，白白消耗一次模型往返。

`partitionToolCalls` 保留原始顺序是**刻意**的（保证并行/串行分桶与消息一致），
问题在于它把“执行顺序”和“模型发射顺序”绑死，而独占工具恰好要求在
“把结果交还给模型、由模型决定下一步”之前停止该轮。

### 独占（turn-boundary）的权威判定

按当前代码，唯一能触发“剩余 tool_call 未执行”回填的路径是
`AgentToolConcurrencyPolicy.isTurnBoundary()` 白名单（`terminal_execute` /
`bash` / `android_privileged_*`）。被测试报告归为“独占”的 `vlm_task` 与
`subagent_dispatch` 经核对其实**不在**该白名单内：它们是 SERIAL_BARRIER
（与并行工具互斥、逐个串行执行），但**不会**中断本轮、也不会丢弃兄弟调用，
因此无需也不应加入 turn-boundary 白名单——把它们标成 boundary 反而会重新引入
兄弟工具饥饿。本轮修复只针对白名单内真正会丢弃兄弟调用的独占工具；
混合 `vlm_task` / `subagent_dispatch` 的轮次按“串行非独占”处理（先并行兄弟、
再串行这两个工具、最后执行独占工具）。

### 为什么不能简单地把“独占工具之后的工具全部允许继续执行”？

上一版修复（PR #3，`hitTurnBoundary` 允许其后的 *parallel-safe* 批次继续执行）
只能覆盖“独占工具恰好排在若干 parallel-safe 工具之前”的组合，且无法覆盖
`terminal_execute + terminal_session_start`（后者是 SERIAL_BARRIER）这类场景，
测试报告 B / A 组仍然阻塞。

### 为什么不能“同轮执行多个独占工具”？

`terminal_execute` 这类工具的语义是**观察点**：它的输出应当被模型看到后再决定
下一步动作。如果同轮强制执行第二个独占工具，等于替模型做了“看到 A 结果后仍要
执行 B”的假设，可能破坏依赖顺序。因此保留“同轮最多一个独占工具执行完毕并结束本轮”
的设计，只修复“非独占兄弟工具被误伤”。

## 3. 修复方案：独占批次稳定后置（partition-turn-boundary-last）

核心思路：**把执行顺序与发射顺序解耦 —— 在同一轮内，先执行所有非独占批次，
再把独占（turn-boundary）批次放到最后执行。**

- 非独占工具与独占工具的相对顺序被打乱，但：
  - 非独占批次之间**保持原始相对顺序**；
  - 独占批次之间**保持原始相对顺序**；
  - parallel-safe 合并分桶规则不变（`partitionToolCalls` 原样复用）。
- 独占工具仍是本轮最后一个观察点：它执行 → `advanceToNextRound = true` →
  结果连同本轮所有兄弟工具的结果一起在下一轮交还模型。
- 原先会被丢弃的兄弟调用现在**全部在同一轮内真实执行**，模型无需重发。

### 语义正确性论证

多工具调用出现在同一条 assistant 消息时，模型侧的契约是“这些调用并行执行、
参数在发射时已固定”。因此把排在前面的独占调用移到末尾执行，不会改变任何工具
的参数与返回内容，只改变副作用发生的先后：

- 原行为（独占靠前）会把其后兄弟调用直接丢弃 → 兄弟工具**不执行**；
- 新行为（独占靠后）让兄弟调用先执行、独占最后执行 → 兄弟工具**执行**。

对模型而言，两者在下一轮看到的结果集合一致（独占结果 + 兄弟结果一起返回），
只是新行为不再需要额外一轮去重发兄弟调用。

### 保留的边界行为

- **同一轮出现多个独占工具**时，仍只执行第一个，其余按原逻辑丢弃并回填
  “独占工具已占用本轮”的合成消息（如 3 个 `terminal_execute` 并发）。
  这是“独占工具 = 观察点”的固有语义，本方案不改动。
- 参数解析/校验失败、对话被停止等终止路径不受影响。
- **会话终止类兄弟**（结果映射为 ChatMessage/Clarify/PermissionRequired 的工具）
  现会先于独占工具执行；若其终止对话，独占工具将按“剩余 tool_call 未执行”回填。
  两种机制都必然丢弃一侧调用（重排前丢的是该会话终止兄弟），属固有取舍。

## 4. 实现变更

### 4.1 `AgentToolConcurrencyPolicy.kt`

新增纯函数（无 Android 依赖，可单测）：

```kotlin
/**
 * 先按原有规则分桶，再把含 turn-boundary（独占）工具的批次稳定移到末尾。
 */
fun partitionTurnBoundaryLast(
    calls: List<AssistantToolCall>,
    parsedArgs: Map<String, JsonObject>,
): List<ToolBatch> = partitionTurnBoundaryLast(calls, parsedArgs) { call, args ->
    classify(call.function.name, args)
}

fun partitionTurnBoundaryLast(
    calls: List<AssistantToolCall>,
    parsedArgs: Map<String, JsonObject>,
    classifier: (AssistantToolCall, JsonObject) -> ToolConcurrency,
): List<ToolBatch> {
    val batches = partitionToolCalls(calls, parsedArgs, classifier)
    if (batches.size <= 1) return batches
    val regular = mutableListOf<ToolBatch>()
    val exclusive = mutableListOf<ToolBatch>()
    for (batch in batches) {
        val isBoundary = batch.calls.any { isTurnBoundary(it.function.name) }
        (if (isBoundary) exclusive else regular).add(batch)
    }
    if (regular.isEmpty() || exclusive.isEmpty()) return batches
    return regular + exclusive
}
```

注：与既有 `partitionToolCalls` 一样提供带 classifier 的重载，
避免在大型 suspend 状态机上产生默认 lambda 桥（对齐文件内既有注释的告诫）。

### 4.2 `AgentOrchestrator.kt`

Phase B 调用点改用新函数（`AgentOrchestrator.kt:776`）：

```kotlin
val batches = AgentToolConcurrencyPolicy.partitionTurnBoundaryLast(
    validatedCalls,
    parsedArgsMap
)
```

批次遍历逻辑（`hitTurnBoundary` 判断等）保持不变。

## 5. 预期效果对照测试报告

| 场景（按模型发射顺序） | 修复前（PR#3 现状） | 修复后 |
|------|--------|--------|
| A: terminal_execute → memory_write_daily → file_list → context_time_now | 后 3 个被阻塞 | 4 个全部执行，terminal 最后 |
| B: terminal_execute → terminal_session_start | session_start 被阻塞 | 2 个全部执行 |
| F: terminal_execute → webfetch | webfetch 被阻塞 | 2 个全部执行 |
| J: terminal_execute → vlm_task → memory_write_daily | vlm_task、memory_write 被阻塞 | 3 个全部执行，terminal 最后 |
| K: bash → subagent_dispatch → memory_write_daily → glob | 后 3 个被阻塞 | 4 个全部执行，bash 最后 |
| C: terminal_execute ×3 | 仅第 1 个执行，其余丢弃 | 仅第 1 个执行（语义保留） |
| D/E/G/H/I: 纯非独占轮 | 全部执行 | 全部执行（不回归，顺序不变） |

## 6. 验证方式与结果

1. JVM 单元测试：`AgentToolConcurrencyPolicyTest` 新增 `partitionTurnBoundaryLast`
   用例（独占批次后置、串行兄弟不被饥饿、多独占相对顺序、空输入、无独占不重排）。
   注意：app 模块单元测试树仍停留在重命名前的 `cn.com.omnimind.bot.*` 包
   （重命名 commit f3197d0 未同步 app/src/test），整个 app 测试源集当前无法编译，
   本文件已同步为引用现存 `cn.com.omnimind.agent.*` 主类；待测试树迁移后可整体运行。
2. 算法镜像验证（本机无 JDK，用 Python 镜像同一贪心分桶 + 稳定后置 + batchLoop
   break 语义）：对 A~K 全部场景推演，结果见下。

```
场景  执行顺序(修复后)                                                        丢弃(修复前)                丢弃(修复后)
A    memory_write_daily + file_list + context_time_now + terminal_execute    memory_write_daily + file_list + context_time_now   -
B    terminal_session_start + terminal_execute                               terminal_session_start                             -
C    terminal_execute                                                        terminal_execute ×2                               terminal_execute ×2
F    webfetch + terminal_execute                                             webfetch                                          -
J    vlm_task + memory_write_daily + terminal_execute                        vlm_task + memory_write_daily                     -
K    subagent_dispatch + memory_write_daily + glob + bash                    subagent_dispatch + memory_write_daily + glob     -
D/E/G/H/I   原顺序全部执行                                                    -                                                 -
不变式 PASS：非独占兄弟全部执行 / 丢弃集合不含非独占 / 独占至多执行一个
```

3. 端到端（待设备验证）：安装新构建 APK，运行含 `terminal_execute` + 若干非独占
   工具的同一轮请求，确认所有工具单轮内完成、不再出现“本轮未执行该工具”提示。
