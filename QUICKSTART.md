# LightBot 快速启动指南

本文档帮助你快速搭建 LightBot 开发环境，从零到运行只需 5 分钟。

---

## 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 推荐 Amazon Corretto 或 Eclipse Temurin |
| Node.js | 18+ | 推荐使用 nvm 管理版本 |
| pnpm | 8+ | 前端包管理器 |
| Maven | 3.9+ | 后端构建工具 |
| Docker | 24+ | 用于运行中间件（可选） |

---

## 1. 启动中间件

LightBot 依赖 4 个中间件服务。推荐使用 Docker Compose 一键启动。

### 方式一：Docker Compose（推荐）

在项目根目录创建 `docker/docker-compose-middleware.yml`：

```yaml
version: '3.8'

services:
  # PostgreSQL 15 + pgvector 向量扩展
  postgres:
    image: pgvector/pgvector:pg16
    container_name: lightbot-postgres
    environment:
      POSTGRES_DB: lightbot
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    restart: unless-stopped

  # Redis 7 缓存 + Sa-Token 会话存储
  redis:
    image: redis:7-alpine
    container_name: lightbot-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    restart: unless-stopped

  # Neo4j 5 知识图谱数据库
  neo4j:
    image: neo4j:5-community
    container_name: lightbot-neo4j
    environment:
      NEO4J_AUTH: neo4j/lightbot
    ports:
      - "7474:7474"   # Web 管理界面
      - "7687:7687"   # Bolt 协议
    volumes:
      - neo4j_data:/data
    restart: unless-stopped

  # MinIO 对象存储（文档、头像等文件）
  minio:
    image: minio/minio:latest
    container_name: lightbot-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports:
      - "9000:9000"   # API 端口
      - "9001:9001"   # Web 管理界面
    volumes:
      - minio_data:/data
    restart: unless-stopped

volumes:
  postgres_data:
  redis_data:
  neo4j_data:
  minio_data:
```

启动所有中间件：

```bash
cd docker
docker-compose -f docker-compose-middleware.yml up -d
```

### 方式二：逐个 Docker Run

如果你不想使用 Docker Compose，可以用以下命令逐个启动：

```bash
# 1. PostgreSQL 15 + pgvector
docker run -d \
  --name lightbot-postgres \
  -e POSTGRES_DB=lightbot \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  -v postgres_data:/var/lib/postgresql/data \
  pgvector/pgvector:pg16

# 2. Redis 7
docker run -d \
  --name lightbot-redis \
  -p 6379:6379 \
  -v redis_data:/data \
  redis:7-alpine

# 3. Neo4j 5
docker run -d \
  --name lightbot-neo4j \
  -e NEO4J_AUTH=neo4j/lightbot \
  -p 7474:7474 \
  -p 7687:7687 \
  -v neo4j_data:/data \
  neo4j:5-community

# 4. MinIO
docker run -d \
  --name lightbot-minio \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  -p 9000:9000 \
  -p 9001:9001 \
  -v minio_data:/data \
  minio/minio:latest server /data --console-address ":9001"
```

### 中间件管理界面

| 服务 | 地址 | 账号密码 |
|------|------|----------|
| PostgreSQL | localhost:5432 | postgres / postgres |
| Redis | localhost:6379 | 无密码 |
| Neo4j Web | http://localhost:7474 | neo4j / lightbot |
| MinIO Web | http://localhost:9001 | minioadmin / minioadmin |

---

## 2. 初始化数据库

### 创建数据库和表结构

```bash
# 连接 PostgreSQL 并执行初始化脚本
psql -U postgres -h localhost -f sql/init-2026-05-29.sql
```

如果提示数据库已存在，可以先删除重建：

```bash
psql -U postgres -h localhost -c "DROP DATABASE IF EXISTS lightbot;"
psql -U postgres -h localhost -f sql/init-2026-05-29.sql
```

### 验证表结构

```bash
psql -U postgres -h localhost -d lightbot -c "\dt"
```

应该看到 35 张表，包括 `users`、`agent`、`chat_session`、`knowledge` 等。

---

## 3. 配置后端

配置文件位置：`lightbot-server/src/main/resources/application.yml`

### 数据库配置

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/lightbot
    username: postgres
    password: postgres    # 修改为你的密码
```

### Redis 配置

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 9         # 使用独立数据库编号
```

### Neo4j 配置（知识图谱功能）

```yaml
neo4j:
  uri: bolt://localhost:7687
  username: neo4j
  password: lightbot      # 修改为你的密码
```

### MinIO 配置（文件存储）

```yaml
minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket: lightbot
```

### 模型 API Key 配置

配置至少一个模型提供商才能使用 AI 功能。支持两种方式：

**方式一：环境变量（推荐，不泄露密钥）**

```bash
export OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
export DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
```

**方式二：直接写入配置文件**

```yaml
spring:
  ai:
    openai:
      api-key: sk-xxxxxxxxxxxxxxxxxxxxxxxx
    dashscope:
      api-key: sk-xxxxxxxxxxxxxxxxxxxxxxxx
```

> 也可以启动后在前端「模型管理」页面配置 API Key。

---

## 4. 启动后端

```bash
cd lightbot-server

# 方式一：Maven 直接启动
mvn spring-boot:run

# 方式二：打包后运行
mvn clean package -DskipTests
java -jar target/lightbot-server-1.5.0.jar
```

启动成功后会看到：

```
Started LightBotApplication in x.xx seconds
```

验证后端启动：

- API 基础路径：http://localhost:8081
- Swagger 文档：http://localhost:8081/swagger-ui.html
- 健康检查：http://localhost:8081/actuator/health

---

## 5. 配置并启动前端

### 安装依赖

```bash
cd lightbot-ui
pnpm install
```

### 配置后端地址

如果后端地址不是默认的 `http://localhost:8081`，修改 Vite 配置：

文件：`lightbot-ui/vite.config.js`

```javascript
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8081',  // 修改为你的后端地址
        changeOrigin: true,
      }
    }
  }
})
```

### 启动开发服务器

```bash
pnpm dev
```

访问 http://localhost:5173 开始使用。

---

## 6. 首次使用

### 注册账号

1. 访问 http://localhost:5173/register
2. 填写用户名和密码完成注册
3. 自动跳转到对话页面

### 配置模型

1. 进入「模型管理」页面
2. 添加模型提供商（如 OpenAI、通义千问等）
3. 填写 API Key 并测试连接
4. 注册可用模型

### 创建 Agent

1. 进入「Agent 管理」页面
2. 点击「创建 Agent」
3. 配置系统提示词、模型、工具等
4. 发布 Agent

### 开始对话

1. 进入「对话」页面
2. 选择 Agent
3. 开始对话

---

## 常见问题

### Q: PostgreSQL 连接失败

```
FATAL: password authentication failed for user "postgres"
```

检查密码是否正确，或重置密码：

```bash
docker exec -it lightbot-postgres psql -U postgres -c "ALTER USER postgres PASSWORD 'postgres';"
```

### Q: pgvector 扩展未安装

```
ERROR: extension "vector" does not exist
```

确保使用 `pgvector/pgvector:pg16` 镜像，而非官方 PostgreSQL 镜像。

### Q: Neo4j 连接失败

```
ServiceUnavailable: WebSocket connection failure
```

等待 Neo4j 启动完成（首次启动较慢），或检查端口：

```bash
docker logs lightbot-neo4j
```

### Q: MinIO Bucket 不存在

MinIO 首次使用需要创建 Bucket。启动后访问 http://localhost:9001，使用 minioadmin/minioadmin 登录，创建名为 `lightbot` 的 Bucket。

### Q: 模型调用失败

检查：
1. API Key 是否正确配置
2. 网络是否能访问模型提供商 API
3. 在「模型管理」页面测试连接

### Q: 前端白页

检查：
1. 后端是否启动成功（http://localhost:8081/swagger-ui.html 能否访问）
2. 浏览器控制台是否有错误
3. Node.js 版本是否 >= 18

---

## 开发模式 vs 生产模式

| 配置项 | 开发模式 | 生产模式 |
|--------|----------|----------|
| 数据库 | 本地 PostgreSQL | 生产 PostgreSQL |
| Redis | 本地 Redis | 生产 Redis |
| Neo4j | 本地 Neo4j（可选） | 生产 Neo4j |
| MinIO | 本地 MinIO（可选） | 生产 MinIO / OSS |
| 日志级别 | DEBUG | INFO / WARN |
| CORS | 允许 localhost | 配置域名白名单 |

---

## 下一步

- 阅读 [ROADMAP.md](ROADMAP.md) 了解项目规划
- 查看 [README.md](README.md) 了解完整功能
- 访问 http://localhost:8081/swagger-ui.html 查看 API 文档
- 提交 [Issue](https://github.com/finch04/LightBot/issues) 反馈问题
