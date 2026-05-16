package com.qs.ai.admian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Login audit log entity (sys_login_log).
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("username")
    private String username;

    @TableField("user_id")
    private Long userId;

    @TableField("client_ip")
    private String clientIp;

    @TableField("user_agent")
    private String userAgent;

    @TableField("success")
    private Integer success;

    @TableField("failure_reason")
    private String failureReason;

    @TableField("trace_id")
    private String traceId;

    @TableField("login_time")
    private LocalDateTime loginTime;
}
