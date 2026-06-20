# 教育 AI 多智能体后端系统 — 项目说明文档

> **版本**: 基于 RuoYi-Vue 3.9.2 | **语言**: Java 17+ | **框架**: Spring Boot 3.x  
> **AI 层**: LangChain4j + LangGraph4j | **LLM**: DeepSeek | **数据库**: MySQL + Redis + MinIO

---

## 目录

1. [项目概览](#1-项目概览)
2. [技术架构](#2-技术架构)
3. [模块结构与目录说明](#3-模块结构与目录说明)
4. [多智能体系统详解](#4-多智能体系统详解)
5. [智能体架构与设计](#5-智能体架构与设计)
6. [当前 Agent 清单与功能](#6-当前-agent-清单与功能)
7. [StateGraph 编排流程](#7-stategraph-编排流程)
8. [SSE 实时流式协议](#8-sse-实时流式协议)
9. [API 接口文档](#9-api-接口文档)
10. [数据存储设计](#10-数据存储设计)
11. [画像系统详细说明](#11-画像系统详细说明)
12. [会话记忆系统](#12-会话记忆系统)
13. [学习报告系统](#13-学习报告系统)
14. [配置说明](#14-配置说明)
15. [如何扩展 Agent](#15-如何扩展-agent)
16. [部署与运行](#16-部署与运行)

---

## 1. 项目概览

本项目是一个**教育 AI 多智能体协同系统**，基于若依（RuoYi-Vue）快速开发框架构建。核心能力是接收学生的学习提问，通过一个**调度官（IntentAnalysis）** 自动分析意图，然后**并行调用**多个 AI Agent（文本回复、文档生成、题库生成、思维导图、代码案例）进行协同工作，最终将生成的多种学习资源通过 SSE 实时推送给前端。

### 核心特性

| 特性 | 说明 |
|------|------|
| **多智能体编排** | LangGraph4j StateGraph 实现，调度官自动决策启动哪些 Agent |
| **实时流式输出** | SSE（Server-Sent Events）协议，逐 token 推送 AI 回复 |
| **动态学习画像** | 6 维学习者画像，从对话中增量抽取，持续更新 |
| **双层记忆系统** | Redis 短期记忆（最近 20 条）+ MySQL 全量持久化 |
| **按需并行生成** | 调度官选中多个 Agent 后并行执行，6 线程池 |
| **资源统一存储** | 文档/代码/思维导图/题库统一存入 `edu_resource` 表 |
| **学习行为追踪** | 全量记录学习行为，支持连续天数/学习时长/正确率统计 |
| **Spring 自动注册** | 新增 Agent 只需实现接口 + @Component，零改动现有代码 |

---

## 2. 技术架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (Vue.js)                            │
│              SSE 事件接收 / REST API 调用                        │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│                    ruoyi-admin (控制器层)                        │
│  EduAgentController  EduProfileController  EduQuizController    │
│  EduResourceController  EduReportController                     │
│  ProgressEventEmitter (SSE 线程安全包装器)                       │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│                   ruoyi-system (核心业务层)                       │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              AI 智能体层 (ai/agent/)                      │   │
│  │  EduAgent(接口) ← ResourceGenAgent(接口)                  │   │
│  │  ├─ TextReplyAgent    ├─ DocGenAgent                     │   │
│  │  ├─ ProfileUpdateAgent├─ QuizGenAgent                    │   │
│  │                       ├─ MindmapGenAgent                 │   │
│  │                      └─ CodeCaseGenAgent                 │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              AI 编排层 (ai/graph/)                        │   │
│  │  EducationGraphBuilder → StateGraph 4节点编排            │   │
│  │  EducationState → 共享状态 (LangGraph4j AgentState)      │   │
│  │  AgentProgressCallback → SSE 进度回调接口                │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              AI 工具层 (ai/tool/)                         │   │
│  │  ProfileTool  BehaviorTool  ResourceTool                  │   │
│  │  QuizTool     CodeTool      MindmapTool                   │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              AI 服务层 (ai/service/)                      │   │
│  │  AgentOrchestrationService  ConversationService           │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              AI 配置层 (ai/config/)                       │   │
│  │  LangChain4jConfig  LangGraph4jConfig                     │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              领域模型 (domain/)                           │   │
│  │  LearnerProfile  ConversationHistory  EduResource         │   │
│  │  QuizQuestion    QuizRecord          LearningBehaviorLog  │   │
│  └──────────────────────────────────────────────────────────┘   │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│                ruoyi-framework (框架层)                          │
│  Spring Security + JWT | Druid 连接池 | Redis | MinIO           │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│              数据存储层                                          │
│  MySQL 8.0 (业务数据)  Redis (缓存/会话)  MinIO (文件)          │
│  DeepSeek API (LLM 推理)                                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 模块结构与目录说明

```
edu-backend/
├── ruoyi-admin/          # Web 控制器层（API 入口）
│   └── src/main/java/com/ruoyi/web/controller/system/
│       ├── EduAgentController.java      # ★ 核心对话接口（/edu/ai/chat, /edu/ai/chat/stream）
│       ├── EduProfileController.java    # 学习画像接口
│       ├── EduQuizController.java       # 题库练习接口
│       ├── EduResourceController.java   # 用户资源接口
│       ├── EduReportController.java     # 学习报告接口
│       └── ProgressEventEmitter.java    # SSE 事件线程安全包装器
│
├── ruoyi-system/         # ★ 核心业务模块（AI 智能体 + 领域模型）
│   └── src/main/java/com/ruoyi/system/
│       ├── ai/
│       │   ├── agent/         # 智能体实现
│       │   │   ├── EduAgent.java              # 智能体基接口
│       │   │   ├── ResourceGenAgent.java      # 资源生成 Agent 接口
│       │   │   ├── ResourceAgentRegistry.java # Agent 注册中心(自动发现)
│       │   │   ├── ProfileUpdateAgent.java    # 画像更新 Agent
│       │   │   ├── TextReplyAgent.java        # 文本回复 Agent
│       │   │   ├── DocGenAgent.java           # 文档生成 Agent
│       │   │   ├── QuizGenAgent.java          # 题库生成 Agent
│       │   │   ├── MindmapGenAgent.java       # 思维导图 Agent
│       │   │   └── CodeCaseGenAgent.java      # 代码案例 Agent
│       │   ├── graph/         # LangGraph4j 编排
│       │   │   ├── EducationState.java        # 共享状态定义
│       │   │   ├── EducationGraphBuilder.java # StateGraph 构建器
│       │   │   └── AgentProgressCallback.java # 进度回调接口
│       │   ├── tool/          # Agent 工具
│       │   │   ├── ProfileTool.java           # 画像加载/保存/合并
│       │   │   ├── BehaviorTool.java          # 行为日志记录/聚合统计
│       │   │   ├── ResourceTool.java          # 资源存储到 edu_resource
│       │   │   ├── QuizTool.java              # 题库写入 quiz_question
│       │   │   ├── CodeTool.java              # 代码存储到 edu_resource
│       │   │   └── MindmapTool.java           # PlantUML 渲染 PNG + 存储
│       │   ├── service/       # AI 服务
│       │   │   ├── AgentOrchestrationService.java # 图执行入口
│       │   │   └── ConversationService.java       # 对话记忆服务
│       │   ├── model/         # AI 模型
│       │   │   ├── DispatchResult.java        # 调度官输出模型
│       │   │   └── GeneratedResource.java     # 生成资源模型
│       │   ├── config/        # AI 配置
│       │   │   ├── LangChain4jConfig.java     # LLM Bean 配置
│       │   │   └── LangGraph4jConfig.java     # 图引擎配置
│       │   └── utils/
│       │       └── SseUtil.java              # 流式输出工具
│       ├── domain/            # 领域实体
│       │   ├── LearnerProfile.java           # 学习者画像
│       │   ├── ConversationHistory.java      # 对话历史
│       │   ├── EduResource.java              # 教育资源
│       │   ├── QuizQuestion.java             # 题库
│       │   ├── QuizRecord.java               # 答题记录
│       │   ├── LearningBehaviorLog.java      # 学习行为日志
│       │   └── SysAttachment.java            # 附件
│       ├── mapper/            # MyBatis Mapper
│       └── service/           # 业务服务
│           └── impl/EduReportServiceImpl.java # 学习报告聚合服务
│
├── ruoyi-framework/      # 框架层（Security/Redis/Filter）
├── ruoyi-common/         # 公共工具类
├── ruoyi-generator/      # 代码生成器
├── ruoyi-quartz/         # 定时任务
├── sql/                  # 数据库初始化脚本
│   ├── ai_init.sql       # ★ AI 核心 5 张表
│   ├── edu_resource.sql  # 资源表
│   └── ry_20260417.sql   # 若依基础表
└── pom.xml               # Maven 父 POM
```

---

## 4. 多智能体系统详解

### 4.1 系统设计理念

本系统采用 **"调度官 + 专家 Agent"** 架构模式：

1. **调度官（IntentAnalysis）**：一个 LLM 调用充当"大脑"，分析用户意图，决定启动哪些 Agent
2. **专家 Agent**：每个 Agent 是特定领域的专家，独立完成各自任务
3. **画像驱动**：所有 Agent 共享一份动态学习画像，实现个性化生成
4. **并行执行**：被选中的 Agent 在 6 线程池中并行执行

### 4.2 一次对话请求的完整生命周期

```
用户发送 "我想学Python二分查找，帮我出点题"
        │
        ▼
┌──────────────────────────────────────────────────────┐
│ ① intentAnalysis (调度官)                             │
│    LLM 分析 → intent=RESOURCE_GEN                    │
│    selectedAgents=["textReply","docGen","quizGen",    │
│                    "codeGen","mindmapGen"]            │
│    entities={"subject":"Python","topic":"二分查找"}    │
└──────────────────┬───────────────────────────────────┘
                   ▼
┌──────────────────────────────────────────────────────┐
│ ② profileUpdate (画像更新)                            │
│    检查冷却期(5分钟) + 意图过滤                       │
│    LLM 增量抽取 6 维新特征 → 合并到已有画像            │
│    保存到 learner_profile 表                          │
└──────────────────┬───────────────────────────────────┘
                   ▼
┌──────────────────────────────────────────────────────┐
│ ③ dispatchAgents (并行调度)  ──── 6线程池             │
│    ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│    │textReply │ │ docGen   │ │ quizGen  │ ...       │
│    │流式LLM   │ │ 文档生成  │ │ 出10道题  │           │
│    │逐token推 │ │ 持久化   │ │ 持久化   │           │
│    └──────────┘ └──────────┘ └──────────┘           │
│        SSE 事件实时推送给前端                          │
└──────────────────┬───────────────────────────────────┘
                   ▼
┌──────────────────────────────────────────────────────┐
│ ④ resultCollect (后处理)                              │
│    记录行为日志 → 检查画像完整性 → 兜底补充            │
│    保存 AI 回复到 conversation_history                 │
└──────────────────────────────────────────────────────┘
```

---

## 5. 智能体架构与设计

### 5.1 接口层次

```
EduAgent (基接口)
├── agentName()       : String      Agent 唯一标识
├── displayName()     : String      中文显示名
├── agentRole()       : String      角色描述
├── capabilities()    : String[]    能力列表
└── tools()           : String[]    使用的工具列表

ResourceGenAgent (资源生成接口，继承 EduAgent)
├── resourceType()    : String      资源类型 (document/quiz/mindmap/code)
├── shouldGenerate()  : boolean     是否应参与本次生成
├── buildSystemPrompt(): String     构建 System Prompt
├── buildUserPrompt() : String     构建 User Prompt
├── generate()        : String     执行 LLM 生成
├── persist()         : String     持久化结果
└── validate()        : double     质量验证 (0.0-1.0)
```

### 5.2 Agent 注册机制（Spring 自动发现）

`ResourceAgentRegistry` 通过构造器注入 `List<ResourceGenAgent>`，Spring 自动收集所有 `@Component` 标注的实现类：

```java
@Component
public class ResourceAgentRegistry {
    private final Map<String, ResourceGenAgent> agentMap = new LinkedHashMap<>();

    public ResourceAgentRegistry(List<ResourceGenAgent> agents) {
        for (ResourceGenAgent agent : agents) {
            agentMap.put(agent.agentName(), agent);  // 自动注册
        }
    }
}
```

**扩展优势**：新增 Agent 时只需新建类实现 `ResourceGenAgent` + `@Component`，注册中心零改动。

---

## 6. 当前 Agent 清单与功能

### 6.1 画像更新 Agent — `profileUpdate`

| 属性 | 值 |
|------|-----|
| **agentName** | `profileUpdate` |
| **displayName** | 画像更新Agent |
| **role** | 从对话中增量抽取 6 维学习特征，构建动态学习画像 |
| **capabilities** | 特征抽取、画像构建 |
| **tools** | ChatModel, ProfileTool, ConversationService |
| **触发条件** | 每轮对话自动触发，含冷却期(5分钟)和意图过滤 |

**核心逻辑**：
1. 意图过滤：`GENERAL_CHAT` 和 `PROFILE_INQUIRY` 时跳过
2. 冷却期检查：距上次更新不到 5 分钟则跳过
3. 从 MySQL 获取最近 10 轮对话上下文
4. LLM 增量抽取：只提取本轮新出现的特征
5. 合并到已有画像 → 保存到 `learner_profile` 表

**6 维特征模型**：
| 维度 | 说明 | 示例值 |
|------|------|--------|
| knowledgeLevel | 知识基础分级 | 入门/基础/中等/进阶/专家 |
| cognitiveStyle | 认知风格 | 视觉型/听觉型/动手型/综合型 |
| weakPoints | 薄弱知识点 | [{"topic":"递归","errorCount":3}] |
| strengths | 优势知识点 | [{"topic":"数组","mastery":"高"}] |
| learningGoal | 学习目标 | "通过Java面试" |
| preferredResourceType | 资源偏好 | [{"type":"视频","score":0.9}] |

### 6.2 文本回复 Agent — `textReply`

| 属性 | 值 |
|------|-----|
| **agentName** | `textReply` |
| **displayName** | 文本回复Agent |
| **role** | 处理对话闲聊、概念讲解、答疑解惑 |
| **capabilities** | 文本问答、概念讲解、知识梳理、学习引导 |
| **tools** | ChatModel, StreamingChatModel, ConversationService |
| **触发条件** | 几乎所有场景都启动（调度官默认必选） |

**特色功能**：
- **三层记忆注入**：画像摘要 + 本会话上下文(Redis 20条) + 跨会话长期记忆(MySQL 5条)
- **同步模式** (`generate`): 非流式接口返回完整文本
- **流式模式** (`generateStreaming`): 逐 token 实时推送，通过 `SseUtil.streamAsync` 实现
- **兜底机制**: 流式失败时回退到同步生成，将文本切块模拟推送

### 6.3 文档生成 Agent — `docGen`

| 属性 | 值 |
|------|-----|
| **agentName** | `docGen` |
| **resourceType** | `document` |
| **displayName** | 学习文档 |
| **role** | 生成个性化 Markdown 教学文档 |
| **capabilities** | 文档生成、概念讲解、例题嵌入、章节梳理 |
| **输出格式** | 结构化 Markdown（标题→学习目标→核心概念→实践应用→常见误区→小结→思考题） |
| **存储位置** | `edu_resource` 表 (type=article) |

### 6.4 题库生成 Agent — `quizGen`

| 属性 | 值 |
|------|-----|
| **agentName** | `quizGen` |
| **resourceType** | `quiz` |
| **displayName** | 练习题 |
| **role** | 生成多样练习题目，适配学习者薄弱点 |
| **题型** | 单选题(single_choice)、多选题(multi_choice)、填空题(fill_blank)、简答题(short_answer)、代码题(code) |
| **输出格式** | 严格 JSON 数组，每题含 stem/options/answer/explanation/difficulty/knowledgePoints |
| **存储位置** | `quiz_question` 表 |
| **质量验证** | 双阶段容错：清理 markdown 包裹 → 提取 JSON 数组 → 验证必填字段 → 计算合法率 |

### 6.5 思维导图 Agent — `mindmapGen`

| 属性 | 值 |
|------|-----|
| **agentName** | `mindmapGen` |
| **resourceType** | `mindmap` |
| **displayName** | 思维导图 |
| **role** | 生成 PlantUML 格式知识结构思维导图，服务端渲染 PNG |
| **格式** | 严格 PlantUML `@startmindmap`...`@endmindmap` 语法 |
| **渲染** | PlantUML SourceStringReader 服务端渲染为 Base64 PNG |
| **存储位置** | `edu_resource` 表 (type=mindmap, content=Base64 PNG) |
| **触发条件** | 画像 cognitiveStyle=视觉型 时优先启动 |
| **降级策略** | PlantUML 渲染失败时保存原始文本 |

### 6.6 代码案例 Agent — `codeGen`

| 属性 | 值 |
|------|-----|
| **agentName** | `codeGen` |
| **resourceType** | `code` |
| **displayName** | 代码案例 |
| **role** | 生成带中文注释的教学代码案例 |
| **输出结构** | 问题描述→解题思路→完整代码(含注释)→关键知识点→测试用例 |
| **支持语言** | Python, Java, C++, JavaScript（自动识别） |
| **存储位置** | `edu_resource` 表 (type=code) |
| **触发条件** | 话题涉及编程/算法时启动 |

---

## 7. StateGraph 编排流程

### 7.1 图结构

使用 **LangGraph4j** 构建有向状态图：

```
START
  │
  ▼
┌─────────────────┐
│ intentAnalysis  │  ← 节点1: 调度官分析意图
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ profileUpdate   │  ← 节点2: 条件画像更新
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ dispatchAgents  │  ← 节点3: 按需并行调度 (6线程池)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ resultCollect   │  ← 节点4: 后处理汇总
└────────┬────────┘
         │
         ▼
        END
```

### 7.2 共享状态 (EducationState)

所有 Agent 节点通过 `EducationState` 共享数据：

| 通道名 | 类型 | 合并策略 | 说明 |
|--------|------|----------|------|
| `user_message` | String | 覆盖 | 用户当前消息 |
| `user_id` | Long | 覆盖 | 用户 ID |
| `session_id` | String | 覆盖 | 会话 ID |
| `intent` | String | 覆盖 | 意图类型 (RESOURCE_GEN/QUIZ_REQUEST/GENERAL_CHAT) |
| `entities` | String | 覆盖 | 实体 JSON |
| `profile_json` | String | 覆盖 | 学习画像完整 JSON |
| `selected_agents` | List | 覆盖 | 调度官选中的 Agent 列表 |
| `text_reply` | String | 覆盖 | 文本回复内容 |
| `generated_resources` | List | 追加 | 生成的资源列表（累加） |
| `recommend_reason` | String | 覆盖 | 推荐理由 |
| `current_phase` | String | 覆盖 | 当前阶段标识 |
| `messages` | List | 追加 | 对话消息列表（累加） |
| `error` | String | 覆盖 | 错误信息 |

### 7.3 两种执行模式

#### 同步模式 (`/edu/ai/chat`)
```java
// 一次性返回所有结果
EducationState finalState = orchestrationService.execute(userId, message, sessionId);
// 返回: intent + selectedAgents + generatedResources + textReply + profileJson
```

#### SSE 流式模式 (`/edu/ai/chat/stream`)
```java
// 分步执行，每个节点完成时实时推送事件
graphBuilder.executeWithProgress(userId, message, sessionId, emitter);
// 实时推送: phase → intent → selectedAgents → agentProgress → resource → token → done
```

---

## 8. SSE 实时流式协议

### 8.1 事件类型

| 事件名 | 数据格式 | 触发时机 | 说明 |
|--------|----------|----------|------|
| `phase` | `"PROFILE_LOADED"` / `"PROFILE_UPDATED"` / `"DONE"` | 阶段切换 | 前端展示进度条 |
| `intent` | `{"intent":"...", "recommendReason":"..."}` | 意图分析完成 | 前端展示识别到的意图 |
| `selectedAgents` | `["textReply","quizGen","mindmapGen"]` | 调度官决策完成 | 前端展示即将执行的 Agent 列表 |
| `agentProgress` | `{"agentName":"...","status":"running/completed/failed","durationMs":1234}` | Agent 状态变化 | 前端展示 Agent 执行动画 |
| `resource` | `{"agentName":"...","resourceType":"...","persistedId":"...","quality":0.9}` | 资源生成完成 | 前端渲染资源卡片 |
| `token` | `"文"` `"本"` `"片"` `"段"` | 每收到一个 token | 前端打字机效果 |
| `tokenDone` | `""` | 文本流结束 | 前端结束打字 |
| `done` | `"complete"` | 全部完成 | 前端展示完成状态 |
| `error` | `"错误信息"` | 异常 | 前端展示错误提示 |

### 8.2 线程安全

`ProgressEventEmitter` 是对 Spring `SseEmitter` 的线程安全包装：
- 所有 `sendEvent()` 调用通过 `synchronized(sendLock)` 同步
- 客户端断开时自动设置 `clientDisconnected=true`，后续事件静默丢弃
- 支持 `isDisconnected()` 查询使 Agent 提前终止

---

## 9. API 接口文档

### 9.1 核心对话

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/edu/ai/chat` | **同步对话** — 返回完整结果 |
| POST | `/edu/ai/chat/stream` | **SSE 流式对话** — 实时推送（推荐） |

**请求体 (JSON)**:
```json
{
  "message": "我想学Python二分查找",
  "sessionId": "abc12345"  // 可选，不传则自动生成
}
```

**同步响应**:
```json
{
  "sessionId": "abc12345",
  "intent": "RESOURCE_GEN",
  "selectedAgents": ["textReply", "docGen", "quizGen", "codeGen"],
  "recommendReason": "用户请求学习具体算法...",
  "textReply": "二分查找是一种高效的搜索算法...",
  "generatedResources": [
    {
      "agentName": "docGen",
      "resourceType": "document",
      "displayName": "学习文档",
      "persistedId": "doc:123",
      "quality": 1.0
    }
  ],
  "resourceCount": 3,
  "profileJson": "{...}",
  "currentPhase": "DONE"
}
```

### 9.2 会话管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/edu/ai/sessions` | 获取用户所有会话列表 |
| POST | `/edu/ai/session/new` | 新建会话，返回 sessionId |
| GET | `/edu/ai/conversation/{sessionId}` | 获取指定会话的完整对话历史 |
| DELETE | `/edu/ai/session/{sessionId}` | 删除会话 |

### 9.3 学习画像

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/edu/profile` | 获取当前用户画像（简要） |
| GET | `/edu/profile/detail` | 画像详情（含维度解析） |

### 9.4 题库

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/edu/quiz/user/list?ids=1,2,3` | 按 ID 列表获取题目 |
| GET | `/edu/quiz/detail/{id}` | 单题详情 |
| POST | `/edu/quiz/submit` | 提交答案并批改 |
| GET | `/edu/quiz/records` | 答题记录 |
| GET | `/edu/quiz/stats` | 答题统计 |
| POST | `/edu/quiz/ai-explain` | AI 引导式讲解 |

### 9.5 用户资源

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/edu/resource/user/list?type=article&pageNum=1&pageSize=20` | 我的 AI 生成资源（分页） |
| GET | `/edu/resource/detail/{id}` | 资源详情（含行为记录） |

### 9.6 学习报告

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/edu/report/summary` | 学习总览（今日时长/正确率/连续天数等） |
| GET | `/edu/report/detailed` | 详细报告（含行为日志/答题记录） |

---

## 10. 数据存储设计

### 10.1 数据库概览

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `learner_profile` | 学习者画像 | user_id(唯一), profile_json, version, dimension_count, last_extract_at |
| `conversation_history` | 对话历史 | user_id, session_id, role, content, intent_type, metadata_json |
| `quiz_question` | 题库 | question_type, stem, options(JSON), answer, explanation, difficulty, knowledge_points(JSON) |
| `quiz_record` | 答题记录 | user_id, question_id, user_answer, is_correct, score, time_spent_sec |
| `learning_behavior_log` | 学习行为日志 | user_id, action_type, target_type, target_id, session_id, duration_sec |
| `edu_resource` | 教育资源 | title, content, type, source, user_id, tag, cover_url, status |
| `sys_attachment` | 附件 | 文件上传关联信息 |
| `sys_*` | 若依基础表 | 用户/角色/菜单/部门/字典/日志等 |

### 10.2 存储策略

```
┌─────────────────────────────────────────────────────┐
│                   MySQL (持久化)                     │
│  ┌───────────────┐  ┌──────────────────┐            │
│  │ learner_profile│  │conversation_history│          │
│  │ (画像全量存储) │  │ (对话全量存储)     │          │
│  └───────────────┘  └──────────────────┘            │
│  ┌───────────────┐  ┌──────────────────┐            │
│  │ quiz_question │  │  quiz_record     │            │
│  │ (题库)        │  │  (答题记录)       │            │
│  └───────────────┘  └──────────────────┘            │
│  ┌───────────────┐  ┌──────────────────┐            │
│  │ learning_     │  │  edu_resource    │            │
│  │ behavior_log  │  │  (文档/代码/导图) │            │
│  └───────────────┘  └──────────────────┘            │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│            Redis (短期记忆 / 缓存)                    │
│  Key: chat:{userId}:{sessionId}                     │
│  Value: JSON 数组，最多 20 条消息                    │
│  TTL: 1 天 (86400秒)                                │
│  用途: LLM 上下文注入、文本回复 Agent 多轮记忆       │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│            MinIO (文件存储)                          │
│  Bucket: edu-agent                                  │
│  用途: 思维导图图片、附件、头像等文件                 │
└─────────────────────────────────────────────────────┘
```

### 10.3 edu_resource 表的多态复用

`edu_resource` 表通过 `type` 字段区分不同资源类型，实现统一存储：

| type 值 | 存储内容 | 生成 Agent |
|---------|----------|------------|
| `article` | Markdown 教学文档 | DocGenAgent |
| `quiz` | 练习题（存 quiz_question 表，不在 edu_resource） | QuizGenAgent |
| `mindmap` | Base64 PNG 图片 (tag=png_base64) 或 PlantUML 源码 (tag=text) | MindmapGenAgent |
| `code` | 带注释的教学代码 | CodeCaseGenAgent |
| `video` / `image` | 视频/图片链接 | 管理端手动录入 |

`user_id` 字段区分：`NULL` = 管理端推荐资源（首页展示），非 `NULL` = 某用户的 AI 生成资源。

---

## 11. 画像系统详细说明

### 11.1 画像数据结构

```json
{
  "version": 5,
  "dimensionCount": 6,
  "lastUpdated": "2026-06-15T10:30:00",
  "dimensions": {
    "knowledgeLevel": {
      "value": "中等",
      "confidence": 0.85,
      "evidence": "学生对数组和循环掌握较好，但对递归理解不深"
    },
    "cognitiveStyle": {
      "value": "视觉型",
      "confidence": 0.72,
      "evidence": "多次请求画图解释，偏好思维导图"
    },
    "weakPoints": {
      "value": [
        {"topic": "递归", "errorCount": 4},
        {"topic": "动态规划", "errorCount": 2}
      ],
      "confidence": 0.90
    },
    "strengths": {
      "value": [
        {"topic": "数组操作", "mastery": "高", "evidence": "答题正确率100%"},
        {"topic": "循环", "mastery": "高", "evidence": "能正确解释for/while区别"}
      ],
      "confidence": 0.88
    },
    "learningGoal": {
      "value": "通过Java后端开发面试",
      "confidence": 0.65,
      "evidence": "多次询问面试相关题目"
    },
    "preferredResourceType": {
      "value": [
        {"type": "video", "score": 0.8},
        {"type": "code", "score": 0.7}
      ],
      "confidence": 0.70
    }
  }
}
```

### 11.2 画像更新策略

```
用户发消息 → 意图分析 → 画像更新检查
                          │
                ┌─────────┼─────────┐
                ▼         ▼         ▼
            冷却期检查  意图过滤   增量抽取
           (默认5分钟)  (闲聊跳过)  (仅新特征)
                │         │         │
                └─────────┼─────────┘
                          ▼
                    合并到已有画像
                          │
                          ▼
                  保存到 learner_profile
```

**关键机制**：
1. **冷却期**：5 分钟内不重复更新，避免高频 LLM 调用
2. **意图过滤**：`GENERAL_CHAT`（闲聊）和 `PROFILE_INQUIRY`（画像查询）不触发更新
3. **增量抽取**：只提取本轮对话中新出现的特征，已有特征保留
4. **证据溯源**：每条特征必须提供 `evidence` 字段，从对话原文或推理得出
5. **置信度**：每条特征带 0.0-1.0 置信度，低置信度特征在后续轮次可被覆盖

### 11.3 画像如何影响 Agent 行为

| 画像维度 | 影响范围 | 具体行为 |
|----------|----------|----------|
| knowledgeLevel | 所有 Agent | 文档/讲解的深度（入门→通俗，进阶→深入） |
| cognitiveStyle | 调度官 | 视觉型→优先启动 mindmapGen |
| weakPoints | QuizGenAgent, TextReplyAgent | 题目/讲解重点覆盖薄弱知识点 |
| strengths | DocGenAgent | 已掌握内容可跳过或简要提及 |
| learningGoal | 调度官 | 影响资源生成的主题方向 |
| preferredResourceType | 调度官 | 影响调度官的资源类型选择权重 |

---

## 12. 会话记忆系统

### 12.1 双层记忆架构

```
┌──────────────────────────────────────────────────────┐
│                   第一层：Redis 短期记忆               │
│                                                      │
│  Key: chat:{userId}:{sessionId}                      │
│  Value: [{role, content, timestamp}, ...]             │
│  容量: 最近 20 条消息（滚动窗口）                      │
│  TTL: 1 天自动过期                                   │
│                                                      │
│  用途：                                               │
│  • TextReplyAgent 的 System Prompt 上下文注入        │
│  • 每次对话的即时历史回溯                             │
│  • 写入失败不阻塞主流程（MySQL 已持久化）             │
└──────────────────────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────┐
│                  第二层：MySQL 全量持久化               │
│                                                      │
│  表: conversation_history                            │
│  字段: user_id, session_id, role, content,            │
│        intent_type, metadata_json, create_time        │
│                                                      │
│  用途：                                               │
│  • 永久保存全部对话记录                               │
│  • 历史会话回看（含资源卡片恢复）                     │
│  • 画像抽取（getRecentContext 取最近 10 轮）          │
│  • 跨会话长期记忆（getRecentCrossSessionContext）     │
│  • 会话管理 API（列表/删除/详情）                     │
└──────────────────────────────────────────────────────┘
```

### 12.2 三层记忆注入（TextReplyAgent）

TextReplyAgent 构建 System Prompt 时注入三层记忆：

1. **画像摘要**：从 profileJson 提取关键维度，结构化为可读文本
2. **本会话上下文**：从 Redis 读取当前 session 最近 20 条消息
3. **跨会话长期记忆**：新会话（Redis 为空）时，从 MySQL 查询最近 5 条用户提问主题

```
System Prompt 结构：
┌──────────────────────────────┐
│ 角色设定（学伴，鼓励性风格）  │
├──────────────────────────────┤
│ 用户学习画像摘要              │  ← 第一层：画像
│ - 知识基础: 中等              │
│ - 认知风格: 视觉型            │
│ - 薄弱点: [递归, 动态规划]   │
├──────────────────────────────┤
│ 本会话对话历史                │  ← 第二层：短期记忆
│ user: 什么是二分查找？        │
│ assistant: 二分查找是...      │
│ user: 能给我写个Python例子吗？│
├──────────────────────────────┤
│ 历史学习记录（跨会话记忆）    │  ← 第三层：长期记忆
│ - Python数据分析基础          │     (仅新会话时注入)
│ - Java面向对象编程           │
└──────────────────────────────┘
```

---

## 13. 学习报告系统

### 13.1 数据聚合源

`EduReportServiceImpl` 聚合 3 张表的数据生成报告：

```
learning_behavior_log ──→ 学习时长、连续天数、行为分布、每日趋势
quiz_record           ──→ 答题总数、正确率
learner_profile       ──→ 画像维度数
```

### 13.2 报告数据结构

```json
{
  "userId": 1,
  "todayMinutes": 45,
  "todayStudyHours": 0.8,
  "totalStudyHours": 12.5,
  "totalActions": 230,
  "completedNodes": 15,
  "consecutiveDays": 7,
  "quizTotal": 120,
  "quizCorrect": 96,
  "quizCorrectRate": "80.0%",
  "masteredConcepts": 5,
  "profileDimensions": 6,
  "dailyDuration": [
    {"date": "2026-06-09", "durationMinutes": 30},
    {"date": "2026-06-10", "durationMinutes": 60}
  ],
  "actionDistribution": [
    {"actionType": "VIEW_RESOURCE", "count": 80},
    {"actionType": "ANSWER_QUIZ", "count": 120}
  ]
}
```

### 13.3 连续学习天数算法

纯 Java 层计算（非 SQL）：

```
1. 查询最近 60 天的活跃日期集合
2. 从今天开始往前遍历
3. 遇到有活跃记录的日期 → 计数 +1
4. 遇到缺失 → 立即停止
5. 返回计数值
```

---

## 14. 配置说明

### 14.1 LLM 配置（application.yml）

```yaml
langchain4j:
  open-ai:
    chat-model:                    # 同步模型（文档/代码/画像抽取用）
      base-url: https://api.deepseek.com/v1
      api-key: your-deepseek-api-key
      model-name: deepseek-v4-flash
      max-tokens: 16384
      temperature: 0.7
      timeout: 600s
    streaming-chat-model:          # 流式模型（SSE 实时推送用）
      base-url: https://api.deepseek.com/v1
      api-key: your-deepseek-api-key
      model-name: deepseek-v4-flash
      max-tokens: 8192
      temperature: 0.7
      timeout: 300s
```

### 14.2 业务配置

```yaml
edu:
  ai:
    profile:
      update-interval-messages: 5    # 每 N 轮对话触发画像更新（当前用冷却期替代）
      context-window-messages: 10    # 画像抽取使用最近 N 条消息
    resource:
      quality-threshold: 0.7         # 质量阈值（低于此分触发重新生成）
      max-retries: 3                 # 最大重试次数
    parallelism:
      resource-generation: 4         # 资源生成并行度
```

### 14.3 数据库初始化

执行以下 SQL 文件（按顺序）：

1. `sql/ry_20260417.sql` — 若依基础表（sys_user, sys_role, sys_menu 等）
2. `sql/ai_init.sql` — AI 核心 5 张表
3. `sql/edu_resource.sql` — 资源表

---

## 15. 如何扩展 Agent

### 15.1 新增一个资源生成 Agent（推荐方式）

**示例：新增 "视频推荐 Agent"**

#### 步骤 1：创建 Agent 类

在 `ruoyi-system/src/main/java/com/ruoyi/system/ai/agent/` 下新建：

```java
package com.ruoyi.system.ai.agent;

import com.ruoyi.system.ai.graph.EducationState;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component  // ← 关键：Spring 自动发现
public class VideoRecommendAgent implements ResourceGenAgent {

    private static final Logger log = LoggerFactory.getLogger(VideoRecommendAgent.class);

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private com.ruoyi.system.ai.tool.ResourceTool resourceTool;

    @Override public String agentName() { return "videoRec"; }
    @Override public String resourceType() { return "video"; }
    @Override public String displayName() { return "视频推荐"; }
    @Override public String agentRole() { return "根据学习主题推荐相关教学视频"; }
    @Override public String[] capabilities() {
        return new String[]{"视频搜索", "内容匹配", "难度筛选"};
    }
    @Override public String[] tools() {
        return new String[]{"ChatModel", "ResourceTool"};
    }

    @Override
    public boolean shouldGenerate(EducationState state) {
        // 根据画像判断：偏好视频类型时启动
        String profile = state.profileJson();
        return profile != null && profile.contains("\"video\"");
    }

    @Override
    public String buildSystemPrompt(EducationState state) {
        // 构建视频推荐的 System Prompt
        return "你是教学视频推荐专家...";
    }

    @Override
    public String buildUserPrompt(EducationState state) {
        return "请推荐关于" + extractTopic(state) + "的教学视频";
    }

    @Override
    public String generate(EducationState state) {
        String systemPrompt = buildSystemPrompt(state);
        String userPrompt = buildUserPrompt(state);
        return chatModel.chat(List.of(
            dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
            dev.langchain4j.data.message.UserMessage.from(userPrompt)
        )).aiMessage().text();
    }

    @Override
    public String persist(EducationState state, String result) {
        Long id = resourceTool.saveResource(
            extractTopic(state), result, "video", "视频推荐", state.userId());
        return "video:" + id;
    }

    private String extractTopic(EducationState state) {
        try {
            return com.alibaba.fastjson2.JSON.parseObject(
                state.entitiesJson()).getString("topic");
        } catch (Exception e) { return state.userMessage(); }
    }
}
```

#### 步骤 2：在调度官 Prompt 中注册

修改 `EducationGraphBuilder.buildDispatchPrompt()` 方法，在可用 Agent 列表中添加：

```java
- videoRec: 视频推荐（用户偏好视频学习，或画像显示 preferredResourceType 含 video 时启动）
```

#### 步骤 3：在 Graph 的 dispatchAgents 中添加 case

在 `EducationGraphBuilder.dispatchAgents()` 和 `dispatchAgentsWithProgress()` 中，default 分支已通过 `ResourceAgentRegistry` 自动处理 — **无需修改**。

#### 完成！
Spring 会自动将你的 `VideoRecommendAgent` 注入到 `ResourceAgentRegistry`，调度官的 LLM 会根据新的 prompt 描述决定是否选中它。

## 16. 部署与运行

### 16.1 环境要求

| 组件 | 版本要求 |
|------|----------|
| JDK | 17+ |
| MySQL | 8.0+ |
| Redis | 6.0+ |
| MinIO | 最新版（可选，文件存储用） |
| Maven | 3.6+ |
| DeepSeek API Key | 需要有效的 API 密钥 |

### 16.2 启动步骤

```bash
# 1. 初始化数据库
mysql -u root -p < sql/ry_20260417.sql
mysql -u root -p < sql/ai_init.sql
mysql -u root -p < sql/edu_resource.sql

# 2. 修改配置
# 编辑 ruoyi-admin/src/main/resources/application.yml
#   - 数据库连接信息 (spring.datasource)
#   - Redis 连接信息 (spring.data.redis)
#   - DeepSeek API Key (langchain4j.open-ai.chat-model.api-key)
#   - MinIO 配置 (file-storage.minio)

# 3. 编译打包
mvn clean package -DskipTests

# 4. 启动
java -jar ruoyi-admin/target/ruoyi-admin.jar

# 或开发模式
cd ruoyi-admin
mvn spring-boot:run
```

### 16.3 关键启动日志

```
初始化 ChatModel: baseUrl=https://api.deepseek.com/v1, model=deepseek-v4-flash
初始化 StreamingChatModel: baseUrl=https://api.deepseek.com/v1, model=deepseek-v4-flash
AgentOrchestrationService 初始化完成，图已编译
构建 EducationGraph（新架构: 意图分析→条件画像→按需并行）...
```

### 16.4 验证服务

```bash
# 测试对话接口
curl -X POST http://localhost:8080/edu/ai/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"message": "你好，我想学Python"}'

# 访问 Swagger 文档
http://localhost:8080/swagger-ui.html
```

---

## 附录

### A. 关键设计模式

| 模式 | 应用位置 | 说明 |
|------|----------|------|
| **策略模式** | ResourceGenAgent 接口 | 每个 Agent 是不同策略实现 |
| **注册表模式** | ResourceAgentRegistry | Spring DI 自动发现所有实现 |
| **状态模式** | EducationState + StateGraph | LangGraph4j 管理对话状态流转 |
| **观察者模式** | AgentProgressCallback | SSE 事件推送 |
| **模板方法** | ResourceGenAgent 的 generate/persist/validate 流程 | 统一资源生成生命周期 |

### B. 降级与容错策略

| 场景 | 策略 |
|------|------|
| LLM 调用失败 | 返回兜底文本，不阻塞其他 Agent |
| JSON 解析失败 | DispatchResult.fallback() 返回 textReply |
| Redis 写入失败 | 仅日志告警，MySQL 已持久化 |
| 客户端 SSE 断开 | 设置 disconnected 标志，后续事件静默丢弃 |
| PlantUML 渲染失败 | 降级保存原始文本 |
| 画像抽取异常 | 返回已有画像，跳过本轮更新 |
| 流式生成无产出 | 回退到同步 generate() 切块模拟推送 |

### C. 文件清单速查

```
核心 Agent (6个):
  ruoyi-system/.../ai/agent/ProfileUpdateAgent.java
  ruoyi-system/.../ai/agent/TextReplyAgent.java
  ruoyi-system/.../ai/agent/DocGenAgent.java
  ruoyi-system/.../ai/agent/QuizGenAgent.java
  ruoyi-system/.../ai/agent/MindmapGenAgent.java
  ruoyi-system/.../ai/agent/CodeCaseGenAgent.java

编排层 (3个):
  ruoyi-system/.../ai/graph/EducationState.java
  ruoyi-system/.../ai/graph/EducationGraphBuilder.java
  ruoyi-system/.../ai/graph/AgentProgressCallback.java

工具层 (6个):
  ruoyi-system/.../ai/tool/ProfileTool.java
  ruoyi-system/.../ai/tool/BehaviorTool.java
  ruoyi-system/.../ai/tool/ResourceTool.java
  ruoyi-system/.../ai/tool/QuizTool.java
  ruoyi-system/.../ai/tool/CodeTool.java
  ruoyi-system/.../ai/tool/MindmapTool.java

API 控制器 (5个):
  ruoyi-admin/.../controller/system/EduAgentController.java
  ruoyi-admin/.../controller/system/EduProfileController.java
  ruoyi-admin/.../controller/system/EduQuizController.java
  ruoyi-admin/.../controller/system/EduResourceController.java
  ruoyi-admin/.../controller/system/EduReportController.java
```

