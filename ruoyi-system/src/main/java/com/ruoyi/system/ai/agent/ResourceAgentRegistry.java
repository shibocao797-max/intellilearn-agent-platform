package com.ruoyi.system.ai.agent;

import com.ruoyi.system.ai.graph.EducationState;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资源生成 Agent 注册中心
 *
 * Spring 自动发现所有 ResourceGenAgent 实现类（通过构造器注入 List）。
 * 新增 Agent 只需实现 ResourceGenAgent 接口 + @Component，零改动此处代码。
 *
 * @author edu-agent
 */
@Component
public class ResourceAgentRegistry {

    private final Map<String, ResourceGenAgent> agentMap = new LinkedHashMap<>();

    /**
     * Spring 自动注入所有 ResourceGenAgent 实现
     */
    public ResourceAgentRegistry(List<ResourceGenAgent> agents) {
        for (ResourceGenAgent agent : agents) {
            agentMap.put(agent.agentName(), agent);
        }
    }

    /**
     * 获取所有已注册的 Agent
     */
    public List<ResourceGenAgent> getAllAgents() {
        return List.copyOf(agentMap.values());
    }

    /**
     * 按名称获取 Agent
     */
    public ResourceGenAgent getAgent(String agentName) {
        return agentMap.get(agentName);
    }

    /**
     * 注册 Agent 总数
     */
    public int size() {
        return agentMap.size();
    }
}
