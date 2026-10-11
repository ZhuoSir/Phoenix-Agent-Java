package com.phoenix.data.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.phoenix.data.dto.knowledge.kb.KnowledgeBaseCreateDTO;
import com.phoenix.data.dto.knowledge.kb.KnowledgeBaseQueryDTO;
import com.phoenix.data.dto.knowledge.kb.KnowledgeBaseUpdateDTO;
import com.phoenix.data.service.knowledge.KnowledgeBaseService;
import com.phoenix.data.vo.ApiResponse;
import com.phoenix.data.vo.KnowledgeBaseVO;
import com.phoenix.data.vo.PageResult;
import com.phoenix.data.vo.PageResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库管理端点（knowledge-base R-01/03/04/12；data 域 ApiResponse 信封风格）。
 * 组授权写侧在 platform 域（组管理页）；绑定端点在 T-03。
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge-base")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class KnowledgeBaseController {

	private final KnowledgeBaseService knowledgeBaseService;

	/** R-03/T-06：检索路由探针（无 LLM 依赖，供运维与验证） */
	private final com.phoenix.data.service.vectorstore.AgentVectorStoreService agentVectorStoreService;

	/** R-04/T-10：重建任务（后台执行/进度/重试/续跑） */
	private final com.phoenix.data.service.knowledge.KnowledgeRebuildService knowledgeRebuildService;

	/** R-18（CR-01/T-25）：超管判定复用 R-08/R-17 守卫（单一口径） */
	private final com.phoenix.data.component.AdminRoleGuard adminRoleGuard;

	/** 当前登录用户 id */
	private String me() {
		return StpUtil.getLoginIdAsString();
	}

	private boolean isSuperAdmin() {
		return adminRoleGuard.isAdmin(me());
	}

	/** R-18：列表 owner 过滤值 —— 超管 null（不过滤），普通用户=本人 id */
	private String ownerFilter() {
		return isSuperAdmin() ? null : me();
	}

	/** R-18：单对象归属校验 —— 非本人且非超管 → 403 */
	private void assertOwner(Long id) {
		if (isSuperAdmin()) {
			return;
		}
		String creator = knowledgeBaseService.getCreatorById(id);
		if (creator == null || !creator.equals(me())) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.FORBIDDEN, "无权访问他人创建的知识库");
		}
	}

	@PostMapping("/query/page")
	public PageResponse<List<KnowledgeBaseVO>> queryByPage(@Valid @RequestBody KnowledgeBaseQueryDTO queryDTO) {
		try {
			PageResult<KnowledgeBaseVO> pr = knowledgeBaseService.queryByConditionsWithPage(queryDTO, ownerFilter());
			return PageResponse.success(pr.getData(), pr.getTotal(), pr.getPageNum(), pr.getPageSize(),
					pr.getTotalPages());
		}
		catch (Exception e) {
			log.error("知识库分页失败：{}", e.getMessage());
			return PageResponse.pageError("查询失败：" + e.getMessage());
		}
	}

	/** BUG-18(T-04)：按库重刷 QA/FAQ 联合向量（幂等） */
	@PostMapping("/{id}/re-embed")
	public ApiResponse<java.util.Map<String, Object>> reEmbed(@PathVariable Long id) {
		assertOwner(id);
		return ApiResponse.success("重刷完成", knowledgeBaseService.reEmbedKnowledgeBase(id));
	}

	/**
	 * T-06 检索路由探针：给出某智能体+向量类型下的检索路由（绑定库/模型/表），可顺带执行一次真实检索。
	 */
	@PostMapping("/retrieval-probe")
	public ApiResponse<java.util.Map<String, Object>> retrievalProbe(
			@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body) {
		try {
			if (!isSuperAdmin()) {
				return ApiResponse.error("仅管理员可执行检索探针");
			}
			String agentId = body.get("agentId") == null ? null : String.valueOf(body.get("agentId"));
			String docVectorType = body.get("docVectorType") == null ? "agentKnowledge"
					: String.valueOf(body.get("docVectorType"));
			java.util.Map<String, Object> out = new java.util.LinkedHashMap<>(
					agentVectorStoreService.retrievalRoute(agentId, docVectorType));
			java.util.List<Long> degradedKbIds = new java.util.ArrayList<>();
			out.put("degradedKbIds", degradedKbIds);
			int hitCount = -1;
			if (body.get("query") != null) {
				com.phoenix.data.dto.search.AgentSearchRequest req = com.phoenix.data.dto.search.AgentSearchRequest
						.builder()
						.agentId(agentId)
						.docVectorType(docVectorType)
						.query(String.valueOf(body.get("query")))
						.topK(body.get("topK") == null ? null : Integer.valueOf(String.valueOf(body.get("topK"))))
						.build();
				java.util.List<org.springframework.ai.document.Document> hits = agentVectorStoreService.search(req);
				hitCount = hits.size();
				java.util.List<java.util.Map<String, Object>> hs = new java.util.ArrayList<>();
				for (org.springframework.ai.document.Document d : hits) {
					java.util.Map<String, Object> h = new java.util.LinkedHashMap<>();
					h.put("id", String.valueOf(d.getId()));
					h.put("score", d.getScore());
					if (d.getMetadata() != null) {
						h.put("retrievalSourceModelId", d.getMetadata().get("retrievalSourceModelId"));
						h.put("retrievalSourceTable", d.getMetadata().get("retrievalSourceTable"));
						h.put("retrievalSourceKbIds", d.getMetadata().get("retrievalSourceKbIds"));
						// R-03/T-08：降级必须**显式提示**（响应可判定，不静默）
						h.put("retrievalDegraded", d.getMetadata().get("retrievalDegraded"));
						h.put("retrievalDegradedMode", d.getMetadata().get("retrievalDegradedMode"));
						h.put("retrievalDegradedReason", d.getMetadata().get("retrievalDegradedReason"));
						if (Boolean.TRUE.equals(d.getMetadata().get("retrievalDegraded"))) {
							degradedKbIds.addAll((java.util.List<Long>) d.getMetadata().getOrDefault("retrievalSourceKbIds",
									java.util.List.of()));
						}
					}
					hs.add(h);
				}
				out.put("hits", hs);
			}
			out.put("hitCount", hitCount);
			return ApiResponse.success("查询成功", out);
		}
		catch (Exception e) {
			return ApiResponse.error(e.getMessage());
		}
	}

	/** R-04/T-10 启动重建任务（管理员）：把该库向量重建到目标模型，完成后原子切换绑定。 */
	@PostMapping("/{id}/rebuild")
	public ApiResponse<java.util.Map<String, Object>> startRebuild(@PathVariable("id") Long id,
			@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body) {
		try {
			if (!isSuperAdmin()) {
				return ApiResponse.error("仅管理员可发起重建");
			}
			Object tm = body.get("toModelId");
			if (tm == null) {
				return ApiResponse.error("缺少 toModelId");
			}
			Long jobId = knowledgeRebuildService.start(id, Integer.valueOf(String.valueOf(tm)), me());
			java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
			out.put("jobId", jobId);
			out.put("kbId", id);
			out.put("toModelId", Integer.valueOf(String.valueOf(tm)));
			return ApiResponse.success("重建任务已创建", out);
		}
		catch (Exception e) {
			return ApiResponse.error("创建重建任务失败: " + e.getMessage());
		}
	}

	/** R-04/T-10 进度查询：最新任务的数值进度与失败清单。 */
	@GetMapping("/{id}/rebuild/latest")
	public ApiResponse<Object> rebuildLatest(@PathVariable("id") Long id) {
		try {
			if (!isSuperAdmin()) {
				return ApiResponse.error("仅管理员可查看重建进度");
			}
			return ApiResponse.success("查询成功", knowledgeRebuildService.latest(id));
		}
		catch (Exception e) {
			return ApiResponse.error(e.getMessage());
		}
	}

	/** R-04/T-10 续跑（断点续跑，亦可由启动钩子自动触发）。 */
	@PostMapping("/rebuild/{jobId}/resume")
	public ApiResponse<String> rebuildResume(@PathVariable("jobId") Long jobId) {
		try {
			if (!isSuperAdmin()) {
				return ApiResponse.error("仅管理员可续跑重建");
			}
			knowledgeRebuildService.resume(jobId);
			return ApiResponse.success("已提交续跑");
		}
		catch (Exception e) {
			return ApiResponse.error(e.getMessage());
		}
	}

	/** R-04/T-10 重试失败条目。 */
	@PostMapping("/rebuild/{jobId}/retry")
	public ApiResponse<String> rebuildRetry(@PathVariable("jobId") Long jobId) {
		try {
			if (!isSuperAdmin()) {
				return ApiResponse.error("仅管理员可重试重建");
			}
			knowledgeRebuildService.retryFailed(jobId);
			return ApiResponse.success("已提交重试");
		}
		catch (Exception e) {
			return ApiResponse.error(e.getMessage());
		}
	}

	/** R-06 归属与失效体检（管理员）：按库列出绑定模型/空间键/规模/可用性。 */
	@GetMapping("/ownership-audit")
	public ApiResponse<java.util.List<java.util.Map<String, Object>>> ownershipAudit() {
	    try {
	        if (!isSuperAdmin()) {
	            return ApiResponse.error("仅管理员可查看归属体检");
	        }
	        return ApiResponse.success("查询成功", knowledgeBaseService.ownershipAudit());
	    }
	    catch (Exception e) {
	        return ApiResponse.error(e.getMessage());
	    }
	}

	@GetMapping("/{id}")
	public ApiResponse<KnowledgeBaseVO> detail(@PathVariable("id") Long id) {
		try {
			assertOwner(id);
			return ApiResponse.success("查询成功", knowledgeBaseService.detail(id));
		}
		catch (org.springframework.web.server.ResponseStatusException e) {
			throw e;
		}
		catch (Exception e) {
			return ApiResponse.error(e.getMessage());
		}
	}

	@PostMapping
	public ApiResponse<KnowledgeBaseVO> create(@Valid @RequestBody KnowledgeBaseCreateDTO dto) {
		try {
			return ApiResponse.success("创建成功", knowledgeBaseService.create(dto, StpUtil.getLoginIdAsString()));
		}
		catch (Exception e) {
			log.warn("知识库创建失败：{}", e.getMessage());
			return ApiResponse.error(e.getMessage());
		}
	}

	@PutMapping
	public ApiResponse<KnowledgeBaseVO> update(@Valid @RequestBody KnowledgeBaseUpdateDTO dto) {
		try {
			assertOwner(dto.getId());
			return ApiResponse.success("更新成功", knowledgeBaseService.update(dto, StpUtil.getLoginIdAsString()));
		}
		catch (org.springframework.web.server.ResponseStatusException e) {
			throw e;
		}
		catch (Exception e) {
			return ApiResponse.error(e.getMessage());
		}
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Boolean> delete(@PathVariable("id") Long id) {
		try {
			assertOwner(id);
			knowledgeBaseService.delete(id, StpUtil.getLoginIdAsString());
			return ApiResponse.success("删除成功", Boolean.TRUE);
		}
		catch (org.springframework.web.server.ResponseStatusException e) {
			throw e;
		}
		catch (Exception e) {
			// R-04：仍被绑定异常 message 即绑定清单，前端直接展示
			return ApiResponse.error(e.getMessage());
		}
	}
}
