package com.ruoyi.system.ai.agent;

import com.ruoyi.system.ai.graph.EducationState;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 教学动画 Agent — 生成可转 HTML/Manim/Remotion 的动画脚本和分镜。
 *
 * @author edu-agent
 */
@Component
public class AnimationGenAgent implements ResourceGenAgent {

    private static final Logger log = LoggerFactory.getLogger(AnimationGenAgent.class);

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private com.ruoyi.system.ai.tool.ResourceTool resourceTool;

    @Override public String agentName() { return "animationGen"; }
    @Override public String resourceType() { return "animation"; }
    @Override public String displayName() { return "教学动画"; }
    @Override public String agentRole() { return "把抽象知识点转成教学动画脚本、分镜和可播放HTML方案"; }
    @Override public String[] capabilities() { return new String[]{"动画脚本", "分镜设计", "交互说明", "多模态资源"}; }
    @Override public String[] tools() { return new String[]{"ChatModel", "ResourceTool"}; }

    @Override
    public boolean shouldGenerate(EducationState state) {
        String message = state.userMessage();
        String profile = state.profileJson();
        return (message != null && (message.contains("动画") || message.contains("视频") || message.contains("可视化")))
                || (profile != null && (profile.contains("视觉") || profile.contains("visual")));
    }

    @Override
    public String buildSystemPrompt(EducationState state) {
        return String.format("""
            你是教学动画导演和前端动画工程师。请把抽象知识点设计成可落地的教学动画资源。

            ## 学生画像
            %s

            ## 输出格式
            使用 Markdown，必须包含：
            ### 动画目标
            ### 分镜脚本
            用表格输出：镜头编号、画面元素、旁白、动画动作、教学意图、预计秒数。
            ### 关键状态
            提取 4-6 个动画步骤，每步包含标题、解释、需要高亮的对象。
            ### Manim/Remotion 实现提示
            ### 交互点
            ### 易错点提示

            要求：
            - 不调用真实视频大模型，先产出可展示的动画分镜。
            - 每个镜头都要服务一个知识点，不做无关装饰。
            - 适合课堂演示和学生自学回放。
            """, state.profileJson());
    }

    @Override
    public String buildUserPrompt(EducationState state) {
        return String.format("请为「%s」生成教学动画脚本和可播放实现方案。", extractTopic(state));
    }

    @Override
    public String generate(EducationState state) {
        log.info("AnimationGenAgent 开始生成动画脚本...");
        String storyboard = chatModel.chat(List.of(
                dev.langchain4j.data.message.SystemMessage.from(buildSystemPrompt(state)),
                dev.langchain4j.data.message.UserMessage.from(buildUserPrompt(state))
        )).aiMessage().text();
        return buildPlayableHtml(state, storyboard);
    }

    @Override
    public String persist(EducationState state, String result) {
        Long resourceId = resourceTool.saveResource(
                extractTopic(state) + " - 可播放教学动画",
                result,
                "animation",
                "教学动画,animation,html_animation,storyboard",
                state.userId());
        log.info("AnimationGenAgent 已持久化: id={}, userId={}", resourceId, state.userId());
        return "animation:" + resourceId;
    }

    @Override
    public double validate(String result) {
        if (result == null || result.isBlank()) return 0.0;
        double score = 0.2;
        if (result.length() >= 1200) score += 0.15;
        if (result.contains("<!doctype html") || result.contains("<canvas")) score += 0.3;
        if (result.contains("data-step") || result.contains("nextStep")) score += 0.2;
        if (result.contains("storyboard") || result.contains("分镜")) score += 0.15;
        if (result.contains("易错") || result.contains("checkpoint")) score += 0.1;
        return Math.min(1.0, score);
    }

    private String buildPlayableHtml(EducationState state, String storyboard) {
        String topic = escapeHtml(extractTopic(state));
        String safeStoryboard = escapeHtml(storyboard);
        return """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>%s - 可播放教学动画</title>
              <style>
                :root { color-scheme: light; --ink:#1f2937; --muted:#64748b; --line:#d8dee9; --accent:#2563eb; --ok:#0f766e; --warn:#b45309; }
                * { box-sizing: border-box; }
                body { margin:0; font-family: Arial, "Microsoft YaHei", sans-serif; background:#f7fafc; color:var(--ink); }
                main { min-height:100vh; display:grid; grid-template-columns:minmax(0, 1.1fr) 360px; gap:18px; padding:18px; }
                .stage { background:white; border:1px solid var(--line); border-radius:8px; padding:16px; display:flex; flex-direction:column; min-height:540px; }
                .title { display:flex; justify-content:space-between; gap:12px; align-items:center; margin-bottom:12px; }
                h1 { font-size:22px; margin:0; }
                .badge { font-size:12px; color:white; background:var(--accent); border-radius:999px; padding:5px 10px; white-space:nowrap; }
                canvas { width:100%%; aspect-ratio:16/9; border:1px solid var(--line); border-radius:8px; background:linear-gradient(180deg,#eff6ff,#ffffff); }
                .controls { display:flex; align-items:center; gap:8px; margin-top:12px; flex-wrap:wrap; }
                button { border:1px solid var(--line); background:white; color:var(--ink); border-radius:6px; padding:8px 12px; cursor:pointer; }
                button.primary { background:var(--accent); color:white; border-color:var(--accent); }
                .stepText { margin-top:12px; border-left:4px solid var(--accent); padding:10px 12px; background:#f8fafc; min-height:86px; }
                .stepText h2 { font-size:18px; margin:0 0 6px; }
                .stepText p { margin:0; line-height:1.7; color:var(--muted); }
                aside { background:white; border:1px solid var(--line); border-radius:8px; padding:16px; overflow:auto; max-height:calc(100vh - 36px); }
                aside h2 { font-size:16px; margin:0 0 10px; }
                .timeline { display:grid; gap:8px; margin-bottom:16px; }
                .timeline div { border:1px solid var(--line); border-radius:6px; padding:8px; color:var(--muted); }
                .timeline div.active { border-color:var(--accent); color:var(--ink); background:#eff6ff; }
                pre { white-space:pre-wrap; word-break:break-word; background:#f8fafc; border:1px solid var(--line); border-radius:6px; padding:10px; font-size:12px; line-height:1.55; }
                @media (max-width: 860px) { main { grid-template-columns:1fr; } aside { max-height:none; } }
              </style>
            </head>
            <body>
              <main>
                <section class="stage">
                  <div class="title">
                    <h1>%s</h1>
                    <span class="badge">HTML Animation</span>
                  </div>
                  <canvas id="board" width="960" height="540" aria-label="教学动画画布"></canvas>
                  <div class="controls">
                    <button id="prevBtn">上一步</button>
                    <button id="playBtn" class="primary">播放</button>
                    <button id="nextBtn">下一步</button>
                    <span id="progress"></span>
                  </div>
                  <div class="stepText">
                    <h2 id="stepTitle"></h2>
                    <p id="stepBody"></p>
                  </div>
                </section>
                <aside>
                  <h2>分镜与讲解</h2>
                  <div id="timeline" class="timeline"></div>
                  <h2>原始分镜脚本</h2>
                  <pre id="storyboard">%s</pre>
                </aside>
              </main>
              <script>
                const topic = "%s";
                const steps = [
                  { title: "问题进入", body: "从学习问题出发，先定位主题、前置知识和容易卡住的位置。", color: "#2563eb" },
                  { title: "概念拆解", body: "把主题拆成核心概念、关系和操作步骤，降低一次性理解负担。", color: "#0f766e" },
                  { title: "动态过程", body: "按时间顺序展示状态变化，让抽象机制变成可观察的过程。", color: "#7c3aed" },
                  { title: "易错提醒", body: "高亮常见错误点，并提示应该怎样检查和修正。", color: "#b45309" },
                  { title: "掌握检查", body: "通过复述、练习或小案例确认是否真正掌握。", color: "#dc2626" }
                ];
                let index = 0;
                let timer = null;
                const canvas = document.getElementById("board");
                const ctx = canvas.getContext("2d");
                const titleEl = document.getElementById("stepTitle");
                const bodyEl = document.getElementById("stepBody");
                const progressEl = document.getElementById("progress");
                const timelineEl = document.getElementById("timeline");

                function draw() {
                  const step = steps[index];
                  ctx.clearRect(0, 0, canvas.width, canvas.height);
                  ctx.fillStyle = "#eff6ff";
                  ctx.fillRect(0, 0, canvas.width, canvas.height);
                  ctx.fillStyle = "#ffffff";
                  roundRect(60, 70, 840, 350, 18);
                  ctx.fill();
                  ctx.strokeStyle = "#d8dee9";
                  ctx.stroke();
                  ctx.fillStyle = step.color;
                  ctx.beginPath();
                  ctx.arc(170 + index * 150, 230, 58, 0, Math.PI * 2);
                  ctx.fill();
                  ctx.fillStyle = "#ffffff";
                  ctx.font = "bold 32px Arial";
                  ctx.textAlign = "center";
                  ctx.fillText(String(index + 1), 170 + index * 150, 242);
                  ctx.fillStyle = "#1f2937";
                  ctx.font = "bold 30px Arial";
                  ctx.fillText(topic, 480, 130);
                  ctx.font = "22px Arial";
                  ctx.fillText(step.title, 480, 330);
                  ctx.strokeStyle = "#94a3b8";
                  ctx.lineWidth = 4;
                  for (let i = 0; i < steps.length - 1; i++) {
                    ctx.beginPath();
                    ctx.moveTo(170 + i * 150 + 58, 230);
                    ctx.lineTo(170 + (i + 1) * 150 - 58, 230);
                    ctx.stroke();
                  }
                  titleEl.textContent = step.title;
                  bodyEl.textContent = step.body;
                  progressEl.textContent = `步骤 ${index + 1} / ${steps.length}`;
                  renderTimeline();
                }
                function roundRect(x, y, w, h, r) {
                  ctx.beginPath();
                  ctx.moveTo(x + r, y);
                  ctx.arcTo(x + w, y, x + w, y + h, r);
                  ctx.arcTo(x + w, y + h, x, y + h, r);
                  ctx.arcTo(x, y + h, x, y, r);
                  ctx.arcTo(x, y, x + w, y, r);
                  ctx.closePath();
                }
                function renderTimeline() {
                  timelineEl.innerHTML = "";
                  steps.forEach((step, i) => {
                    const item = document.createElement("div");
                    item.className = i === index ? "active" : "";
                    item.dataset.step = String(i);
                    item.textContent = `${i + 1}. ${step.title}`;
                    item.onclick = () => { index = i; stop(); draw(); };
                    timelineEl.appendChild(item);
                  });
                }
                function nextStep() { index = (index + 1) %% steps.length; draw(); }
                function prevStep() { index = (index + steps.length - 1) %% steps.length; draw(); }
                function play() {
                  if (timer) { stop(); return; }
                  document.getElementById("playBtn").textContent = "暂停";
                  timer = setInterval(nextStep, 1800);
                }
                function stop() {
                  if (timer) clearInterval(timer);
                  timer = null;
                  document.getElementById("playBtn").textContent = "播放";
                }
                document.getElementById("nextBtn").onclick = () => { stop(); nextStep(); };
                document.getElementById("prevBtn").onclick = () => { stop(); prevStep(); };
                document.getElementById("playBtn").onclick = play;
                draw();
              </script>
              <!-- storyboard: %s -->
              <!-- checkpoint: 易错点需要在播放到对应步骤时暂停复述。 -->
            </body>
            </html>
            """.formatted(topic, topic, safeStoryboard, jsString(extractTopic(state)), safeStoryboard);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String jsString(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", " ");
    }

    private String extractTopic(EducationState state) {
        try {
            String topic = com.alibaba.fastjson2.JSON.parseObject(state.entitiesJson()).getString("topic");
            if (topic != null && !topic.isBlank()) return topic;
        } catch (Exception ignored) {}
        return state.userMessage();
    }
}
