# QS-AI-ADMIAN

Java Spring Boot 3.2.5 AI 应用后端脚手架（JDK 17）。

## 关联仓库

- 前端仓库：https://github.com/senshiqin/qs-ai-admin-web
- 前端开发地址：`http://localhost:5173`
- 后端默认地址：`http://localhost:8080`

## 技术栈
- Spring Boot 3.2.5
- Spring Web
- MyBatis-Plus 3.5.5.1
- MySQL（建议 8.4.x）
- Lombok 1.18.30

## 分层结构
- `controller`
- `service`
- `service.impl`
- `mapper`
- `entity`
- `util`
- `config`

## IDEA 2025 开发建议
- Project SDK: JDK 17
- Build Tool: Maven (3.9+)
- Annotation Processors: Enable
- Lombok Plugin: Install and Enable

## 快速启动
1. 修改 `src/main/resources/application.yml` 的数据库连接信息。
2. 启动数据库并创建库（如 `qs_ai`）。
3. 执行 `schema.sql` 初始化示例表。
4. 运行：
   - `mvn clean spring-boot:run`
5. 验证：
   - `GET http://localhost:8080/health`
   - `GET http://localhost:8080/api/users`
