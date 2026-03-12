package com.emby.mvp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.emby.mvp.entity.MediaItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MediaItemMapper extends BaseMapper<MediaItem> {}
