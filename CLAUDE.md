# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# 1. Start middleware services + dev Nginx (routes to host port 8081)
docker compose up -d sp-mysql sp-redis sp-es sp-mongodb sp-kibana sp-nginx-dev

# 2. Build & run Spring Boot app
./mvnw compile          # Compile
./mvnw package          # Package as JAR
./mvnw spring-boot:run  # Start app on port 8081
./mvnw test             # Run all tests
./mvnw test -Dtest=ClassName#methodName  # Run a single test
```

Environment variables (see `.env.example`): `PASSWORD` (MySQL/Redis/JWT secret), `DEEPSEEK_API_KEY` (DeepSeek AI), `HTML_PATH` (Nginx static files root, images stored under `${HTML_PATH}/imgs`). Optional: `CORS_ORIGIN` (default `http://127.0.0.1:5173,http://127.0.0.1:8080,http://localhost:5173,http://localhost:8080`) and `BASE_URL` (image access prefix).

`spring.config.import: optional:file:.env[.properties]` is set in `application.yaml`, so Spring picks up `.env` automatically — no need to `source .env` before running.

Dependencies: MySQL (3306), Redis (6379/db1), Elasticsearch (9200), MongoDB (27017), Kibana (5601), Nginx (8080). `docker-compose.yml` includes `sp-app` (full container deploy) and `sp-nginx-dev` (host-routed for local IDEA debugging) — see README §"本地日常开发模式".

Database setup is automatic via `docker-entrypoint-initdb.d` mount of `src/main/resources/social_platform.sql` on first MySQL container boot. The schema includes seed users (`li`/`admin`, `dufu`, `wangwei`, `baijvyi`, `libai` — all password `123456`).

## Architecture

Spring Boot 3.4.12 + Java 21 social platform with MyBatis-Plus, Redis, Elasticsearch, MongoDB, and LangChain4j (AI chat).

### Documentation Index
- `docs/architecture.html` — interactive Archify diagram of the runtime topology (Nginx → Spring Boot → MySQL/Redis/ES/MongoDB). Open locally in a browser.
- `docs/home-recommendation.md` — detailed writeup of the personalized feed: interest score model (Redis Hash + async MySQL persist), ES Function Score query with Gaussian time decay, fallback time-stream.
- `docs/class-diagram.md` — Mermaid ER diagram of all 19 tables.
- `README.md` — full deployment guide (full-container vs. local-IDE hybrid mode, port table, FAQ).

### Data Store Roles
- **MySQL** — primary persistent storage for all entities (MyBatis-Plus, no XML mappers, all queries via `LambdaQueryWrapper`)
- **Redis** — cache layer (likes, follows, post feeds via ZSets, view counters, token blacklist, sign-in bitmaps, sliding-window rate limit, interest score hash, captcha tokens). Key prefixes are centralized in `common/constant/KeyConstant`.
- **Elasticsearch** — full-text search for Posts and Users. `User` and `Post` entities are dual-annotated with both `@TableName` (MyBatis-Plus) and `@Document` (Spring Data ES). ES indices are bootstrapped by `DataInitializer` on startup.
- **MongoDB** — AI chat memory only (`InMongoChatMemoryStore`)

### Key Patterns

**Response wrapper**: All controllers return `Result` (code 1=success, 0=failure, message, data, total). Pagination responses use either `ScrollResult<T>` (cursor: `lastId` + `offset`) or standard offset pagination via MyBatis-Plus `IPage`.

**Auth**: JWT stateless auth via `JwtAuthenticationFilter` (`Authorization: Bearer <token>`). Token blacklist lives in Redis (`token-blacklist:*`). Three roles: USER(1), ADMIN(2), REVIEWER(3). `/admin/**` requires `ROLE_ADMIN`, `/reviewer/**` requires `ROLE_REVIEWER`. Public endpoints are whitelisted in `SecurityConfig.filterChain()`. CORS origin is configurable via `CORS_ORIGIN` env var.

**STOMP WebSocket**: Private messaging uses STOMP. `WebSocketConfig` performs JWT auth on the CONNECT frame (the HTTP filter doesn't run for WS upgrades), validates against the Redis blacklist, and loads the user from MySQL. Clients subscribe to `/user/queue/messages`.

**Cache-aside + async persistence**: High-frequency writes (likes, follows, view counts, interest scores) go to Redis synchronously, then persist to MySQL asynchronously via `@Async` methods in `AsyncTaskUtil` (executor `mvcTaskExecutor`). Reads use `DataCacheUtil` with 7-day TTL, falling back to MySQL on miss.

**Interest scoring**: `UserIntersetScoreUtil.changeScore(userId, categoryId, delta)` increments `sp:user:interest:{userId}` (Redis Hash) and queues an async MySQL upsert. Score deltas: view +1, comment +5, like +10/-10. Used by the home-feed ES Function Score query.

**Rate limiting**: `@RateLimit(maxRequests, timeWindow, timeUnit, limitType)` annotation + `RateLimitAspect` AOP, backed by Redis ZSet sliding window. `limitType` ∈ `{USER, IP, GLOBAL}`. Used on sensitive endpoints (`/post` publish, `/chat`, `/message`, login, etc.).

**Current user**: `UserIdUtil.getUserId()` reads from `SecurityContextHolder`. `ThreadLocalUtil` carries per-request context (used by some services for logging).

**ID generation**: Most entities use MyBatis-Plus `IdType.ASSIGN_ID` (snowflake). Post IDs are pre-generated via `RedisIdUtils.nextId()` exposed at `POST /post` (no-args), then passed back in the publish DTO.

**Entity convention**: Lombok `@Data` + `@AllArgsConstructor` + `@NoArgsConstructor`. All `Long` IDs serialized as strings via `@JsonFormat(shape = JsonFormat.Shape.STRING)`. `MyMetaObjectHandler` auto-fills `createTime`.

**Startup self-heal**: `DataInitializer` (implements `ApplicationRunner`) verifies ES indices exist, re-indexes Posts/Users if missing, refreshes the home-feed Redis ZSet from `home_post`, and clears stale `like-count:*` keys after a rebuild. Safe to run on every boot.

**View count sync**: `ViewCountSyncTask` runs every 5 minutes (`@Scheduled(fixedDelay = 5 * 60 * 1000)`), flushing accumulated `post-view-count:*` Redis values into MySQL and ES, then deleting the Redis key.

**LangChain4j (AI chat)**: `Assistant` and `TitleAssistant` interfaces in `assistant/` use `wiringMode = AiServiceWiringMode.EXPLICIT` and stream via `Flux<String>` (SSE). System prompt lives at `src/main/resources/system-prompt.txt` and is loaded via `@SystemMessage(fromResource = ...)`. Chat memory is per-session (`@MemoryId`) with a 20-message window (`MessageWindowChatMemory`), persisted to MongoDB through `InMongoChatMemoryStore`. Model: `deepseek-v4-flash` via DeepSeek's OpenAI-compatible base URL.

### Package Layout
```
com.li.socialplatform
  assistant/           — LangChain4j @AiService interfaces (Assistant, TitleAssistant)
  bean/                — Shared message beans (e.g. Messages — MongoDB doc for chat memory)
  common/annotation/   — @RateLimit
  common/aspect/       — RateLimitAspect (sliding-window check via Redis ZSet)
  common/constant/     — KeyConstant (Redis key prefixes), MessageConstant (error messages), AuthorityConstant (role names)
  common/exception/    — BizException (business logic exceptions, caught by GlobalExceptionHandler)
  common/properties/   — SystemConstants (image dir, page sizes, max lengths — injected from application.yaml)
  common/utils/        — JwtUtils, UserIdUtil, ThreadLocalUtil, RedisIdUtils, DataCacheUtil, AsyncTaskUtil, BanCacheUtil, HtmlUtils, DeleteFileUtil, UserIntersetScoreUtil
  config/              — SecurityConfig, RedisConfig (JSON serializer), MybatisPlusConfig, WebMvcConfig, WebSocketConfig, Knife4jConfig, MemoryChatConfig, JacksonConfig, DBUserDetailsManager, DataInitializer (startup self-heal)
  filter/              — JwtAuthenticationFilter (OncePerRequestFilter)
  handler/             — GlobalExceptionHandler, MyMetaObjectHandler (auto-fill createTime), auth entry/fail/success/logout handlers
  pojo/dto/            — Request DTOs
  pojo/entity/         — DB/ES entities + Result + ScrollResult
  pojo/vo/             — Response view objects
  server/controller/   — REST controllers (User, Captcha, Category, Post, Comment, Like, Follow, Session, Chat, PrivateMessage, UploadFile, Admin, Reviewer)
  server/mapper/       — MyBatis-Plus BaseMapper interfaces (no custom methods); DashboardMapper for admin stats
  server/repository/   — Elasticsearch repositories (PostElasticsearchRepository, UserElasticsearchRepository)
  server/service/      — Service interfaces (extend IService)
  server/service/impl/ — Service implementations (extend ServiceImpl)
  server/task/         — Scheduled tasks (ViewCountSyncTask — flushes Redis view counts to MySQL/ES every 5min)
  store/               — InMongoChatMemoryStore (LangChain4j ChatMemoryStore backed by MongoDB)
```

### Database Schema

Full DDL with seed data at `src/main/resources/social_platform.sql` (also auto-loaded on first MySQL container boot via `docker-entrypoint-initdb.d`). ER diagram in `docs/class-diagram.md`. Tables: user, authority, follow, category, post, comment, like_record, session, ban_record, user_interest_score, file, search_history, private_conversation, private_message, home_post, user_inbox.

### API Docs

Knife4j (OpenAPI) available at `http://localhost:8081/doc.html` when running.

### File Uploads

`UploadFileController` handles post-image uploads. Images are stored as `SHA-256(content)` under `<HTML_PATH>/imgs/aa/bb/<hash>.jpg` (two-level hex sharding) so duplicate uploads dedupe on disk. Validates image dimensions/aspect-ratio via TwelveMonkeys ImageIO (supports WebP + CMYK JPEG). `DeleteFileUtil` cleans up orphaned files when a post is deleted. The `BASE_URL` env var is the public prefix prepended to the stored relative path.

### Tests

The Maven test suite is essentially empty — only the Spring Boot context-load smoke test (`SocialPlatformApplicationTests`) exists. Integration testing happens via the running Docker stack + manual/Postman flows against `/doc.html`. Don't add unit tests as a reflex; if you do add coverage, focus on utilities (rate limit, cache invalidation, JWT) rather than controllers.

### Adding a New Feature

1. Create entity in `pojo/entity/` with `@TableName`, `@TableId(type = IdType.ASSIGN_ID)`, Lombok annotations
2. Create mapper in `server/mapper/` extending `BaseMapper<Entity>` with `@Mapper`
3. Create DTO/VO in `pojo/dto/` and `pojo/vo/`
4. Create service interface extending `IService`, implementation extending `ServiceImpl`
5. Create controller returning `Result`, inject service via `@RequiredArgsConstructor`
6. Add error message constants to `MessageConstant`, Redis key prefixes to `KeyConstant`
7. If endpoint is public, add path to `permitAll()` in `SecurityConfig`
