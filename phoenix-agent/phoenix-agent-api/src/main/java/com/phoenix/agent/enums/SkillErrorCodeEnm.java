package com.phoenix.agent.enums;

/**
 * 技能管理错误码（41xxx 段，模块集中维护，禁散落硬编码）
 */
public enum SkillErrorCodeEnm {
    SKILL_NOT_FOUND(41001, "技能不存在"),
    SKILL_MUST_OFFLINE(41002, "已发布技能需先下线才能删除"),
    SKILL_ZIP_INVALID(41003, "技能包无效，未找到或无法解析 SKILL.md"),
    SKILL_NAME_CONFLICT(41004, "同名技能已存在，请确认后以覆盖方式上传"),
    SKILL_EXCEED_LIMIT(41005, "单轮显式执行技能数量超出上限"),
    SKILL_ACCESS_DENIED(41006, "无权使用该技能"),
    SKILL_FILE_TOO_LARGE(41007, "技能包超过大小限制"),
    SKILL_NOT_PUBLISHED(41008, "仅已发布技能可绑定"),
    SKILL_GROUP_NOT_FOUND(41009, "授权目标组不存在");

    private final int code;
    private final String msg;

    SkillErrorCodeEnm(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
