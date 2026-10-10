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
