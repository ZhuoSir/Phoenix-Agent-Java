package com.phoenix.data.dto;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 智能体发布/授权请求体（agent-publish-group-grant T-01，镜像技能 SkillPublishDTO）。
 * publish 端点 body 可选：缺省=仅置状态不动授权（向后兼容 A-1）；groupIds 非 null=覆盖式重写组授权。
 */
@Data
public class AgentPublishDTO implements Serializable {

    /** 授权组 id 集；空列表=清空授权（新语义下即全公开，R-05） */
    private List<String> groupIds;
}
