package com.emby.mvp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.emby.mvp.entity.Actor;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ActorMapper extends BaseMapper<Actor> {
}
