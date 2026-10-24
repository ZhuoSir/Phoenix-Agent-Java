package com.phoenix.platform.service.front.impl;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryChain;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.phoenix.common.exception.BusinessException;
import com.phoenix.common.vo.front.GroupMemberVO;
import com.phoenix.platform.mapper.front.AccountInfoMapper;
import com.phoenix.platform.model.front.AccountInfo;
import com.phoenix.platform.mapper.front.AccountGroupInfoMapper;
import com.phoenix.platform.model.front.AccountGroupInfo;
import com.phoenix.platform.model.front.GroupInfo;
import com.phoenix.platform.service.front.AccountGroupInfoService;
import com.phoenix.common.vo.front.UserGroupVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class AccountGroupInfoServiceImpl extends ServiceImpl<AccountGroupInfoMapper, AccountGroupInfo>
		implements AccountGroupInfoService {

	/** 仅注入 mapper（AccountInfoServiceImpl 依赖本 Service，注入 Service 会成环） */
	private final AccountInfoMapper accountInfoMapper;

	public AccountGroupInfoServiceImpl(AccountInfoMapper accountInfoMapper) {
		this.accountInfoMapper = accountInfoMapper;
	}

	@Override
	public Page<AccountGroupInfo> page(Page<AccountGroupInfo> page, AccountGroupInfo query) {
		return QueryChain.of(getMapper())
			.eq(AccountGroupInfo::getDelFlag, 0)
			.eq(AccountGroupInfo::getGroupId, query.getGroupId(), StrUtil.isNotBlank(query.getGroupId()))
			.eq(AccountGroupInfo::getAccountId, query.getAccountId(), StrUtil.isNotBlank(query.getAccountId()))
			.orderBy(AccountGroupInfo::getCreateTime, false)
			.page(page);
	}

	@Override
	public List<AccountGroupInfo> getByGroupId(String groupId) {
		return QueryChain.of(getMapper())
			.eq(AccountGroupInfo::getDelFlag, 0)
			.eq(AccountGroupInfo::getGroupId, groupId)
			.orderBy(AccountGroupInfo::getCreateTime, false)
			.list();
	}

	@Override
	public List<AccountGroupInfo> getByAccountId(String accountId) {
		return QueryChain.of(getMapper())
			.eq(AccountGroupInfo::getDelFlag, 0)
			.eq(AccountGroupInfo::getAccountId, accountId)
			.orderBy(AccountGroupInfo::getCreateTime, false)
			.list();
	}

	@Override
	public List<UserGroupVO> getUserGroupsByAccountId(String accountId) {
		QueryWrapper qw = QueryWrapper.create()
			.select(AccountGroupInfo::getGroupId)
			.select(AccountGroupInfo::getGroupName)
			.select(GroupInfo::getDescription)
			.from(AccountGroupInfo.class)
			.leftJoin(GroupInfo.class).on(AccountGroupInfo::getGroupId, GroupInfo::getId)
			.where(AccountGroupInfo::getAccountId).eq(accountId)
			.and(AccountGroupInfo::getDelFlag).eq(0);
		return getMapper().selectListByQueryAs(qw, UserGroupVO.class);
	}

	@Override
	public Page<GroupMemberVO> pageMembers(String groupId, long page, long size) {
		if (StrUtil.isBlank(groupId)) {
			throw new BusinessException("groupId 不能为空");
		}
		if (page < 1 || size < 1 || size > 500) {
			log.warn("[group-members] 非法分页参数 groupId={}, page={}, size={}（要求 page>=1, 1<=size<=500）", groupId, page, size);
			throw new BusinessException("分页参数非法：page 需 >=1，size 需在 1~500");
		}
		Page<AccountGroupInfo> relPage = QueryChain.of(getMapper())
			.eq(AccountGroupInfo::getDelFlag, 0)
			.eq(AccountGroupInfo::getGroupId, groupId)
			.orderBy(AccountGroupInfo::getCreateTime, false)
			.page(new Page<>(page, size));
		List<AccountGroupInfo> rows = relPage.getRecords();
		Map<String, AccountInfo> accountMap = rows.isEmpty() ? Map.of()
				: accountInfoMapper
					.selectListByQuery(QueryWrapper.create().in(AccountInfo::getId,
							rows.stream().map(AccountGroupInfo::getAccountId).toList()))
					.stream()
					.collect(Collectors.toMap(AccountInfo::getId, java.util.function.Function.identity(), (a, b) -> a));
		List<GroupMemberVO> members = rows.stream().map(rel -> {
			GroupMemberVO vo = new GroupMemberVO();
			vo.setRelationId(rel.getId());
			vo.setAccountId(rel.getAccountId());
			vo.setJoinTime(rel.getCreateTime());
			AccountInfo acc = accountMap.get(rel.getAccountId());
			if (acc != null) {
				vo.setAccountExists(true);
				vo.setUsername(acc.getUsername());
				vo.setRealName(acc.getRealName());
				vo.setAccountStatus(acc.getStatus());
			}
			else {
				// BUG-207：悬挂关联（账号已删除）——标记出来，前端据此提示"账号已删除"，避免整页 500
				vo.setAccountExists(false);
				vo.setUsername(rel.getAccountName());
			}
			return vo;
		}).toList();
		Page<GroupMemberVO> out = new Page<>(relPage.getPageNumber(), relPage.getPageSize(), relPage.getTotalRow());
		out.setRecords(members);
		log.info("[group-members] groupId={}, page={}, size={}, total={}", groupId, page, size, relPage.getTotalRow());
		return out;
	}

	@Override
	public boolean deleteByGroupIdAndAccountId(String groupId, String accountId) {
		boolean removed = getMapper().deleteByGroupIdAndAccountId(groupId, accountId) > 0;
		// T-02：成员移除是安全敏感操作，留 INFO 审计日志（plan §六 日志点 2）
		if (removed) {
			log.info("[group-members] 移除成员 groupId={}, accountId={}", groupId, accountId);
		}
		else {
			log.warn("[group-members] 移除成员失败（关联不存在）groupId={}, accountId={}", groupId, accountId);
		}
		return removed;
	}

	@Override
	public boolean deleteByAccountId(String accountId) {
		return getMapper().deleteByAccountId(accountId) > 0;
	}

}
