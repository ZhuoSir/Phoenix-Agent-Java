package com.phoenix.platform.dto.front;

import lombok.Data;

import java.util.List;

/** 知识库 id 集合载荷（组分配/智能体绑定共用，全量替换语义）。 */
@Data
public class KbaseIdsDTO {
    private List<Long> kbaseIds;
}
