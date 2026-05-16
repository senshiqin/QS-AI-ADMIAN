package com.qs.ai.admian.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qs.ai.admian.entity.SysLoginLog;
import com.qs.ai.admian.mapper.SysLoginLogMapper;
import com.qs.ai.admian.service.SysLoginLogService;
import org.springframework.stereotype.Service;

/**
 * Login audit log service implementation.
 */
@Service
public class SysLoginLogServiceImpl extends ServiceImpl<SysLoginLogMapper, SysLoginLog> implements SysLoginLogService {
}
