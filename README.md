# Demo Agent - 读书问答 Agent 示例

这是一个基于 Spring AI Alibaba 框架的读书问答 Agent 示例项目，使用阿里云百炼 DashScope API 调用 Qwen 系列模型。

## 功能特性

- 📚 解答关于书籍的各类问题
- 📖 提供书籍解读、人物分析、主题探讨
- 🎯 推荐相关书籍和延伸阅读
- 📝 分享有效的阅读策略和笔记方法
- 🔄 支持会话状态管理

## 技术栈

- Spring Boot 3.5.8
- Spring AI Alibaba 1.1.0.0-RC1
- Spring AI Alibaba DashScope 客户端
- Qwen 系列模型（默认：qwen-max）

## 快速开始

### 1. 获取 API Key

1. 访问 [阿里云百炼控制台](https://bailian.console.aliyun.com/)
2. 注册并登录账号
3. 进入 API-KEY 管理页面，创建您的 API Key

### 2. 配置环境变量

```bash
export DASHSCOPE_API_KEY=your-api-key-here
# 可选：覆盖默认模型
export DASHSCOPE_MODEL=qwen-max
```

### 3. 启动 Redis

```bash
docker compose up -d
```

### 4. 运行应用

```bash
./mvnw spring-boot:run
```

应用将在 `http://localhost:8080` 启动。

## Swagger API 文档

启动应用后，访问 Swagger UI 进行 API 测试：

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

## API 使用示例

### 读书问答（流式响应）

```bash
curl -X POST http://localhost:8080/api/book/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "《百年孤独》的主题是什么？",
    "modelId": "qwen-max"
  }'
```

### 获取可用模型列表

```bash
curl http://localhost:8080/api/book/models
```

## 项目结构

```
src/main/java/com/example/demo/
├── DemoAgentApplication.java      # Spring Boot 主应用类
├── config/
│   ├── ModelConfig.java           # 模型配置
│   └── OpenApiConfig.java         # Swagger 配置
└── book/
    ├── BookAgentConfig.java       # Agent 基础配置
    ├── BookAgentFactory.java      # Agent 工厂类
    ├── BookService.java           # 业务服务层
    ├── BookController.java        # REST API 控制器
    └── BookResponseEvent.java     # 响应事件类
```

## 核心组件说明

### BookAgentFactory

配置了 ReactAgent，使用阿里云百炼 Qwen 模型，能够：
- 理解用户关于书籍的问题
- 提供详尽的书籍解读和分析
- 推荐相关书籍和阅读方法

### BookController

提供 RESTful API 接口，支持：
- GET `/api/book/models` - 获取可用模型列表
- POST `/api/book/ask` - 流式问答接口

## 可用模型

| 模型 ID | 名称 | 说明 |
|---------|------|------|
| qwen-max | Qwen Max | 通义千问旗舰模型，综合能力最强 |
| qwen-plus | Qwen Plus | 通义千问增强模型，性价比高 |
| qwen-turbo | Qwen Turbo | 通义千问快速模型，响应速度最快 |
| qwen3-235b-a22b | Qwen3 235B | 通义千问3代超大模型 |

## 参考文档

- [Spring AI Alibaba 文档](https://github.com/alibaba/spring-ai-alibaba)
- [阿里云百炼文档](https://help.aliyun.com/zh/model-studio/)
- [DashScope API 文档](https://help.aliyun.com/zh/dashscope/)

## 许可证

本项目使用 Apache 2.0 许可证。
