package com.emby.mvp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.emby.mvp.entity.MediaCategory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MediaCategoryMapper extends BaseMapper<MediaCategory> {
}
