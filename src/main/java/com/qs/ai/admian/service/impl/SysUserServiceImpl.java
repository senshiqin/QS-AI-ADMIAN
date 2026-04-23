package com.qs.ai.admian.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qs.ai.admian.entity.SysUser;
import com.qs.ai.admian.mapper.SysUserMapper;
import com.qs.ai.admian.service.SysUserService;
import org.springframework.stereotype.Service;

/**
 * 系统用户 Service 实现。
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
}
