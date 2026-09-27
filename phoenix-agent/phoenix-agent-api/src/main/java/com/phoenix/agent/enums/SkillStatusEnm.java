package com.phoenix.agent.enums;

/**
 * 技能发布状态枚举（tbl_harness_skills.status）
 */
public enum SkillStatusEnm {
    /** 草稿：上传后初始态，前台不可见不可用，不可被新绑定 */
    DRAFT("draft", "草稿"),
    /** 已发布：可绑定智能体，按组授权对前台可见可用 */
    PUBLISHED("published", "已发布");

    private final String code;
    private final String desc;

    SkillStatusEnm(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
