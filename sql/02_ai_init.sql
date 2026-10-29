-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: ry-vue
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `learner_profile`
--

DROP TABLE IF EXISTS `learner_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learner_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID（关联 sys_user）',
  `profile_json` mediumtext NOT NULL COMMENT '完整画像JSON（≥6维度）',
  `version` int DEFAULT '1' COMMENT '画像版本号（每次更新+1）',
  `dimension_count` int DEFAULT '0' COMMENT '已抽取维度数量（目标≥6）',
  `last_extract_at` datetime DEFAULT NULL COMMENT '最近一次画像抽取时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习者画像表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `learner_profile`
--

LOCK TABLES `learner_profile` WRITE;
/*!40000 ALTER TABLE `learner_profile` DISABLE KEYS */;
/*!40000 ALTER TABLE `learner_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `conversation_session`
--

DROP TABLE IF EXISTS `conversation_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `conversation_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `session_id` varchar(64) NOT NULL COMMENT '会话ID',
  `title` varchar(80) DEFAULT NULL COMMENT '首条用户消息标题',
  `last_active_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近活跃时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_session_user_session` (`user_id`,`session_id`),
  KEY `idx_conversation_session_user_active` (`user_id`,`last_active_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI会话表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `conversation_history`
--

DROP TABLE IF EXISTS `conversation_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `conversation_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `session_id` varchar(64) NOT NULL COMMENT '会话ID（UUID）',
  `role` varchar(16) NOT NULL COMMENT '角色: user/assistant/system',
  `content` mediumtext NOT NULL COMMENT '消息内容',
  `intent_type` varchar(32) DEFAULT NULL COMMENT '识别的意图类型',
  `metadata_json` json DEFAULT NULL COMMENT '元数据（实体、置信度等）',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_session` (`user_id`,`session_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对话历史表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `conversation_history`
--

LOCK TABLES `conversation_history` WRITE;
/*!40000 ALTER TABLE `conversation_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `conversation_history` ENABLE KEYS */;
UNLOCK TABLES;

-- Backfill the session table from bundled legacy conversation history.
INSERT INTO `conversation_session` (`user_id`, `session_id`, `title`, `last_active_time`, `create_time`, `update_time`)
SELECT
  ch.`user_id`,
  ch.`session_id`,
  (
    SELECT SUBSTRING(TRIM(first_user.`content`), 1, 80)
    FROM `conversation_history` first_user
    WHERE first_user.`user_id` = ch.`user_id`
      AND first_user.`session_id` = ch.`session_id`
      AND first_user.`role` = 'user'
      AND first_user.`content` IS NOT NULL
      AND TRIM(first_user.`content`) != ''
    ORDER BY first_user.`create_time` ASC, first_user.`id` ASC
    LIMIT 1
  ),
  COALESCE(MAX(ch.`create_time`), CURRENT_TIMESTAMP),
  COALESCE(MIN(ch.`create_time`), CURRENT_TIMESTAMP),
  COALESCE(MAX(ch.`create_time`), CURRENT_TIMESTAMP)
FROM `conversation_history` ch
GROUP BY ch.`user_id`, ch.`session_id`
ON DUPLICATE KEY UPDATE
  `title` = COALESCE(NULLIF(`conversation_session`.`title`, ''), VALUES(`title`)),
  `last_active_time` = GREATEST(`conversation_session`.`last_active_time`, VALUES(`last_active_time`)),
  `update_time` = GREATEST(`conversation_session`.`update_time`, VALUES(`update_time`));

--
-- Table structure for table `quiz_question`
--

DROP TABLE IF EXISTS `quiz_question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_question` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `question_type` varchar(20) NOT NULL COMMENT '题型: single_choice/multi_choice/fill_blank/short_answer/code',
  `stem` text NOT NULL COMMENT '题干',
  `options` json DEFAULT NULL COMMENT '选项JSON（选择题用）',
  `answer` text NOT NULL COMMENT '正确答案',
  `explanation` text COMMENT '答案解析',
  `difficulty` varchar(10) DEFAULT 'medium' COMMENT '难度: easy/medium/hard',
  `knowledge_points` json DEFAULT NULL COMMENT '关联知识点',
  `source` varchar(20) DEFAULT 'AI_GENERATED' COMMENT '来源: AI_GENERATED/MANUAL/IMPORTED',
  `correct_rate` decimal(5,2) DEFAULT NULL COMMENT '历史正确率（动态更新）',
  `status` char(1) DEFAULT '0' COMMENT '状态: 0=启用 1=停用',
  `create_by` varchar(64) DEFAULT 'AI' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_difficulty` (`difficulty`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题库表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quiz_question`
--

LOCK TABLES `quiz_question` WRITE;
/*!40000 ALTER TABLE `quiz_question` DISABLE KEYS */;
INSERT INTO `quiz_question` (`id`, `question_type`, `stem`, `options`, `answer`, `explanation`, `difficulty`, `knowledge_points`, `source`, `correct_rate`, `status`, `create_by`, `create_time`, `update_time`) VALUES (1,'single_choice','在SQL中，以下哪个子句用于对分组后的结果进行筛选？','[\"A. WHERE\", \"B. GROUP BY\", \"C. HAVING\", \"D. ORDER BY\"]','C','正确答案是C。`HAVING`子句专门用于筛选`GROUP BY`分组后的结果，通常与聚合函数（如`COUNT()`, `SUM()`）一起使用。A选项`WHERE`用于在分组前对原始数据进行筛选。B选项`GROUP BY`用于指定分组依据。D选项`ORDER BY`用于对最终结果集进行排序。理解`WHERE`和`HAVING`的执行顺序（`WHERE`在`GROUP BY`之前，`HAVING`在之后）是SQL查询逻辑的关键。','medium','[\"SQL执行顺序\", \"分组与聚合\"]','AI_GENERATED',NULL,'0','AI','2026-06-17 10:38:46','2026-06-17 10:38:46');
INSERT INTO `quiz_question` (`id`, `question_type`, `stem`, `options`, `answer`, `explanation`, `difficulty`, `knowledge_points`, `source`, `correct_rate`, `status`, `create_by`, `create_time`, `update_time`) VALUES (2,'single_choice','假设有一个`orders`表，包含`customer_id`, `order_amount`字段。要查询订单总金额超过500的客户及其订单数量，正确的SQL语句是？','[\"A. SELECT customer_id, COUNT(*) FROM orders WHERE order_amount > 500 GROUP BY customer_id;\", \"B. SELECT customer_id, COUNT(*) FROM orders GROUP BY customer_id HAVING SUM(order_amount) > 500;\", \"C. SELECT customer_id, COUNT(*) FROM orders GROUP BY customer_id WHERE SUM(order_amount) > 500;\", \"D. SELECT customer_id, COUNT(*) FROM orders GROUP BY customer_id HAVING order_amount > 500;\"]','B','正确答案是B。需求是筛选“订单总金额超过500”的客户，这是对分组（客户）后聚合（`SUM`）结果的筛选，必须使用`HAVING`子句，且`WHERE`中不能使用聚合函数。A选项错误，它在`WHERE`中直接筛选单个订单金额>500，再计数，筛选的是单笔订单而非总额。C选项语法错误，`WHERE`后不能跟聚合函数。D选项错误，`HAVING`后应该是基于聚合函数的条件，`order_amount > 500`是对单笔订单的筛选，而非总额。','medium','[\"聚合函数\", \"HAVING子句\", \"分组筛选\"]','AI_GENERATED',NULL,'0','AI','2026-06-17 10:38:46','2026-06-17 10:38:46');
INSERT INTO `quiz_question` (`id`, `question_type`, `stem`, `options`, `answer`, `explanation`, `difficulty`, `knowledge_points`, `source`, `correct_rate`, `status`, `create_by`, `create_time`, `update_time`) VALUES (3,'single_choice','关于SQL中的窗口函数，以下说法正确的是？','[\"A. 窗口函数在`GROUP BY`和`WHERE`之后执行。\", \"B. 使用`ROW_NUMBER()`函数，即使排序字段值相同，也会分配不同的序号。\", \"C. `PARTITION BY`子句的作用与`GROUP BY`完全相同。\", \"D. 所有聚合函数都不能用作窗口函数。\"]','B','正确答案是B。`ROW_NUMBER()`是行号函数，它为结果集的每一行分配一个唯一的、连续的整数，即使在分区内排序字段的值相同，行号也不同。A选项错误，窗口函数的执行顺序在`WHERE`、`GROUP BY`、`HAVING`之后，在`ORDER BY`之前。C选项错误，`PARTITION BY`仅对窗口函数生效，它为函数计算划定了逻辑分区，但结果集不会像`GROUP BY`那样减少行数。D选项错误，大多数聚合函数（如`SUM`, `AVG`, `COUNT`）都可以在窗口函数中使用，如`SUM(salary) OVER()`。','medium','[\"窗口函数\", \"ROW_NUMBER()\", \"PARTITION BY\"]','AI_GENERATED',NULL,'0','AI','2026-06-17 10:38:46','2026-06-17 10:38:46');
INSERT INTO `quiz_question` (`id`, `question_type`, `stem`, `options`, `answer`, `explanation`, `difficulty`, `knowledge_points`, `source`, `correct_rate`, `status`, `create_by`, `create_time`, `update_time`) VALUES (4,'single_choice','在设计数据库索引时，以下哪种情况通常不建议创建索引？','[\"A. 经常出现在`WHERE`子句中的字段。\", \"B. 经常用于`JOIN`连接的字段。\", \"C. 频繁进行大量`INSERT`、`UPDATE`、`DELETE`操作的表。\", \"D. 值的区分度（唯一值比例）很高的字段，如用户ID。\"]','C','正确答案是C。索引虽然能极大提高查询（`SELECT`）速度，但会降低数据写入（`INSERT`, `UPDATE`, `DELETE`）的性能，因为每次数据变更都需要同时维护索引结构。对于写操作频繁的表，过多的索引会成为性能瓶颈。A、B、D都是创建索引的典型场景：`WHERE`和`JOIN`字段是查询性能的关键路径；高区分度的字段（如主键）能极大缩小搜索范围，索引效果显著。','easy','[\"索引原理\", \"数据库性能优化\"]','AI_GENERATED',NULL,'0','AI','2026-06-17 10:38:46','2026-06-17 10:38:46');
INSERT INTO `quiz_question` (`id`, `question_type`, `stem`, `options`, `answer`, `explanation`, `difficulty`, `knowledge_points`, `source`, `correct_rate`, `status`, `create_by`, `create_time`, `update_time`) VALUES (5,'short_answer','请简述SQL查询语句中`SELECT`, `FROM`, `WHERE`, `GROUP BY`, `HAVING`, `ORDER BY`各子句的逻辑执行顺序。',NULL,'逻辑执行顺序为：1. `FROM`：确定查询的数据源（表），可能涉及连接（`JOIN`）。2. `WHERE`：对`FROM`阶段的数据进行行级筛选。3. `GROUP BY`：将筛选后的数据按指定字段分组。4. `HAVING`：对`GROUP BY`分组后的结果进行筛选（可使用聚合函数）。5. `SELECT`：选择要输出的列，可以包含表达式、聚合函数或窗口函数。6. `ORDER BY`：对最终结果集进行排序。注意，这个顺序是逻辑处理顺序，并非SQL语句的书写顺序（书写顺序通常为`SELECT... FROM... WHERE...`）。','本题考察对SQL核心查询逻辑的理解。掌握这个顺序有助于理解为什么在`WHERE`中不能使用聚合函数（因为`WHERE`在`GROUP BY`之前执行），以及为什么在`GROUP BY`之后的`SELECT`中可以使用聚合函数。这是编写复杂查询和调试SQL错误的基础。','medium','[\"SQL执行顺序\"]','AI_GENERATED',NULL,'0','AI','2026-06-17 10:38:46','2026-06-17 10:38:46');
INSERT INTO `quiz_question` (`id`, `question_type`, `stem`, `options`, `answer`, `explanation`, `difficulty`, `knowledge_points`, `source`, `correct_rate`, `status`, `create_by`, `create_time`, `update_time`) VALUES (6,'short_answer','请说明`LEFT JOIN`和`INNER JOIN`的主要区别，并各举一个适用场景。',NULL,'主要区别：`INNER JOIN`（内连接）只返回两个表中连接条件相匹配的行，如果一个表中的行在另一个表中没有匹配项，则该行不会出现在结果中。`LEFT JOIN`（左外连接）返回左表（`LEFT JOIN`关键字左边的表）中的所有行，即使右表中没有匹配的行。对于右表中没有匹配的行，其对应的右表字段值将显示为`NULL`。适用场景：`INNER JOIN`：查询“有对应订单的客户信息”。`LEFT JOIN`：查询“所有客户的信息，以及他们可能有的订单信息”（包括没有下过单的客户）。','本题考察对连接类型核心差异的掌握。理解`INNER JOIN`和`LEFT JOIN`的语义区别是编写正确多表查询的关键。`LEFT JOIN`确保了左表数据的完整性，在需要保留主表所有记录时（如生成报表）非常有用。','easy','[\"多表连接查询\", \"LEFT JOIN\", \"INNER JOIN\"]','AI_GENERATED',NULL,'0','AI','2026-06-17 10:38:46','2026-06-17 10:38:46');
INSERT INTO `quiz_question` (`id`, `question_type`, `stem`, `options`, `answer`, `explanation`, `difficulty`, `knowledge_points`, `source`, `correct_rate`, `status`, `create_by`, `create_time`, `update_time`) VALUES (7,'short_answer','在SQL中，使用窗口函数和使用`GROUP BY`聚合函数都能实现分组计算，但它们有什么根本区别？',NULL,'根本区别在于对结果集行数的影响。`GROUP BY`聚合函数会将多行数据“压缩”成一行，每个分组只输出一行结果，原始行数减少。而窗口函数在计算聚合（如`SUM`、`AVG`）时，为每一行都进行一次计算，但计算的“窗口”可以定义（通过`PARTITION BY`）。因此，窗口函数的结果集行数与原表完全一致，每一行都保留并附带了一个计算后的值。这使得窗口函数能够在保留明细数据的同时进行分组统计。','本题是SQL高级特性的核心对比。`GROUP BY`用于生成汇总报告，而窗口函数（`OVER`子句）用于在明细行上附加分析结果。例如，使用`GROUP BY`只能得到每个部门的总薪资；而使用窗口函数`SUM(salary) OVER(PARTITION BY department)`，可以在每个员工的记录后直接看到其所在部门的总薪资，同时不丢失该员工的个人信息。','medium','[\"窗口函数\", \"聚合函数\", \"GROUP BY\"]','AI_GENERATED',NULL,'0','AI','2026-06-17 10:38:46','2026-06-17 10:38:46');
INSERT INTO `quiz_question` (`id`, `question_type`, `stem`, `options`, `answer`, `explanation`, `difficulty`, `knowledge_points`, `source`, `correct_rate`, `status`, `create_by`, `create_time`, `update_time`) VALUES (8,'code','假设有两张表：`students`（学生表，字段：`id`, `name`, `class_id`）和`scores`（成绩表，字段：`student_id`, `subject`, `score`）。请编写一个SQL查询，找出每个班级（`class_id`）中，平均分最高的学生姓名（`name`）及其平均分。',NULL,'WITH StudentAvg AS (\\n    SELECT s.class_id, sc.student_id, s.name, AVG(sc.score) AS avg_score\\n    FROM students s\\n    JOIN scores sc ON s.id = sc.student_id\\n    GROUP BY s.class_id, sc.student_id, s.name\\n),\\nClassMaxAvg AS (\\n    SELECT class_id, MAX(avg_score) AS max_avg_score\\n    FROM StudentAvg\\n    GROUP BY class_id\\n)\\nSELECT sa.class_id, sa.name, sa.avg_score\\nFROM StudentAvg sa\\nJOIN ClassMaxAvg cma ON sa.class_id = cma.class_id AND sa.avg_score = cma.max_avg_score;\\n-- 备选方案（使用窗口函数）：\\nSELECT class_id, name, avg_score\\nFROM (\\n    SELECT s.class_id, s.name, AVG(sc.score) AS avg_score,\\n           ROW_NUMBER() OVER(PARTITION BY s.class_id ORDER BY AVG(sc.score) DESC) as rn\\n    FROM students s\\n    JOIN scores sc ON s.id = sc.student_id\\n    GROUP BY s.class_id, s.id, s.name\\n) t\\nWHERE rn = 1;','本题考察多表连接、分组聚合以及子查询/窗口函数的综合应用。思路分为两步：1. 计算每个学生的平均分（需要连接学生表和成绩表，并按学生和班级分组）。2. 在每个班级内，找出平均分最高的学生。提供的两种方案都是可行的。第一种使用公共表表达式（CTE），逻辑清晰，分步计算。第二种使用窗口函数`ROW_NUMBER()`，在每个班级分区内按平均分降序编号，然后取编号为1的行，代码更简洁。需要注意处理可能存在的同分情况（如果要求列出所有最高分学生，应使用`DENSE_RANK()`）。','hard','[\"多表连接\", \"分组聚合\", \"公共表表达式(CTE)\", \"窗口函数\", \"子查询\"]','AI_GENERATED',NULL,'0','AI','2026-06-17 10:38:46','2026-06-17 10:38:46');
INSERT INTO `quiz_question` (`id`, `question_type`, `stem`, `options`, `answer`, `explanation`, `difficulty`, `knowledge_points`, `source`, `correct_rate`, `status`, `create_by`, `create_time`, `update_time`) VALUES (9,'code','假设有一个`logs`访问日志表，字段为`user_id`, `page_url`, `access_time`。请编写SQL查询，计算每个用户（`user_id`）连续访问相同页面（`page_url`）的最大天数。',NULL,'WITH GroupedLogs AS (\\n    SELECT user_id, page_url, access_time,\\n           -- 核心步骤：通过日期减去一个行号来标识连续的日期组\\n           DATE(access_time) - INTERVAL (ROW_NUMBER() OVER(PARTITION BY user_id, page_url ORDER BY access_time)) DAY AS grp\\n    FROM (\\n        -- 先去重，如果用户一天内多次访问同一页面，只保留一次\\n        SELECT DISTINCT user_id, page_url, DATE(access_time) as access_time\\n        FROM logs\\n    ) AS distinct_logs\\n),\\nConsecutiveDays AS (\\n    SELECT user_id, page_url, grp, COUNT(*) AS consecutive_days\\n    FROM GroupedLogs\\n    GROUP BY user_id, page_url, grp\\n)\\nSELECT user_id, MAX(consecutive_days) AS max_consecutive_days\\nFROM ConsecutiveDays\\nGROUP BY user_id;','本题是经典的“连续登录/访问”问题变种，考察窗口函数的高级应用和创造性思维。解题关键在于如何将连续的日期标识为同一组。思路：1. 首先需要按`user_id`, `page_url`, `DATE(access_time)`去重，保证每天每个页面只计一次。2. 使用窗口函数`ROW_NUMBER()`在`(user_id, page_url)`分区内，按日期排序生成序号。3. 计算`日期 - 序号`（需要将日期转为数值，如`DATEDIFF`），如果访问是连续的（如1月1日、2日、3日），那么`日期 - 序号`的结果会相同（1-1=0, 2-2=0, 3-3=0），这个相同的结果`grp`就可以作为连续段的分组标识。4. 最后，按`(user_id, page_url, grp)`分组计数，即可得到每个连续段的天数，再取最大值。这种方法巧妙地利用了数学关系将连续问题转化为分组问题。','hard','[\"窗口函数\", \"ROW_NUMBER()\", \"连续性问题\", \"日期处理\"]','AI_GENERATED',NULL,'0','AI','2026-06-17 10:38:46','2026-06-17 10:38:46');
/*!40000 ALTER TABLE `quiz_question` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `quiz_record`
--

DROP TABLE IF EXISTS `quiz_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `question_id` bigint NOT NULL COMMENT '题目ID',
  `session_id` varchar(64) DEFAULT NULL COMMENT '会话ID',
  `user_answer` text COMMENT '用户答案',
  `is_correct` tinyint(1) DEFAULT NULL COMMENT '是否正确: 0/1',
  `score` decimal(5,2) DEFAULT NULL COMMENT '得分',
  `time_spent_sec` int DEFAULT NULL COMMENT '答题耗时（秒）',
  `answer_time` datetime DEFAULT NULL COMMENT '答题时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_question` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='答题记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quiz_record`
--

LOCK TABLES `quiz_record` WRITE;
/*!40000 ALTER TABLE `quiz_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `quiz_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `learning_behavior_log`
--

DROP TABLE IF EXISTS `learning_behavior_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learning_behavior_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `action_type` varchar(32) NOT NULL COMMENT '行为类型: VIEW_RESOURCE/COMPLETE_NODE/START_QUIZ/SUBMIT_ANSWER/ASK_QUESTION/GENERATE_RESOURCE',
  `target_type` varchar(32) DEFAULT NULL COMMENT '目标类型: resource/quiz/agent',
  `target_id` bigint DEFAULT NULL COMMENT '目标ID',
  `session_id` varchar(64) DEFAULT NULL COMMENT '会话ID',
  `duration_sec` int DEFAULT NULL COMMENT '持续时间（秒）',
  `metadata_json` json DEFAULT NULL COMMENT '扩展元数据',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_action_type` (`action_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习行为日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `learning_behavior_log`
--

LOCK TABLES `learning_behavior_log` WRITE;
/*!40000 ALTER TABLE `learning_behavior_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `learning_behavior_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
