package com.qs.ai.admian.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qs.ai.admian.entity.UserEntity;

import java.util.List;

/**
 * 用户服务接口。
 */
public interface UserService extends IService<UserEntity> {

    /**
     * 查询全部用户。
     *
     * @return 用户列表
     */
    List<UserEntity> listAll();
}
