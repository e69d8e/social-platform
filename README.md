# Y 社区 — 社交平台后端

基于 Spring Boot 3 + Java 21 的全功能社交互动平台，支持帖子发布、关注流、点赞评论、私信聊天、全文检索、AI 智能助手（DeepSeek）与图片分片存储。

- **后端仓库**：[social-platform](https://github.com/e69d8e/social-platform) (当前项目)
- **前端项目**：[social_platform_vue](https://github.com/e69d8e/social_platform_vue) (Vue 3 + Vite + Element Plus + Pinia)

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

> 🌟 **交互式架构全景图**：项目内置了由 Archify 渲染的**高层运行时架构交互式全景图**，直接在浏览器中打开本地文件 [`docs/architecture.html`](docs/architecture.html) 即可体验：
> - 🎨 **多主题与视觉预设**：深色 / 浅色主题无缝切换，内置「经典 (Classic)」、「信号流 (Signal Flow)」、「工程蓝图 (Blueprint)」与「现场笔记 (Editorial)」4 种视觉渲染预设（按快捷键 `S` 循环切换）。
> - 🧭 **引导故事视图 (Guided Views)**：内置 4 大业务引导章节（按 `[` / `]` 切换或 `P` 键自动播放演示）。
> - 🔍 **语义雷达与路径探测**：支持任意节点检索（`/`）、组件间有向调用路径追踪（`R`）、语义透镜分类比对（`L`）与全景雷达地图（`M`）。
> - 📐 **高清矢量导出**：支持一键导出高分辨率 PNG、无损 SVG 矢量图及 6 秒 WebM 动效。

### 运行时架构拓扑

```mermaid
flowchart TB
    subgraph ClientLayer["客户端与外部依赖"]
        Client["Web 客户端<br/>(Vue 3 SPA)"]
        DeepSeek["DeepSeek API<br/>(云端 LLM 推理服务)"]
    end

    subgraph DockerNet["Docker 容器编排网络 (sp bridge / 私有隔离网络)"]
        subgraph GatewayGroup["统一网关与入口层"]
            Nginx["Nginx 网关 (:8080 / :80)<br/>反向代理 & 静态资源直出"]
            WS["WebSocket 消息网关<br/>(STOMP Broker :8081)"]
        end

        subgraph AppBoundary["应用核心与安全边界 (Internal :8081)"]
            Security["安全与认证中心<br/>(Spring Security + JJWT + 滑动窗口限流)"]
            App["社交核心服务<br/>(Spring Boot 3 + MyBatis-Plus)"]
            AIAgent["AI 智能助理<br/>(LangChain4j 编排)"]
        end

        subgraph DataCluster["数据持久化与检索集群"]
            MySQL[("MySQL 8.0<br/>主关系型数据库 :3306")]
            Redis[("Redis 6.0<br/>分布式缓存 & 位图 :6379")]
            ES[("Elasticsearch 9.2<br/>全文检索 + IK 分词 :9200")]
            Mongo[("MongoDB 8.0<br/>AI 会话记忆库 :27017")]
        end
    end

    Client -->|"HTTPS / WS"| Nginx
    Nginx -->|"REST 代理 (/api)"| App
    Nginx -->|"WS 升级连接 (/ws)"| WS
    App -->|"安全过滤与鉴权"| Security
    Security -->|"黑名单 / 滑动窗口"| Redis
    App -->|"SQL 读写 (Lambda CRUD)"| MySQL
    App -->|"IK 中文检索 / 高亮"| ES
    App -->|"对话调度"| AIAgent
    AIAgent -.->|"SSE 流式推理"| DeepSeek
    AIAgent -->|"多轮上下文持久化"| Mongo
```

### 核心架构子系统与数据流向

1. **核心业务主路径 (Core Request Path)**
   - **请求链路**：`Web 客户端 (Vue 3 SPA)` $\rightarrow$ `Nginx 网关 (:8080)` $\rightarrow$ `社交核心服务 (:8081)` $\rightarrow$ `MySQL 8.0`。
   - **业务职责**：客户端 SPA 发起 RESTful 请求，由 Nginx 反向代理转发至后端；MyBatis-Plus 全 Lambda 条件构造器完成核心表（用户、帖子、评论、关注）的高性能 CRUD；首页个性化推荐引擎结合用户兴趣评分矩阵与多维时间/热度衰减算法，实时计算推荐流。

2. **安全防护与高速缓存 (Security & Cache System)**
   - **防御链路**：`应用入口` $\rightarrow$ `Spring Security 拦截链` $\rightarrow$ `Redis 6.0`。
   - **安全职责**：基于 JWT 双 Token（Access Token / Refresh Token）实现无状态鉴权，结合 Redis 实现黑名单快速吊销与滑动延期；集成行为滑块验证码阻断恶意撞库；自定义 `@RateLimit` 注解配合 Redis ZSet 滑动窗口算法，在 AOP 切面层实现防刷与接口限流。

3. **网关分发与实时通信 (Gateway & Realtime Messaging)**
   - **通信链路**：`Web 客户端` $\leftrightarrow$ `Nginx 网关` $\leftrightarrow$ `WebSocket STOMP Broker` $\rightarrow$ `社交核心服务`。
   - **分发职责**：Nginx 挂载宿主机静态目录直出 Vue 3 前端产物与用户上传图片（`/imgs`），实现零业务容器开销；支持 STOMP over WebSocket 协议，Nginx 维持 HTTP `Upgrade` 长连接，实现毫秒级点对点私信推送与未读数实时同步；图片上传经 SHA-256 哈希计算实现物理去重与两级 16 进制目录分片存储。

4. **智能 AI 与全文检索 (AI & Search Infrastructure)**
   - **数据链路**：`社交核心服务` $\rightarrow$ `AI 智能助理 (LangChain4j)` $\leftrightarrow$ `DeepSeek API` / `MongoDB 8.0` / `Elasticsearch 9.2`。
   - **检索与智能**：基于 LangChain4j 编排 DeepSeek-V4-Flash 大模型，利用 SSE（Server-Sent Events）打字机流式输出回答并异步提炼会话标题；多轮对话上下文与历史记录持久化至 MongoDB 文档数据库；Elasticsearch 9.2 集成 IK 中文分词插件，提供帖子正文、标签与用户名的中文模糊匹配与关键词高亮搜索。

---

## 🚀 跨机器 / 服务器一键部署指南

本项目已实现 **100% 容器化自动编排**，并针对在全新电脑、云服务器（Linux / macOS / Windows）部署进行了全自动权限自愈与挂载适配。

### 1. 前置准备

- 已安装 **Docker** 与 **Docker Compose**（推荐 Docker Desktop 4.0+ 或 Docker Engine 24.0+）。
- **调整虚拟内存区域限制（满足 Elasticsearch 启动要求）**：
  - **Linux 服务器**：
    ```bash
    sudo sysctl -w vm.max_map_count=262144
    # 永久生效：
    echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf && sudo sysctl -p
    ```
  - **Windows (Docker Desktop / WSL2)**：
    在 PowerShell (管理员) 中执行：
    ```powershell
    wsl -d docker-desktop sysctl -w vm.max_map_count=262144
    ```
- **国内网络加速配置（避免拉取基础镜像时报 `failed to copy: ... EOF` 网络中断）**：
  - 打开 **Docker Desktop** -> 右上角 ⚙️ **Settings** -> **Docker Engine**，在 JSON 配置中加入或更新镜像加速源（或在 **Proxies** 中配置本地网络代理）：
    ```json
    {
      "registry-mirrors": [
        "https://docker.m.daocloud.io",
        "https://dockerproxy.com",
        "https://docker.1panel.live"
      ]
    }
    ```
    配置后点击右下角 **Apply & restart** 生效。

- **云服务器防火墙 / 安全组放行端口**：
  - `8080`：Nginx 门户（前端、接口、图片、WebSocket）
  - `5601`：（可选）Kibana 控制台
  - `8081`：（可选）Spring Boot 直连端口

---

### 2. 部署步骤

#### 步骤 1：克隆代码仓库
```bash
git clone git@github.com:e69d8e/social-platform.git
cd social-platform
```

#### 步骤 2：生成并配置环境变量
- **Linux / macOS**：
  ```bash
  cp .env.example .env
  vim .env
  ```
- **Windows (PowerShell)**：
  ```powershell
  Copy-Item .env.example .env
  notepad .env
  ```
- **Windows (CMD)**：
  ```cmd
  copy .env.example .env
  notepad .env
  ```

`.env` 核心配置说明：
```env
# 必填项：数据库与 Redis 密码（如 123456）
PASSWORD=your_password_here

# 必填项：DeepSeek API Key
DEEPSEEK_API_KEY=your_deepseek_api_key_here

# 图片上传落盘物理路径（本地 IDEA 开发保持默认相对路径 ./html/imgs；全容器部署由 compose 自动指定）
IMAGE_PATH=./html/imgs

# ⚠️ 远程/局域网部署必填：图片访问地址（本地单机测试保持 127.0.0.1 即可；跨机/服务器部署请填真实服务器 IP）
BASE_URL=http://127.0.0.1:8080/imgs

# 允许跨域的前端地址（支持逗号分隔多个地址）
CORS_ORIGIN=http://127.0.0.1:5173,http://127.0.0.1:8080,http://localhost:5173,http://localhost:8080
```

#### 步骤 3：前端资源说明（开箱即用，已内置）
> 💡 **已内置打包产物**：项目代码库中的 `html/` 目录下**已经自带完整编译打包好的前端静态资源**（包含 `html/index.html` 与 `html/assets/` 下的全部 JS/CSS 生产文件）。克隆项目后**无需安装 Node.js / pnpm 或执行任何前端构建命令**，启动 Docker 后即可直接通过浏览器访问完整功能的前端页面！

若后续您需要对前端进行二次开发或定制，可前往前端源码仓库：[social_platform_vue](https://github.com/e69d8e/social_platform_vue)，修改后将编译生成的 `dist/` 产物覆盖至宿主机 `./html/` 目录下，刷新浏览器即可实时热更新生效：
```text
html/
├── index.html           # 前端 SPA 页面入口（已内置打包产物）
├── assets/              # 前端 JS/CSS 静态资源库（已内置打包产物）
└── imgs/                # 用户上传图片落盘存储目录
```

#### 步骤 4：一键构建并后台启动所有服务
- **Linux / macOS**：
  ```bash
  DOCKER_BUILDKIT=1 docker compose up -d --build
  ```
- **Windows (PowerShell)**：
  ```powershell
  $env:DOCKER_BUILDKIT=1; docker compose up -d --build
  # 或者直接运行（Docker Desktop 默认已启用 BuildKit）：
  docker compose up -d --build
  ```
- **Windows (CMD)**：
  ```cmd
  set DOCKER_BUILDKIT=1 && docker compose up -d --build
  ```

> 💡 **提示**：加上 `--build` 参数会在拉取公共镜像的同时直接在本地构建 `sp-app` 和 `sp-es`，避免触发远端仓库拉取警告。

#### 步骤 5：查看各服务健康状态
```bash
docker compose ps
```
所有容器状态显示为 `Up` 或 `healthy` 即表示全套服务就绪。

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

日常编码与断点调试时，推荐使用 Docker 运行中间件，在本地 IDE（IntelliJ IDEA）中直接运行 Spring Boot 应用。

本项目内置了**双 Nginx 环境体系**，解决本地 IDEA 运行时 Nginx 无法跨容器转发到宿主机的问题：
- **`sp-nginx`（全容器化/生产模式）**：反代目标为 `sp-app:8081` 容器，默认随 `docker compose up -d` 启动。
- **`sp-nginx-dev`（本地 IDEA 开发模式）**：反代目标为 `host.docker.internal:8081`（宿主机本地 IDEA 运行的后端），无需修改前端代码或端口，前端页面（`8080`）、图片直出、WebSocket、API 文档均开箱即用。

### 本地开发标准启动流程：

```bash
# 1. 启动基础设施中间件 + 本地开发版 Nginx（反向代理至宿主机 IDEA）
docker compose up -d sp-mysql sp-redis sp-es sp-mongodb sp-kibana sp-nginx-dev

# 2. 复制环境变量（首次启动需配置）
cp .env.example .env

# 3. 宿主机在 IDEA 中直接点击 Debug/Run 运行 SocialPlatformApplication，或命令行运行：
# Linux / macOS:
./mvnw spring-boot:run
# Windows:
.\mvnw.cmd spring-boot:run
```

启动完成后，直接在浏览器访问 **`http://localhost:8080`** 即可进行全功能联调与断点调试！

> 💡 **本地调试与图片落盘机制**：
> - 本地开发时 `.env` 中的 `IMAGE_PATH=./html/imgs` 会指示 IDEA 运行的 Spring Boot 直接将图片存入当前工程下的 `./html/imgs` 目录。
> - `sp-nginx-dev` 容器挂载了宿主机的 `./html` 目录，上传完成后可通过 `http://localhost:8080/imgs/...` 即时直出查看，无需拷贝任何文件。

> 💡 **模式切换提示**：
> - 从全容器模式切到本地 IDEA 模式：`docker compose stop sp-app sp-nginx && docker compose up -d sp-nginx-dev`
> - 从本地 IDEA 模式切回全容器模式：`docker compose stop sp-nginx-dev && docker compose up -d sp-app sp-nginx`

---

## 🔧 常见问题与排错指南（FAQ）

### Q1: 拉取镜像时报 `failed to copy: httpReadSeeker: failed open... EOF` 或连接超时？
* **原因**：这是国内直连 Docker Hub 官方源网络波动、丢包或被限制引起的连接中断。
* **解决**：
  1. 在 Docker Desktop 设置中的 **Docker Engine** 配置国内加速镜像源（见前置准备章节），或在 **Settings -> Resources -> Proxies** 中配置本地网络代理。
  2. 也可尝试单独拉取耗时镜像重试，如：`docker pull mysql:8.0.46`、`docker pull mongo:8.0.16`、`docker pull redis:6.0.16`。

### Q2: 出现 `! sp-es Warning pull access denied for sp-es` 警告？
* **说明**：`sp-es` 是本项目本地基于 Dockerfile 构建的镜像（带 IK 中文分词插件），而非公网公共镜像。Docker Compose 启动时如果本地尚未生成该镜像，会尝试从 Docker Hub 查询并输出该提示，随后自动降级为本地构建。
* **解决**：使用 `docker compose up -d --build` 即可直接指示 Docker 在本地构建镜像，不会受到该警告影响。

### Q3: Windows 下执行命令报 `DOCKER_BUILDKIT=1 : 无法将...识别为 cmdlet`？
* **原因**：`KEY=VALUE command` 是 Linux/macOS Shell 语法。
* **解决**：
  - PowerShell 中使用：`$env:DOCKER_BUILDKIT=1; docker compose up -d --build` 或直接运行 `docker compose up -d --build`。
  - CMD 中使用：`set DOCKER_BUILDKIT=1 && docker compose up -d --build`。

### Q4: 在其他电脑打开网页，上传图片后图片显示破图？
* **原因**：`.env` 中的 `BASE_URL` 仍保持默认的 `127.0.0.1`，导致图片地址生成为 `http://127.0.0.1:8080/imgs/...`，外部设备访问时会尝试向其自身请求图片。
* **解决**：在部署机器的 `.env` 中将 `BASE_URL` 改为 `http://<服务器真实公网或局域网IP>:8080/imgs`，然后重启应用容器：`docker compose restart sp-app`。

### Q5: 访问前端报跨域错误（CORS Policy Error）？
* **解决**：在 `.env` 中的 `CORS_ORIGIN` 加上您访问前端所使用的完整 URL（例如 `http://192.168.1.100:8080`），后端已支持 `addAllowedOriginPattern` 通配匹配。

### Q6: Linux / WSL2 启动时 Elasticsearch 报错退出（`vm.max_map_count [65530] is too low`）？
* **Linux 解决**：执行 `sudo sysctl -w vm.max_map_count=262144`。
* **Windows WSL2 解决**：PowerShell 执行 `wsl -d docker-desktop sysctl -w vm.max_map_count=262144`。

### Q7: 如何更新前端页面或后端代码？
* **更新前端**：在前端项目 [social_platform_vue](https://github.com/e69d8e/social_platform_vue) 中修改后执行打包（`npm run build`），将生成的 `dist` 产物直接覆盖到本项目的 `./html/` 目录，浏览器刷新即可，**无需重启任何容器**。
* **更新后端代码**：
  ```bash
  # 利用 BuildKit 极速增量重构应用镜像（仅打包应用层，秒级完成）
  docker compose up -d --build sp-app
  ```

### Q8: 上传大图被拦截（413 Payload Too Large）？
* **说明**：Nginx（`client_max_body_size 10M`）与 Spring Boot（`max-file-size: 10MB`）默认已配置为 10MB。若需更大，请同步修改 `nginx/conf.d/default.conf` 与 `src/main/resources/application.yaml`。

### Q9: `sp-app` 报错 `exec ... docker-entrypoint.sh: no such file or directory`，同时 `nginx` 报错 `host not found in upstream "sp-app:8081"`？
* **根本原因**：在 Windows 上克隆代码时，Git 默认将 shell 脚本的换行符转换为了 Windows 格式（CRLF `\r\n`）。当容器执行 `#!/bin/sh\r` 时，Linux 无法识别包含 `\r` 的解释器路径，从而触发 `no such file or directory` 并退出；而 Nginx 启动时无法在网络中解析已崩溃退出的 `sp-app`，连带抛出 upstream 错误。
* **解决**：
  1. 本项目最新 `Dockerfile` 与 `.gitattributes` 已自动兼容并强制清理 CRLF 换行符。
  2. 重新构建启动即可：`docker compose up -d --build sp-app sp-nginx`。

---

## 📂 项目工程目录

```text
social-platform/
├── Dockerfile               # 多阶段分层镜像构建（BuildKit 缓存加速 + 非 Root 安全运行）
├── docker-compose.yml       # 全套 7 大服务容器编排与健康检查配置
├── docker-entrypoint.sh     # 容器启动自愈脚本（解决 Linux 挂载权限并降权运行）
├── .dockerignore            # 构建上下文过滤（排除本地大文件）
├── .env.example             # 环境变量模版
├── nginx/                   # Nginx 配置文件（双环境配置体系）
│   ├── nginx.conf           # 主配置文件（Gzip/日志/超时）
│   ├── conf.d/default.conf  # 容器生产模式路由网关（反代至 sp-app:8081 容器）
│   └── conf.dev.d/default.conf # 本地开发模式路由网关（反代至 host.docker.internal:8081 宿主机 IDEA）
├── html/                    # 宿主机挂载目录（前端静态文件与图片落盘）
│   ├── index.html           # 前端 SPA 首页（已内置打包产物，开箱即用）
│   ├── assets/              # 前端 JS/CSS 静态资源库（已内置打包产物）
│   └── imgs/                # 图片落盘存储目录（头像 avatar/ 与 帖子封面）
├── elasticsearch/           # Elasticsearch Docker 构建目录（集成 IK 中文分词）
├── docs/                    # 详细设计与架构说明文档
│   ├── architecture.html        # 高层运行时架构交互式图表（支持深浅色主题、蓝图/信号流模式、路径探索与动效导出）
│   ├── home-recommendation.md   # 首页推荐系统与推荐算法说明文档
│   ├── docker-upload-nginx.md   # Docker 数据卷绑定与图片上传架构说明
│   └── class-diagram.md         # 类图与领域模型关系
└── src/                     # Spring Boot 源码与资源文件
```

---

## 📜 许可证

[MIT License](LICENSE)

