-- Landing 页面配置
INSERT INTO system_config (config_key, config_value, description) VALUES (
  'landing_config',
  '{
    "title": "LightBot",
    "subtitle": "AI Native 智能体平台",
    "subtitles": [
      "AI Native 智能体平台",
      "一站式 RAG 知识库引擎",
      "可视化 Workflow 编排",
      "MCP 协议生态集成",
      "全链路评测与可观测"
    ],
    "description": "构建智能体、知识库、工作流与工具集成的统一平台。从 Prompt 工程到 RAG 检索增强，从 Workflow 编排到 MCP 工具生态，LightBot 为 AI 应用开发提供全栈能力支撑。",
    "features": [
      {"icon": "Agent", "title": "智能体", "desc": "多模型驱动的自主推理 Agent，支持工具调用、记忆管理和多轮对话"},
      {"icon": "Knowledge", "title": "知识库", "desc": "向量检索 + 图谱融合的 RAG 引擎，精准召回知识增强生成"},
      {"icon": "Workflow", "title": "工作流", "desc": "可视化 DAG 编排，支持条件分支、并行执行和人工审批节点"},
      {"icon": "Mcp", "title": "MCP 协议", "desc": "标准 Model Context Protocol 集成，即插即用外部工具生态"},
      {"icon": "Tool", "title": "工具系统", "desc": "HTTP/函数/脚本多类型工具，统一 Schema 定义与安全沙箱执行"},
      {"icon": "Skill", "title": "技能市场", "desc": "可复用的 Prompt + Tool 组合，一键发布到技能市场共享"},
      {"icon": "Eval", "title": "评测中心", "desc": "数据集管理、自动评估、实验对比，量化 Agent 质量持续优化"},
      {"icon": "Observability", "title": "可观测性", "desc": "全链路 Trace 追踪、Token 消耗统计、工具调用日志实时监控"}
    ],
    "github": "https://github.com/finch04/LightBot",
    "copyright": "© 2026 LightBot. All Rights Reserved."
  }',
  'Landing 页面配置（标题、描述、功能列表等）'
)
ON CONFLICT (config_key) DO NOTHING;
