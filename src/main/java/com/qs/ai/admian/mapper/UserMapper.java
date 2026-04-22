package com.qs.ai.admian.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qs.ai.admian.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
