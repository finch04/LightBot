# Word 文档图片提取功能 - 技术设计文档

> 参考 Yuxi 项目实现，将 Word 文档中的内嵌图片提取并展示

---

## 1. 需求分析

### 1.1 现状

| 项目 | 当前行为 |
|------|----------|
| LightBot | `TikaUtil.parseDocxToMarkdown()` 使用 Apache POI 提取文本和表格，**完全忽略图片** |
| Yuxi | 使用 Docling 解析 DOCX，提取图片上传 MinIO，Markdown 中以 `![img](url)` 引用 |

### 1.2 需求描述

用户上传包含图片的 Word 文档后：
1. 文本预览中能看到图片（以 Markdown 图片语法渲染）
2. 图片存储在 MinIO 中，通过 URL 访问
3. 不影响现有的文本/表格提取逻辑

### 1.3 影响范围

| 模块 | 影响 |
|------|------|
| `TikaUtil` | 新增图片提取逻辑 |
| `DocumentUploadExecutor` | 可能需要传递额外参数（图片存储路径） |
| 前端 `FilePreview` / 文本预览 | Markdown 渲染需支持图片展示 |
| MinIO | 新增图片存储桶/路径 |

---

## 2. 技术设计

### 2.1 整体流程

```
Word 文档
    │
    ▼
Apache POI 解析 XWPFDocument
    │
    ├── 文本段落 → Markdown 文本
    ├── 表格 → Markdown 表格
    └── 图片 → 提取二进制数据
                  │
                  ▼
            上传到 MinIO
            (knowledge/{id}/images/{timestamp}_{filename})
                  │
                  ▼
            生成访问 URL
                  │
                  ▼
            插入 Markdown 对应位置
            ![image_xxx.png](http://minio-url/xxx)
```

### 2.2 核心实现方案

**方案 A：基于 Apache POI 原生提取（推荐）**

POI 的 `XWPFDocument` 本身支持图片提取：

```java
// 伪代码示意
XWPFDocument doc = new XWPFDocument(inputStream);

// 1. 遍历文档中的所有图片
for (XWPFPictureData picture : doc.getAllPictures()) {
    byte[] data = picture.getData();           // 图片二进制
    String fileName = picture.getFileName();    // 如 image1.png
    String contentType = picture.getContentType(); // 如 image/png
}

// 2. 遍历段落，在图片出现的位置插入 Markdown 引用
for (XWPFParagraph para : doc.getParagraphs()) {
    for (XWPFRun run : para.getRuns()) {
        if (run.getEmbeddedPictures() != null) {
            for (XWPFPicture pic : run.getEmbeddedPictures()) {
                // 获取图片数据 → 上传 MinIO → 插入 ![img](url)
            }
        }
    }
}
```

**方案 B：Docling（Python 子进程）**

调用 Docling 的 Python 脚本作为子进程，适合需要更高质量解析的场景，但引入了 Python 依赖。

**方案 C：Apache Tika 内置图片提取**

Tika 3.x 支持提取嵌入资源，但不保留图片在文档中的位置信息。

### 2.3 推荐方案：方案 A

| 维度 | 评估 |
|------|------|
| 依赖 | 已有 `poi-ooxml`，无需新增 |
| 复杂度 | 中等，需处理图片位置映射 |
| 性能 | 纯 Java，无进程开销 |
| 图片位置 | 可通过 `XWPFRun.getEmbeddedPictures()` 精确定位 |

### 2.4 数据流设计

```
TikaUtil.parseDocxToMarkdown(inputStream)
    │
    ├── 返回: MarkdownResult { markdown: String, images: List<ImageData> }
    │
    └── ImageData { fileName, contentType, data, position }
            │
            ▼
DocumentUploadExecutor
    │
    ├── 遍历 images，上传到 MinIO
    ├── 替换 Markdown 中的占位符为实际 URL
    └── 存储最终 Markdown（含图片 URL）
```

### 2.5 图片存储路径

```
MinIO 路径: knowledge/{knowledgeId}/images/{timestamp}_{originalFilename}
示例:       knowledge/123/images/1718612345678_image1.png
```

---

## 3. 难点分析

### 3.1 图片位置映射

**问题**：POI 的 `getAllPictures()` 返回所有图片，但不告诉你图片在文档中的位置。

**解决**：通过 `XWPFParagraph → XWPFRun → getEmbeddedPictures()` 逐段落遍历，可以精确定位图片出现的位置，在对应位置插入 Markdown 图片引用。

```
段落1: "这是标题"
段落2: "下面是图片"  ← run 中包含图片
        → 插入 ![img](url)
段落3: "图片说明"
```

### 3.2 图片去重

**问题**：同一张图片可能被多次引用（Word 内部通过 `rId` 引用同一个图片资源）。

**解决**：
- 维护 `Map<String, String>` 映射：`rId → MinIO URL`
- 相同 `rId` 只上传一次，后续复用 URL

### 3.3 大文件内存压力

**问题**：大型 Word 文档可能包含大量高分辨率图片，全部加载到内存可能 OOM。

**解决**：
- 流式处理：逐段落处理，处理完一张图片后释放引用
- 图片大小限制：超过阈值（如 10MB）的图片跳过或压缩
- 并发上传：使用线程池并行上传多张图片到 MinIO

### 3.4 图片格式兼容

**问题**：Word 支持多种图片格式（EMF、WMF、TIFF 等），浏览器不一定能渲染。

**解决**：
- 优先保留原始格式（PNG、JPG、GIF、BMP 直接使用）
- EMF/WMF 等矢量格式：可考虑转为 PNG（需额外依赖 `batik` 或调用 ImageMagick）
- 初期可跳过不支持的格式，在 Markdown 中用 `[图片: 不支持的格式]` 占位

### 3.5 图文混排的 Markdown 局限

**问题**：Markdown 不支持图文混排（图片只能独占一行），Word 中的"文字环绕图片"布局无法还原。

**解决**：
- 接受 Markdown 的局限性，图片作为独立块展示
- 可在图片前后保留上下文文字，维持阅读顺序
- 高级排版需求建议直接预览源文件（PDF 渲染）

### 3.6 PPT/PDF 中的图片

**问题**：当前需求仅针对 Word，但 PPT/PDF 也可能需要图片提取。

**解决**：
- PPT：POI 的 `XMLSlideShow` 支持提取图片，逻辑类似
- PDF：PDFBox 支持提取嵌入图片
- 本期仅实现 Word，后续按需扩展

---

## 4. 需要新增的依赖

### 4.1 已有依赖（无需新增）

| 依赖 | 版本 | 用途 |
|------|------|------|
| `poi-ooxml` | 5.x（Spring AI Tika 传递） | DOCX 解析 + 图片提取 |
| `minio` | 8.x | 图片上传存储 |

### 4.2 可能需要的依赖

| 依赖 | 用途 | 是否必须 |
|------|------|----------|
| `org.apache.xmlgraphics:batik-transcoder` | EMF/WMF 转 PNG | 否（首期跳过不支持格式） |
| `com.twelvemonkeys.imageio:imageio-*` | 扩展图片格式支持 | 否（首期跳过） |

**结论**：首期实现无需新增任何 Maven 依赖，完全基于已有 POI + MinIO。

---

## 5. 技术栈迁移问题（Python → Java）

### 5.1 Yuxi（Python） vs LightBot（Java）对比

| 维度 | Yuxi (Python) | LightBot (Java) |
|------|---------------|-----------------|
| DOCX 解析库 | Docling | Apache POI |
| 图片提取方式 | `doc.pictures` → data URI | `XWPFDocument.getAllPictures()` → byte[] |
| 图片上传 | MinIO Python SDK | MinIO Java SDK |
| Markdown 替换 | `re.sub(r"<!-- image -->", ...)` | `String.replace()` / 正则 |
| 异步模型 | asyncio | CompletableFuture / 线程池 |

### 5.2 关键差异

**Docling vs POI 的图片提取**

Docling 是一个专门的文档 AI 库，会自动：
- 提取图片并转为 data URI
- 在 Markdown 中插入 `<!-- image -->` 占位符
- 保留图片与文本的位置关系

POI 需要手动：
- 遍历段落和 Run 找到图片
- 手动提取 `byte[]` 数据
- 手动在 Markdown 对应位置插入图片引用
- 处理图片去重（同一图片被多处引用）

**结论**：POI 的实现代码量更多，但不需要引入额外 Python 运行时。

### 5.3 不需要 Python 运行时

Yuxi 使用 Docling 需要 Python 环境 + 大量 ML 依赖（torch 等）。LightBot 基于纯 Java 栈，使用 POI 完全可以实现同等功能，且：
- 部署更简单，无需 Python 环境
- 内存占用更可控
- 与现有 Java 代码无缝集成

---

## 6. 前端适配

### 6.1 Markdown 图片渲染

当前前端使用 `marked` 库渲染 Markdown，已支持 `![alt](url)` 语法。需确认：

1. MinIO 的图片 URL 可被浏览器访问（CORS 配置）
2. 图片 URL 使用相对路径还是绝对路径
3. 大图片是否需要限制展示尺寸

### 6.2 图片展示优化

```css
/* Markdown 内图片样式 */
.markdown-content img {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 8px 0;
}
```

---

## 7. 实施计划

### Phase 1：核心功能（1-2 天）

- [ ] `TikaUtil.parseDocxToMarkdown()` 改造：提取图片 + 位置映射
- [ ] 新增 `ImageData` 数据类
- [ ] `DocumentUploadExecutor` 中上传图片到 MinIO
- [ ] 替换 Markdown 中的图片占位符为 URL
- [ ] 前端 Markdown 图片样式优化

### Phase 2：健壮性（1 天）

- [ ] 图片去重（相同 rId 只上传一次）
- [ ] 大文件/大图片限制
- [ ] 异常处理（图片提取失败不影响文本提取）
- [ ] 日志记录

### Phase 3：扩展（按需）

- [ ] PPT 图片提取
- [ ] PDF 图片提取
- [ ] EMF/WMF 格式转换

---

## 8. 测试用例

| 场景 | 预期结果 |
|------|----------|
| 纯文本文档 | 正常解析，无图片 |
| 含 PNG/JPG 图片的文档 | 图片显示在对应位置 |
| 同一图片被多次引用 | 只上传一次，多处复用 URL |
| 超大图片（>10MB） | 跳过或压缩，不阻塞解析 |
| 不支持的图片格式（EMF） | 显示占位文字，不影响其他内容 |
| 图片提取失败 | 回退到纯文本解析，不抛异常 |
