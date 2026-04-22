package com.qs.ai.admian.controller;

import com.qs.ai.admian.entity.UserEntity;
import com.qs.ai.admian.service.UserService;
import com.qs.ai.admian.util.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户控制器。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<List<UserEntity>> listUsers() {
        return ApiResponse.success(userService.listAll());
    }
}
