# Docker × 文件上传 × Nginx 逻辑说明

> 总结自当前代码库（`docker-compose.yml`、`Dockerfile`、`UploadFileController`、nginx 配置）。核心一句话：**应用负责把图片写入宿主机 `html/imgs` 目录，Nginx 负责把这个目录里的图片以 `http://127.0.0.1:8080/imgs/...` 的 URL 直接吐给浏览器，前端页面与图片文件均通过 Docker 的 Bind Mount 挂载机制实现宿主机与容器间的无缝共享与实时热更新。**

---

## 1. 总体架构：数据卷与目录挂载体系

在整个系统的持久化与文件共享架构中，主要分为两类挂载：

1. **宿主机目录挂载（Bind Mount）**：用于前端静态资源、用户上传图片及 Nginx 配置文件，支持宿主机与容器双向实时同步、热更新。
2. **具名数据卷（Named Volume）**：用于 MySQL、Redis、Elasticsearch、MongoDB 等有状态中间件的数据持久化，保障容器重建时数据不丢失。

### 1.1 前端与图片存储体系（Bind Mount）

宿主机项目根目录下的 `./html` 目录是前端页面和上传图片的**唯一事实存储**（前端源码仓库：[social_platform_vue](https://github.com/e69d8e/social_platform_vue)）：

```
<项目根目录>/html
├── index.html           ← 前端 SPA 单页应用入口（内置打包产物）
├── assets/              ← 前端打包生成的 js / css 静态资源
└── imgs/                ← 用户上传的图片（头像、帖子封面，按两级 hash 分片）
```

Docker Compose 对静态资源和图片的挂载关系如下：

| 服务/容器 | 挂载类型 | 宿主机路径 → 容器内路径 | 读写权限 | 作用 |
|---|---|---|---|---|
| **sp-app**（Spring Boot） | Bind Mount | `${HTML_PATH:-./html}/imgs` → `/usr/local/nginx/html/imgs` | 读写 (`rw`) | 后端接收图片上传后直接落盘到此目录 |
| **sp-nginx**（容器模式 Nginx） | Bind Mount | `${HTML_PATH:-./html}` → `/usr/share/nginx/html`<br>`./nginx/nginx.conf` → `/etc/nginx/nginx.conf:ro`<br>`./nginx/conf.d` → `/etc/nginx/conf.d:ro` | 读写 (`rw`) / 只读 (`ro`) | 生产与全容器模式：托管前端与图片，反代至 `sp-app:8081` 容器 |
| **sp-nginx-dev**（开发模式 Nginx） | Bind Mount | `${HTML_PATH:-./html}` → `/usr/share/nginx/html`<br>`./nginx/nginx.conf` → `/etc/nginx/nginx.conf:ro`<br>`./nginx/conf.dev.d` → `/etc/nginx/conf.d:ro` | 读写 (`rw`) / 只读 (`ro`) | 本地 IDEA 开发模式：托管前端与图片，反代至 `host.docker.internal:8081` |

#### 目录共享与挂载拓扑图

```
宿主机 /Users/li/Code/.../social-platform
│
├── ./html/
│   ├── index.html / assets/ ──┐
│   └── imgs/ ──────┬──────────┼────────────────────────────────────────────────────────┐
│                   │          │                                                        │
├── sp-app 容器 (8081)         │                                                        │
│     └── /usr/local/nginx/html/imgs (写图片)                                           │
│           ↑                                                                           │
│           └── [Bind Mount] ./html/imgs                                                │
│                                                                                       │
├── 【容器模式】sp-nginx 容器 (8080→80)                                                  │
│     ├── 挂载 ./nginx/conf.d (upstream sp-app:8081)                                    │
│     └── /usr/share/nginx/html (读前端 / 读图片) ───────────────────────────────────────┤
│                                                                                       │
└── 【本地模式】sp-nginx-dev 容器 (8080→80)                                              │
      ├── 挂载 ./nginx/conf.dev.d (upstream host.docker.internal:8081)                   │
      └── /usr/share/nginx/html (读前端 / 读图片) ───────────────────────────────────────┘
```

**关键设计优势**：
- **双 Nginx 环境无缝切换**：全容器模式（`sp-nginx`）反代容器内部 `sp-app`，本地开发模式（`sp-nginx-dev`）反代宿主机 IDEA 进程（`host.docker.internal:8081`），端口（`8080`）与业务路径保持 100% 一致，前端完全无感。
- **零拷贝与实时生效**：无论应用运行在容器还是本地 IDEA，图片均直接落盘在宿主机的 `./html/imgs`，Nginx 挂载的 `/usr/share/nginx/html/imgs` 瞬间可见，无需任何文件拷贝或网络同步。
- **前端热部署**：前端构建产物（`dist` 中的文件）直接放入宿主机 `./html` 目录即可即时生效，无需重启 Nginx 容器。
- **后端隔离与最小权限**：`sp-app` 只挂载 `./html/imgs` 子目录，无需也无法访问前端 HTML/JS 静态代码，职责清晰安全。

---

### 1.2 中间件数据持久化体系（Named Volume）

`docker-compose.yml` 中统一定义了规范命名的 Named Volume，保障数据库与搜索引擎等组件的数据安全隔离与自动管理：

| 卷名称（Compose 别名） | Docker 内部卷标识（`name`） | 挂载的目标容器与路径 | 存储内容 |
|---|---|---|---|
| `mysql-data` | `sp-mysql-data` | `sp-mysql` → `/var/lib/mysql` | MySQL 8.0 表数据、索引与事务日志 |
| `redis-data` | `sp-redis-data` | `sp-redis` → `/data` | Redis AOF 与 RDB 持久化文件 |
| `es-data` | `sp-es-data` | `sp-es` → `/usr/share/elasticsearch/data` | Elasticsearch 索引数据分片 |
| `mongo-data` | `sp-mongodb-data` | `sp-mongodb` → `/data/db` | MongoDB 集合数据（AI 对话记忆） |
| `mongo-config` | `sp-mongo-config` | `sp-mongodb` → `/data/configdb` | MongoDB 内部配置元数据 |

> [!NOTE]
> 在全新机器上首次执行 `docker compose up -d` 时，Docker 会自动创建上述 5 个具名卷，并在 `sp-mysql` 首次初始化时自动执行 `./src/main/resources/social_platform.sql` 初始化脚本（通过只读 Bind Mount 挂载到 `/docker-entrypoint-initdb.d/init.sql`）。

---

## 2. 图片上传链路（写路径）

```
浏览器 → POST /api/upload/post 或 /api/upload/avatar（文件 + 参数）
  → Nginx :8080 location /api（rewrite 去掉 /api 前缀，proxy_pass 到 sp-app:8081）
    → Spring Boot :8081 UploadFileController.upload()
```

### 2.1 应用内校验（`UploadFileController`）

| 校验项 | 规则 | 说明 |
|---|---|---|
| 空文件 | `image.isEmpty()` | 拦截空文件请求 |
| 大小限制 | 最大 **10MB**（`MAX_FILE_SIZE`） | 与 Nginx `client_max_body_size 10M` 完全对齐 |
| 扩展名白名单 | `jpg / jpeg / png / gif / bmp / webp` | 限制合法图片后缀 |
| 宽高比例 | 头像 **1:1**（±2% 容差），帖子图片无比例限制 | 仅在需校验比例时优先读取图片文件头（ImageReader）快速校验尺寸，避免完整解码的内存与 CPU 开销 |
| 启动自检 | `@PostConstruct initUploadDir()` | 检查 `imageUploadDir`（即 `${HTML_PATH}/imgs`）目录是否存在且具备写权限，异常时输出明确 ERROR 日志 |

### 2.2 智能去重（SHA-256）

1. 计算上传图片二进制内容的 **SHA-256** 哈希值；
2. 若 `file` 表中已存在 **同 hash + 同 userId + 同 postId** 记录 → 直接返回已有 URL（上传幂等）；
3. 若同 hash 但属于不同帖子或用户（例如多人上传同一张公用图）→ **物理文件不重复存储**，仅在 `file` 表新增一条记录并复用既有相对 URL；
4. 若为全新 hash → 生成两级分片文件名并物理落盘。

### 2.3 文件名与目录分片

```java
String name = sha256Hash.substring(0, 16);      // hash 前 16 位当作文件名
int d1 = name.hashCode() & 0xF;                 // 一级目录 0~f (16个桶)
int d2 = (name.hashCode() >> 4) & 0xF;          // 二级目录 0~f (16个桶)
// 文件相对路径格式：/{d1}/{d2}/{name}.{suffix}
// 例如：/9/a/ab12cd34ef567890.jpg
```

两级 16 进制目录分片共形成 $16 \times 16 = 256$ 个子目录桶，有效防止单目录下文件过多引起的 Linux 文件系统 inode 检索性能瓶颈。

### 2.4 落盘与入库

- **物理存储路径**：通过 `application.yaml` 读取 `${IMAGE_PATH:${HTML_PATH:./html}/imgs}`，默认直接落盘到 `${HTML_PATH}/imgs` 目录。
  - **Docker 容器环境**：Compose 配置为容器内 `/usr/local/nginx/html/imgs` → 经 Bind Mount 落到宿主机 `${HTML_PATH:-./html}/imgs`。
  - **本地开发环境**：`.env` 中配置 `HTML_PATH=./html`（或自定义路径） → 后端自动将图片写入 `${HTML_PATH}/imgs` 宿主机目录，无需单独配置 `IMAGE_PATH`。
- **数据库记录**：向 MySQL `file` 表插入 `id / post_id / user_id / url / hash`（`url` 记录相对路径，如 `/9/a/ab12...jpg`）。
- **接口返回 URL**：`BASE_URL + url`，例如 `http://127.0.0.1:8080/imgs/9/a/ab12...jpg`。
  - `BASE_URL` 环境变量默认 `http://127.0.0.1:8080/imgs`，必须指向 **Nginx 端口**，客户端不直连后端访问静态图片。

---

## 3. 图片访问链路（读路径）

```
浏览器请求：GET http://127.0.0.1:8080/imgs/9/a/ab12....jpg
  → Nginx 监听 8080 (容器内 80)
  → 命中 location /imgs { root /usr/share/nginx/html; try_files $uri =404; }
  → Nginx 拼接路径：/usr/share/nginx/html + /imgs/9/a/ab12....jpg
  → 经 Bind Mount 读取宿主机 ./html/imgs/9/a/ab12....jpg
  → 高速直接响应（带 Cache-Control 协商缓存控制响应头）
```

- **零业务开销**：图片读取完全由 Nginx 静态内核处理（`sendfile on;`），请求**完全不进入 Spring Boot 容器**。
- **缓存策略**：`/imgs` 配置了 `expires -1` 与 `Cache-Control: no-cache, must-revalidate`，确保图片删除或替换后，浏览器每次均回源确认，防止出现脏缓存。

---

## 4. 删除链路与引用清理

| 接口 | 鉴权与执行逻辑 |
|---|---|
| `DELETE /upload/delete?url=...` | 1. 校验 URL 前缀属于 `BASE_URL`、防止 `..` 路径遍历、校验规范化路径（Canonical Path）处于允许的图片根目录下；<br>2. 校验 `file.user_id == 当前登录用户`；<br>3. 物理删除文件并删除数据库记录。 |
| `DELETE /upload/delete/{postId}` | 1. 校验当前用户为帖子作者；<br>2. 删除该帖子在 `file` 表的所有记录；<br>3. **基于引用计数清理物理文件**：针对每张图的 hash 检查是否仍被其他帖子/用户引用，仅当 `count == 0` 时才真正执行物理删除（`FileUtil.del`）。 |

由于物理文件位于宿主机 `./html/imgs` 目录，删除完成后 Nginx 再次收到该图片请求将立即返回 404，结合 `no-cache` 机制，前端不会残留旧图片显示。

---

## 5. Nginx 双环境完整配置说明

Nginx 配置已完整纳入 Git 仓库管理，提供两种开箱即用的环境配置：
- `nginx/nginx.conf`：主配置（Gzip、连接数、`client_max_body_size 10M`）
- `nginx/conf.d/default.conf`：**生产 / 容器全量模式**（反向代理至 `sp-app:8081` 容器）
- `nginx/conf.dev.d/default.conf`：**本地 IDEA 开发模式**（反向代理至 `host.docker.internal:8081` 宿主机）

### 5.1 生产/容器全量配置（`nginx/conf.d/default.conf`）
```nginx
upstream sp-app {
    server sp-app:8081;
    keepalive 32;
}

server {
    listen 80;
    server_name localhost;
    charset utf-8;

    client_max_body_size 10M;

    # 前端 SPA 静态页面
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 图片静态直出（直接通过 Nginx 高速响应，不经过后端）
    location /imgs {
        root /usr/share/nginx/html;
        try_files $uri =404;
        expires -1;
        add_header Cache-Control "no-cache, must-revalidate";
    }

    # 后端 REST 接口代理（去 /api 前缀，转容器 sp-app）
    location /api {
        rewrite /api(/.*) $1 break;
        proxy_pass http://sp-app;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        client_max_body_size 10M;
    }

    # WebSocket 实时消息代理
    location /ws {
        proxy_pass http://sp-app;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    # Knife4j API 文档支持
    location ~* ^/(doc\.html|webjars|v3/api-docs|swagger-resources) {
        proxy_pass http://sp-app;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_http_version 1.1;
    }
}
```

### 5.2 本地 IDEA 开发配置（`nginx/conf.dev.d/default.conf`）
```nginx
server {
    listen 80;
    server_name localhost;
    charset utf-8;

    client_max_body_size 10M;

    # Docker 内置 DNS 动态解析，避免 Nginx 在启动时因同步解析 host.docker.internal 失败而崩溃
    resolver 127.0.0.11 ipv6=off valid=10s;
    set $sp_backend "http://host.docker.internal:8081";

    # 前端 SPA 静态页面
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 图片静态直出（直接读取宿主机挂载的 ./html/imgs）
    location /imgs {
        root /usr/share/nginx/html;
        try_files $uri =404;
        expires -1;
        add_header Cache-Control "no-cache, must-revalidate";
    }

    # 后端 REST 接口代理（去 /api 前缀，转宿主机本地 IDEA）
    location /api {
        rewrite ^/api/?(.*)$ /$1 break;
        proxy_pass $sp_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        client_max_body_size 10M;
    }

    # WebSocket 实时消息代理（转宿主机本地 IDEA）
    location /ws {
        proxy_pass $sp_backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    # Knife4j API 文档支持
    location ~* ^/(doc\.html|webjars|v3/api-docs|swagger-resources) {
        proxy_pass $sp_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_http_version 1.1;
    }
}
```

---

## 6. 两种运行模式对照

| 维度 | 纯 Docker 容器化模式（生产部署 / 多机部署） | 本地混合开发模式（IntelliJ IDEA 调试） |
|---|---|---|
| **Spring Boot 运行位置** | `sp-app` 容器（Dockerfile 多阶段镜像） | 本地 IDEA / `./mvnw spring-boot:run`（端口 `8081`） |
| **Nginx 服务名称** | `sp-nginx`（依赖 `sp-app`） | `sp-nginx-dev`（配置 `profiles: ["dev"]`） |
| **Nginx 配置文件** | `nginx/conf.d/default.conf` | `nginx/conf.dev.d/default.conf` |
| **Nginx upstream** | `sp-app:8081`（Docker 容器网络） | `host.docker.internal:8081`（宿主机网络） |
| **启动命令** | `docker compose up -d` | `docker compose up -d sp-mysql sp-redis sp-es sp-mongodb sp-kibana sp-nginx-dev` |
| **前端访问入口** | `http://<服务器IP>:8080` | `http://localhost:8080` |
| **`HTML_PATH` 配置** | `${HTML_PATH:-./html}`（挂载点 `${HTML_PATH}/imgs`） | 宿主机本地路径，如 `./html`（图片自动存入 `./html/imgs`） |
| **`BASE_URL` 配置** | `http://<服务器IP/域名>:8080/imgs` | `http://127.0.0.1:8080/imgs` |
| **图片落盘机制** | 经 Bind Mount 写入宿主机 `${HTML_PATH:-./html}/imgs` | IDEA 后端直接写入宿主机 `${HTML_PATH}/imgs` |
| **图片读取机制** | `sp-nginx` 经 Bind Mount 读取 `${HTML_PATH:-./html}/imgs` | `sp-nginx-dev` 经 Bind Mount 读取 `${HTML_PATH:-./html}/imgs` |
| **权限管理** | `docker-entrypoint.sh` + `su-exec spring` 启动自愈赋权 | 宿主机当前登录用户原生文件读写权限 |

---

## 7. 部署与数据卷配置检查清单（防踩坑必读）

1. **宿主机挂载目录写权限自愈机制**：
   - 在 Linux 环境中，Docker Bind Mount 创建宿主机目录时默认所有者可能为 `root`。
   - `Dockerfile` 与 `docker-entrypoint.sh` 内置了 `su-exec`，容器启动阶段自动以 root 执行 `mkdir -p /usr/local/nginx/html/imgs` 并 `chown -R spring:spring`，随后以非 root 用户 `spring` 启动应用，彻底解决 `Permission Denied` 异常。
2. **`BASE_URL` 配置与跨机访问（避免破图）**：
   - 默认值为 `http://127.0.0.1:8080/imgs`。
   - 若部署到公网或局域网服务器，**必须在 `.env` 中修改为服务器真实 IP 或域名**（如 `BASE_URL=http://192.168.1.100:8080/imgs`），否则客户端上传图片后获取到的 URL 为 `127.0.0.1`，在其他设备上访问会出现 404 破图。
3. **前端跨域 `CORS_ORIGIN`**：
   - 在 `.env` 中按需添加允许的前端域名/端口（如 `CORS_ORIGIN=http://192.168.1.100:5173,http://192.168.1.100:8080`）。
4. **具名数据卷生命周期**：
   - 数据库、Redis、ES 和 Mongo 使用具名数据卷（如 `sp-mysql-data`），即使执行 `docker compose down` 重建容器，数据卷依然完好保留。
   - 如需彻底清空重置所有数据，需显式执行 `docker compose down -v`。
5. **上传大小对齐**：
   - Nginx（`client_max_body_size 10M`）与 Spring Boot（`max-file-size: 10MB`、`max-request-size: 10MB`）已完全对齐。若需扩大限制，需同步修改 `application.yaml`、`nginx/nginx.conf` 和 `nginx/conf.d/default.conf`。