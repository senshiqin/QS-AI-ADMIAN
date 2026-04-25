package com.qs.ai.admian.controller;

import com.qs.ai.admian.controller.request.ChatPointsTxRequest;
import com.qs.ai.admian.controller.response.ChatPointsTxResponse;
import com.qs.ai.admian.service.ChatPointsTxService;
import com.qs.ai.admian.util.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transaction demo APIs.
 */
@Tag(name = "Transaction Demo", description = "Transactional demo for chat insert + user points update")
@RestController
@RequestMapping("/api/v1/tx")
@RequiredArgsConstructor
public class TransactionDemoController {

    private final ChatPointsTxService chatPointsTxService;

    @Operation(summary = "Execute transaction", description = "Insert chat record and update user points in one transaction")
    @PostMapping("/chat-points")
    public ApiResponse<ChatPointsTxResponse> chatPoints(@RequestBody @Valid ChatPointsTxRequest request) {
        return ApiResponse.success(chatPointsTxService.saveChatAndUpdatePoints(request));
    }

    @Operation(summary = "Rollback test", description = "Simulate points update failure and verify chat insert rollback")
    @PostMapping("/chat-points/rollback-test")
    public ApiResponse<ChatPointsTxResponse> rollbackTest(@RequestBody @Valid ChatPointsTxRequest request) {
        request.setSimulatePointUpdateFail(true);
        return ApiResponse.success(chatPointsTxService.saveChatAndUpdatePoints(request));
    }
}
