# 智学协同教育智能体平台

English name: **IntelliLearn Agent Platform**  
GitHub repository: **intellilearn-agent-platform**

智学协同教育智能体平台是一个面向学习场景的 AI 多智能体教育系统，基于 RuoYi-Vue 扩展实现。系统接收学生学习提问后，由调度官分析意图，并行调用文本答疑、文档生成、题库生成、思维导图、代码案例和学习画像等专家 Agent，最终通过同步接口或 SSE 流式接口返回学习资源。

## 核心能力

- 调度官识别学习意图并选择合适的专家 Agent。
- 多个资源 Agent 并行生成答疑、文档、题库、思维导图和代码案例。
- 学习画像结合行为日志、练习结果和历史会话持续更新。
- SSE 实时推送任务阶段、Agent 进度、资源结果和文本 token。
- 支持 MySQL、Redis、MinIO 与 OpenAI 协议兼容的大模型服务。

## 技术栈

- 后端：Java 17、Spring Boot、Spring Security、MyBatis
- AI 编排：LangChain4j、LangGraph4j
- 数据与存储：MySQL、Redis、MinIO
- 前端：Vue 2 + Element UI，以及 `edu-ai-vue3` 学生端
- 构建工具：Maven、npm

## 目录结构

```text
ruoyi-admin       Web 控制器层和启动模块
ruoyi-system      核心业务层、AI Agent、领域模型和工具层
ruoyi-framework   框架层，包括安全、JWT、Redis、数据源
ruoyi-common      公共工具、注解和异常处理
ruoyi-generator   代码生成器
ruoyi-quartz      定时任务
ruoyi-ui          RuoYi 管理端前端
edu-ai-vue3       教育 AI 学生端前端
sql               数据库初始化脚本
```

## 快速启动

### 后端构建

```bash
mvn clean package
```

构建完成后运行：

```bash
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

Windows 可使用：

```bash
bin/package.bat
bin/run.bat
```

### 前端运行

管理端：

```bash
cd ruoyi-ui
npm install
npm run dev
```

学生端：

```bash
cd edu-ai-vue3
npm install
npm run dev
```

## 配置说明

主要配置位于：

- `ruoyi-admin/src/main/resources/application.yml`
- `ruoyi-admin/src/main/resources/application-druid.yml`

本地敏感配置请放在 `config/local-secrets.yml` 或 `ruoyi-admin/config/local-secrets.yml`，这些文件已被 `.gitignore` 排除，不应提交到仓库。
可复制 `config/local-secrets.example.yml` 作为模板。应用启动时会校验 JWT、数据库、Druid 和 MinIO（启用时）凭据；仓库不再提供可直接使用的默认密码。
`sql/01_ry_20260417.sql` 中的初始化密码也只是占位符；导入后请立即修改，并为 SQL 中的示例管理员账号设置新的强密码。

Compose 仅绑定到本机回环地址，并要求显式提供基础设施密码：

```powershell
$env:MYSQL_ROOT_PASSWORD = "replace-me"
$env:MINIO_ROOT_USER = "minio"
$env:MINIO_ROOT_PASSWORD = "replace-me"
docker compose up -d
```

### 测试

```bash
mvn test
```

当前 Maven 测试会执行 `ruoyi-system` 下的 12 个契约测试，以及管理端通用上传接口测试；契约测试覆盖任务幂等、并发事件、Agent 隔离、画像确认和文本清理等核心行为。

## 数据库初始化

SQL 脚本位于 `sql/`：

- `01_ry_20260417.sql`
- `02_ai_init.sql`
- `03_edu_resource.sql`
- `04_sys_attachment.sql`
- `05_quartz.sql`
- `05_repair_media_urls.sql`
- `06_gen_table.sql`
- `07_edu_knowledge.sql`
- `08_edu_user_workspace.sql`
- `09_profile_trust.sql`
- `10_edu_chat_attachment.sql`
- `11_edu_action_plan.sql`
- `12_sys_notice_read_hidden.sql`
- `13_quiz_practice_session.sql`
- `14_quiz_practice_current_position.sql`
- `15_edu_ai_task.sql`
- `16_learner_profile_confirmation.sql`
- `17_quiz_practice_question_snapshot.sql`
- `18_conversation_session.sql`
- `19_edu_user_account_profile.sql`

这些脚本是新库初始化材料，不是可重复执行的数据库迁移。`01` 至 `05_quartz` 中包含 `DROP TABLE`，`12`、`14`、`17` 含直接 `ALTER TABLE`；对已有数据库重跑可能删除数据或因字段已存在而失败。`docker-compose.yml` 将此目录挂载到 MySQL 首次初始化目录，仅应用于空数据卷。现有环境升级前应先备份，并为变更编写单独、经过验证的版本化迁移脚本；不要直接重放整套初始化文件。

## 开源基础

本项目基于 [RuoYi-Vue 3.9.2](https://gitee.com/y_project/RuoYi-Vue) 二次开发。上游项目采用 MIT License，相关许可信息见根目录 `LICENSE`。
