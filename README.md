# Y 社区 — 社交平台后端

基于 Spring Boot 3 的全功能社交平台，提供帖子发布、用户互动、私信聊天、AI 智能助手等能力。

## 技术栈

| 类别 | 技术 | 版本 |
|---|---|---|
| 语言 / 框架 | Java / Spring Boot | 21 / 3.4.12 |
| 安全 | Spring Security + JWT (jjwt) | 0.9.1 |
| ORM | MyBatis-Plus | 3.5.15 |
| 数据库 | MySQL | 8.0.46 |
| 缓存 | Redis | 6.0.16 |
| 搜索引擎 | Elasticsearch + IK 分词 | 9.2.0 |
| 文档数据库 | MongoDB | 8.0.16 |
| AI 大模型 | LangChain4j + DashScope (通义千问) | 1.3.0-beta9 |
| API 文档 | Knife4j (OpenAPI 3) | 4.6.0 |
| 实时通信 | STOMP over WebSocket | — |
| 工具库 | Hutool / Fastjson2 / Jsoup / Commons Lang3 | — |

## 功能特性

- **用户系统** — 注册、JWT 双 Token 登录、个人信息管理、每日签到（Redis Bitmap）
- **帖子系统** — 发布/删除帖子、基于兴趣的个性化推荐、关注流、分类浏览
- **社交互动** — 关注/取关、点赞、两级评论、互关好友列表
- **搜索** — Elasticsearch 全文检索帖子和用户、搜索历史记录
- **私信** — STOMP WebSocket 实时一对一聊天、会话列表、未读消息计数
- **AI 助手** — 基于通义千问的智能对话，SSE 流式响应，聊天记忆持久化到 MongoDB
- **内容审核** — 管理员封禁用户、审核员封禁帖子/删除评论
- **文件上传** — 图片上传（SHA-256 去重）、头像上传，支持 jpg/png/gif/webp
- **限流** — `@RateLimit` 注解 + Redis 分布式限流

## 系统架构

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│    MySQL     │     │    Redis     │     │Elasticsearch │
│  持久化存储   │     │ 缓存 / 排行榜 │     │  全文搜索     │
└──────┬───────┘     └──────┬───────┘     └──────┬───────┘
       │                    │                     │
       └────────────┬───────┘─────────────────────┘
                    │
           ┌────────┴────────┐
           │  Spring Boot 3  │
           │   SocialPlatform│
           └────────┬────────┘
                    │
           ┌────────┴────────┐
           │     MongoDB     │
           │  AI 聊天记忆     │
           └─────────────────┘
```

### 核心设计模式

| 模式 | 说明 |
|---|---|
| **Cache-Aside + 异步持久化** | 高频写操作（点赞、关注）先写 Redis，再通过 `@Async` 异步落库 MySQL |
| **游标分页** | 信息流使用 Redis ZSet 实现基于时间戳的游标分页（`ScrollResult`） |
| **无状态认证** | JWT Token + Redis 黑名单，Access Token 1 天 / Refresh Token 7 天 |
| **RBAC 权限** | 三种角色：USER(1)、ADMIN(2)、REVIEWER(3) |
| **分布式限流** | `@RateLimit` 注解 + `RateLimitAspect` AOP，基于 Redis 滑动窗口 |

## 快速启动

### 方式一：Docker Compose（推荐）

```bash
# 1. 克隆项目
git clone git@github.com:e69d8e/social-platform.git
cd social-platform

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 填入 PASSWORD、DASH_SCOPE_API_KEY 等实际值

# 3. 创建外部卷（首次运行）
docker volume create sp-mysql-data
docker volume create sp-redis-data
docker volume create sp-es-data
docker volume create sp-mongodb-data
docker volume create sp-nginx-config
docker volume create sp-nginx-html

# 4. 启动所有服务
docker compose up -d

# 5. 导入数据库（首次运行，MySQL 健康检查通过后自动执行）
# 如需手动导入：
docker compose exec sp-mysql mysql -uroot -p${PASSWORD} social_platform < /docker-entrypoint-initdb.d/init.sql
```

启动后访问：

| 服务 | 地址 |
|---|---|
| 应用 | http://localhost:8081 |
| API 文档 (Knife4j) | http://localhost:8081/doc.html |
| Nginx 静态资源 | http://localhost:8080 |
| Kibana | http://localhost:5601 |

### 方式二：本地运行

```bash
# 1. 导入数据库
mysql -u root -p social_platform < src/main/resources/social_platform.sql

# 2. 设置环境变量
export PASSWORD=your_password
export DASH_SCOPE_API_KEY=your_api_key
export IMAGE_PATH=/path/to/nginx/imgs

# 3. 启动应用
./mvnw spring-boot:run
```

> 需要本地安装并启动 MySQL (3306)、Redis (6379)、Elasticsearch (9200，需安装 IK 分词插件)、MongoDB (27017)。

## 环境变量

| 变量 | 必填 | 说明 |
|---|---|---|
| `PASSWORD` | ✅ | MySQL / Redis / JWT 共用密码 |
| `DASH_SCOPE_API_KEY` | ✅ | 阿里云 DashScope (通义千问) API Key |
| `IMAGE_PATH` | ✅ | Nginx 静态图片目录（上传图片存储路径） |
| `BASE_URL` | — | 图片访问基础 URL，默认 `http://127.0.0.1:8080/imgs` |
| `CORS_ORIGIN` | — | 跨域允许来源，默认 `http://127.0.0.1:5173`，多来源逗号分隔 |

完整示例见 [`.env.example`](.env.example)。

## 开发

### 常用命令

```bash
./mvnw compile                           # 编译
./mvnw package                           # 打包 JAR
./mvnw spring-boot:run                   # 启动（端口 8081）
./mvnw test                              # 运行全部测试
./mvnw test -Dtest=ClassName             # 运行单个测试类
./mvnw test -Dtest=ClassName#method      # 运行单个测试方法
```

### 项目结构

```
src/main/java/com/li/socialplatform/
├── assistant/           # LangChain4j AI 助手接口（Assistant, TitleAssistant）
├── bean/                # 消息 Bean
├── common/
│   ├── annotation/      # 自定义注解（@RateLimit）
│   ├── aspect/          # AOP 切面（RateLimitAspect）
│   ├── constant/        # 常量（Redis Key、错误消息、权限）
│   ├── exception/       # 业务异常（BizException）
│   ├── properties/      # 系统配置常量（SystemConstants）
│   └── utils/           # 工具类（JWT、缓存、异步任务、限流等）
├── config/              # 配置类（Security、Redis、MyBatis、WebSocket、Knife4j）
├── filter/              # JWT 认证过滤器（JwtAuthenticationFilter）
├── handler/             # 全局异常处理、MyMetaObjectHandler（自动填充 createTime）
├── pojo/
│   ├── dto/             # 请求 DTO
│   ├── entity/          # 数据库实体 + Result + ScrollResult
│   └── vo/              # 响应视图对象
├── server/
│   ├── controller/      # REST 控制器
│   ├── mapper/          # MyBatis-Plus Mapper 接口
│   ├── repository/      # Elasticsearch Repository
│   ├── service/         # 服务接口（IService）
│   ├── service/impl/    # 服务实现（ServiceImpl）
│   └── task/            # 定时任务（ViewCountSyncTask — 每 5 分钟同步浏览量）
└── store/               # MongoDB 聊天记忆存储（InMongoChatMemoryStore）
```

### API 接口

| 模块 | 路径前缀 | 说明 |
|---|---|---|
| 用户 | `/user` | 登录、注册、个人信息、签到、搜索 |
| 帖子 | `/post` | 发布、删除、信息流、分类筛选、生成 ID |
| 评论 | `/comment` | 发表评论/回复、获取评论列表 |
| 关注 | `/follow` | 关注/取关、粉丝/关注列表、互关好友 |
| 点赞 | `/like` | 点赞/取消点赞 |
| 分类 | `/category` | 获取帖子分类 |
| 文件 | `/upload` | 上传/删除图片、头像 |
| 私信 | `/message` | 发消息、会话列表、未读计数 |
| 管理员 | `/admin` | 封禁用户、设置审核员、数据统计 |
| 审核员 | `/reviewer` | 封禁帖子、删除评论 |
| AI 对话 | `/chat` | 智能助手流式对话（SSE） |
| AI 会话 | `/session` | 会话创建、查询、删除 |

> 运行后访问 http://localhost:8081/doc.html 查看完整 API 文档。

### 数据库

完整 DDL 及种子数据：[`src/main/resources/social_platform.sql`](src/main/resources/social_platform.sql)

主要表：`user`、`authority`、`follow`、`category`、`post`、`comment`、`like_record`、`session`、`ban_record`、`user_interest_score`、`file`、`search_history`、`private_conversation`、`private_message`、`home_post`、`user_inbox`

### 新增功能开发流程

1. `pojo/entity/` — 创建实体，使用 `@TableName` + `@TableId(type = IdType.ASSIGN_ID)` + Lombok
2. `server/mapper/` — 创建 Mapper 继承 `BaseMapper<Entity>`
3. `pojo/dto/` / `pojo/vo/` — 创建请求 DTO 和响应 VO
4. `server/service/` + `server/service/impl/` — 创建 Service 接口及实现
5. `server/controller/` — 创建 Controller，返回 `Result`
6. `common/constant/` — 添加错误消息到 `MessageConstant`，Redis Key 到 `KeyConstant`
7. `config/SecurityConfig` — 如需公开接口，添加到 `permitAll()`

## 许可证

[MIT](LICENSE)
