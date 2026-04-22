package com.qs.ai.admian.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qs.ai.admian.entity.UserEntity;
import com.qs.ai.admian.mapper.UserMapper;
import com.qs.ai.admian.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户服务实现。
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    @Override
    public List<UserEntity> listAll() {
        return this.list();
    }
}
