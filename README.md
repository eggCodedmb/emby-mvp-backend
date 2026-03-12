# emby-mvp-backend

基于 **Java 21 + Spring Boot 3.3 + MyBatis-Plus + PostgreSQL + Redis + JWT** 的 Emby 简化版后端 MVP。

## 已实现接口

- `POST /api/auth/login`
- `GET /api/auth/me`
- `GET /api/media?page=1&size=20`
- `GET /api/media/{id}`
- `GET /api/media/{id}/stream`（支持 Range）
- `GET /api/playback/{mediaId}/progress`
- `POST /api/playback/{mediaId}/progress`
- `POST /api/library/scan`（admin）

## 快速启动

1. 创建数据库：`emby_mvp`
2. 执行建表脚本：`src/main/resources/sql/init.sql`
3. 修改配置：`src/main/resources/application.yml`
4. 启动：
   - `mvn spring-boot:run`
   - 或 `mvn -DskipTests package && java -jar target/emby-mvp-backend-0.0.1-SNAPSHOT.jar`

## 默认管理员账号

- 用户名：`admin`
- 密码：`password`

> 密码以 BCrypt 存储在初始化 SQL 中，生产请立即修改。

## 流媒体 Range 测试

1. 先登录拿 token：
   - `POST /api/auth/login`
2. 请求流媒体接口并带 `Authorization: Bearer <token>`
3. 示例：

```bash
curl -H "Authorization: Bearer <token>" \
     -H "Range: bytes=0-1023" \
     -i http://localhost:8080/api/media/1/stream
```

返回应包含：
- `206 Partial Content`
- `Content-Range: bytes 0-1023/xxx`

## 说明

- 媒体路径使用 `app.media.root-path` 作为根目录。
- `/api/media/{id}/stream` 会做路径归一化与前缀校验，阻止 `../` 路径穿越。
- Swagger: `http://localhost:8080/swagger-ui.html`
