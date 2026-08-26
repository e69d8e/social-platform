# Y 社区 — 社交平台后端

基于 Spring Boot 3 + Java 21 的全功能社交互动平台，支持帖子发布、关注流、点赞评论、私信聊天、全文检索、AI 智能助手（DeepSeek）与图片分片存储。

---

## 🛠️ 技术栈

| 类别 | 技术 / 组件 | 版本 | 说明 |
|---|---|---|---|
| **语言 / 核心框架** | Java / Spring Boot | 21 / 3.4.12 | 基础开发框架，开启容器内存感知与分层打包 |
| **安全与认证** | Spring Security + JJWT | 6.4.2 / 0.9.1 | JWT 双 Token 无状态认证 + Redis 黑名单 |
| **ORM / 数据持久化** | MyBatis-Plus | 3.5.15 | 简化 CRUD，自动填充字段，全 Lambda 条件构造 |
| **关系型数据库** | MySQL | 8.0.46 | 业务核心数据存储（utf8mb4 字符集） |
| **分布式缓存** | Redis | 6.0.16 | 点赞/关注缓存、用户签到位图、滑动窗口限流、游标分页 |
| **全文搜索引擎** | Elasticsearch + IK 分词 | 9.2.0 | 帖子与用户全文检索、中文高亮分词 |
| **文档数据库** | MongoDB | 8.0.16 | LangChain4j AI 助手多轮对话记忆持久化 |
| **AI 大模型** | LangChain4j + DeepSeek | 1.3.0-beta9 | DeepSeek-V4-Flash 模型，SSE 流式对话与标题生成 |
| **网关与反向代理** | Nginx | 1.30 (Alpine) | SPA 前端路由、静态图片高速直出、WebSocket 代理 |
| **接口文档** | Knife4j (OpenAPI 3) | 4.6.0 | 在线接口文档与调试控制台 |
| **实时通信** | STOMP over WebSocket | — | 点对点私信推送与未读计数 |

---

## ✨ 功能特性

- **用户系统**：注册/登录、JWT 双 Token（Access 1天 / Refresh 7天）、滑块验证码、个人信息管理、每日签到（Redis Bitmap）、用户主页。
- **帖子系统**：发布/删除、分类筛选、基于兴趣评分的个性化推荐、关注动态流、浏览量异步同步。
- **社交互动**：关注/取关、点赞/取消点赞、二级评论楼中楼、互关好友列表。
- **全文检索**：基于 Elasticsearch + IK 分词的帖子与用户模糊搜索、搜索历史记录。
- **私信聊天**：STOMP WebSocket 实时点对点消息通信、会话列表维护、未读数统计。
- **AI 助手**：集成 DeepSeek 智能模型，支持流式打字机输出（SSE），对话上下文自动持久化至 MongoDB。
- **内容安全与管理**：管理员后台、用户封禁、审核员帖子下架与违规评论删除。
- **文件与图床**：图片上传（最大 10MB，支持 jpg/png/gif/webp）、宽高比合法性校验、SHA-256 自动去重、两级 16 进制目录分片存储。
- **分布式限流**：自定义 `@RateLimit` 注解 + AOP 切面，基于 Redis 滑动窗口防御刷量。

---

## 🏗️ 架构设计

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

---

## 🚀 跨机器 / 服务器一键部署指南

本项目已实现 **100% 容器化自动编排**，并针对在全新电脑、云服务器（Linux/macOS/Windows）部署进行了全自动权限自愈与挂载适配。

### 1. 前置准备

- 已安装 **Docker** 与 **Docker Compose**（推荐 Docker Desktop 或 Docker Engine 24.0+）。
- **（仅 Linux 服务器必须）** 调整虚拟内存区域限制（满足 Elasticsearch 启动要求）：
  ```bash
  sudo sysctl -w vm.max_map_count=262144
  # 永久生效：
  echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf && sudo sysctl -p
  ```
- **云服务器防火墙 / 安全组放行端口**：
  - `8080`：Nginx 门户（前端、接口、图片、WebSocket）
  - `5601`：（可选）Kibana 控制台
  - `8081`：（可选）Spring Boot 直连端口

---

### 2. 部署步骤

```bash
# 步骤 1：克隆代码仓库
git clone git@github.com:e69d8e/social-platform.git
cd social-platform

# 步骤 2：生成并配置环境变量
cp .env.example .env
vim .env
```

`.env` 核心配置说明：
```env
# 必填项：数据库与 Redis 密码
PASSWORD=your_password_here

# 必填项：DeepSeek API Key
DEEPSEEK_API_KEY=your_deepseek_api_key_here

# ⚠️ 远程/局域网部署必填：图片访问地址（将 127.0.0.1 替换为您的服务器 IP 或域名）
BASE_URL=http://<YOUR_SERVER_IP>:8080/imgs

# 允许跨域的前端地址
CORS_ORIGIN=http://127.0.0.1:5173,http://127.0.0.1:8080,http://localhost:5173,http://localhost:8080,http://<YOUR_SERVER_IP>:8080
```

```bash
# 步骤 3：（可选）放入前端静态文件
# 将前端编译生成的 dist 目录中的文件直接拷贝放入 ./html/ 根目录下即可：
# html/
# ├── index.html
# ├── assets/
# └── favicon.ico

# 步骤 4：一键构建并后台启动所有服务
DOCKER_BUILDKIT=1 docker compose up -d

# 步骤 5：查看各服务健康状态（均显示 healthy 即可）
docker compose ps
```

---

### 3. 访问入口

| 模块 | 访问地址 | 说明 |
|---|---|---|
| 🌐 **平台前端主页** | `http://<服务器IP>:8080` | 前端 SPA 页面（自动响应路由） |
| 🖼️ **图片静态资源直出** | `http://<服务器IP>:8080/imgs/...` | Nginx 直出，无业务开销 |
| 📖 **API 在线接口文档** | `http://<服务器IP>:8080/doc.html` | Knife4j 调试接口文档 |
| 📊 **Kibana 控制台** | `http://<服务器IP>:5601` | ES 数据可视化看板 |

---

## 👥 默认演示与测试账号

数据库首次启动时已自动导入初始种子数据（`src/main/resources/social_platform.sql`）：

| 账号（用户名） | 默认密码 | 角色身份 | 权限说明 |
|---|---|---|---|
| **`li`** | `123456` | **管理员 (ROLE_ADMIN)** | 拥有系统全量管理权限、封禁用户、指派审核员 |
| **`dufu`** | `123456` | 普通用户 (ROLE_USER) | 发帖、评论、互动、关注 |
| **`wangwei`** | `123456` | 普通用户 (ROLE_USER) | 发帖、评论、互动、关注 |
| **`baijvyi`** | `123456` | 普通用户 (ROLE_USER) | 发帖、评论、互动、关注 |
| **`libai`** | `123456` | 普通用户 (ROLE_USER) | 发帖、评论、互动、关注 |

---

## 💻 本地日常开发模式（混合模式）

日常编码时，推荐使用 Docker 运行中间件，在本地 IDE（IntelliJ IDEA）中直接运行 Spring Boot 应用：

```bash
# 1. 启动基础设施中间件（不启动容器内的 sp-app）
docker compose up -d sp-mysql sp-redis sp-es sp-mongodb sp-kibana sp-nginx

# 2. 复制环境变量
cp .env.example .env

# 3. 宿主机运行 Spring Boot 应用（端口 8081）
./mvnw spring-boot:run
```

---

## 🔧 常见问题与排错指南（FAQ）

### Q1: 在其他电脑打开网页，上传图片后图片显示破图？
* **原因**：`.env` 中的 `BASE_URL` 仍保持默认的 `127.0.0.1`，导致图片地址生成为 `http://127.0.0.1:8080/imgs/...`，外部设备访问时会尝试向其自身请求图片。
* **解决**：在部署机器的 `.env` 中将 `BASE_URL` 改为 `http://<服务器真实公网或局域网IP>:8080/imgs`，然后重启应用容器：`docker compose restart sp-app`。

### Q2: 访问前端报跨域错误（CORS Policy Error）？
* **解决**：在 `.env` 中的 `CORS_ORIGIN` 加上您访问前端所使用的完整 URL（例如 `http://192.168.1.100:8080`），后端已支持 `addAllowedOriginPattern` 通配匹配。

### Q3: Linux 服务器启动时 Elasticsearch 报错退出？
* **报错**：`max virtual memory areas vm.max_map_count [65530] is too low`
* **解决**：执行 `sudo sysctl -w vm.max_map_count=262144` 即可。

### Q4: 如何更新前端页面或后端代码？
* **更新前端**：重新打包生成 `dist` 后，直接覆盖到 `./html/` 目录，浏览器刷新即可，**无需重启任何容器**。
* **更新后端代码**：
  ```bash
  # 利用 BuildKit 极速增量重构应用镜像（仅打包应用层，秒级完成）
  DOCKER_BUILDKIT=1 docker compose up -d --build sp-app
  ```

### Q5: 上传大图被拦截（413 Payload Too Large）？
* **说明**：Nginx（`client_max_body_size 10M`）与 Spring Boot（`max-file-size: 10MB`）默认已配置为 10MB。若需更大，请同步修改 `nginx/conf.d/default.conf` 与 `src/main/resources/application.yaml`。

---

## 📂 项目工程目录

```text
social-platform/
├── Dockerfile               # 多阶段分层镜像构建（BuildKit 缓存加速 + 非 Root 安全运行）
├── docker-compose.yml       # 全套 7 大服务容器编排与健康检查配置
├── docker-entrypoint.sh     # 容器启动自愈脚本（解决 Linux 挂载权限并降权运行）
├── .dockerignore            # 构建上下文过滤（排除本地大文件）
├── .env.example             # 环境变量模版
├── nginx/                   # Nginx 配置文件（已纳入版本管理）
│   ├── nginx.conf           # 主配置文件（Gzip/日志/超时）
│   └── conf.d/default.conf  # 路由网关配置（SPA/直出/反代/WS）
├── html/                    # 宿主机挂载目录
│   ├── index.html           # 前端 SPA 首页（dist 产物）
│   ├── assets/              # 前端打包静态资源
│   └── imgs/                # 图片落盘存储目录（avatar/ 与 帖子封面）
├── elasticsearch/           # Elasticsearch Docker 构建目录（集成 IK 中文分词）
├── docs/                    # 详细设计与架构说明文档
└── src/                     # Spring Boot 源码与资源文件
```

---

## 📜 许可证

[MIT License](LICENSE)

