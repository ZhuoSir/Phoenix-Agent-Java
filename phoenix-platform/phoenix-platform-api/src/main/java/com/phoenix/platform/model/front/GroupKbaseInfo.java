package com.phoenix.platform.model.front;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import com.phoenix.common.model.BaseModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 组×知识库授权（knowledge-base R-13，与 GroupAgentInfo 同构；kbase_id 用 bigint 对齐库主键）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("tbl_platform_group_kbase_info")
public class GroupKbaseInfo extends BaseModel {
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private String id;
    private String groupId;
    private Long kbaseId;
}
