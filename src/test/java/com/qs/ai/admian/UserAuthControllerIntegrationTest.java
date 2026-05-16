package com.qs.ai.admian;

import com.qs.ai.admian.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers login security, audit logging, and traceId responses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private RedisUtil redisUtil;

    @Test
    void loginShouldReturnTokenTraceIdAndWriteAuditLog() throws Exception {
        when(redisUtil.get(anyString())).thenReturn(null);

        mockMvc.perform(post("/api/v1/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "trace-login-success")
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-login-success"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.traceId").value("trace-login-success"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(7200));

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from sys_login_log where username = ? and success = 1 and trace_id = ?",
                Integer.class,
                "admin",
                "trace-login-success"
        );
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }

    @Test
    void badPasswordShouldReturnUnauthorizedAndWriteAuditLog() throws Exception {
        when(redisUtil.get(anyString())).thenReturn(null);
        when(redisUtil.increment(anyString())).thenReturn(1L);

        mockMvc.perform(post("/api/v1/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "trace-login-failure")
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.traceId").value("trace-login-failure"));

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from sys_login_log where username = ? and success = 0 and failure_reason = ? and trace_id = ?",
                Integer.class,
                "admin",
                "BAD_CREDENTIALS",
                "trace-login-failure"
        );
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }

    @Test
    void lockedLoginShouldReturnTooManyRequests() throws Exception {
        when(redisUtil.get(anyString())).thenReturn("1");

        mockMvc.perform(post("/api/v1/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(429));
    }
}
