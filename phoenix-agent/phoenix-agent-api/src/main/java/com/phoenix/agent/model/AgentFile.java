package com.phoenix.agent.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 智能体会话产物文件登记表（BL-19 会话文件面板）。
 * 下载恒走 relPath 的 tee 副本；storeKey 仅溯源原后端。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("tbl_data_agent_file")
public class AgentFile implements Serializable {
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private String id;
    /** 产出智能体（对齐 tbl_data_agent.id 的 bigint） */
    private Long agentId;
    /** 所属会话（tbl_data_chat_session.id） */
    private String sessionId;
    /** 清洗后的展示文件名 */
    private String fileName;
    /** tee 副本相对 uploads 根的路径（下载权威） */
    private String relPath;
    private Long sizeBytes;
    private String mime;
    /** tool=fs 工具写入 / scan=轮末扫描 / materialize=消息物化 */
    private String source;
    /** 原产物后端 local | remote */
    private String backend;
    /** 原后端定位串（溯源用，可空） */
    private String storeKey;
    private String creator;
    private Integer delFlag;
    private LocalDateTime createTime;
}
