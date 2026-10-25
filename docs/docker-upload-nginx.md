# Docker × 文件上传 × MinIO × Nginx 逻辑说明

> 总结自当前代码库（`docker-compose.yml`、`UploadFileController`、`MinioConfig`、`MinioFileStorageService`、nginx 配置）。核心一句话：**应用负责把图片写入 MinIO 对象存储的 `social-platform` 桶，Nginx 把 `/imgs` 请求代理到 MinIO 以 `http://127.0.0.1:8080/imgs/...` 的 URL 直出给浏览器；前端页面仍由 Nginx 挂载宿主机 `html` 目录直出，URL 结构与旧的磁盘直出方案完全兼容，前端与数据库均无需改动。**

---

## 1. 总体架构：存储与挂载体系

系统持久化与文件共享分为三类：

1. **宿主机目录挂载（Bind Mount）**：用于前端静态资源（`./html`）及 Nginx 配置文件，支持热更新。
2. **具名数据卷（Named Volume）**：用于 MySQL、Redis、Elasticsearch、MongoDB、MinIO 等有状态中间件的数据持久化，保障容器重建时数据不丢失。
3. **对象存储（MinIO Bucket）**：用户上传图片（头像、帖子封面）统一存入 `social-platform` 桶，桶策略为公共读（匿名可下载），对象名沿用旧方案的两级 hash 分片路径。

### 1.1 MinIO 存储体系

```
docker-compose 服务：sp-minio（alpine/minio:RELEASE.2025-10-15T17-29-55Z，MinIO 官方社区镜像已从 Docker Hub 下架）
  ├── 9000 端口：S3 API（sp-app 上传/删除 + Nginx 代理直出）
  ├── 9001 端口：Web 管理控制台（仅宿主机回环可访问）
  ├── 数据卷：minio-data（Docker 卷标识 sp-minio-data）→ /data（由 sp-minio-volume-init 一次性修正属主）
  ├── 账号：MINIO_ROOT_USER（默认 minioadmin）/ MINIO_ROOT_PASSWORD（复用 .env 的 PASSWORD）
  └── 桶：social-platform（MINIO_BUCKET 可改），公共读策略（匿名 s3:GetObject）
```

| 服务/容器 | 连接方式 | 地址 | 作用 |
|---|---|---|---|
| **sp-app**（Spring Boot） | MinIO Java SDK | `http://sp-minio:9000`（容器网络） | 上传 `putObject` / 删除 `removeObject` / 启动建桶设策略 |
| **sp-nginx**（容器模式） | HTTP 反向代理 | `http://sp-minio:9000/social-platform/` | 生产模式图片直出 |
| **sp-nginx-dev**（开发模式） | HTTP 反向代理 | `http://host.docker.internal:9000/social-platform/` | 本地 IDEA 模式图片直出（宿主机 MinIO 回环端口） |
| **sp-minio-init**（一次性任务） | rclone | `http://sp-minio:9000` | 幂等迁移：rclone 拷贝存量 `html/imgs` 图片入桶（建桶与公共读策略由后端启动逻辑兜底） |

> [!NOTE]
> 本地 IDEA 开发模式下，Spring Boot 通过 `MINIO_ENDPOINT` 默认值 `http://127.0.0.1:9000` 访问 `sp-minio` 映射到宿主机回环的端口，无需任何额外配置。

### 1.2 中间件数据持久化体系（Named Volume）

`docker-compose.yml` 中统一定义了规范命名的 Named Volume，保障数据库与搜索引擎等组件的数据安全隔离与自动管理：

| 卷名称（Compose 别名） | Docker 内部卷标识（`name`） | 挂载的目标容器与路径 | 存储内容 |
|---|---|---|---|
| `mysql-data` | `sp-mysql-data` | `sp-mysql` → `/var/lib/mysql` | MySQL 8.0 表数据、索引与事务日志 |
| `redis-data` | `sp-redis-data` | `sp-redis` → `/data` | Redis AOF 与 RDB 持久化文件 |
| `es-data` | `sp-es-data` | `sp-es` → `/usr/share/elasticsearch/data` | Elasticsearch 索引数据分片 |
| `mongo-data` | `sp-mongodb-data` | `sp-mongodb` → `/data/db` | MongoDB 集合数据（AI 对话记忆） |
| `mongo-config` | `sp-mongo-config` | `sp-mongodb` → `/data/configdb` | MongoDB 内部配置元数据 |
| `minio-data` | `sp-minio-data` | `sp-minio` → `/data` | MinIO 桶数据（用户上传图片） |

> [!NOTE]
> 在全新机器上首次执行 `docker compose up -d` 时，Docker 会自动创建上述具名卷，并在 `sp-mysql` 首次初始化时自动执行 `./src/main/resources/social_platform.sql` 初始化脚本（通过只读 Bind Mount 挂载到 `/docker-entrypoint-initdb.d/init.sql`）。

---

## 2. 图片上传链路（写路径）

```
浏览器 → POST /api/upload/post 或 /api/upload/avatar（文件 + 参数）
  → Nginx :8080 location /api（rewrite 去掉 /api 前缀，proxy_pass 到 sp-app:8081）
    → Spring Boot :8081 UploadFileController.upload()
      → MinioFileStorageService.upload() → MinIO putObject(social-platform 桶)
```

### 2.1 应用内校验（`UploadFileController`）

| 校验项 | 规则 | 说明 |
|---|---|---|
| 空文件 | `image.isEmpty()` | 拦截空文件请求 |
| 大小限制 | 最大 **10MB**（`MAX_FILE_SIZE`） | 与 Nginx `client_max_body_size 10M` 完全对齐 |
| 扩展名白名单 | `jpg / jpeg / png / gif / bmp / webp` | 限制合法图片后缀 |
| 宽高比例 | 头像 **1:1**（±2% 容差），帖子图片无比例限制 | 仅在需校验比例时优先读取图片文件头（ImageReader）快速校验尺寸，避免完整解码的内存与 CPU 开销 |
| 启动自检 | `MinioConfig.initBucket()`（`@PostConstruct`） | 检查 MinIO 连通性，自动创建缺失桶并设置公共读策略，异常时输出明确 ERROR 日志 |

### 2.2 经典秒传与智能去重（SHA-256）

上传分两步走，命中即零传输：

1. **前端预检（经典秒传）**：图片经裁剪/压缩后，前端先计算 SHA-256（优先 `crypto.subtle`，非安全上下文回退纯 JS 实现），调用 `GET /upload/check?hash=...` 询问服务端；
2. **命中**：服务端已存在同哈希内容 → 直接补登一条复用记录（同 hash+userId+postId 已存在则幂等跳过）并返回已有 URL，**客户端跳过整个文件传输**；
3. **未命中**：客户端正常上传文件内容，服务端仍按哈希兜底去重：
   - 若 `file` 表中已存在 **同 hash + 同 userId + 同 postId** 记录 → 直接返回已有 URL（上传幂等）；
   - 若同 hash 但属于不同帖子或用户（例如多人上传同一张公用图）→ **存储对象不重复上传**，仅在 `file` 表新增一条记录并复用既有相对 URL。

> [!NOTE]
> SHA-256 前缀即对象名，因此从返回 URL 即可直观看出客户端哈希与服务端哈希是否一致（E2E 已验证两者完全一致）。

### 2.3 对象名与分片

```java
String name = sha256Hash.substring(0, 16);      // hash 前 16 位当作对象名
int d1 = name.hashCode() & 0xF;                 // 一级前缀 0~f (16个桶)
int d2 = (name.hashCode() >> 4) & 0xF;          // 二级前缀 0~f (16个桶)
// 数据库记录的相对 URL 格式：/{d1}/{d2}/{name}.{suffix}
// MinIO 对象名 = 相对 URL 去掉开头的 /，例如：9/a/ab12cd34ef567890.jpg
```

两级 16 进制前缀分片共形成 $16 \times 16 = 256$ 个逻辑分组，沿用旧磁盘方案的目录分片设计，保证数据库中历史 `url` 记录与 MinIO 对象名一一对应、无缝迁移。

### 2.4 上传与入库

- **存储层**：`FileStorageService` 接口抽象文件存储能力，当前实现 `MinioFileStorageService` 基于 MinIO Java SDK（`io.minio:minio`）：
  - `upload(objectKey, bytes, contentType)` → `putObject`（带文件 MIME 类型，浏览器可直接内联预览）；
  - `delete(objectKey)` → `removeObject`（幂等，对象不存在视为成功）。
- **连接配置**（`application.yaml` → `social-platform.minio`）：

| 配置项 | 环境变量 | 默认值 |
|---|---|---|
| `endpoint` | `MINIO_ENDPOINT` | `http://127.0.0.1:9000`（容器内覆写为 `http://sp-minio:9000`） |
| `access-key` | `MINIO_ACCESS_KEY` | `minioadmin` |
| `secret-key` | `MINIO_SECRET_KEY` | 复用 `PASSWORD` |
| `bucket` | `MINIO_BUCKET` | `social-platform` |

- **数据库记录**：向 MySQL `file` 表插入 `id / post_id / user_id / url / hash`（`url` 记录相对路径，如 `/9/a/ab12...jpg`，与旧方案完全一致）。
- **接口返回 URL**：`IMG_BASE_URL + url`，例如 `http://127.0.0.1:8080/imgs/9/a/ab12...jpg`。
  - `IMG_BASE_URL` 环境变量默认 `http://127.0.0.1:8080/imgs`，必须指向 **Nginx 端口**，客户端不直连 MinIO 或后端访问静态图片。

---

## 3. 图片访问链路（读路径）

```
浏览器请求：GET http://127.0.0.1:8080/imgs/9/a/ab12....jpg
  → Nginx 监听 8080 (容器内 80)
  → 命中 location /imgs/ { proxy_pass http://sp-minio:9000/social-platform/; }
  → Nginx 路径改写：/imgs/9/a/ab12....jpg → /social-platform/9/a/ab12....jpg
  → MinIO 返回对象（桶为公共读，无需签名）
  → Nginx 高速代理响应（带 Cache-Control 协商缓存控制响应头）
```

- **零业务开销**：图片读取由 Nginx 直接代理 MinIO，请求**完全不进入 Spring Boot 容器**；桶策略为匿名 `s3:GetObject`，代理无需携带签名。
- **缓存策略**：`/imgs/` 配置了 `expires -1` 与 `Cache-Control: no-cache, must-revalidate`，确保图片删除或替换后，浏览器每次均回源确认，防止出现脏缓存。

---

## 4. 删除链路与引用清理

| 接口 | 鉴权与执行逻辑 |
|---|---|
| `DELETE /upload/delete?url=...` | 1. 校验 URL 前缀属于 `IMG_BASE_URL`、拦截 `..` 非法对象名；<br>2. 校验 `file.user_id == 当前登录用户`；<br>3. 删除数据库记录，并基于引用计数清理对象：同 hash 无其他引用时才调用 `removeObject`。 |
| `DELETE /upload/delete/{postId}` | 1. 校验当前用户为帖子作者；<br>2. 删除该帖子在 `file` 表的所有记录；<br>3. **基于引用计数清理对象**：针对每张图的 hash 检查是否仍被其他帖子/用户引用，仅当 `count == 0` 时才真正执行 `removeObject`。 |

- 物理删除统一收敛到 `DeleteFileUtil.deleteFile(url)`：将相对 URL 转为对象名后删除 MinIO 对象；删除失败仅记录日志不中断业务流程（孤儿对象由自动清理任务兜底）。
- 删除完成后 Nginx 再次代理该图片请求将从 MinIO 拿到 404，结合 `no-cache` 机制，前端不会残留旧图片显示。

### 4.1 自动清理（FileCleanupTask 定时对账）

除业务触发的删除外，`FileCleanupTask`（启动 1 分钟后首轮，之后每 6 小时一轮）以**业务引用为事实源**与 MinIO 桶对账：

| 清理对象 | 判定条件 | 说明 |
|---|---|---|
| 孤儿对象 | 桶内存在但无任何业务引用，且超过宽限期（默认 24h） | 兜底"上传成功但入库失败、历史删除失败"等漏网场景 |
| 废弃草稿记录 | `file` 表中 post_id 非空但对应帖子不存在，且超过宽限期 | 发布页放弃/发布失败的封面与插图，连带清理无引用对象 |

- **合法引用**包括：`file` 表记录、帖子封面（`post.cover`）、用户头像（`user.avatar`）——后两类种子/历史数据直接指向 `/imgs` 对象而不经过上传链路；外部图床 URL（如 picsum.photos）自动忽略。
- **静态资源白名单**：`avatar/default.jpg`、`logo.png` 随仓库迁移入桶，永不清理。
- **宽限期保护**：按对象最后修改时间，默认 24 小时内的新对象一律不动，避免误伤"上传→入库""草稿编辑中"的正常流转窗口（`MINIO_CLEANUP_GRACE_HOURS` 可配，`MINIO_CLEANUP_ENABLED=false` 可关闭）。
- **一致性告警**：业务引用指向但桶内缺失的对象仅记录 WARN 日志，不做自动修复。

---

## 5. 存量数据迁移（sp-minio-init）

旧版本图片直接落盘宿主机 `./html/imgs`，升级后由一次性任务 `sp-minio-init`（`rclone/rclone` 镜像，通过环境变量配置 MinIO S3 连接）自动迁移：

```bash
rclone mkdir sp:social-platform                      # 建桶（幂等）
rclone copy /imgs sp:social-platform --transfers 4   # 增量拷贝存量图片
```

- 任务挂载 `${HTML_PATH:-./html}/imgs`（只读），迁移完成后宿主机目录仅作备份保留，不再参与线上读写。
- `rclone copy` 为增量同步（按大小+修改时间跳过相同文件），重复执行安全；后端启动时的 `initBucket()` 也会兜底建桶与设置策略，两条路径互为幂等保障。
- 辅助任务 `sp-minio-volume-init` 在 MinIO 启动前把命名卷属主修正为容器内的 minio 用户（uid 100），解决 Docker 新建卷默认 root 属主导致的 `file access denied`。

---

## 6. Nginx 双环境关键配置

Nginx 配置已完整纳入 Git 仓库管理：
- `nginx/nginx.conf`：主配置（Gzip、连接数、`client_max_body_size 10M`）
- `nginx/conf.d/default.conf`：**生产 / 容器全量模式**（API 反代至 `sp-app:8081` 容器，图片代理至 `sp-minio:9000`）
- `nginx/conf.dev.d/default.conf`：**本地 IDEA 开发模式**（API 反代至 `host.docker.internal:8081` 宿主机，图片代理至 `host.docker.internal:9000`）

### 6.1 图片代理（生产 / 容器全量模式）

```nginx
# 路径映射：/imgs/<对象名> -> MinIO social-platform 桶/<对象名>，桶名需与 MINIO_BUCKET 保持一致
location /imgs/ {
    proxy_pass http://sp-minio:9000/social-platform/;
    expires -1;
    add_header Cache-Control "no-cache, must-revalidate";
}
```

> [!NOTE]
> `proxy_pass` 带 URI（`/social-platform/`）且 `location` 为前缀匹配时，Nginx 自动将匹配前缀 `/imgs/` 替换为 `/social-platform/`，无需额外 rewrite。

### 6.2 图片代理（本地 IDEA 开发模式）

```nginx
resolver 127.0.0.11 ipv6=off valid=10s;   # Docker 内置 DNS，动态解析 host.docker.internal
set $sp_minio "http://host.docker.internal:9000";

location /imgs/ {
    rewrite ^/imgs/(.*)$ /social-platform/$1 break;   # 变量形式的 proxy_pass 不做自动改写，需显式 rewrite
    proxy_pass $sp_minio;
    expires -1;
    add_header Cache-Control "no-cache, must-revalidate";
}
```

---

## 7. 两种运行模式对照

| 维度 | 纯 Docker 容器化模式（生产部署 / 多机部署） | 本地混合开发模式（IntelliJ IDEA 调试） |
|---|---|---|
| **Spring Boot 运行位置** | `sp-app` 容器（Dockerfile 多阶段镜像） | 本地 IDEA / `./mvnw spring-boot:run`（端口 `8081`） |
| **Nginx 服务名称** | `sp-nginx`（依赖 `sp-app`） | `sp-nginx-dev`（配置 `profiles: ["dev"]`） |
| **Nginx 配置文件** | `nginx/conf.d/default.conf` | `nginx/conf.dev.d/default.conf` |
| **API upstream** | `sp-app:8081`（Docker 容器网络） | `host.docker.internal:8081`（宿主机网络） |
| **图片代理目标** | `sp-minio:9000/social-platform/` | `host.docker.internal:9000/social-platform/` |
| **后端 MinIO 地址** | `MINIO_ENDPOINT=http://sp-minio:9000` | 默认 `http://127.0.0.1:9000` |
| **启动命令** | `docker compose up -d` | `docker compose up -d sp-mysql sp-redis sp-es sp-mongodb sp-minio sp-kibana sp-nginx-dev` |
| **前端访问入口** | `http://<服务器IP>:8080` | `http://localhost:8080` |
| **`IMG_BASE_URL` 配置** | `http://<服务器IP/域名>:8080/imgs` | `http://127.0.0.1:8080/imgs` |
| **图片存储位置** | MinIO `minio-data` 具名卷 | 同左（共用 sp-minio 容器） |
| **权限管理** | 容器内进程无磁盘写权限需求，彻底规避 Bind Mount 赋权问题 | 同左 |

---

## 8. 部署与配置检查清单（防踩坑必读）

1. **存量图片迁移**：
   - 老版本部署升级后，首次 `docker compose up -d` 会自动运行 `sp-minio-init`，将宿主机 `./html/imgs` 下的历史图片（含 `avatar/default.jpg` 默认头像）迁移进 MinIO 桶；任务显示 `Exited (0)` 即成功。
   - 迁移幂等可重复：手动补跑 `docker compose up sp-minio-init` 即可。
2. **`MINIO_ROOT_USER` / 密码**：
   - 密码复用 `.env` 的 `PASSWORD`；修改后需同步重建 MinIO 数据卷（`docker compose down -v`，会清空全部数据）。
   - MinIO 端口（9000/9001）仅绑定宿主机回环地址，公网无法直接访问，图片统一走 Nginx 8080 代理直出。
3. **`IMG_BASE_URL` 配置与跨机访问（避免破图）**：
   - 默认值为 `http://127.0.0.1:8080/imgs`。
   - 若部署到公网或局域网服务器，**必须在 `.env` 中修改为服务器真实 IP 或域名**（如 `IMG_BASE_URL=http://192.168.1.100:8080/imgs`），否则客户端上传图片后获取到的 URL 为 `127.0.0.1`，在其他设备上访问会出现 404 破图。
4. **桶名一致性**：
   - 若通过 `MINIO_BUCKET` 自定义桶名，需同步修改 `nginx/conf.d/default.conf` 与 `nginx/conf.dev.d/default.conf` 两处 `proxy_pass` / `rewrite` 中的桶名。
5. **前端跨域 `CORS_ORIGIN`**：
   - 在 `.env` 中按需添加允许的前端域名/端口（如 `CORS_ORIGIN=http://192.168.1.100:5173,http://192.168.1.100:8080`）。
6. **具名数据卷生命周期**：
   - 数据库、Redis、ES、Mongo 与 MinIO 使用具名数据卷（如 `sp-mysql-data`、`sp-minio-data`），即使执行 `docker compose down` 重建容器，数据卷依然完好保留。
   - 如需彻底清空重置所有数据，需显式执行 `docker compose down -v`。
7. **上传大小对齐**：
   - Nginx（`client_max_body_size 10M`）与 Spring Boot（`max-file-size: 10MB`、`max-request-size: 10MB`）已完全对齐。若需扩大限制，需同步修改 `application.yaml`、`nginx/nginx.conf` 和 `nginx/conf.d/default.conf`。
