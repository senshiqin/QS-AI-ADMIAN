# QS-AI-ADMIAN 项目开发进度文档

## 1. 项目基础信息

项目基于 Spring Boot 3.2.5 开发，适配 JDK 17、Maven 3.9+ / Maven 8.9、MySQL 8.4.x、Redis 6.2.14。

当前项目已完成基础后端工程、JWT 登录鉴权、Swagger 文档、MyBatis-Plus 数据访问、Redis 上下文缓存、多模型 AI 对话、SSE 流式输出、接口限流、文案生成接口、AI API 工具类封装等能力。

## 2. 技术栈

- Spring Boot 3.2.5
- JDK 17
- Maven 3.9+ / Maven 8.9 兼容
- MySQL 8.4.x
- Redis 6.2.14
- MyBatis-Plus 3.5.5
- Springdoc OpenAPI 2.2.0
- JWT jjwt 0.11.5
- Spring Retry
- Spring AOP
- H2 Test

## 3. 项目分层结构

已建立标准分层：

- controller
- service
- service.impl
- mapper
- entity
- config
- util
- exception
- dto / request / response

已配置：

- `@MapperScan`
- MyBatis-Plus 分页插件
- MyBatis-Plus 乐观锁插件
- MyBatis-Plus 逻辑删除
- 下划线转驼峰

## 4. 统一响应与异常处理

统一响应体：

- `ApiResponse<T>`
- `ResultCode`

全局异常处理器：

- 参数校验异常
- 请求参数异常
- 业务异常
- AI API 异常
- 空指针异常
- 兜底异常

AI API 异常：

- `AiApiException`
- 默认返回业务码 `5102`

限流异常：

- HTTP 状态码：`429`
- 业务码：`429`

## 5. Swagger / OpenAPI

Swagger 地址：

```text
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON：

```text
http://localhost:8080/v3/api-docs
```

已配置 JWT Bearer 鉴权。

Swagger 可测试：

- 用户登录
- AI 对话
- AI 流式对话
- Redis 上下文
- 文案生成
- 事务示例

## 6. JWT 登录与权限控制

登录接口：

```text
POST /api/v1/user/login
```

登录逻辑：

- 查询 `sys_user` 表
- 校验用户名密码
- 登录成功生成 JWT
- 登录成功更新 `last_login_time`

JWT 工具类支持：

- 生成 token
- 解析 token
- 校验 token
- 判断 token 是否过期

JWT 拦截器：

- 拦截 `/api/v1/ai/**`
- 未登录返回 `401`
- token 过期返回 `403`
- 登录成功后写入 request attribute：
  - `loginUserId`
  - `loginUsername`

当前注意点：

- 登录密码目前仍是明文比对
- 后续建议改为 BCrypt

## 7. 数据库设计

核心表：

- `sys_user`
- `ai_chat_record`
- `ai_knowledge_file`

`ai_chat_record` 支持字段：

- 会话 ID
- 用户 ID
- 角色类型
- 对话内容
- 模型名称
- prompt token
- completion token
- total token
- 请求延迟
- request_id
- knowledge_file_id
- error_code
- error_message
- ext_json
- 逻辑删除
- 创建时间
- 更新时间
- version 乐观锁

测试环境：

- 使用 H2
- MySQL 兼容模式

后续建议：

- 将 `schema.sql` 迁移到 Flyway

## 8. MyBatis-Plus

已完成：

- 实体 Entity
- Mapper extends BaseMapper
- Service extends IService
- ServiceImpl extends ServiceImpl
- 分页插件
- 乐观锁插件
- 逻辑删除配置

已验证：

- `AiChatRecordOptimisticLockTest`
- 并发旧版本更新失败返回 `0`

## 9. Redis 上下文缓存

核心服务：

- `ChatContextService`
- `ChatContextServiceImpl`

Redis 数据结构：

- Redis List

Key 格式：

```text
ai:chat:context:{userId}:{conversationId}
```

TTL：

```text
60 分钟
```

上下文裁剪：

- 最多保留最近 `10` 条消息
- 使用 Redis `LTRIM`

批量写入优化：

- 使用 Redis pipeline
- 一次完成：
  - `RPUSH`
  - `LTRIM`
  - `EXPIRE`

上下文接口：

```text
POST   /api/v1/ai/context/messages
GET    /api/v1/ai/context/messages
DELETE /api/v1/ai/context/messages
```

## 10. AI 模型接入

当前已支持 OpenAI-compatible 格式的多模型调用。

支持模型供应商：

- 通义千问 / DashScope / 百炼
- DeepSeek

配置项：

```yaml
dashscope:
  api-key: ${DASHSCOPE_API_KEY:}
  base-url: ${DASHSCOPE_BASE_URL:https://dashscope.aliyuncs.com}
  chat-path: /compatible-mode/v1/chat/completions

deepseek:
  api-key: ${DEEPSEEK_API_KEY:}
  base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
  chat-path: /chat/completions
```

环境变量：

```powershell
$env:DASHSCOPE_API_KEY="你的通义Key"
$env:DEEPSEEK_API_KEY="你的DeepSeek Key"
```

## 11. AI API 工具类

核心类：

- `AiApiUtil`
- `AiApiService`
- `AiApiServiceImpl`

DTO：

- `AiModelProvider`
- `AiChatOptions`
- `AiApiChatResult`
- `AiStreamHandler`
- `AiApiCall`

支持能力：

- 非流式调用：`chat`
- 流式调用：`streamChat`
- 结构化输出：`structuredChat`
- 多模型路由：
  - `AiModelProvider.QWEN`
  - `AiModelProvider.DEEPSEEK`
- 模型参数：
  - `temperature`
  - `maxTokens`
  - `maxInputTokens`
- API Key 校验
- 统一异常转换
- Spring Retry 重试
- Token 粗略估算
- 输入 token 上限校验

Spring Retry 规则：

- 重试异常：
  - `TimeoutException`
  - `HttpTimeoutException`
  - `SocketTimeoutException`
  - `ConnectException`
  - `ResourceAccessException`
- 最大重试次数：3
- 重试间隔：1 秒

Token 粗略估算：

- 中文 / CJK 字符按 1 字符约 1 token
- 英文按约 4 字符约 1 token
- 每条 message 额外估算 4 token

## 12. AI 对话接口

统一非流式对话接口：

```text
POST /api/v1/ai/chat/send
```

支持通过请求体切换模型。

通义千问示例：

```json
{
  "conversationId": "conv-qwen-001",
  "provider": "QWEN",
  "model": "qwen-turbo",
  "temperature": 0.7,
  "messages": [
    {
      "role": "user",
      "content": "你好"
    }
  ]
}
```

DeepSeek 示例：

```json
{
  "conversationId": "conv-deepseek-001",
  "provider": "DEEPSEEK",
  "model": "deepseek-chat",
  "temperature": 0.7,
  "messages": [
    {
      "role": "user",
      "content": "计算一下35乘68"
    }
  ]
}
```

自动路由逻辑：

- `provider=QWEN` 走通义
- `provider=DEEPSEEK` 走 DeepSeek
- 不传 `provider` 时：
  - `model` 以 `deepseek` 开头则走 DeepSeek
  - 其他默认走通义

接口主流程：

```text
JWT 校验
-> Redis 读取上下文
-> 拼接历史上下文 + 当前消息
-> 调用 AI API
-> 返回 AI 结果
-> 异步写 MySQL 对话记录
-> 异步刷新 Redis 上下文
```

## 13. AI 流式对话 SSE

流式接口：

```text
POST /api/v1/ai/chat/stream
```

支持：

- 通义千问
- DeepSeek
- SSE 输出
- 逐字 / 分片推送
- Redis 上下文
- MySQL 异步入库

SSE 事件：

```text
event: message
data: 你

event: done
data: [DONE]

event: error
data: 错误信息
```

Swagger 对 SSE 展示不稳定，推荐使用 curl：

```powershell
curl.exe -N -X POST "http://localhost:8080/api/v1/ai/chat/stream" `
  -H "Authorization: Bearer 你的token" `
  -H "Content-Type: application/json" `
  -d "{\"provider\":\"QWEN\",\"model\":\"qwen-turbo\",\"messages\":[{\"role\":\"user\",\"content\":\"讲一个短故事\"}]}"
```

## 14. 文案生成接口

Controller：

- `AiController`

接口：

```text
POST /api/v1/ai/copywriting/generate
```

功能：

- 基于通义千问 API
- 根据文案类型和关键词生成文案
- 返回结构化 JSON
- 支持 JWT 权限控制
- 支持参数校验

请求示例：

```json
{
  "copyType": "product",
  "keywords": ["AI客服", "降本增效", "7x24小时"],
  "targetAudience": "企业客服团队",
  "tone": "professional",
  "length": "medium",
  "model": "qwen-turbo",
  "temperature": 0.7
}
```

返回结构：

```json
{
  "title": "...",
  "subtitle": "...",
  "body": "...",
  "sellingPoints": ["...", "...", "..."],
  "callToAction": "...",
  "tags": ["...", "...", "..."]
}
```

当前说明：

- 文案生成接口暂不入库
- 后续可新增文案生成记录表

## 15. 接口限流

基于 Redis 实现 AI 对话接口限流。

限流范围：

```text
/api/v1/ai/chat/**
```

规则：

```text
每个用户每分钟最多 10 次
```

Redis Key：

```text
ai:rate:chat:{userId}:yyyyMMddHHmm
```

超限响应：

HTTP 状态码：

```text
429
```

响应体：

```json
{
  "code": 429,
  "message": "AI chat rate limit exceeded, max 10 requests per minute",
  "data": null
}
```

配置：

```yaml
ai:
  rate-limit:
    chat-per-minute: ${AI_CHAT_RATE_LIMIT_PER_MINUTE:10}
```

## 16. 性能优化

已完成优化：

### 16.1 AI 专用线程池

线程名前缀：

```text
ai-task-
```

配置：

```text
corePoolSize=8
maxPoolSize=32
queueCapacity=200
```

### 16.2 非流式接口优化

主链路：

```text
读取上下文
-> 调 AI API
-> 返回结果
```

异步链路：

```text
写 MySQL 用户消息
写 MySQL AI 回复
刷新 Redis 上下文
```

优点：

- 降低用户等待时间
- 接口耗时主要取决于 AI API 本身

注意：

- 异步入库失败只打日志，不阻塞响应

### 16.3 流式接口优化

- SSE 使用 AI 专用线程池
- 推送完成后异步入库
- 推送完成后异步刷新 Redis 上下文

### 16.4 Redis 优化

- 上下文最多保留 10 条
- 使用 Redis pipeline 批量执行：
  - `RPUSH`
  - `LTRIM`
  - `EXPIRE`

## 17. 事务示例

事务接口：

```text
POST /api/v1/tx/chat-points
POST /api/v1/tx/chat-points/rollback-test
```

验证点：

- 对话记录保存
- 用户积分更新
- 失败时事务回滚

事务隔离级别：

```text
READ_COMMITTED
```

## 18. 已有测试

当前项目中已有测试类：

- 乐观锁测试
- ChatController 集成测试
- Redis 上下文裁剪测试
- AiApiUtil 复用性测试

但后续偏好：

```text
优先使用 Swagger 手工测试
不默认新增本地测试类
```

## 19. 常用启动与测试命令

编译：

```powershell
mvn -DskipTests compile
```

启动：

```powershell
mvn spring-boot:run
```

Swagger：

```text
http://localhost:8080/swagger-ui.html
```

登录后在 Swagger 右上角 Authorize 中填写：

```text
Bearer 你的token
```

## 20. 当前注意事项

1. 登录密码仍是明文比对，建议后续改 BCrypt。
2. `schema.sql` 仍是脚本式初始化，建议迁移 Flyway。
3. 文案生成结果暂未入库。
4. AI 对话入库是异步执行，用户体验更快，但异步失败只记录日志。
5. Swagger 对 SSE 展示不够友好，建议用 curl 或前端页面验证。
6. 如果 Redis 旧 key 有序列化问题，可清理：

```text
ai:chat:context:*
```

7. `mvn package` 曾因本机 Maven 仓库写权限不足失败：

```text
D:\study\soft\apache-maven-3.9.15\repository
```

这不是代码问题。

## 21. 推荐后续开发任务

1. 登录密码改 BCrypt。
2. 文案生成结果入库。
3. 增加 AI 调用日志表，记录模型、耗时、token、错误原因。
4. 将 `schema.sql` 迁移到 Flyway。
5. 给 Swagger 增加更完整的 ExampleObject。
6. 增加管理端接口：
   - 用户调用次数
   - token 消耗
   - 模型调用失败率
   - 慢请求统计
7. 增加前端页面测试 SSE 流式输出。
8. 增加模型供应商配置化开关，支持更多模型。
