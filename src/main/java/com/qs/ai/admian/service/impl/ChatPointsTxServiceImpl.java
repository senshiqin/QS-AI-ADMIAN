package com.qs.ai.admian.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qs.ai.admian.controller.request.ChatPointsTxRequest;
import com.qs.ai.admian.controller.response.ChatPointsTxResponse;
import com.qs.ai.admian.entity.AiChatRecord;
import com.qs.ai.admian.entity.SysUser;
import com.qs.ai.admian.service.AiChatRecordService;
import com.qs.ai.admian.service.ChatPointsTxService;
import com.qs.ai.admian.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction demo implementation.
 */
@Service
@RequiredArgsConstructor
public class ChatPointsTxServiceImpl implements ChatPointsTxService {

    private final AiChatRecordService aiChatRecordService;
    private final SysUserService sysUserService;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public ChatPointsTxResponse saveChatAndUpdatePoints(ChatPointsTxRequest request) {
        SysUser user = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, request.getUserId())
                .eq(SysUser::getDeleted, 0)
                .last("LIMIT 1"));
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + request.getUserId());
        }

        int beforePoints = user.getUserPoints() == null ? 0 : user.getUserPoints();
        String conversationId = "tx-" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();

        AiChatRecord chatRecord = new AiChatRecord();
        chatRecord.setConversationId(conversationId);
        chatRecord.setUserId(user.getId());
        chatRecord.setRoleType("user");
        chatRecord.setChatTime(now);
        chatRecord.setContent(request.getContent());
        chatRecord.setModelName(StringUtils.hasText(request.getModelName()) ? request.getModelName() : "deepseek");
        chatRecord.setPromptTokens(Math.max(1, request.getContent().length() / 2));
        chatRecord.setCompletionTokens(0);
        chatRecord.setTotalTokens(chatRecord.getPromptTokens());
        chatRecord.setDeleted(0);
        chatRecord.setCreateTime(now);
        chatRecord.setUpdateTime(now);
        boolean saved = aiChatRecordService.save(chatRecord);
        if (!saved) {
            throw new RuntimeException("Failed to insert chat record");
        }

        if (Boolean.TRUE.equals(request.getSimulatePointUpdateFail())) {
            boolean updated = sysUserService.lambdaUpdate()
                    .setSql("user_points = user_points + " + request.getPointsDelta())
                    .eq(SysUser::getId, -1L)
                    .update();
            if (!updated) {
                throw new RuntimeException("Simulated points update failure");
            }
        }

        user.setUserPoints(beforePoints + request.getPointsDelta());
        user.setUpdateTime(now);
        boolean userUpdated = sysUserService.updateById(user);
        if (!userUpdated) {
            throw new RuntimeException("Failed to update user points");
        }

        return ChatPointsTxResponse.builder()
                .userId(user.getId())
                .chatRecordId(chatRecord.getId())
                .beforePoints(beforePoints)
                .afterPoints(user.getUserPoints())
                .conversationId(conversationId)
                .build();
    }
}
