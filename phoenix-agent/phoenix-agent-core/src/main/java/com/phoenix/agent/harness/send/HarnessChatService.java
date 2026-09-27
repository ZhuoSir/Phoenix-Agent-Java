package com.phoenix.agent.harness.send;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.phoenix.agent.harness.request.ConfirmRequest;
import com.phoenix.agent.harness.request.HarnessRequest;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface HarnessChatService {
    /**
     * 发送消息堵塞式返回
     * @param sn sn
     * @param request request
     * @return
     */
    Mono<Msg> call(String sn, HarnessRequest request);

    /**
     * 发送消息流式消息返回
     * @param sn sn
     * @param request request
     * @return
     */
    Flux<NodeOutput> stream(String sn, HarnessRequest request);

    /**
     * 按请求寻址发送消息（R-08：agentId 优先走运行时注册表，harnessSn 为存量兼容路径）
     */
    Mono<Msg> call(HarnessRequest request);

    /**
     * 按请求寻址流式返回（R-08）
     */
    Flux<NodeOutput> stream(HarnessRequest request);

    /**
     * 按请求寻址的人工确认（agentId 优先）
     */
    Flux<NodeOutput> confirmStream(ConfirmRequest request);

    /**
     * 获取智能体（按 agentId 走注册表；按 sn 走存量静态加载器）
     * @param sn 标识
     * @return
     */
    HarnessAgent getHarnessAgent(String sn);

    /**
     * 按智能体 id 获取运行时实例（库配置路径；存量自注册智能体自动回退其 Java 实例）
     */
    HarnessAgent getHarnessAgent(Long agentId);

    /**
     * 人工确认
     * @param request 参数
     * @return
     */
    Flux<NodeOutput> confirmStream(String sn, ConfirmRequest request);
}
