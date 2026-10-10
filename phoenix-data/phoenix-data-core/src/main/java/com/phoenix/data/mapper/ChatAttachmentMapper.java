package com.phoenix.data.mapper;

import com.mybatisflex.core.BaseMapper;
import com.phoenix.data.entity.ChatAttachment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话附件 mapper（chat-attachment-understanding T-02）。
 */
@Mapper
public interface ChatAttachmentMapper extends BaseMapper<ChatAttachment> {

}
