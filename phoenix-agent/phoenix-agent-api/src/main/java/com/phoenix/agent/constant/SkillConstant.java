package com.phoenix.agent.constant;

import java.util.Set;

/**
 * 技能管理常量（按域归类，不并入巨型 Constants）
 */
public final class SkillConstant {

    /** 上传 ZIP 大小上限：10MB */
    public static final long MAX_ZIP_BYTES = 10L * 1024 * 1024;

    /** 文本资源扩展白名单（spec 假设7：非文本拒绝） */
    public static final Set<String> TEXT_EXTENSIONS = Set.of("md", "markdown", "txt", "py", "js", "ts", "json",
        "yaml", "yml", "sh", "sql", "csv", "html", "css", "xml", "properties", "toml");

    /** 上传来源标记（tbl_harness_skills.source） */
    public static final String SOURCE_UPLOAD = "upload";

    private SkillConstant() {
    }
}
