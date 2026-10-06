package com.phoenix.agent.service.file;

import com.phoenix.agent.enums.AgentFileBackendEnm;
import com.phoenix.agent.enums.AgentFileSourceEnm;
import com.phoenix.agent.model.AgentFile;
import com.phoenix.agent.vo.AgentFileVO;
import com.phoenix.data.entity.ChatSession;
import lombok.Builder;

import java.util.List;

/**
 * 会话产物文件门面（BL-19）：登记（tee 落 uploads + 入库）、列表、下载、逻辑删除。
 * 下载恒走 tee 副本；storeKey 仅溯源。
 */
public interface AgentFileService {

    /** 登记命令：content 为文件字节；fileName 可未净化（服务端清洗）。 */
    @Builder
    record RegisterCmd(Long agentId, String sessionId, String fileName, byte[] content,
                       AgentFileSourceEnm source, AgentFileBackendEnm backend,
                       String storeKey, String creator) {
    }

    /** tee 落盘 + 登记；超 50MB 抛 FILE_TOO_LARGE。 */
    AgentFile register(RegisterCmd cmd);

    /** 会话属主校验后的列表（del_flag=0，新→旧）。 */
    List<AgentFileVO> listBySession(String sessionId, String requesterUserId);

    /** 会话文件树的**历史行**聚合节点路径（旧 `store_key` 无会话维度或属他会话）。 */
    String HISTORY_PATH = "__history__";
    /** 历史行聚合节点显示名。 */
    String HISTORY_NAME = "历史文件";

    /**
     * 会话文件树**单层**（v1.7.0 R-02）：以会话目录为根，目录优先；框架内部件隐藏；历史行归「历史文件」节点。
     *
     * @param type dir/file/history
     * @param dirCount 直接子目录数（目录节点）
     * @param fileCount 后代文件数（目录节点）/ 该节点条目数（history 节点）
     */
    @Builder
    record TreeNode(String type, String name, String path, int dirCount, int fileCount,
                    String id, Long sizeBytes, String mime, String source,
                    java.time.LocalDateTime createTime) {
    }

    /** 单层返回：payload 只含当前层，避免大目录一次吐数千条。 */
    @Builder
    record TreeLevel(String rootName, String path, String parentPath, int dirTotal, int fileTotal,
                     int historyTotal, List<TreeNode> entries) {
    }

    /** 会话文件树单层（属主/管理员校验口径与 {@link #listBySession} 一致）。 */
    TreeLevel treeLevel(String sessionId, String requestedPath, String requesterUserId);

    /** 下载载荷（属主校验 + inline 白名单）。 */
    @Builder
    record Download(String fileName, String mime, byte[] content) {
    }

    Download download(String fileId, String requesterUserId, boolean inline);

    /** 属主逻辑删（不物理删，R-10/R-19）。 */
    void logicalDelete(String fileId, String requesterUserId);

    /**
     * 会话产物可访问性（BUG-80）：属主本人 **或** 后台管理员。
     * admin 运行页需要查看/管理前台用户会话的产物，原实现只认 userId 相等 → 列表可见但删除 42031。
     */
    boolean canAccessSession(ChatSession session, String requesterUserId);

    /** inline 预览白名单（HTML/图片/文本类，R-09；.sh 等仅下载）。 */
    static boolean inlineAllowed(String mime) {
        if (mime == null) {
            return false;
        }
        String m = mime.toLowerCase();
        return m.startsWith("image/") || m.startsWith("text/") || m.contains("json") || m.contains("xml");
    }
}
