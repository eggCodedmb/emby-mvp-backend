package com.emby.mvp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.emby.mvp.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {
}
