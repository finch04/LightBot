# 知识库文件在线编辑 + 全量重建 设计文档

> 作者：finch
> 日期：2026-06-15
> 状态：设计阶段
> 参考：[agent-knowledge-hub](https://github.com/bcefghj/agent-knowledge-hub) 文档级变更重建方案

---

## 一、需求分析

### 1.1 核心需求

| 需求 | 描述 |
|------|------|
| 在线编辑 | 用户在知识库详情页可直接编辑文档内容，无需下载-修改-重新上传 |
| 多格式支持 | 按格式适配不同编辑器（Monaco / 富文本 / 表格） |
| 自动重建 | 编辑保存后自动触发该文档的全量重建（chunk → 向量化 → 知识图谱） |
| 适配入口 | 只有已适配在线编辑的文件格式才显示"编辑"按钮 |

### 1.2 用户场景

```
场景1：用户发现 Markdown 文档某个章节有错误
  → 点击"编辑" → Monaco Editor 打开 → 修改内容 → 保存
  → 系统删除该文档旧 chunk/向量 → 重新解析 → 重新分块 → 重新向量化 → 重新抽取图谱

场景2：用户需要更新 DOCX 产品手册
  → 点击"编辑" → 富文本编辑器打开（Mammoth 解析为 HTML）→ 修改 → 保存
  → 后端将 HTML 转回 DOCX → 更新 MinIO 文件 → 触发全量重建

场景3：用户需要更新 Excel 数据表
  → 点击"编辑" → 表格编辑器打开 → 修改数据 → 保存
  → 后端将表格数据转回 XLSX → 更新 MinIO 文件 → 触发全量重建
```

### 1.3 非目标（本期不做）

- 图片/PPT 的在线编辑
- 多人协同编辑
- 编辑冲突解决（单用户场景）
- 版本历史管理（UI Tab 预留，功能后续迭代）
- Chunk 级增量更新（复杂度高，本期采用文档级全量重建）

---

## 二、现状分析

### 2.1 现有文档处理流程

```
用户上传文件
    ↓
uploadDocument()  →  保存到 MinIO + 创建 Document 记录（UPLOADING）
    ↓
异步：Tika 解析 → Markdown 转换 → 存储 parsed/ 路径
    ↓
ingestDocument()  →  分块 → 向量化 → 完成
    ↓
completeDocument()  →  示例问题生成 + 图谱抽取（如开启）
```

### 2.2 核心数据模型

```
Document（文档）
├── filePath          → MinIO 原始文件路径
├── markdownPath      → MinIO 解析后 Markdown 路径
├── fileHash          → MD5 去重
├── embeddingJson     → 分块配置
└── status            → UPLOADING → UPLOADED → PROCESSING → COMPLETED

Chunk（分块）         → 关联 documentId
Embedding（向量）     → 关联 chunkId
GraphDocument（图谱） → 关联 documentId + graphId
```

### 2.3 现有重建能力

现有 `processDocumentWithProgress()` 已具备**删除旧数据 + 全量重建**的能力：

```java
// DocumentServiceImpl.processDocumentWithProgress() 开头
chunkService.deleteByDocumentId(documentId);   // 删旧 chunk
embeddingService.deleteByDocumentId(documentId); // 删旧向量
// 然后重新分块 + 向量化
```

**结论**：在线编辑保存后，只需更新文件内容，复用现有的全量重建流程即可。

---

## 三、技术方案设计

### 3.1 整体流程

```
用户点击"编辑"按钮（仅已适配格式显示）
    ↓
前端根据文件类型选择编辑器，加载文档内容
    ↓
用户编辑内容
    ↓
点击"保存" → 调用保存 API
    ↓
后端处理：
  1. 将编辑内容转回原格式（MD 直接保存 / DOCX 需 HTML→DOCX / XLSX 需 JSON→XLSX）
  2. 覆盖 MinIO 中的原文件 + 更新 markdownPath
  3. 更新 Document.fileHash
  4. 异步触发全量重建（复用现有 ingestDocument 流程）
    ↓
前端轮询重建进度 → 完成后刷新文档列表
```

### 3.2 分格式编辑策略

| 格式 | 编辑器 | 加载方式 | 保存回写 | 编辑按钮 |
|------|--------|----------|----------|----------|
| **MD / TXT** | Monaco Editor | 直接读文本 | 直接保存文本 | 显示 |
| **CSV** | Monaco Editor（CSV 模式）| 直接读文本 | 直接保存文本 | 显示 |
| **DOCX** | TinyMCE / Quill 富文本 | Mammoth 解析为 HTML | html-docx-js 生成 DOCX | 显示 |
| **XLSX / XLS** | Luckysheet 表格 | SheetJS 解析为 JSON | SheetJS 生成 XLSX | 显示 |
| **PDF** | 暂不支持 | — | — | **不显示** |
| **PPT** | 暂不支持 | — | — | **不显示** |
| **图片** | 暂不支持 | — | — | **不显示** |

**分阶段实现**：

```
Phase 1（MVP）：MD / TXT / CSV  → Monaco Editor
Phase 2：DOCX                  → 富文本编辑器
Phase 3：XLSX / XLS            → 表格编辑器
```

### 3.3 保存 + 重建流程

```java
/**
 * 文档编辑保存后触发全量重建
 * 参考 agent-knowledge-hub 的 handleModify 策略：
 * 删除旧数据 → 重新解析 → 重新分块 → 重新向量化 → 重新抽取图谱
 */
@Async
public void saveAndRebuild(Long documentId, String newContent, String editMode) {
    Document doc = documentService.getById(documentId);
    Long knowledgeId = doc.getKnowledgeId();

    // 1. 将编辑内容转回原格式并更新 MinIO
    switch (editMode) {
        case "editor" -> {
            // MD/TXT/CSV：直接覆盖 markdownPath
            minioUtil.upload(doc.getMarkdownPath(), newContent.getBytes(StandardCharsets.UTF_8));
        }
        case "richtext" -> {
            // DOCX：HTML → DOCX → 覆盖 filePath，同时更新 markdownPath
            byte[] docxBytes = docxConverter.htmlToDocx(newContent);
            minioUtil.upload(doc.getFilePath(), docxBytes);
            // 更新 Markdown 版本
            String markdown = docxConverter.htmlToMarkdown(newContent);
            minioUtil.upload(doc.getMarkdownPath(), markdown.getBytes(StandardCharsets.UTF_8));
        }
        case "spreadsheet" -> {
            // XLSX：JSON → XLSX → 覆盖 filePath，同时更新 markdownPath
            byte[] xlsxBytes = xlsxConverter.jsonToXlsx(newContent);
            minioUtil.upload(doc.getFilePath(), xlsxBytes);
            String markdown = xlsxConverter.jsonToMarkdown(newContent);
            minioUtil.upload(doc.getMarkdownPath(), markdown.getBytes(StandardCharsets.UTF_8));
        }
    }

    // 2. 更新文件 hash
    doc.setFileHash(DigestUtils.md5Hex(newContent));
    doc.setLastEditTime(LocalDateTime.now());
    documentService.updateById(doc);

    // 3. 异步触发全量重建（复用现有流程）
    documentService.ingestDocument(documentId, doc.getEmbeddingJson());
}
```

### 3.4 乐观锁（防并发编辑）

```
1. 用户打开编辑时，后端返回当前 fileHash
2. 用户保存时，携带 expectedHash
3. 后端校验：
   - currentHash == expectedHash → 允许保存
   - currentHash != expectedHash → 返回 409 Conflict
4. 前端提示："文档已被修改，请刷新后重试"
```

---

## 四、接口设计

### 4.1 获取文档可编辑内容

```
GET /api/documents/{documentId}/editable-content
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "documentId": 123,
    "fileName": "产品手册.docx",
    "fileType": "docx",
    "editMode": "richtext",
    "content": "<h1>产品手册</h1><p>第一章...</p>",
    "fileHash": "a1b2c3d4...",
    "editable": true,
    "metadata": {
      "totalLines": 520,
      "totalChunks": 35
    }
  }
}
```

| editMode | 前端编辑器 | 适用格式 |
|----------|-----------|----------|
| `editor` | Monaco Editor | MD, TXT, CSV |
| `richtext` | TinyMCE / Quill | DOCX |
| `spreadsheet` | Luckysheet | XLSX, XLS |
| `unsupported` | 不可编辑 | PDF, PPT, 图片 |

### 4.2 保存编辑内容

```
PUT /api/documents/{documentId}/content
```

**请求体：**

```json
{
  "content": "# 产品手册\n\n## 第一章（已修改）...",
  "editMode": "editor",
  "expectedHash": "a1b2c3d4..."
}
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "documentId": 123,
    "newHash": "e5f6g7h8...",
    "rebuildTaskId": 456,
    "message": "保存成功，正在重新处理文档..."
  }
}
```

**乐观锁冲突：**

```json
{
  "code": 409,
  "message": "文档已被其他人修改，请刷新后重试"
}
```

### 4.3 查询重建进度

```
GET /api/documents/{documentId}/rebuild-progress
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "status": "processing",
    "progress": 65,
    "message": "正在向量化..."
  }
}
```

---

## 五、数据库设计

### 5.1 Document 表扩展

```sql
ALTER TABLE document ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE document ADD COLUMN last_edit_time TIMESTAMP;

COMMENT ON COLUMN document.version IS '文档内容版本号，每次编辑递增';
COMMENT ON COLUMN document.last_edit_time IS '最后一次在线编辑时间';
```

> 版本管理 UI Tab 本期预留，version 字段先入库但不展示。

---

## 六、前端设计

### 6.1 编辑器选型

| 编辑器 | 用途 | 包大小 | 许可证 |
|--------|------|--------|--------|
| **Monaco Editor** | MD/TXT/CSV 代码编辑 | ~2MB（按需加载） | MIT |
| **TinyMCE** | DOCX 富文本编辑 | ~500KB | MIT（社区版） |
| **Luckysheet** | XLSX/XLS 表格编辑 | ~2MB | MIT |

### 6.2 前端交互流程

```
知识库详情页 → 文档列表
    │
    ├─ 每个文档行：
    │   ├─ 已适配格式 → 显示"编辑"按钮（EditOutlined 图标）
    │   └─ 未适配格式 → 不显示"编辑"按钮
    │
    ├─ 点击"编辑" → 打开编辑器弹窗（全屏 Modal）
    │   ├─ 加载文档内容（调用 editable-content API）
    │   ├─ 根据 editMode 渲染对应编辑器
    │   ├─ 用户编辑
    │   └─ 点击"保存" → 调用保存 API
    │       ├─ 成功 → 显示 "保存成功，正在重新处理..."
    │       │       → 轮询 rebuild-progress
    │       │       → 完成 → 关闭弹窗 + 刷新列表
    │       └─ 409 → 提示 "文档已被修改，请刷新"
    │
    └─ 版本历史 Tab（预留，本期不实现）
```

### 6.3 前端关键组件

```
src/components/DocumentEditor/
├── DocumentEditorModal.vue     # 编辑器弹窗主容器（根据 editMode 切换子组件）
├── MarkdownEditor.vue           # Monaco Editor 封装（MD/TXT/CSV）
├── RichtextEditor.vue           # TinyMCE 封装（DOCX）
└── SpreadsheetEditor.vue        # Luckysheet 封装（XLSX）
```

### 6.4 编辑按钮显示逻辑

```javascript
// 已适配在线编辑的格式
const EDITABLE_TYPES = new Set(['md', 'txt', 'csv', 'docx', 'doc', 'xlsx', 'xls'])

function isEditable(fileName) {
  const ext = fileName.split('.').pop().toLowerCase()
  return EDITABLE_TYPES.has(ext)
}
```

---

## 七、业务逻辑流程

### 7.1 完整流程图

```
┌──────────────────────────────────────────────────────────────┐
│                     在线编辑 + 全量重建                         │
│                                                              │
│  1. 用户点击"编辑"按钮                                         │
│     └─ 校验文件格式是否支持编辑                                  │
│                                                              │
│  2. 前端调用 GET /editable-content                             │
│     ├─ 后端读取 MinIO 文件                                     │
│     ├─ 根据格式解析为编辑器可接受的内容（文本/HTML/JSON）           │
│     └─ 返回 {content, editMode, fileHash}                     │
│                                                              │
│  3. 前端根据 editMode 渲染编辑器                                │
│     ├─ editor → Monaco Editor（直接编辑文本）                   │
│     ├─ richtext → TinyMCE（富文本编辑）                        │
│     └─ spreadsheet → Luckysheet（表格编辑）                    │
│                                                              │
│  4. 用户编辑 → 点击"保存"                                      │
│     └─ 前端调用 PUT /content {content, editMode, expectedHash} │
│                                                              │
│  5. 后端保存处理                                               │
│     ├─ 校验 expectedHash（乐观锁）                             │
│     ├─ 根据 editMode 将内容转回原格式                           │
│     │   ├─ editor → 直接覆盖 markdownPath                     │
│     │   ├─ richtext → HTML→DOCX 覆盖 filePath                 │
│     │   └─ spreadsheet → JSON→XLSX 覆盖 filePath              │
│     ├─ 更新 Document.fileHash                                 │
│     └─ 异步触发全量重建                                        │
│                                                              │
│  6. 全量重建（复用现有 ingestDocument 流程）                     │
│     ├─ 删除旧 Chunk + Embedding                               │
│     ├─ 重新解析（Tika / 读取 markdownPath）                    │
│     ├─ 重新分块                                               │
│     ├─ 重新向量化                                             │
│     ├─ 重新生成示例问题                                        │
│     └─ 重新抽取知识图谱（如开启 graphEnabled）                   │
│                                                              │
│  7. 前端轮询进度 → 完成 → 关闭弹窗 → 刷新文档列表               │
└──────────────────────────────────────────────────────────────┘
```

---

## 八、涉及文件清单

### 后端新增

| 文件 | 说明 |
|------|------|
| `controller/DocumentEditController.java` | 文档编辑 Controller（3 个接口） |
| `service/DocumentEditService.java` | 在线编辑服务接口 |
| `service/impl/DocumentEditServiceImpl.java` | 在线编辑服务实现 |
| `dto/EditableContentVO.java` | 可编辑内容响应 DTO |
| `dto/DocumentEditRequest.java` | 编辑保存请求 DTO |
| `util/DocxConverter.java` | DOCX ↔ HTML/Markdown 双向转换 |
| `util/XlsxConverter.java` | XLSX ↔ JSON/Markdown 双向转换 |

### 后端修改

| 文件 | 改动 |
|------|------|
| `entity/Document.java` | 新增 version, lastEditTime 字段 |
| `util/TikaUtil.java` | 新增 readMarkdownContent() 方法（读取已解析的 Markdown） |

### 前端新增

| 文件 | 说明 |
|------|------|
| `components/DocumentEditor/DocumentEditorModal.vue` | 编辑器弹窗主容器 |
| `components/DocumentEditor/MarkdownEditor.vue` | Monaco Editor 封装 |
| `components/DocumentEditor/RichtextEditor.vue` | 富文本编辑器（Phase 2） |
| `components/DocumentEditor/SpreadsheetEditor.vue` | 表格编辑器（Phase 3） |
| `api/documentEdit.js` | 编辑相关 API |

### 前端修改

| 文件 | 改动 |
|------|------|
| `views/KnowledgeDetail.vue` | 文档列表增加"编辑"按钮（按格式条件显示） |

---

## 九、实施计划

### Phase 1（MVP）：MD / TXT / CSV 在线编辑

**周期**：1.5 周

| 任务 | 工作量 |
|------|--------|
| DocumentEditController + Service（3 个接口） | 1d |
| Document 实体扩展（version, lastEditTime） | 0.5d |
| 前端 Monaco Editor 封装 + 编辑弹窗 | 2d |
| 前端编辑按钮条件显示 | 0.5d |
| 前端保存 + 进度轮询 | 1d |
| 联调测试 | 1d |

### Phase 2：DOCX 富文本编辑

**周期**：1.5 周

| 任务 | 工作量 |
|------|--------|
| DocxConverter（Mammoth 解析 + html-docx-js 生成） | 1.5d |
| 前端 TinyMCE 集成 | 2d |
| 联调测试 | 1d |

### Phase 3：XLSX 表格编辑

**周期**：1.5 周

| 任务 | 工作量 |
|------|--------|
| XlsxConverter（SheetJS 双向转换） | 1d |
| 前端 Luckysheet 集成 | 2d |
| 联调测试 | 1d |

---

## 十、难点与风险

### 10.1 二进制格式转换信息丢失

**问题**：DOCX → HTML → DOCX 过程中，复杂排版（合并单元格、SmartArt、嵌套表格）会丢失。

**缓解**：
- 编辑弹窗顶部提示："在线编辑仅保留文字和基础格式，复杂排版建议下载本地编辑"
- 保留原始文件备份，编辑失败可回滚

### 10.2 大文件编辑性能

**问题**：大 DOCX/XLSX 解析为 HTML/JSON 可能很慢。

**缓解**：
- XLSX 限制加载前 10 个 sheet，每个 sheet 前 1000 行
- DOCX 超过 5MB 不支持在线编辑，提示下载本地编辑
- 加载时显示 loading 状态

### 10.3 重建期间文档不可用

**问题**：全量重建期间（删旧 chunk → 重建），该文档的 RAG 检索会暂时失效。

**缓解**：
- 重建过程快速（通常 < 30s）
- Document 状态标记为 PROCESSING，前端显示"处理中"标签
- 不影响其他文档的检索

### 10.4 并发编辑冲突

**问题**：多人同时编辑同一文档。

**缓解**：
- 乐观锁（expectedHash 校验），后保存者收到 409 提示
- 本期不做实时协同，简单冲突检测即可

---

## 十一、与参考项目的对比

| 维度 | agent-knowledge-hub | LightBot 方案 |
|------|---------------------|---------------|
| 变更检测 | Watchdog 文件监听 + Kafka CDC | 用户主动编辑保存 |
| 重建策略 | 删旧向量 → 重新解析 → 重建（文档级） | 同左（复用现有 ingestDocument） |
| 在线编辑 | 未实现（仅监听外部修改） | 内置多种格式编辑器 |
| 增量粒度 | 文档级（改 A 只重建 A） | 同左 |
| 触发方式 | 自动（文件系统事件） | 手动（用户保存触发） |

**核心思路一致**：改了哪个文档就重建哪个文档，不碰其他文档。区别是 LightBot 增加了在线编辑能力，用户无需离开系统即可完成修改。
