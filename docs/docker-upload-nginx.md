# Docker × 文件上传 × Nginx 逻辑说明

> 总结自当前代码库（`docker-compose.yml`、`Dockerfile`、`UploadFileController`、nginx 配置）。核心一句话：**应用负责把图片写入宿主机 `html/imgs` 目录，Nginx 负责把这个目录里的图片以 `http://127.0.0.1:8080/imgs/...` 的 URL 直接吐给浏览器，中间经过 Docker 的 bind mount 把宿主机目录共享进 nginx 容器。**

---

## 1. 总体架构：一个目录，三处共享

所有图片（帖子封面、头像）的**唯一事实存储**是宿主机上的项目目录：

```
<项目根目录>/html/imgs   ← 图片真正存在这里（宿主机）
```

在这个目录周围，Docker 用 **两种挂载** 把它共享出去：

| 挂载类型 | 内容 | 作用 |
|---|---|---|
| **bind mount**（宿主机路径 → 容器路径） | `./html/imgs` → 容器内 imgs 目录 | sp-app 和 sp-nginx 两个容器**共享同一批文件** |
| **named volume**（`sp-nginx-html`） | nginx 的网页根目录（前端 dist + imgs） | 持久化前端静态文件，容器重建不丢 |

挂载关系图：

```
宿主机 /Users/li/Code/.../social-platform
│
├── sp-app 容器（8081，Spring Boot）
│     ├── /usr/local/nginx/html        ← sp-nginx-html 卷（前端文件）
│     └── /usr/local/nginx/html/imgs   ← bind mount → ./html/imgs（可写，写图片）
│
├── sp-nginx 容器（8080→80，nginx）
│     ├── /usr/share/nginx/html        ← sp-nginx-html 卷（前端 dist：assets/index.html）
│     └── /usr/share/nginx/html/imgs   ← bind mount → ./html/imgs（只读，读图片）
│
└── ./html/imgs（宿主机目录，双方共享）
```

关键点：sp-app 写的和 sp-nginx 读的是**同一个宿主机目录**，这也是为什么"应用存文件、nginx 出文件"能无缝衔接。

---

## 2. 图片上传链路（写路径）

```
浏览器 → POST /api/upload/post（文件 + postId）
  → nginx :8080  location /api（rewrite 去掉 /api 前缀，proxy_pass 到 sp-app）
    → Spring Boot :8081  UploadFileController.upload()
```

### 2.1 应用内校验（`UploadFileController`）

| 校验项 | 规则 |
|---|---|
| 空文件 | `image.isEmpty()` 拒绝 |
| 大小 | 最大 **10MB**（`MAX_FILE_SIZE`，与 nginx `client_max_body_size 10M` 一致） |
| 扩展名 | 白名单：`jpg / jpeg / png / gif / bmp / webp` |
| 宽高比 | 头像 **1:1**、帖子封面 **5:3**（±2% 容差），只读文件头解析尺寸 |
| 启动自检 | `@PostConstruct` 校验 IMAGE_PATH 目录存在且可写，失败仅记日志不阻塞启动 |

### 2.2 去重（SHA-256）

1. 计算文件内容的 **SHA-256** 哈希；
2. 若 `file` 表中已存在 **同 hash + 同 userId + 同 postId** 的记录 → 直接返回旧 URL（重复上传幂等）；
3. 若同 hash 但归属不同（如别人也传了同一张图）→ **物理文件不重复存储**，只往 `file` 表插入一条新记录（复用已有 URL）；
4. 全新文件 → 生成文件名并落盘。

### 2.3 文件名与目录分片

```java
String name = sha256Hash.substring(0, 16);      // hash 前 16 位当作文件名
int d1 = name.hashCode() & 0xF;                 // 一级目录 0~f
int d2 = (name.hashCode() >> 4) & 0xF;          // 二级目录 0~f
// 文件路径 = /{d1}/{d2}/{name}.{后缀}
```

两级 16 进制目录分片（每层 16 个桶、两层共 256 个），避免单目录下积压几十万张图。

### 2.4 落盘与入库

- 落盘目录：`IMAGE_PATH`（读取 application.yaml 的 `${IMAGE_PATH}`）
- **Docker 模式下** compose 覆盖为 `/usr/local/nginx/html/imgs` → 经 bind mount 落到宿主机 `./html/imgs`
- **当前本地混合模式**（应用跑在宿主机）`.env` 里 `IMAGE_PATH=/Users/li/Code/Java/JavaProjects/social-platform/html/imgs` → 直接写宿主机目录，殊途同归
- 同时在 MySQL `file` 表插一条记录：`id / post_id / user_id / url / hash`（url 为容器内相对路径，如 `/9/a/ab12...jpg`）
- 接口返回的完整 URL：`BASE_URL + url` = `http://127.0.0.1:8080/imgs/9/a/ab12....jpg`
  - `BASE_URL` 默认 `http://127.0.0.1:8080/imgs`（.env 可改，永远指向 **nginx 的 8080 端口**，而不是应用端口）

---

## 3. 图片访问链路（读路径）

```
浏览器 <img src="http://127.0.0.1:8080/imgs/9/a/ab12....jpg">
  → nginx :8080   location /imgs { root /usr/share/nginx/html; try_files $uri =404; }
    → 直接读 /usr/share/nginx/html/imgs/9/a/ab12....jpg
      → bind mount → 宿主机 ./html/imgs/9/a/ab12....jpg
```

- 图片走 **nginx 静态服务**，请求**不经过 Spring Boot**，零业务开销；
- `/imgs` 加了 `expires -1` + `Cache-Control: no-cache, must-revalidate`（图片可能被删除/替换，要求浏览器每次都回源校验）；
- 前端收到的 URL 之所以是 `8080/imgs`，正是因为 `BASE_URL` 指向 nginx。

---

## 4. 删除链路

| 接口 | 行为 |
|---|---|
| `DELETE /upload/delete?url=...` | 校验 URL 以 `BASE_URL` 开头、无 `..`、canonical path 必须落在上传目录内；校验 `file.user_id == 当前登录人` 后才能删物理文件 + 删 DB 记录 |
| `DELETE /upload/delete/{postId}` | 校验是帖子作者；删除该帖子全部 file 记录；**引用计数**：同 hash 在其他帖子/用户还有记录则不删物理文件，计数为 0 才真正 `FileUtil.del` |

删除的物理文件位于 IMAGE_PATH 目录 —— 因为和 nginx 是同一 bind mount，删完 nginx 立刻 404，配合 no-cache 头浏览器不会显示旧图。

---

## 5. Nginx 配置（已内置在项目 `nginx/` 目录）

Nginx 配置文件已直接纳入 Git 版本管理（`nginx/nginx.conf` 与 `nginx/conf.d/default.conf`），在任何新电脑上克隆代码后直接 `docker compose up -d` 即可生效。

核心配置摘要（`nginx/conf.d/default.conf`）：

```nginx
upstream sp-app {
    server sp-app:8081;        # 容器间标准通信
    keepalive 32;
}

server {
    listen 80;
    server_name localhost;

    # 客户端上传上限（与后端 10MB 保持一致）
    client_max_body_size 10M;

    location / {              # 前端 SPA（dist 打包产物）
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    location /imgs {          # 图片静态服务（不经过后端，高速直出）
        root /usr/share/nginx/html;
        try_files $uri =404;
        expires -1;
        add_header Cache-Control "no-cache, must-revalidate";
    }

    location /api {           # 接口反向代理到 Spring Boot
        rewrite /api(/.*) $1 break;   # 去掉 /api 前缀
        proxy_pass http://sp-app;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        client_max_body_size 10M;
    }

    location /ws {            # WebSocket 实时通信代理
        proxy_pass http://sp-app;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 3600s;
    }
}
```

端口规划：Nginx `8080→80`，应用 `8081`，MySQL `3306`，Redis `6379`，ES `9200`，Kibana `5601`，MongoDB `27017`。

---

## 6. 两种运行模式对照

| 维度 | 纯 Docker 模式（生产部署 / 多机部署） | 本地混合开发模式（日常开发） |
|---|---|---|
| Spring Boot 位置 | `sp-app` 容器（Dockerfile 多阶段分层构建） | 宿主机 IntelliJ / `./mvnw spring-boot:run`（`:8081`） |
| `IMAGE_PATH` | `/usr/local/nginx/html/imgs`（容器内路径） | 本地绝对路径 或 `./html/imgs` |
| nginx upstream | `sp-app:8081`（默认） | 可在 `nginx/conf.d/default.conf` 临时切为 `host.docker.internal:8081` |
| 图片落盘 | 经 bind mount 落到宿主机 `./html/imgs` | 直接写宿主机 `./html/imgs` |
| 图片读取 | Nginx 容器内 bind mount（`:ro` 只读直出） | 同一个 Nginx 容器，同样 bind mount |
| 权限处理 | `docker-entrypoint.sh` + `su-exec` 启动自动纠正权限 | 宿主机用户原生读写权限 |

---

## 7. 部署到其他电脑时的核心检查清单（防踩坑必读）

1. **宿主机文件读写权限（已通过 entrypoint 自愈）**：
   - 在 Linux 机器上，Docker bind mount 默认可能以 root 创建目录。已通过 Dockerfile 中的 `su-exec` 与 `docker-entrypoint.sh` 在容器启动瞬间自动给 `spring` 用户分配写权限，杜绝了 `Permission Denied`。
2. **`BASE_URL` 配置（跨机访问破图问题）**：
   - 默认配置为 `http://127.0.0.1:8080/imgs`。
   - 若部署到公网服务器或局域网其他电脑，**务必在 `.env` 中配置 `BASE_URL=http://<服务器IP或域名>:8080/imgs`**，否则其他客户端上传/访问图片时会请求客户端自身的 `127.0.0.1` 导致破图 404。
3. **前端跨域 `CORS_ORIGIN`**：
   - 在 `.env` 中添加允许访问的前端地址，例如 `CORS_ORIGIN=http://<前端IP>:5173,http://<前端IP>:8080`。
4. **持久化卷声明**：
   - `docker-compose.yml` 中已统一采用具名卷定义并移除了强制 `external: true`，全新电脑上执行 `docker compose up -d` 会自动创建所有数据卷，历史机器上部署则无缝继承既有卷数据。
5. **上传文件大小限制**：
   - Nginx（`client_max_body_size 10M`）与 Spring Boot（`max-file-size: 10MB`）已完全对齐。若后续需扩大上传限制，需同时修改 `application.yaml` 和 `nginx/conf.d/default.conf`。