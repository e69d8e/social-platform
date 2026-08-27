SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

drop database if exists `social_platform`;
CREATE DATABASE if not exists `social_platform` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `social_platform`;
SET NAMES utf8mb4;

drop table if exists `user`;
# 用户表
CREATE TABLE if not exists `user`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`       VARCHAR(64)  NOT NULL UNIQUE COMMENT '用户名',
    `nickname`       VARCHAR(64)  NOT NULL COMMENT '昵称',
    `password`       VARCHAR(128) NOT NULL COMMENT '密码（加密后的哈希值）',
    `avatar`         VARCHAR(255)          DEFAULT 'http://127.0.0.1:8080/imgs/avatar/default.jpg' COMMENT '头像URL',
    `bio`            VARCHAR(255) NULL COMMENT '个人简介',
    `gender`         TINYINT               DEFAULT 0 COMMENT '性别 (0: 未知, 1: 男, 2: 女)',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `authority_id`   INT                   DEFAULT 1 COMMENT '权限ID',   # 默认为普通用户
    `enabled`        TINYINT(1)            DEFAULT 1 COMMENT '是否启用', # 默认启用
    `fans_private`   TINYINT(1)            DEFAULT 0 COMMENT '是否允许他人查看粉丝列表 (0: 允许, 1: 不允许)',
    `follow_private` TINYINT(1)            DEFAULT 0 COMMENT '是否允许他人查看关注列表 (0: 允许, 1: 不允许)',
    `fans_count`     INT                   DEFAULT 0 COMMENT '粉丝数',
    PRIMARY KEY (`id`),
    KEY `idx_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

insert into `user` (`id`, `username`, `nickname`, `password`, `bio`, `authority_id`, `fans_private`, `follow_private`)
values (1, 'li', '管理员',
        '$2a$10$Zzd1nV8xK0wYC3337/SCD.hH39iDgWUzDbBZ5ew5AhMrILnm.8Eqm',
        '管理员', 2, 1, 1);
insert into `user` (`id`, `username`, `nickname`, `password`, `bio`, `authority_id`)
values (2, 'dufu', 'dufu',
        '$2a$10$Zzd1nV8xK0wYC3337/SCD.hH39iDgWUzDbBZ5ew5AhMrILnm.8Eqm',
        '呵呵呵', 1);
insert into `user` (`id`, `username`, `nickname`, `password`, `bio`, `authority_id`)
values (3, 'wangwei', 'wangwei',
        '$2a$10$Zzd1nV8xK0wYC3337/SCD.hH39iDgWUzDbBZ5ew5AhMrILnm.8Eqm',
        '哈哈哈', 1);
insert into `user` (`id`, `username`, `nickname`, `password`, `bio`, `authority_id`, `fans_private`, `follow_private`)
values (4, 'baijvyi', 'baijvyi',
        '$2a$10$Zzd1nV8xK0wYC3337/SCD.hH39iDgWUzDbBZ5ew5AhMrILnm.8Eqm',
        '审核', 3, 1, 0);

# 权限表
CREATE TABLE if not exists `authority`
(
    `id`        INT         NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    `authority` VARCHAR(64) NOT NULL COMMENT '权限名称',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`authority`)
);
insert into `authority` (`id`, `authority`)
values (1, 'USER'); # 普通用户
insert into `authority` (`id`, `authority`)
values (2, 'ADMIN'); # 管理员
insert into `authority` (`id`, `authority`)
values (3, 'REVIEWER'); # 审核员


drop table if exists `follow`;
# 关注关系表
CREATE TABLE if not exists `follow`
(
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '关注关系ID',
    `follower_id` BIGINT   NOT NULL COMMENT '关注者ID',
    `followee_id` BIGINT   NOT NULL COMMENT '被关注者ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    PRIMARY KEY (`id`),
    KEY `idx_followee` (`followee_id`),
    KEY `idx_follower` (`follower_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='关注关系表';

drop table if exists `category`;
# 分类表
CREATE TABLE if not exists `category`
(
    `id`   Int         NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `name` VARCHAR(64) NOT NULL COMMENT '分类名称',
    PRIMARY KEY (`id`)
);
insert into `category` (`id`, `name`)
values (1, '其他');
insert into `category` (`id`, `name`)
values (2, '摄影');
insert into `category` (`id`, `name`)
values (3, 'AI');
insert into `category` (`id`, `name`)
values (4, '科技');
insert into `category` (`id`, `name`)
values (5, '数码');
insert into `category` (`id`, `name`)
values (6, '经济');
insert into `category` (`id`, `name`)
values (7, '教程');
insert into `category` (`id`, `name`)
values (8, '音乐');
insert into `category` (`id`, `name`)
values (9, '历史');
insert into `category` (`id`, `name`)
values (11, '游戏');

drop table if exists `post`;
# 帖子表
CREATE TABLE if not exists `post`
(
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '帖子ID',
    `user_id`     BIGINT   NOT NULL COMMENT '用户ID (帖子发布者)',
    `cover`       VARCHAR(255) NULL COMMENT '封面图URL',
    `title`       VARCHAR(100) NULL COMMENT '帖子标题',
    `content`     TEXT     NOT NULL COMMENT '帖子内容',
    `category_id` Int               default 1 COMMENT '分类ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    `enabled`     TINYINT(1)        DEFAULT 1 COMMENT '是否被封禁',
    `like_count`  INT               DEFAULT 0,
    `view_count`  INT               DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `create_time` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='帖子表';

drop table if exists `comment`;
# 评论表
CREATE TABLE if not exists `comment`
(
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `post_id`     BIGINT   NOT NULL COMMENT '关联帖子ID',
    `user_id`     BIGINT   NOT NULL COMMENT '评论发布者ID',
    `content`     TEXT     NOT NULL COMMENT '评论内容',
    `parent_id`   BIGINT   NULL COMMENT '父评论ID (用于二级评论，若为一级评论则NULL)',
    `reply_to`    BIGINT   NULL COMMENT '回复目标用户ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    PRIMARY KEY (`id`),
    KEY `idx_post_time` (`post_id`, `create_time`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='评论表';

drop table if exists `like_record`;
# 点赞记录表
CREATE TABLE if not exists `like_record`
(
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '点赞记录ID',
    `post_id`     BIGINT   NOT NULL COMMENT '被点赞的帖子ID',
    `user_id`     BIGINT   NOT NULL COMMENT '点赞用户ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='点赞记录表';

DROP TABLE IF EXISTS `session`;

CREATE TABLE IF NOT EXISTS `session`
(
    `id`      VARCHAR(255) COMMENT '会话id',
    `name`    VARCHAR(255) NOT NULL,
    `user_id` BIGINT       NOT NULL COMMENT '用户id',
    `time`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '会话时间',
    PRIMARY KEY (`id`),
    INDEX idx_user_id (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='会话表';

DROP TABLE IF EXISTS `ban_record`;

CREATE TABLE `ban_record`
(
    `id`          BIGINT   NOT NULL COMMENT '封禁记录ID',
    `user_id`     BIGINT   NOT NULL COMMENT '执行封禁操作的管理员/审查员ID',
    `target_id`   BIGINT   NOT NULL COMMENT '被封禁的目标ID（用户或帖子）',
    `type`        TINYINT  NOT NULL COMMENT '封禁类型：0=用户封禁，1=帖子封禁',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='封禁记录表';

DROP TABLE IF EXISTS `user_interest_score`;

CREATE TABLE user_interest_score
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL COMMENT '用户id',
    category_id INT    NOT NULL COMMENT '分类id',
    score       INT DEFAULT 0 COMMENT '用户对分类兴趣评分',
    UNIQUE KEY uk_user_category (user_id, category_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '用户对分类兴趣评分表';

DROP TABLE IF EXISTS `file`;

CREATE TABLE `file`
(
    id      BIGINT PRIMARY KEY COMMENT '文件ID',
    post_id BIGINT DEFAULT null COMMENT '帖子id，作为用户头像时值为null',
    user_id BIGINT DEFAULT null COMMENT '用户id，删除时判断是不是当前登录用户',
    url     VARCHAR(30) COMMENT '文件url',
    hash    CHAR(64) COMMENT 'SHA-256值',
    INDEX idx_hash (hash),
    INDEX idx_post_id (post_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='文件表';

DROP TABLE IF EXISTS `search_history`;

CREATE TABLE `search_history`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '搜索记录ID',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
    `keyword`     VARCHAR(255) NOT NULL COMMENT '搜索关键词',
    `type`        TINYINT      NOT NULL COMMENT '搜索类型 (0: 帖子搜索, 1: 用户搜索)',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '搜索时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户搜索记录表';

DROP TABLE IF EXISTS `private_conversation`;

-- 私信会话表
CREATE TABLE IF NOT EXISTS `private_conversation`
(
    `id`                BIGINT   NOT NULL COMMENT '会话ID',
    `user_a_id`         BIGINT   NOT NULL COMMENT '参与者A的用户ID (较小的ID)',
    `user_b_id`         BIGINT   NOT NULL COMMENT '参与者B的用户ID (较大的ID)',
    `last_message`      VARCHAR(500)      DEFAULT NULL COMMENT '最后一条消息内容摘要',
    `last_message_time` DATETIME          DEFAULT NULL COMMENT '最后一条消息时间',
    `unread_a`          INT      NOT NULL DEFAULT 0 COMMENT '用户A的未读数',
    `unread_b`          INT      NOT NULL DEFAULT 0 COMMENT '用户B的未读数',
    `create_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_pair` (`user_a_id`, `user_b_id`),
    KEY `idx_user_a` (`user_a_id`, `last_message_time` DESC),
    KEY `idx_user_b` (`user_b_id`, `last_message_time` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='私信会话表';

DROP TABLE IF EXISTS `private_message`;

-- 私信消息表
CREATE TABLE IF NOT EXISTS `private_message`
(
    `id`              BIGINT     NOT NULL COMMENT '消息ID',
    `conversation_id` BIGINT     NOT NULL COMMENT '会话ID',
    `sender_id`       BIGINT     NOT NULL COMMENT '发送者ID',
    `receiver_id`     BIGINT     NOT NULL COMMENT '接收者ID',
    `content`         TEXT       NOT NULL COMMENT '消息内容',
    `is_read`         TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读 (0: 未读, 1: 已读)',
    `create_time`     DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (`id`),
    KEY `idx_conversation_time` (`conversation_id`, `create_time` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='私信消息表';

DROP TABLE IF EXISTS `home_post`;

-- 首页帖子表
CREATE TABLE IF NOT EXISTS `home_post`
(
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '首页帖子ID',
    `post_id`     BIGINT   NOT NULL COMMENT '帖子ID',
    `user_id`     BIGINT   NOT NULL COMMENT '发布者ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_id` (`post_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='首页帖子表';

# 初始帖子数据
INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (1, 2, 'https://picsum.photos/seed/alone/800/400', '欢迎来到 Y 社区',
        '<p>大家好！欢迎来到 Y 社区，这是一个集帖子发布、社交互动、私信聊天和 AI 助手于一体的社交平台。</p><p>在这里你可以：</p><ul><li>发布图文帖子，与大家分享你的想法</li><li>关注感兴趣的人，获取个性化推荐</li><li>使用 AI 助手，让通义千问为你解答问题</li></ul><p>快开始探索吧！</p>',
        1, '2026-06-01 10:00:00', 12, 256);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (2, 3, 'https://picsum.photos/seed/python/800/400', 'Spring Boot 3 + MyBatis-Plus 快速入门',
        '<p>分享一下 Spring Boot 3 整合 MyBatis-Plus 的开发经验：</p><p><strong>1. 依赖引入</strong></p><p>使用 <code>mybatis-plus-spring-boot3-starter</code>，注意不要用 Boot 2 的版本。</p><p><strong>2. 实体类配置</strong></p><p>使用 <code>@TableName</code> 注解映射表名，<code>@TableId(type = IdType.ASSIGN_ID)</code> 配置雪花算法主键。</p><p><strong>3. 查询构造器</strong></p><p><code>LambdaQueryWrapper</code> 是最常用的查询方式，类型安全、链式调用，推荐优先使用。</p><p>更多细节欢迎评论区交流～</p>',
        7, '2026-06-03 14:30:00', 28, 512);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (3, 2, 'https://picsum.photos/seed/redis/800/400', 'Redis 在社交平台中的妙用',
        '<p>在我们的社交平台中，Redis 承担了多种关键角色：</p><p><strong>1. 点赞/关注计数</strong> — 高频写操作先落 Redis，再异步持久化到 MySQL，大幅降低数据库压力。</p><p><strong>2. 信息流（Feed）</strong> — 使用 ZSet 存储关注者的帖子时间线，支持高效的游标分页。</p><p><strong>3. JWT 黑名单</strong> — 用户登出后将 Token 加入 Redis 黑名单，实现无状态认证的 Token 失效。</p><p><strong>4. 每日签到</strong> — 使用 Bitmap 记录签到状态，O(1) 复杂度查询某天是否签到。</p><p><strong>5. 分布式限流</strong> — 基于 Redis 的滑动窗口算法，防止接口被恶意刷请求。</p><p>Redis 真的是社交场景的利器！</p>',
        4, '2026-06-05 09:15:00', 35, 678);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (4, 4, 'https://picsum.photos/seed/ai/800/400', 'Elasticsearch 全文搜索实践笔记',
        '<p>项目中使用 Elasticsearch 实现帖子和用户的全文搜索，记录几个要点：</p><p><strong>IK 分词器</strong> — 中文搜索必须安装 IK 分词插件，推荐使用 <code>ik_max_word</code> 索引时分词 + <code>ik_smart</code> 搜索时分词的组合。</p><p><strong>双注解模式</strong> — 我们的实体类同时使用 <code>@TableName</code>（MyBatis-Plus）和 <code>@Document</code>（Spring Data ES），写入时双写，读取时按需选择数据源。</p><p><strong>高亮搜索</strong> — 利用 ES 的 <code>highlight</code> 功能，在搜索结果中高亮显示匹配的关键词，提升用户体验。</p>',
        4, '2026-06-08 16:45:00', 19, 342);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (5, 3, 'https://picsum.photos/seed/llm/800/400', '聊聊 AI 大模型在社交平台中的应用',
        '<p>最近在项目中集成了通义千问大模型（通过 LangChain4j），实现了 AI 智能助手功能，分享一些心得：</p><p><strong>流式响应（SSE）</strong> — 大模型生成回复需要时间，使用 SSE 流式输出让用户逐字看到回复，体验远优于等待完整响应。</p><p><strong>聊天记忆</strong> — 通过 MongoDB 持久化对话历史，让 AI 能记住上下文，实现多轮对话。</p><p><strong>系统提示词</strong> — 在 <code>system-prompt.txt</code> 中定义 AI 的角色和行为准则，引导它给出更有用的回答。</p><p>AI + 社交的结合还有很多想象空间，比如智能内容推荐、自动摘要、情感分析等。</p>',
        3, '2026-06-12 11:20:00', 42, 856);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (6, 2, 'https://picsum.photos/seed/monitor/800/400', 'Vue 3 + Pinia 构建社交平台前端',
        '<p>前端使用 Vue 3 Composition API + Pinia 状态管理，记录一些开发模式：</p><p><strong>Pinia 持久化</strong> — 使用 <code>pinia-plugin-persistedstate</code> 将用户登录状态持久化到 localStorage，刷新页面不丢失登录态。</p><p><strong>Element Plus 按需引入</strong> — 通过 unplugin 自动导入组件，无需手动注册，开发效率很高。</p><p><strong>STOMP WebSocket</strong> — 私信功能使用 STOMP 协议，通过 <code>@stomp/stompjs</code> 库实现，比原生 WebSocket 更易管理消息路由。</p><p><strong>Axios 封装</strong> — 统一的请求拦截器处理 JWT Token 注入和 401 跳转登录。</p>',
        7, '2026-06-15 20:00:00', 23, 445);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (7, 4, 'https://picsum.photos/seed/transformer/800/400', 'Docker Compose 一键部署前后端项目',
        '<p>分享一下使用 Docker Compose 部署整个项目的经验：</p><p>我们用一个 <code>docker-compose.yml</code> 编排了 7 个服务：Spring Boot 应用、MySQL、Redis、Elasticsearch（内置 IK 分词）、MongoDB、Nginx 和 Kibana。</p><p><strong>关键配置：</strong></p><ul><li>使用命名卷（named volumes）持久化数据，容器重建不丢数据</li><li><code>depends_on</code> + <code>healthcheck</code> 控制启动顺序，确保 MySQL 和 ES 就绪后再启动应用</li><li>容器间通过服务名通信，如 <code>jdbc:mysql://sp-mysql:3306</code></li><li>环境变量覆盖 <code>application.yaml</code> 配置，实现不同环境的灵活切换</li></ul><p>一条命令 <code>docker compose up -d</code> 就能启动整个技术栈，非常方便！</p>',
        7, '2026-06-18 15:30:00', 31, 623);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (8, 3, 'https://picsum.photos/seed/ethics/800/400', 'JWT 双 Token 认证方案详解',
        '<p>项目采用 JWT 双 Token 认证机制，简单介绍一下设计思路：</p><p><strong>Access Token</strong> — 有效期 1 天，用于接口认证。每次请求在 Header 中携带 <code>Bearer &lt;token&gt;</code>。</p><p><strong>Refresh Token</strong> — 有效期 7 天，仅用于刷新 Access Token。Access Token 过期后，用 Refresh Token 换取新的 Token 对。</p><p><strong>Redis 黑名单</strong> — 用户登出时将 Token 加入 Redis 黑名单。过滤器每次请求先检查 Token 是否在黑名单中，实现即时失效。</p><p><strong>安全考虑：</strong> 密码使用 BCrypt 加密存储，Token 使用 HS256 签名，敏感操作需要重新验证身份。</p>',
        4, '2026-06-22 10:00:00', 26, 489);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (9, 2, 'https://picsum.photos/seed/landscape/800/400', '古诗词中的数学之美',
        '<p>分享几首蕴含数学思维的古诗词：</p><p>李白《早发白帝城》：「朝辞白帝彩云间，千里江陵一日还。」—— 速度 = 路程 ÷ 时间，千里 / 一日 ≈ 日行千里，虽是夸张但暗合运动学。</p><p>杜甫《绝句》：「两个黄鹂鸣翠柳，一行白鹭上青天。」—— 从 2 到 1，从个体到群体，从低到高，层次分明的空间构图。</p><p>数学与诗歌，理性与感性，在中国古典文化中交融得如此自然。</p>',
        9, '2026-06-25 19:30:00', 18, 321);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (10, 4, 'https://picsum.photos/seed/edge/800/400', '分布式限流：保护你的 API 不被打爆',
        '<p>在社交平台中，热门帖子的点赞接口可能瞬间被大量请求打垮。我们使用 <code>@RateLimit</code> 注解 + Redis 实现了分布式限流。</p><p><strong>实现方式：</strong></p><p>自定义 <code>@RateLimit</code> 注解标注在 Controller 方法上，配合 <code>RateLimitAspect</code> AOP 切面，在方法执行前检查 Redis 中的请求计数。</p><p><strong>滑动窗口算法：</strong> 使用 Redis 的 ZSet 记录请求时间戳，每次请求时清理窗口外的记录，统计窗口内的请求数是否超过阈值。</p><p><strong>使用示例：</strong></p><p><code>@RateLimit(count = 10, time = 60)</code> 表示 60 秒内最多允许 10 次请求。</p>',
        4, '2026-06-28 14:00:00', 22, 410);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (11, 3, 'https://picsum.photos/seed/street/800/400', '周末摄影分享：城市夜景',
        '<p>上周末带着相机去拍了一组城市夜景照片，分享一些拍摄心得：</p><p><strong>器材：</strong> 全画幅相机 + 24-70mm f/2.8，三脚架是必备的。</p><p><strong>参数：</strong> ISO 100-400，光圈 f/8-f/11 保证画面锐利，快门 2-10 秒记录车流光轨。</p><p><strong>构图技巧：</strong></p><ul><li>利用水面倒影增加画面层次</li><li>寻找引导线（道路、桥梁）引导视线</li><li>蓝色时刻（日落后 30 分钟）天空色彩最丰富</li></ul><p>夜景摄影的魅力在于，同样的场景在白天和夜晚可以呈现完全不同的气质。</p>',
        2, '2026-07-01 21:00:00', 45, 789);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (12, 2, 'https://picsum.photos/seed/vr/800/400', '独立游戏推荐：值得一玩的像素风佳作',
        '<p>推荐几款最近玩过的优秀独立游戏：</p><p><strong>1. Hollow Knight（空洞骑士）</strong> — 精妙的地图设计和爽快的战斗手感，2D 类银河战士恶魔城的巅峰之作。</p><p><strong>2. Celeite（蔚蓝）</strong> — 极具挑战性的平台跳跃，同时讲述了一个关于自我接纳的温暖故事。</p><p><strong>3. Stardew Valley（星露谷物语）</strong> — 像素田园生活的治愈体验，种地、钓鱼、下矿、社交，应有尽有。</p><p>像素风不等于落后，反而是一种独特的艺术表达方式。你最喜欢哪款像素游戏？</p>',
        11, '2026-07-03 18:00:00', 38, 654);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (13, 3, 'https://picsum.photos/seed/pc/800/400', 'MacBook Pro M4 开发体验分享',
        '<p>用 M4 MacBook Pro 做了两个月的 Java 开发，聊聊真实感受：</p><p><strong>编译速度</strong> — <code>mvn clean package</code> 从原来的 45 秒缩短到 18 秒，Gradle 项目提升更明显。</p><p><strong>Docker 性能</strong> — Docker Desktop 原生支持 ARM 架构，启动 Elasticsearch + MySQL + Redis 的 compose 项目，内存占用比 x86 低了约 30%。</p><p><strong>续航</strong> — 开着 IDEA + Docker + Chrome 能撑 6 小时以上，开会不用带充电器了。</p><p>唯一的小问题是部分旧版 JDK 的 ARM 兼容性，但 JDK 21 完全没有问题。</p>',
        5, '2026-07-04 09:00:00', 33, 567);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (14, 4, 'https://picsum.photos/seed/rl/800/400', '用 AOP 实现统一接口日志记录',
        '<p>分享一个实用的 AOP 切面技巧 —— 自动记录接口调用日志：</p><p>通过自定义注解 <code>@ApiLog</code> 标注在 Controller 方法上，AOP 切面自动记录请求参数、响应结果、耗时等信息。</p><p><strong>核心代码思路：</strong></p><pre>@Around("@annotation(apiLog)")\npublic Object around(ProceedingJoinPoint point, ApiLog apiLog) {\n    long start = System.currentTimeMillis();\n    Object result = point.proceed();\n    long cost = System.currentTimeMillis() - start;\n    log.info("接口: {}, 耗时: {}ms", method, cost);\n    return result;\n}</pre><p>配合 <code>@RateLimit</code> 切面，AOP 在这个项目里发挥了很大作用。面向切面编程真的是 Spring 的灵魂特性。</p>',
        7, '2026-07-04 10:30:00', 17, 298);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (15, 2, 'https://picsum.photos/seed/chatgpt/800/400', '从零搭建一个 STOMP WebSocket 私信系统',
        '<p>项目中的私信功能使用 STOMP over WebSocket 实现，记录一下技术方案：</p><p><strong>为什么选 STOMP？</strong> — 原生 WebSocket 没有消息路由概念，STOMP 提供了 <code>/user/queue/messages</code> 这样的目的地语义，可以轻松实现点对点消息推送。</p><p><strong>认证方案：</strong> 在 WebSocket 握手阶段通过 STOMP CONNECT 帧传递 JWT Token，服务端验证后建立连接。</p><p><strong>前端集成：</strong> 使用 <code>@stomp/stompjs</code> 库，自动重连、心跳检测都内置了，配合 Pinia 管理消息状态。</p><p><strong>未读计数：</strong> 会话表维护 <code>unread_a</code> 和 <code>unread_b</code> 字段，每次发送消息时 +1，对方打开会话时清零。</p>',
        7, '2026-07-04 11:00:00', 24, 421);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (16, 3, 'https://picsum.photos/seed/smarthome/800/400', '推荐几款免费好用的开发工具',
        '<p>分享一些日常开发中离不开的免费工具：</p><p><strong>IDE</strong> — IntelliJ IDEA Community（Java）、VS Code（前端/脚本）</p><p><strong>API 测试</strong> — 项目集成了 Knife4j，直接在浏览器里调试接口，比 Postman 还方便。</p><p><strong>数据库管理</strong> — DBeaver（通用）、RedisInsight（Redis 专用）</p><p><strong>容器管理</strong> — Docker Desktop + OrbStack（Mac 用户推荐，比 Docker Desktop 更轻量）</p><p><strong>终端</strong> — Warp（AI 辅助命令行）、iTerm2（经典之选）</p><p><strong>版本控制</strong> — Git + GitHub CLI（<code>gh</code> 命令行操作 PR/Issue 超方便）</p><p>工欲善其事，必先利其器。你有什么好用的开发工具推荐吗？</p>',
        4, '2026-07-04 12:00:00', 29, 502);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (17, 4, 'https://picsum.photos/seed/ssd/800/400', '深入理解 MyBatis-Plus 的雪花算法 ID',
        '<p>项目中大多数实体使用 <code>IdType.ASSIGN_ID</code>（雪花算法）生成主键，简单分析一下原理：</p><p><strong>结构：</strong> 64 位 Long 型 ID = 1 位符号 + 41 位时间戳 + 10 位机器 ID + 12 位序列号。</p><p><strong>优势：</strong></p><ul><li>趋势递增，对 B+ 树索引友好</li><li>分布式环境下无需中心化 ID 生成器</li><li>每台机器每毫秒可生成 4096 个 ID</li></ul><p><strong>注意事项：</strong> 雪花算法依赖系统时钟，时钟回拨会导致 ID 重复。MyBatis-Plus 内置了时钟回拨检测，遇到异常会抛出错误。</p><p>我们的帖子 ID 通过 <code>/post/generateId</code> 接口预生成，客户端拿到 ID 后再提交帖子内容，避免了创建失败导致 ID 浪费的问题。</p>',
        4, '2026-07-04 13:00:00', 15, 267);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (18, 2, 'https://picsum.photos/seed/travel/800/400', '唐朝诗人的朋友圈（如果他们有社交平台的话）',
        '<p>如果唐朝诗人有社交平台，大概会是这样：</p><p><strong>李白</strong> — 旅游博主，天天发风景照，配文都是「飞流直下三千尺」这种夸张文案。评论区全是杜甫的点赞。</p><p><strong>杜甫</strong> — 民生评论员，关注时事，写的内容都很沉重。「朱门酒肉臭，路有冻死骨」这种帖子估计会被限流。</p><p><strong>王维</strong> — 佛系生活博主，发的都是山水田园照片，配文极简。「空山新雨后」— 三个字就够了。</p><p><strong>白居易</strong> — 知名度最高的博主，据说他的诗「老妪能解」，放在今天就是篇篇 10w+ 的爆款作者。</p><p>你觉得哪位诗人最适合当社交平台博主？</p>',
        9, '2026-07-04 14:00:00', 56, 934);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (19, 3, 'https://picsum.photos/seed/tablet/800/400', '2026 年前端技术趋势观察',
        '<p>聊聊 2026 年前端领域的一些趋势：</p><p><strong>1. AI 辅助编码成为标配</strong> — GitHub Copilot、Cursor 等工具已经深度融入开发流程，写代码的效率提升了 30-50%。</p><p><strong>2. 服务端组件成熟</strong> — React Server Components、Vue 的 Vapor 模式让 SSR 更高效，首屏性能大幅提升。</p><p><strong>3. 全栈框架竞争</strong> — Next.js、Nuxt、SvelteKit 持续迭代，框架之争的焦点从「怎么写」转向「怎么部署」。</p><p><strong>4. WebAssembly 落地</strong> — 从实验性项目走向生产环境，图片处理、音视频编解码等场景越来越多。</p><p>作为 Java 全栈开发者，保持对前端趋势的关注很重要，前后端的边界越来越模糊了。</p>',
        4, '2026-07-04 15:00:00', 21, 389);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (20, 4, 'https://picsum.photos/seed/crossborder/800/400', '全球半导体产业链深度解析',
        '<p>半导体是现代科技的基石，梳理一下全球产业链格局：</p><p><strong>设计</strong> — 美国主导（英伟达、高通、AMD），ARM 架构来自英国。</p><p><strong>制造</strong> — 台积电（中国台湾）占全球先进制程 60% 以上份额，三星（韩国）紧随其后。</p><p><strong>设备</strong> — 荷兰 ASML 垄断 EUV 光刻机，日本在材料和设备领域有深厚积累。</p><p><strong>封测</strong> — 中国大陆和东南亚（马来西亚、越南）是主要封测基地。</p><p><strong>国内进展：</strong> 中芯国际已实现 14nm 量产，华为海思在芯片设计领域持续突破。国产替代正在加速。</p><p>芯片行业的特点是投入巨大、周期很长、技术壁垒极高，每一步突破都来之不易。</p>',
        6, '2026-07-04 16:00:00', 37, 645);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (21, 2, 'https://picsum.photos/seed/oneplus/800/400', 'Spring Security 过滤器链工作原理',
        '<p>深入理解 Spring Security 的核心机制 —— 过滤器链：</p><p><strong>核心概念：</strong> Spring Security 本质上是一组 Servlet 过滤器，按顺序执行。每个过滤器负责一个安全功能（认证、授权、CSRF 防护等）。</p><p><strong>我们的配置：</strong></p><ul><li><code>JwtAuthenticationFilter</code> — 自定义过滤器，解析 JWT Token，检查 Redis 黑名单</li><li><code>UsernamePasswordAuthenticationFilter</code> — 处理登录请求</li><li><code>ExceptionTranslationFilter</code> — 处理认证/授权失败的异常</li></ul><p><strong>关键代码：</strong> 在 <code>SecurityConfig</code> 中通过 <code>addFilterBefore()</code> 将 JWT 过滤器插入到合适的位置。</p><p>理解过滤器链的执行顺序，是掌握 Spring Security 的关键。</p>',
        7, '2026-07-04 17:00:00', 20, 376);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (22, 3, 'https://picsum.photos/seed/battery/800/400', '如何用 Redis Bitmap 实现每日签到',
        '<p>项目中使用 Redis Bitmap 实现用户每日签到功能，简单高效：</p><p><strong>原理：</strong> Bitmap 本质上是字符串的位操作。每个用户一个 Bitmap，key 为 <code>sign:用户ID:年月</code>，偏移量为日期（1-31）。</p><p><strong>签到：</strong> <code>SETBIT sign:1:202607 4 1</code> — 用户 1 在 7 月 4 日签到。</p><p><strong>查询某天是否签到：</strong> <code>GETBIT sign:1:202607 4</code> — 返回 1 表示已签到。</p><p><strong>统计本月签到天数：</strong> <code>BITCOUNT sign:1:202607</code> — O(1) 复杂度。</p><p><strong>连续签到检测：</strong> <code>BITFIELD sign:1:202607 GET u31 0</code> 一次取出 31 位，位运算判断连续天数。</p><p>一个用户一个月的签到数据只需 4 字节，1 亿用户也只要 400MB 内存，非常适合高频读写场景。</p>',
        4, '2026-07-04 18:00:00', 34, 587);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (23, 4, 'https://picsum.photos/seed/global/800/400', 'CORS 跨域问题一次搞懂',
        '<p>前后端分离开发中最常遇到的问题就是 CORS，一次讲清楚：</p><p><strong>什么是跨域？</strong> 浏览器的同源策略限制了不同源（协议+域名+端口）之间的请求。前端 <code>localhost:5173</code> 请求后端 <code>localhost:8081</code> 就是跨域。</p><p><strong>解决方案：</strong></p><p>后端配置 CORS 响应头：</p><ul><li><code>Access-Control-Allow-Origin</code> — 允许的来源</li><li><code>Access-Control-Allow-Methods</code> — 允许的 HTTP 方法</li><li><code>Access-Control-Allow-Headers</code> — 允许的请求头（包括 Authorization）</li></ul><p><strong>我们的做法：</strong> 通过 <code>CORS_ORIGIN</code> 环境变量配置允许的来源，支持多个来源逗号分隔，部署时无需改代码。</p><p>记住：CORS 是浏览器的安全机制，服务端之间调用不受限制。</p>',
        7, '2026-07-04 19:00:00', 19, 334);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (24, 2, 'https://picsum.photos/seed/office/800/400', '周末读书推荐：《设计模式》经典再读',
        '<p>重读 GoF《设计模式》，结合实际项目聊聊最常用的几个模式：</p><p><strong>策略模式</strong> — 项目中的限流策略（滑动窗口、令牌桶）就是典型的策略模式，通过注解切换实现。</p><p><strong>观察者模式</strong> — Spring 的事件机制（<code>ApplicationEvent</code>）就是观察者模式的实现，点赞后发送通知事件。</p><p><strong>代理模式</strong> — Spring AOP 本质上就是动态代理，<code>@RateLimit</code>、<code>@Async</code> 都是代理模式的应用。</p><p><strong>模板方法模式</strong> — <code>ServiceImpl</code> 提供了通用的 CRUD 模板，子类只需关注业务差异。</p><p>设计模式不是教条，而是解决特定问题的经验总结。理解场景比记住名称更重要。</p>',
        9, '2026-07-04 20:00:00', 25, 412);

-- 初始化首页帖子数据（自动将种子帖子导入 home_post 表）
INSERT INTO `home_post` (`post_id`, `user_id`, `create_time`)
SELECT `id`, `user_id`, `create_time` FROM `post`;

DROP TABLE IF EXISTS `user_inbox`;

-- 用户收件箱表
CREATE TABLE IF NOT EXISTS `user_inbox`
(
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '收件箱ID',
    `user_id`     BIGINT   NOT NULL COMMENT '接收者用户ID',
    `post_id`     BIGINT   NOT NULL COMMENT '帖子ID',
    `author_id`   BIGINT   NOT NULL COMMENT '发布者ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '推送时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_post` (`user_id`, `post_id`),
    KEY `idx_user_time` (`user_id`, `create_time` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户收件箱表';
