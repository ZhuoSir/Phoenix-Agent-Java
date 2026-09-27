package com.phoenix.agent.harness.agent;

import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class HarnessStaticLoader {
    private Map<String, HarnessAgent> agents = new ConcurrentHashMap<>();

    public void addAgent(String sn, HarnessAgent agent) {
        agents.put(sn, agent);
    }


    protected Map<String, HarnessAgent> loadAgentMap() {
        return agents;
    }

    public HarnessAgent loadAgent(String sn) {
        if (sn == null || sn.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent name cannot be null or empty");
        }
        HarnessAgent agent = agents.get(sn);
        if (agent == null) {
            throw new NoSuchElementException("Agent not found: " + sn);
        } else {
            return agent;
        }
    }

    /**
     * 可空查询：用于 Registry 判定「该智能体是否由存量 Java 自注册类承载」（T-06 双路径分支）。
     *
     * @return 命中返回实例；未命中返回 null（不抛异常）
     */
    public HarnessAgent findAgent(String sn) {
        if (sn == null || sn.trim().isEmpty()) {
            return null;
        }
        return agents.get(sn);
    }

}
