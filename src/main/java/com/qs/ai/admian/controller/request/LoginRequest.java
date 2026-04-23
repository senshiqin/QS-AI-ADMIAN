package com.qs.ai.admian.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Login request parameters.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "username must not be blank")
    private String username;

    @NotBlank(message = "password must not be blank")
    private String password;
}
