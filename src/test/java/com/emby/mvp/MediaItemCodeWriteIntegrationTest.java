package com.emby.mvp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emby.mvp.entity.MediaItem;
import com.emby.mvp.mapper.MediaItemMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@SpringBootTest
class MediaItemCodeWriteIntegrationTest {

    @Autowired
    private MediaItemMapper mediaItemMapper;

    @Test
    @Transactional
    void shouldWriteDass286CodeToDatabase() {
        MediaItem item = new MediaItem();
        item.setTitle("DASS-286 test item");
        item.setFilePath("/tmp/dass-286-test.mp4");
        item.setCode("DASS-286");
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());

        int inserted = mediaItemMapper.insert(item);
        Assertions.assertEquals(1, inserted, "插入 media_items 失败");
        Assertions.assertNotNull(item.getId(), "插入后未返回主键 ID");

        MediaItem saved = mediaItemMapper.selectById(item.getId());
        Assertions.assertNotNull(saved, "数据库中未查到刚插入的记录");
        Assertions.assertEquals("DASS-286", saved.getCode(), "code 字段写入异常");

        MediaItem byCode = mediaItemMapper.selectOne(
                new LambdaQueryWrapper<MediaItem>()
                        .eq(MediaItem::getCode, "DASS-286")
                        .eq(MediaItem::getId, item.getId())
                        .last("limit 1")
        );
        Assertions.assertNotNull(byCode, "按 code=DASS-286 未查询到记录");
    }
}
