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
mvn clean package -Dmaven.test.skip=true
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

## 数据库初始化

SQL 脚本位于 `sql/`：

- `ry_20260417.sql`
- `ai_init.sql`
- `edu_resource.sql`

## 开源基础

本项目基于 [RuoYi-Vue 3.9.2](https://gitee.com/y_project/RuoYi-Vue) 二次开发。上游项目采用 MIT License，相关许可信息见根目录 `LICENSE`。
