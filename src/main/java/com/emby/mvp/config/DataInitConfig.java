package com.emby.mvp.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emby.mvp.entity.User;
import com.emby.mvp.mapper.UserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

@Configuration
public class DataInitConfig {

    @Bean
    public CommandLineRunner adminInit(UserMapper userMapper, PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("ALTER TABLE media_items ADD COLUMN IF NOT EXISTS poster_url VARCHAR(255)");
            jdbcTemplate.execute("ALTER TABLE media_items ADD COLUMN IF NOT EXISTS file_hash VARCHAR(64)");
            jdbcTemplate.execute("ALTER TABLE media_items ADD COLUMN IF NOT EXISTS bitrate_kbps INT");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS operation_logs (" +
                    "id BIGSERIAL PRIMARY KEY," +
                    "type VARCHAR(32) NOT NULL," +
                    "content TEXT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS media_subtitles (" +
                    "id BIGSERIAL PRIMARY KEY," +
                    "media_id BIGINT NOT NULL REFERENCES media_items(id)," +
                    "video_title VARCHAR(255)," +
                    "language VARCHAR(32)," +
                    "code VARCHAR(32)," +
                    "file_path TEXT NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            User admin = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, "admin")
                    .last("limit 1"));

            String encoded = passwordEncoder.encode("password");
            if (admin == null) {
                admin = new User();
                admin.setUsername("admin");
                admin.setPasswordHash(encoded);
                admin.setRole("admin");
                admin.setCreatedAt(LocalDateTime.now());
                userMapper.insert(admin);
            } else if (!passwordEncoder.matches("password", admin.getPasswordHash())) {
                admin.setPasswordHash(encoded);
                userMapper.updateById(admin);
            }
        };
    }
}
