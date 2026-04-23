package com.qs.ai.admian.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qs.ai.admian.entity.AiChatRecord;
import com.qs.ai.admian.mapper.AiChatRecordMapper;
import com.qs.ai.admian.service.AiChatRecordService;
import org.springframework.stereotype.Service;

/**
 * AI对话记录 Service 实现。
 */
@Service
public class AiChatRecordServiceImpl extends ServiceImpl<AiChatRecordMapper, AiChatRecord> implements AiChatRecordService {
}
