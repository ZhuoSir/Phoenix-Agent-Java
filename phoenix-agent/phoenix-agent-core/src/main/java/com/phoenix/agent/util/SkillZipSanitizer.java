package com.phoenix.agent.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 技能包 zip 清洗器。
 *
 * <p>背景：上游 AgentScope 的 {@code SkillUtil.createFromZip} 要求 zip 内所有条目位于**单一根目录**下，
 * 而真实用户包（尤其 macOS 压缩）常带 {@code __MACOSX/}、{@code .DS_Store}、AppleDouble {@code ._*} 等系统垃圾，
 * 或把 SKILL.md 所在目录与零散说明文件并列，从而被判定为"多根目录"拒绝。
 *
 * <p>本类只做**兼容清洗**（剥离系统垃圾 + 锁定 SKILL.md 所在根目录），随后仍交由上游解析做格式权威校验，
 * 不放宽对 SKILL.md/frontmatter 的实质要求。
 */
public final class SkillZipSanitizer {

    private static final String SKILL_FILE = "SKILL.MD";

    /**
     * 重建 zip 时统一套用的合成根目录名。
     *
     * <p>上游判据实为「不允许任何根级文件」（entry 必须含 '/' 且位置 &gt; 0，见 SkillUtil 字节码），
     * 故无论原包是扁平结构、单层文件夹还是多层嵌套，一律归一化为「单根目录 + 根下 SKILL.md」。
     */
    private static final String SYNTHETIC_ROOT = "skill-package/";

    private SkillZipSanitizer() {
    }

    /**
     * 清洗 zip：剥离系统垃圾、锁定 SKILL.md 所在层级，并统一归一化为单根目录结构。
     *
     * @throws IllegalArgumentException zip 为空或清洗后无有效文件
     */
    public static byte[] sanitize(byte[] raw) throws IOException {
        Map<String, byte[]> entries = readEntries(raw);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("技能包内没有有效文件");
        }
        String skillPath = entries.keySet()
            .stream()
            .filter(SkillZipSanitizer::isSkillFile)
            .min(Comparator.comparingInt(p -> p.split("/").length))
            .orElse(null);
        // 无 SKILL.md 时不猜测根目录，原样交上游报错（保持 41003 语义）
        String prefix = "";
        if (skillPath != null && skillPath.contains("/")) {
            prefix = skillPath.substring(0, skillPath.lastIndexOf('/') + 1);
        }
        Map<String, byte[]> kept = new LinkedHashMap<>();
        String finalPrefix = prefix;
        entries.forEach((path, content) -> {
            if (path.startsWith(finalPrefix)) {
                kept.put(SYNTHETIC_ROOT + path.substring(finalPrefix.length()), content);
            }
        });
        return writeZip(kept);
    }

    private static Map<String, byte[]> readEntries(byte[] raw) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(raw))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = normalize(entry.getName());
                if (name.isEmpty() || isSystemJunk(name)) {
                    continue;
                }
                entries.put(name, zis.readAllBytes());
            }
        }
        return entries;
    }

    private static byte[] writeZip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            entries.forEach((path, content) -> {
                try {
                    zos.putNextEntry(new ZipEntry(path));
                    zos.write(content);
                    zos.closeEntry();
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        return bos.toByteArray();
    }

    /** 统一分隔符并去掉 ./ 与绝对路径前缀 */
    private static String normalize(String name) {
        String path = name == null ? "" : name.replace('\\', '/').trim();
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    /** 压缩工具/操作系统产生的系统垃圾，非技能内容 */
    private static boolean isSystemJunk(String path) {
        String upper = path.toUpperCase();
        if (upper.startsWith("__MACOSX/")) {
            return true;
        }
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return ".DS_STORE".equals(upper)
                || fileName.startsWith("._")
                || "THUMBS.DB".equalsIgnoreCase(fileName)
                || upper.startsWith("__MACOSX");
    }

    private static boolean isSkillFile(String path) {
        return SKILL_FILE.equals(path.toUpperCase()) || path.toUpperCase().endsWith("/" + SKILL_FILE);
    }
}
