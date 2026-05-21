# 文档预览与下载接口文档

## 1. 获取文档下载信息

获取文档的预签名下载URL和文件类型信息，用于前端预览和下载。

### 请求

```
GET /api/knowledge/documents/{docId}/download
```

### 路径参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| docId | Long | 是 | 文档ID |

### 响应

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "url": "https://minio.example.com/bucket/knowledge/1/doc/xxx.pdf?X-Amz-Algorithm=...",
    "fileType": "pdf",
    "fileName": "文档.pdf",
    "contentType": "application/pdf"
  }
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| url | String | 预签名下载URL（7天有效） |
| fileType | String | 文件扩展名（如 pdf、docx） |
| fileName | String | 原始文件名 |
| contentType | String | MIME类型 |

### 文件类型与MIME对照

| 文件类型 | MIME类型 |
|----------|----------|
| pdf | application/pdf |
| doc | application/msword |
| docx | application/vnd.openxmlformats-officedocument.wordprocessingml.document |
| ppt | application/vnd.ms-powerpoint |
| pptx | application/vnd.openxmlformats-officedocument.presentationml.presentation |
| xls | application/vnd.ms-excel |
| xlsx | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |
| csv | text/csv |
| html/htm | text/html |
| md | text/markdown |
| txt | text/plain |

---

## 2. 预览文档内容

获取文档的纯文本内容（由Tika解析），用于Markdown预览模式。

### 请求

```
GET /api/knowledge/documents/{docId}/preview
```

### 路径参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| docId | Long | 是 | 文档ID |

### 响应

```json
{
  "code": 0,
  "message": "success",
  "data": "文档的纯文本内容..."
}
```

---

## 前端预览策略

### 源文件预览模式

| 文件类型 | 预览方式 |
|----------|----------|
| PDF | iframe嵌入预签名URL |
| 图片(png/jpg/gif/webp/svg) | img标签直接显示 |
| Office(doc/ppt/xls等) | Microsoft Office Online Preview iframe |
| 文本(md/txt/html/csv) | pre标签显示Tika解析文本 |
| 其他 | 提示不支持，提供下载按钮 |

### Markdown预览模式

所有文件类型统一使用Tika解析为纯文本，然后通过marked库渲染为HTML显示。

### 下载功能

点击下载按钮直接打开预签名URL，浏览器自动下载文件。
