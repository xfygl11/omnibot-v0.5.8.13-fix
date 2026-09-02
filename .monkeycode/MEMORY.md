# User Instruction Memory

## Format

### User Instruction Entry
[User Instruction Summary]
- Date: [YYYY-MM-DD]
- Context: [Mentioned scenario or time]
- Instructions:
  - [Content of user teaching or instruction, described line by line]

### Project Knowledge Entry
[Project Knowledge Summary]
- Date: [YYYY-MM-DD]
- Context: Discovered by Agent while performing [specific task description]
- Category: [Operations & Deployment|Build Methods|Testing Methods|Troubleshooting & Debugging|Workflow & Collaboration|Environment Configuration]
- Instructions:
  - [Specific knowledge points, described line by line]

## Entries

[User Instruction Summary]
- Date: 2026-09-02
- Context: Tool execution failure - multiple tools in single message
- Instructions:
  - 当一条消息中同时调用多个工具时，如果包含 bash 工具，其他工具（如 glob、read 等）会被跳过，报错"独占工具 bash 已占用本轮"
  - 避免在同一轮消息中同时调用 bash 和其他工具，如需执行多个操作，分批调用
  - 合并多个查询到单个 bash 命令中，避免并发调用不同工具

[Project Knowledge Summary]
- Date: 2026-09-02
- Context: 发现 opencode 平台工具调用限制
- Category: Environment Configuration
- Instructions:
  - bash 工具是"独占工具"，当在单轮消息中调用 bash 时，其他工具调用会被跳过
  - 正确做法：先调用非 bash 工具获取信息，再单独调用 bash 执行命令
  - 或者将多个查询合并到单个 bash 命令中一次性执行
