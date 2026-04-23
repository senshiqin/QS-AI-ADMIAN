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
 * 系统用户表实体（sys_user）。
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户编号(业务唯一) */
    @TableField("user_no")
    private String userNo;

    /** 登录用户名 */
    @TableField("username")
    private String username;

    /** 密码哈希(BCrypt/Argon2) */
    @TableField("password_hash")
    private String passwordHash;

    /** 用户昵称 */
    @TableField("nickname")
    private String nickname;

    /** 邮箱 */
    @TableField("email")
    private String email;

    /** 手机号 */
    @TableField("phone")
    private String phone;

    /** 头像URL */
    @TableField("avatar_url")
    private String avatarUrl;

    /** 状态:0禁用,1启用 */
    @TableField("status")
    private Integer status;

    /** 最后登录时间 */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /** 逻辑删除:0未删除,1已删除 */
    @TableField("deleted")
    private Integer deleted;

    /** 创建人ID */
    @TableField("create_by")
    private Long createBy;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新人ID */
    @TableField("update_by")
    private Long updateBy;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
