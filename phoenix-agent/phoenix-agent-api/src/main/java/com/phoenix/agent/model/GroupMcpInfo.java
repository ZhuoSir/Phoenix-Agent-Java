package com.phoenix.agent.model;

import java.io.Serializable;
import java.util.Date;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.core.keygen.KeyGenerators;
import com.mybatisflex.annotation.Table;

import lombok.Data;

/**
 * 用户组-MCP 授权（多对多，与 tbl_platform_group_skill_info 同构）。
 * 未授权任何组的 MCP 对前台用户不可用（默认非公开，同技能语义）。
 */
@Data
@Table("tbl_platform_group_mcp_info")
public class GroupMcpInfo implements Serializable {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private String id;
    private String groupId;
    private String mcpId;
    private String creator;
    private Date createTime = new Date();
    private String updator;
    private Date updateTime = new Date();
    private Integer delFlag = 0;
}
