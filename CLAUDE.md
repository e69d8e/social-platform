# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
./mvnw compile          # Compile
./mvnw package          # Package as JAR
./mvnw spring-boot:run  # Start app on port 8081
./mvnw test             # Run all tests
./mvnw test -Dtest=ClassName#methodName  # Run a single test
```

Requires environment variables: `PASSWORD` (MySQL/Redis/JWT secret), `DASH_SCOPE_API_KEY` (DashScope AI).

Dependencies: MySQL (3306), Redis (6379/db1), Elasticsearch (9200), MongoDB (27017).

Database setup: `mysql -u root -p social_platform < src/main/resources/social_platform.sql`

## Architecture

Spring Boot 3.4.12 + Java 21 social platform with MyBatis-Plus, Redis, Elasticsearch, MongoDB, and LangChain4j (AI chat).

### Data Store Roles
- **MySQL** — primary persistent storage for all entities (MyBatis-Plus, no XML mappers, all queries via `LambdaQueryWrapper`)
- **Redis** — cache layer for social data (likes, follows, post feeds via ZSets, view counters, token blacklist, sign-in bitmaps)
- **Elasticsearch** — full-text search for Posts and Users. `User` and `Post` entities are dual-annotated with both `@TableName` (MyBatis-Plus) and `@Document` (Spring Data ES)
- **MongoDB** — AI chat memory only (`InMongoChatMemoryStore`)

### Key Patterns

**Response wrapper**: All controllers return `Result` (code 1=success, 0=failure, message, data, total).

**Auth**: JWT stateless auth. `JwtAuthenticationFilter` reads `Authorization: Bearer <token>`, checks Redis blacklist, loads user from DB. Three roles: USER(1), ADMIN(2), REVIEWER(3). Endpoints under `/admin/**` require ROLE_ADMIN, `/reviewer/**` require ROLE_REVIEWER. CORS is hardcoded to `http://127.0.0.1:5173` in `SecurityConfig`.

**Cache-aside + async persistence**: High-frequency writes (likes, follows) go to Redis synchronously, then persist to MySQL asynchronously via `@Async` methods in `AsyncTaskUtil`. `DataCacheUtil` implements cache-aside with 7-day TTL on reads.

**Pagination**: Feed uses cursor-based scrolling (`ScrollResult<T>` with `lastId` + `offset`, backed by Redis ZSets scored by timestamp). Search/admin uses standard offset pagination (MyBatis-Plus `IPage`).

**WebSocket (STOMP)**: Private messaging uses STOMP over WebSocket. JWT auth happens in `WebSocketConfig` via STOMP CONNECT headers. Messages route to `/user/queue/messages`.

**Rate limiting**: `@RateLimit` annotation + `RateLimitAspect` AOP aspect, backed by Redis for distributed limiting.

**Current user**: `UserIdUtil.getUserId()` reads from `SecurityContextHolder` to get the authenticated user's ID.

**ID generation**: Most entities use MyBatis-Plus `IdType.ASSIGN_ID` (snowflake). Post IDs are pre-generated via `RedisIdUtils.nextId()` through the `/post/generateId` endpoint before post creation.

**Entity convention**: Lombok `@Data` + `@AllArgsConstructor` + `@NoArgsConstructor`. All `Long` IDs serialized as strings via `@JsonFormat(shape = JsonFormat.Shape.STRING)`.

### Package Layout
```
com.li.socialplatform
  assistant/           — LangChain4j @AiService interfaces (Assistant, TitleAssistant)
  bean/                — Shared message beans
  common/annotation/   — Custom annotations (@RateLimit)
  common/aspect/       — AOP aspects (RateLimitAspect)
  common/constant/     — KeyConstant (Redis key prefixes), MessageConstant (error messages), AuthorityConstant
  common/exception/    — BizException (business logic exceptions)
  common/properties/   — SystemConstants (configurable system constants)
  common/utils/        — JwtUtils, UserIdUtil, RedisIdUtils, DataCacheUtil, AsyncTaskUtil, BanCacheUtil, HtmlUtils
  config/              — SecurityConfig, RedisConfig, MybatisPlusConfig, WebMvcConfig, WebSocketConfig, Knife4jConfig, MemoryChatConfig
  filter/              — JwtAuthenticationFilter
  handler/             — GlobalExceptionHandler, MyMetaObjectHandler (auto-fill createTime), auth handlers
  pojo/dto/            — Request DTOs
  pojo/entity/         — DB entities + Result + ScrollResult
  pojo/vo/             — Response view objects
  server/controller/   — REST controllers
  server/mapper/       — MyBatis-Plus BaseMapper interfaces (no custom methods)
  server/repository/   — Elasticsearch repositories
  server/service/      — Service interfaces (extend IService)
  server/service/impl/ — Service implementations (extend ServiceImpl)
  server/task/         — Scheduled tasks (ViewCountSyncTask syncs view counts every 5min)
  store/               — InMongoChatMemoryStore (MongoDB chat memory persistence)
```

### Database Schema

Full DDL with seed data at `src/main/resources/social_platform.sql`. Tables: user, authority, follow, category, post, comment, like_record, session, ban_record, user_interest_score, file, search_history, private_conversation, private_message, home_post, user_inbox.

### API Docs

Knife4j (OpenAPI) available at `http://localhost:8081/doc.html` when running.

### AI Assistant

LangChain4j with DashScope (Qwen) powers the AI chat. The `@AiService` interfaces in `assistant/` use `@SystemMessage(fromResource = "system-prompt.txt")` to load the system prompt from `src/main/resources/system-prompt.txt`. Chat memory is persisted to MongoDB via `InMongoChatMemoryStore`.

### Adding a New Feature

1. Create entity in `pojo/entity/` with `@TableName`, `@TableId(type = IdType.ASSIGN_ID)`, Lombok annotations
2. Create mapper in `server/mapper/` extending `BaseMapper<Entity>` with `@Mapper`
3. Create DTO/VO in `pojo/dto/` and `pojo/vo/`
4. Create service interface extending `IService`, implementation extending `ServiceImpl`
5. Create controller returning `Result`, inject service via `@RequiredArgsConstructor`
6. Add error message constants to `MessageConstant`, Redis key prefixes to `KeyConstant`
7. If endpoint is public, add path to `permitAll()` in `SecurityConfig`
