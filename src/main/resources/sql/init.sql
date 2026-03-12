CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS media_items (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    file_path TEXT UNIQUE NOT NULL,
    file_size BIGINT,
    duration_sec INT,
    width INT,
    height INT,
    codec VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS playback_progress (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    media_id BIGINT NOT NULL REFERENCES media_items(id),
    position_sec INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_media UNIQUE (user_id, media_id)
);

CREATE TABLE IF NOT EXISTS library_scan_jobs (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    total_files INT DEFAULT 0,
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0
);

INSERT INTO users(username, password_hash, role)
VALUES ('admin', '$2a$10$7EqJtq98hPqEX7fNZaFWoO5X6x6tEN84ImU8vz5KM38XxjPR9U1uO', 'admin')
ON CONFLICT (username) DO NOTHING;
