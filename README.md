# Y社区 - 社交平台

基于 Spring Boot 3 的社交平台后端，提供帖子发布、用户互动、私信聊天、AI 助手等功能。

## 技术栈

| 类别 | 技术 | 版本 |
|---|---|---|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 3.4.12 |
| 安全 | Spring Security + JWT (jjwt) | 0.9.1 |
| ORM | MyBatis-Plus | 3.5.15 |
| 数据库 | MySQL | - |
| 缓存 | Redis | - |
| 搜索 | Elasticsearch | - |
| 文档数据库 | MongoDB | - |
| AI 大模型 | LangChain4j + 阿里云 DashScope (通义千问 Plus) | 1.3.0-beta9 |
| API 文档 | Knife4j (OpenAPI 3) | 4.6.0 |
| JSON | Alibaba Fastjson2 | 2.0.15 |

## 功能特性

- **用户系统** — 注册、登录（JWT 双 Token）、个人信息管理、每日签到
- **帖子系统** — 发布/删除帖子、基于兴趣的个性化推荐、关注流、分类浏览
- **社交互动** — 关注/取关、点赞、两级评论、互关好友列表
- **搜索** — Elasticsearch 全文检索帖子和用户、搜索历史记录
- **私信** — 用户间一对一聊天、会话列表、未读消息计数
- **AI 助手** — 基于通义千问的智能对话，支持流式响应（SSE），聊天记忆持久化到 MongoDB
- **内容审核** — 管理员封禁用户、审核员封禁帖子/删除评论
- **文件上传** — 图片上传（SHA-256 去重）、头像上传，支持 jpg/png/gif/webp

## 系统架构

```
┌─────────────┐     ┌─────────────┐     ┌──────────────┐
│   MySQL     │     │    Redis    │     │ Elasticsearch│
│ 持久化存储   │     │  缓存/排行榜  │     │  全文搜索     │
└──────┬──────┘     └──────┬──────┘     └──────┬───────┘
       │                   │                    │
       └───────────┬───────┘────────────────────┘
                   │
          ┌────────┴────────┐
          │  Spring Boot 3  │
          │  SocialPlatform │
          └────────┬────────┘
                   │
          ┌────────┴────────┐
          │     MongoDB     │
          │  AI 聊天记忆     │
          └─────────────────┘
```

**核心设计模式：**
- **Cache-Aside + 异步持久化** — 高频写操作（点赞、关注）先写 Redis，再通过 `@Async` 异步落库 MySQL
- **游标分页** — 信息流使用 Redis ZSet 实现基于时间戳的游标分页（`ScrollResult`）
- **无状态认证** — JWT Token + Redis 黑名单机制，Access Token 有效期 1 天，Refresh Token 有效期 7 天
- **RBAC 权限** — 三种角色：普通用户（USER）、管理员（ADMIN）、审核员（REVIEWER）

## API 接口

| 模块 | 路径前缀 | 功能 |
|---|---|---|
| 用户 | `/user` | 登录、注册、个人信息、签到、搜索 |
| 帖子 | `/post` | 发布、删除、信息流、分类筛选 |
| 评论 | `/comment` | 发表评论/回复、获取评论列表 |
| 关注 | `/follow` | 关注/取关、粉丝/关注列表、互关好友 |
| 点赞 | `/like` | 点赞/取消点赞 |
| 分类 | `/category` | 获取帖子分类 |
| 文件 | `/upload` | 上传/删除图片、头像 |
| 私信 | `/message` | 发消息、会话列表、未读计数 |
| 管理员 | `/admin` | 封禁用户、设置审核员 |
| 审核员 | `/reviewer` | 封禁帖子、删除评论 |
| AI 对话 | `/chat` | 智能助手流式对话 |
| AI 会话 | `/session` | 会话创建、查询、删除 |

完整 API 文档可通过 Knife4j 访问：`http://localhost:8081/doc.html`

## 环境要求

### 依赖服务

| 服务 | 端口 | 用途 |
|---|---|---|
| MySQL | 3306 | 数据库 `social_platform` |
| Redis | 6379 | 缓存（DB 1） |
| Elasticsearch | 9200 | 全文搜索 |
| MongoDB | 27017 | AI 聊天记忆（DB `chat_memory_db`） |
| Nginx | 8080 | 静态文件服务（图片访问） |

### 环境变量

```bash
PASSWORD=<数据库密码/Redis密码/JWT密钥>
DASH_SCOPE_API_KEY=<阿里云 DashScope API Key>
IMAGE_PATH=<Nginx静态文件目录，用于存储上传的图片，如 /usr/share/nginx/html/imgs>
```

## 快速启动

```bash
# 1. 克隆项目
git clone https://github.com/<your-username>/SocialPlatform.git
cd SocialPlatform

# 2. 导入数据库
mysql -u root -p social_platform < src/main/resources/social_platform.sql

# 3. 设置环境变量
export PASSWORD=your_password
export DASH_SCOPE_API_KEY=your_api_key

# 4. 启动应用
./mvnw spring-boot:run
```

应用启动后访问 `http://localhost:8081/doc.html` 查看 API 文档。

## 项目结构

```
src/main/java/com/li/socialplatform/
├── common/
│   ├── constant/       # 常量（Redis Key 前缀、错误消息、权限）
│   └── utils/          # 工具类（JWT、用户ID、ID生成、缓存、异步任务）
├── config/             # 配置类（安全、Redis、MyBatis、WebMvc、Knife4j）
├── filter/             # JWT 认证过滤器
├── handler/            # 全局异常处理、自动填充字段
├── pojo/
│   ├── dto/            # 请求 DTO
│   ├── entity/         # 实体类
│   └── vo/             # 响应 VO
├── server/
│   ├── controller/     # REST 控制器
│   ├── mapper/         # MyBatis-Plus Mapper 接口
│   ├── repository/     # Elasticsearch Repository
│   ├── service/        # 服务接口
│   ├── service/impl/   # 服务实现
│   └── task/           # 定时任务（浏览量同步）
└── SocialPlatformApplication.java
```
