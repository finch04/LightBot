# Ask User 交互式弹窗功能设计文档

> 版本：v2.0 | 日期：2026-06-23 | 作者：finch

---

## 一、需求背景

当前 `ask_user` 工具被 AI 调用后，结果仅作为静态文本展示在聊天记录中，用户无法直接在界面上选择选项或输入回答，需要手动打字回复。

**目标**：AI 调用 `ask_user` 工具后，页面自动弹出交互式弹窗，展示问题和选项，用户点击选项或输入文本后自动发送回复，AI 实时接收并继续推理。

**核心原则**：ask_user 只是一个普通工具，不改变 AI 框架的流程控制。通过后端强制中断工具循环 + 前端弹窗交互实现解耦。

---

## 二、问题分析与解决方案

### 问题 1：AI 调用 ask_user 后不停止，继续调用其他工具

**根因**：`ChatServiceImpl.processToolCallsRecursively` 的递归循环没有针对 ask_user 的中断机制。工具返回后，结果注入上下文，AI 继续推理，可能再次调用工具。工具描述中写的"暂停执行"只是文本提示，AI 不一定遵守。

**解决方案**：在后端工具循环中增加 ask_user 检测，执行完后强制中断循环。

**改动位置**：`ChatServiceImpl.java` 的 `processToolCallsRecursively` 方法

**具体逻辑**：
```java
// 在工具执行完毕、结果注入上下文之前，检测是否为 ask_user
boolean hasAskUser = toolResponses.stream()
    .anyMatch(r -> "ask_user".equals(r.name()));

if (hasAskUser) {
    // 强制中断工具循环，不再递归调用 AI
    // 仅返回工具执行事件，不进入下一轮推理
    return toolEventFlux;  // 不 concatWith 递归调用
}
```

**效果**：ask_user 工具执行后，AI 不会继续推理或调用其他工具。前端收到 tool_result + done 事件后弹窗。用户回复后触发新一轮独立对话。

**解耦说明**：这是通用的"工具中断循环"机制，不针对 ask_user 做特殊业务逻辑。未来其他需要中断循环的工具也可以复用此机制（通过在工具结果中标记 `break_loop: true`）。

### 问题 2：ask_user 必须提供至少 3 个选项

**根因**：当前 `AskUserTool` 对 options 参数没有校验，AI 可能只传 1-2 个选项。

**解决方案**：在 `AskUserTool.java` 中增加校验，不足 3 个选项时自动补充提示。

**改动位置**：`AskUserTool.java` 的 `askUser` 方法

**具体逻辑**：
```java
// 校验选项数量
if (hasOptions && optionItems.size() < 3) {
    // 选项不足 3 个，返回错误提示让 AI 重新生成
    return objectMapper.writeValueAsString(Map.of(
        "error", true,
        "message", "选项数量不足，请提供至少 3 个选项"
    ));
}
```

**同时更新工具描述**，明确要求 AI 提供至少 3 个选项：
```
description = "向用户提问并等待回答。必须提供至少3个选项供用户选择。
调用后会立即停止执行，等待用户回复后继续。不要在调用此工具后再调用其他工具。"
```

### 问题 3：弹窗中点击选项和输入回答无反应

**根因分析**：

弹窗在 `onDone` 回调中通过 `nextTick` 触发，此时 `loading.value` 已被设为 `false`。但 `submitAskUserResponse` 中有 `loading.value` 守卫：

```javascript
async function submitAskUserResponse(answer) {
  if (!answer?.trim() || loading.value) return  // ← 如果 loading 仍为 true，直接 return
}
```

可能的场景：
1. `onDone` 中 `loading.value = false` 的赋值与 `nextTick` 弹窗存在时序问题
2. 弹窗打开后，用户操作时 `loading.value` 可能仍为 true（如果 onDone 回调延迟）

**解决方案**：

1. `submitAskUserResponse` 中移除 `loading.value` 守卫（因为此时流已结束，不需要此检查）
2. 改为检查 `_streaming` 状态来确认流是否真的结束
3. 增加调试日志确认问题

```javascript
async function submitAskUserResponse(answer) {
  if (!answer?.trim()) return
  // 不检查 loading.value，因为此时 SSE 流已结束
  // loading.value 可能因时序问题仍为 true
  askUserModal.visible = false
  const text = answer.trim()
  messages.value.push({ role: 'user', content: text, _attachments: [] })
  isNearBottom.value = true
  scrollToBottom()
  await runChatStream({ message: text, attachments: [], regenerate: false })
}
```

### 问题 4：工具只是工具，不要影响 AI 流程

**设计原则**：ask_user 的"等待"效果通过以下机制实现，不依赖 AI 的自觉行为：

| 层级 | 机制 | 说明 |
|------|------|------|
| 后端工具循环 | 执行 ask_user 后中断递归 | 硬性保障，AI 不可能继续调工具 |
| 工具描述 | 明确告知 AI 行为边界 | 软性引导，AI 不再尝试调其他工具 |
| 前端弹窗 | 流结束后自动弹窗 | 用户交互入口 |
| 前端发送 | 用户回复作为新消息 | 完全复用现有 sendMessage 流程 |

**关键**：后端中断循环后，AI 本轮推理结束。用户回复是全新的消息，触发新一轮推理。两轮之间完全独立，ask_user 工具本身不持有任何状态。

---

## 三、后端技术设计

### 3.1 ChatServiceImpl — 工具循环中断机制

**文件**：`ChatServiceImpl.java`

**改动位置**：`processToolCallsRecursively` 方法，工具执行完毕后、递归调用前（约 line 695-765）

**改动内容**：

```java
// 1. 工具执行完毕，收集结果
toolResponses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result));

// 2. 注入结果到上下文
messages.add(ToolResponseMessage.builder().responses(toolResponses).build());

// 3. 【新增】检测是否包含 ask_user 工具，如果是则中断循环
boolean hasAskUser = toolResponses.stream()
    .anyMatch(r -> "ask_user".equals(r.name()));
if (hasAskUser) {
    log.info("[Chat][Trace] ask_user 工具调用，中断工具循环，等待用户回复");
    return toolEventFlux;  // 不进入下一轮递归
}

// 4. 原有逻辑：继续递归
return toolEventFlux.concatWith(
    processToolCallsRecursively(ctx, depth + 1, nextLlmStart, eventSink));
```

**同时改动非流式路径** `processChatWithToolCalls`（约 line 235-241）：

```java
messages.add(ToolResponseMessage.builder().responses(toolResponses).build());

// 【新增】检测 ask_user
boolean hasAskUser = toolResponses.stream()
    .anyMatch(r -> "ask_user".equals(r.name()));
if (hasAskUser) {
    log.info("[Chat][Trace] ask_user 工具调用，中断工具循环，等待用户回复");
    return fullReply.toString();  // 提前返回
}

// 原有逻辑：继续循环
```

### 3.2 AskUserTool — 选项校验 + 工具描述优化

**文件**：`AskUserTool.java`

**改动 1：工具描述**
```java
@Tool(name = "ask_user",
      description = "向用户提问并等待回答。当需要确认信息、请求补充说明、让用户选择选项时调用此工具。" +
            "必须提供至少3个选项供用户选择（用逗号分隔）。" +
            "调用此工具后系统会自动停止执行，等待用户回复后继续。" +
            "重要：调用此工具后不要再调用其他工具，也不要输出任何内容，系统会自动处理。")
```

**改动 2：选项校验**
```java
// 在构建 output 之前校验
if (hasOptions && optionItems.size() < 3) {
    log.warn("[Tool:ask_user] 选项不足3个: {}", optionItems);
    return objectMapper.writeValueAsString(Map.of(
        "_error", true,
        "message", "选项数量不足，请提供至少3个有意义的选项供用户选择"
    ));
}
```

**改动 3：输出增加 break_loop 标记**

为未来的通用中断机制预留字段：
```java
output.put("break_loop", true);  // 告知框架此工具执行后应中断循环
```

### 3.3 AskUserTool 输出格式

```json
{
  "question": "请选择编程语言",
  "options": ["Java", "Python", "Go", "Rust"],
  "is_open_ended": false,
  "wait_for_user": true,
  "break_loop": true
}
```

### 3.4 异常场景处理

| 场景 | 处理方式 |
|------|----------|
| AI 选项不足 3 个 | 返回 `_error` 让 AI 重新生成 |
| AI 不传 options | `is_open_ended=true`，弹窗只显示输入框 |
| AI 同时调用 ask_user + 其他工具 | 后端先执行所有工具，但检测到 ask_user 后中断循环 |
| ask_user 执行抛异常 | 走正常工具异常处理，不特殊对待 |

---

## 四、前端技术设计

### 4.1 弹窗触发流程

```
SSE 流结束 → onDone 回调
  → loading.value = false, streaming = false
  → nextTick 检测最后一条 assistant 消息是否包含未回答的 ask_user
  → 是 → showAskUserModal(msgIndex) → 弹窗展示
```

### 4.2 弹窗交互

**选项点击**：直接发送选项文本，弹窗关闭
**文本输入**：输入框 + 发送按钮，Ctrl+Enter 快捷键
**关闭弹窗**：不发送，用户可稍后通过 AskUserResult 中的"回答"按钮重新打开

### 4.3 submitAskUserResponse 关键改动

```javascript
async function submitAskUserResponse(answer) {
  if (!answer?.trim()) return
  askUserModal.visible = false
  const text = answer.trim()
  messages.value.push({ role: 'user', content: text, _attachments: [] })
  isNearBottom.value = true
  scrollToBottom()
  // 直接调用 runChatStream，不检查 loading.value
  // 因为此时 SSE 流已结束，loading 可能因时序问题仍为 true
  await runChatStream({ message: text, attachments: [], regenerate: false })
}
```

### 4.4 历史消息恢复

- 刷新页面后，`loadHistory` 扫描消息列表，对未回答的 ask_user 自动弹窗
- 已回答的显示为静态卡片（蓝色主题 + "用户已回答"标记）
- AskUserResult.vue 中未回答状态显示"回答"按钮，点击重新打开弹窗

### 4.5 messageIndex 透传链路

```
Chat.vue (:message-index="virtualRow.index")
  → ToolCallsGroupComponent (messageIndex prop)
    → ToolCallRenderer (messageIndex prop)
      → AskUserResult.vue (messageIndex prop + inject)
```

---

## 五、数据流时序图

```
用户发送消息
    ↓
后端：AI 推理 → 调用 ask_user 工具
    ↓
后端：执行 AskUserTool → 返回 JSON
    ↓
后端：【关键】检测到 ask_user → 中断工具循环（不递归）
    ↓
后端：注入工具结果到上下文 → AI 看到结果 → 生成引导语
    ↓
SSE：tool_call → tool_status → tool_result → content → [DONE]
    ↓
前端：onDone → loading=false → nextTick → 检测 ask_user → 弹窗
    ↓
用户：点击选项 / 输入文本
    ↓
前端：submitAskUserResponse → push user 消息 → runChatStream
    ↓
后端：新一轮推理 → AI 收到用户回复 → 继续对话
```

---

## 六、改动范围

| 文件 | 改动内容 | 改动量 |
|------|----------|--------|
| `ChatServiceImpl.java` | 工具循环中增加 ask_user 检测和中断逻辑 | ~10 行 |
| `AskUserTool.java` | 选项校验 + 工具描述优化 + break_loop 标记 | ~15 行 |
| `Chat.vue` | 弹窗状态/函数/provide/模板/触发逻辑/messageIndex 透传 | ~100 行 |
| `AskUserResult.vue` | 双模式渲染（已回答/未回答）+ inject | ~50 行 |
| `ToolCallsGroupComponent.vue` | messageIndex prop 透传 | ~2 行 |
| `ToolCallRenderer.vue` | messageIndex prop 透传 | ~2 行 |

---

## 七、FAQ

### Q1：后端中断循环后，AI 生成的引导语怎么办？

中断循环后，AI 不会再被调用。工具结果注入上下文后，如果需要 AI 生成引导语（如"请选择一个选项："），需要在中断前让 AI 完成一轮推理。但当前设计是：中断循环后直接返回工具事件，AI 的引导语由前端在弹窗标题中展示。

实际上，由于中断发生在工具执行后、AI 推理前，AI 根本不会生成引导语。前端弹窗的问题展示已经替代了这个功能。

### Q2：如果 AI 同时调用了 ask_user 和其他工具怎么办？

后端先执行所有工具（包括 ask_user 和其他工具），收集所有结果后检测到 ask_user 存在，中断循环。其他工具的结果也会返回给前端正常展示。

### Q3：用户关闭弹窗不回答怎么办？

ask_user 结果已保存在消息历史中。AskUserResult.vue 检测到未回答状态，显示"回答"按钮。用户随时可以点击重新打开弹窗。

### Q4：这个机制会影响其他工具吗？

不会。中断逻辑只在 `ask_user` 工具被调用时触发。其他工具的递归调用流程完全不受影响。`break_loop` 标记为未来扩展预留，当前只检查 `ask_user` 工具名。

### Q5：选项不足 3 个时 AI 怎么办？

`AskUserTool` 返回 `_error` 错误信息，AI 看到错误后会在下一轮重新调用并提供足够的选项。由于错误结果也触发了循环中断，AI 的"下一轮"需要用户手动发送任意消息来触发（或前端自动发送一个提示消息）。
