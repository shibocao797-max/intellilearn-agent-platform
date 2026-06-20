# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

教育 AI 多智能体后端系统，基于 RuoYi-Vue 3.9.2 构建。核心能力：接收学生学习提问，通过调度官分析意图，并行调用多个 AI Agent 生成学习资源，通过 SSE 实时推送。

## 技术栈

- **后端**: Java 17, Spring Boot 4.0.3, MyBatis 4.0.1
- **AI 框架**: LangChain4j 1.15.1 + LangGraph4j 1.8.17
- **LLM**: 通过 OpenAI 协议兼容（配置于 application.yml 的 langchain4j 节点）
- **数据库**: MySQL + Redis + MinIO
- **前端**: Vue 2.6 + Element UI（ruoyi-ui 目录）

## 常用命令

### 后端构建

```bash
# 完整构建（跳过测试）
mvn clean package -Dmaven.test.skip=true

# 仅清理
mvn clean

# 运行（构建后）
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

Windows 用户可使用 `bin/package.bat`、`bin/clean.bat`、`bin/run.bat`。

### 前端开发

```bash
cd ruoyi-ui
npm install        # 首次安装依赖
npm run dev        # 启动开发服务器
npm run build:prod # 生产构建
```

## 模块架构

```
ruoyi-admin       → Web 控制器层，API 入口（EduAgentController 等）
ruoyi-system      → 核心业务层，AI 智能体实现 + 领域模型
ruoyi-framework   → 框架层（Spring Security + JWT、Redis、数据源）
ruoyi-common      → 公共工具类、注解、异常处理
ruoyi-generator   → 代码生成器
ruoyi-quartz      → 定时任务
```

## 多智能体系统架构

核心路径：`ruoyi-system/src/main/java/com/ruoyi/system/ai/`

采用 **"调度官 + 专家 Agent"** 模式：

1. **调度官（IntentAnalysis）**：LLM 分析用户意图，决定启动哪些 Agent
2. **专家 Agent**：独立完成特定任务，通过 `ResourceAgentRegistry` 自动发现
3. **共享状态**：`EducationState` 基于 LangGraph4j AgentState
4. **并行执行**：被选中的 Agent 在 6 线程池中并行执行

### Agent 清单

| Agent | 职责 |
|-------|------|
| TextReplyAgent | 文本答疑 |
| DocGenAgent | 文档生成 |
| QuizGenAgent | 题库生成 |
| MindmapGenAgent | 思维导图（PlantUML 渲染 PNG） |
| CodeCaseGenAgent | 代码案例 |
| ProfileUpdateAgent | 学习画像更新 |

### 编排流程

`EducationGraphBuilder` 构建 StateGraph，4 节点编排：
- 意图分析 → Agent 调度 → 并行执行 → 结果汇总

### 工具层（ai/tool/）

- `ProfileTool` — 画像加载/保存/合并
- `BehaviorTool` — 行为日志记录/聚合统计
- `ResourceTool` — 资源存储到 edu_resource
- `QuizTool` — 题库写入 quiz_question
- `MindmapTool` — PlantUML 渲染 PNG + 存储

## 关键配置

配置文件：`ruoyi-admin/src/main/resources/application.yml`

- **LLM 配置**: `langchain4j.open-ai` 节点
- **MinIO 配置**: `file-storage.minio` 节点
- **Redis**: `spring.data.redis` 节点
- **数据库**: `application-druid.yml`（Spring profile: druid）
- **业务配置**: `edu.ai` 节点（画像更新间隔、质量阈值等）

## 数据库初始化

SQL 脚本位于 `sql/` 目录：
- `ry_20260417.sql` — 若依基础表
- `ai_init.sql` — AI 核心表（学习画像、对话历史、行为日志等）
- `edu_resource.sql` — 教育资源表

## 扩展 Agent

新增 Agent 只需：
1. 实现 `EduAgent` 或 `ResourceGenAgent` 接口
2. 添加 `@Component` 注解
3. Spring 自动注册到 `ResourceAgentRegistry`，无需修改现有代码

## API 端点

- `/edu/ai/chat` — 普通对话
- `/edu/ai/chat/stream` — SSE 流式对话
- `/edu/profile` — 学习画像
- `/edu/quiz` — 题库练习
- `/edu/resource` — 用户资源
- `/edu/report` — 学习报告
