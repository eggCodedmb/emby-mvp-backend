package com.emby.mvp.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaPatchRunner implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public SchemaPatchRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("ALTER TABLE media_items ADD COLUMN IF NOT EXISTS code VARCHAR(64)");
        jdbcTemplate.execute("ALTER TABLE media_items ADD COLUMN IF NOT EXISTS actor_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE media_items ADD COLUMN IF NOT EXISTS issue_date DATE");

        jdbcTemplate.execute(
                """
                        CREATE TABLE IF NOT EXISTS actors (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(128) NOT NULL UNIQUE,
                            avatar_url VARCHAR(255),
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )"""
        );

        jdbcTemplate.execute(
                """
                        CREATE TABLE IF NOT EXISTS categories (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(128) NOT NULL UNIQUE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )"""
        );

        jdbcTemplate.execute(
                """
                        CREATE TABLE IF NOT EXISTS media_actor (
                            id BIGSERIAL PRIMARY KEY,
                            media_id BIGINT NOT NULL REFERENCES media_items(id) ON DELETE CASCADE,
                            actor_id BIGINT NOT NULL REFERENCES actors(id) ON DELETE CASCADE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            CONSTRAINT uk_media_actor UNIQUE (media_id, actor_id)
                        )"""
        );

        jdbcTemplate.execute(
                """
                        CREATE TABLE IF NOT EXISTS media_category (
                            id BIGSERIAL PRIMARY KEY,
                            media_id BIGINT NOT NULL REFERENCES media_items(id) ON DELETE CASCADE,
                            category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            CONSTRAINT uk_media_category UNIQUE (media_id, category_id)
                        )"""
        );
    }
}
