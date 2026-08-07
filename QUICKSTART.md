# LightBot 快速启动

> 适用于 LightBot 2.1.0。本文以本机直接启动为主；仓库当前未维护 Docker Compose 编排文件。

## 1. 运行依赖

| 组件 | 建议版本 | 用途 | 启动要求 |
| --- | --- | --- | --- |
| JDK | 17 | 后端运行时 | 必需 |
| Maven | 3.9+ | 后端构建与启动 | 必需 |
| Node.js / pnpm | Node.js 20+ / pnpm 9+ | 前端开发服务 | 前端必需 |
| PostgreSQL | 15+，含 `pgvector` | 平台主数据与向量类型 | 必需 |
| Redis | 7+ | 登录会话、缓存、任务队列 | 必需 |
| DashScope API Key | 可用密钥 | 默认 Spring AI 配置 | 必需（按本文默认配置） |
| MinIO | 任意兼容 S3 的版本 | 文件、附件、知识库源文件 | 文档/附件功能需要 |
| Milvus | 2.4+ | 知识库向量检索 | RAG 向量检索需要 |
| Neo4j | 5+ | 知识图谱 | 图谱功能需要 |

`MinIO`、`Milvus`、`Neo4j` 均按需连接：未启动时基础启动不会因其立即退出，但相关功能不可用。项目默认 `lightbot.milvus.enabled=true`；本机暂不使用 Milvus 时请在启动前显式关闭它。

## 2. 准备 PostgreSQL 与 Redis

### 2.1 初始化全新数据库

`sql/2026-07-28-init.sql` 为全量 DDL（建库、扩展、建表）；`sql/insert-sql.sql` 为预制数据。请在仓库根目录依次执行；执行账号需要具备创建数据库和扩展的权限。

```bash
psql -v ON_ERROR_STOP=1 -U postgres -h localhost -f sql/2026-07-28-init.sql
psql -v ON_ERROR_STOP=1 -U postgres -h localhost -d lightbot -f sql/insert-sql.sql
```

该脚本会创建 `lightbot` 数据库并执行 `CREATE EXTENSION vector`。执行账号需要具备建库与创建扩展权限。

已有库升级时不要重跑快照；请按 [SQL 迁移说明](sql/README.md) 执行尚未应用的增量脚本（含企业会话来源、`caller_context` 等）。

验证连接：

```bash
psql -U postgres -h localhost -d lightbot -c "\dt"
```

已有 LightBot 数据库请不要重复执行基线脚本；请按[SQL 迁移说明](sql/README.md)中的升级顺序执行增量迁移。

### 2.2 启动 Redis

默认配置连接 `localhost:6379`，密码为 `123456`，逻辑库为 `9`。可以让 Redis 使用该配置，也可以通过环境变量覆盖 LightBot 配置：

```powershell
# 示例：Redis 使用无密码或不同密码时，按实际环境覆盖
$env:SPRING_DATA_REDIS_HOST = "localhost"
$env:SPRING_DATA_REDIS_PORT = "6379"
$env:SPRING_DATA_REDIS_PASSWORD = "your-redis-password"
$env:SPRING_DATA_REDIS_DATABASE = "9"
```

## 3. 配置后端

默认配置文件是 [application.yml](lightbot-server/src/main/resources/application.yml)。本机开发可使用其默认地址；不要将生产凭证提交回该文件，生产环境应通过环境变量或外部配置覆盖。

### 3.1 最小环境变量

`spring.ai.dashscope.api-key` 直接读取 `DASHSCOPE_API_KEY`，按默认配置启动时必须提供此变量：

```powershell
$env:DASHSCOPE_API_KEY = "sk-xxx"

# 本机未部署 Milvus 时关闭向量检索客户端
$env:LIGHTBOT_MILVUS_ENABLED = "false"
```

如使用 OpenAI 兼容模型，可同时设置：

```powershell
$env:OPENAI_API_KEY = "sk-xxx"
```

启动后可在“模型管理”中维护模型提供商、模型与默认模型；密钥不应写入前端代码。

### 3.2 常用配置覆盖

下列变量与当前 `application.yml` 的配置项一一对应：

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/lightbot"
$env:SPRING_DATASOURCE_USERNAME = "postgres"
$env:SPRING_DATASOURCE_PASSWORD = "postgres"

# 只读 SQL 工具的数据源；未单独部署时可与主库保持一致
$env:SPRING_DATASOURCE_READONLY_URL = "jdbc:postgresql://localhost:5432/lightbot"
$env:SPRING_DATASOURCE_READONLY_USERNAME = "postgres"
$env:SPRING_DATASOURCE_READONLY_PASSWORD = "postgres"

$env:MINIO_ENDPOINT = "http://localhost:9000"
$env:MINIO_ACCESS_KEY = "minioadmin"
$env:MINIO_SECRET_KEY = "minioadmin"
$env:MINIO_BUCKET = "lightbot"

$env:LIGHTBOT_MILVUS_URI = "http://localhost:19530"
$env:NEO4J_URI = "bolt://localhost:7687"
$env:NEO4J_USERNAME = "neo4j"
$env:NEO4J_PASSWORD = "your-neo4j-password"
```

使用 Dify Dataset 连接或 Dify 工作流导入导出时，还应在生产环境设置 `LIGHTBOT_DIFY_ENCRYPTION_KEY`。该值必须是 Base64 编码的 32 字节 AES 密钥；未配置时，平台不会保存 Dify 凭证。

## 4. 启动服务

### 4.1 启动后端

在仓库根目录执行：

```bash
mvn -pl lightbot-server -am spring-boot:run
```

后端默认监听 `8081` 端口，接口文档地址为 <http://localhost:8081/swagger-ui.html>。

需要先构建再运行时：

```bash
mvn -pl lightbot-server -am package -DskipTests
java -jar lightbot-server/target/lightbot-server-2.1.0.jar
```

### 4.2 启动前端

另开一个终端：

```bash
cd lightbot-ui
pnpm install
pnpm dev
```

前端开发服务器默认地址为 <http://localhost:5173>，已在 [vite.config.js](lightbot-ui/vite.config.js) 中代理 `/api` 到 `http://localhost:8081`。

## 5. 首次使用

1. 访问前端页面并完成管理员初始化（公开注册已关闭，后续账号由管理员创建）。
2. 在模型管理中配置可用模型并设置默认模型。
3. 创建 Agent，或从内置 Agent/工作流模板开始。
4. 创建会话，选择 Agent 后发起流式对话。
5. 需要 RAG 时，再启动 MinIO 与 Milvus，创建知识库并上传文档；需要知识图谱时再连接 Neo4j。

## 6. 常见问题

### `extension "vector" is not available`

PostgreSQL 尚未安装 pgvector，或当前账号无权执行 `CREATE EXTENSION`。安装与 PostgreSQL 主版本匹配的 pgvector 后重新初始化空库；已有库请先备份并按迁移流程处理。

### 后端无法连接 Redis

核对 `host`、`port`、`password`、`database` 是否与 Redis 实例一致。登录会话、缓存及 Redis Stream 任务均依赖 Redis，不能仅关闭该连接继续使用。

### 未部署 Milvus

设置 `LIGHTBOT_MILVUS_ENABLED=false` 后启动。基础 Agent 对话可使用，但知识库向量写入与检索不可用。

### MinIO 或 Neo4j 未连接

这两个客户端按需连接。未部署 MinIO 时文件、附件和知识库上传不可用；未部署 Neo4j 时知识图谱不可用。请根据功能范围启动对应服务并更新配置。

### 数据库已存在

不要对已部署环境重跑 `2026-07-28-init.sql`。从旧版本升级时先备份，再按 [sql/README.md](sql/README.md) 执行尚未应用的增量迁移。

## 下一步

- [README.md](README.md)：项目定位、功能与架构概览
- [部署指南](docs/deployment.md)：生产部署、反向代理与发布检查
- [SQL 迁移说明](sql/README.md)：基线、升级路径与执行规范
- [Roadmap](ROADMAP.md)：已完成能力与后续规划
