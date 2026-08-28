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

-- 分类 1: 其他 / 生活
INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (25, 2, 'https://picsum.photos/seed/yoga/800/400', '极简主义生活方式：断舍离的正确打开方式',
        '<p>极简主义不是一味地扔东西，而是有意识地选择真正重要的物品。断舍离的核心是：断绝不需要的东西，舍弃多余的物品，脱离对物品的执着。</p><p>实践建议：从一个抽屉开始整理，一年没用过的物品果断处理，购物前思考是否真正需要。生活空间清爽了，心灵也会更加轻松。</p>',
        1, '2026-07-05 09:30:00', 31, 512);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (26, 3, 'https://picsum.photos/seed/rent/800/400', '租房避坑指南：租房前必须知道的10件事',
        '<p>租房是很多年轻人必须面对的问题。签约前要注意：核实房东身份和房产证，检查房屋设施是否完好，明确水电燃气费用标准，了解退租条件和押金退还规则。</p><p>看房时注意采光、通风、隔音、周边配套等因素。建议拍照记录房屋现状，避免退租时产生纠纷。合同条款要仔细阅读，不要怕麻烦。</p>',
        1, '2026-07-05 11:15:00', 45, 780);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (27, 4, 'https://picsum.photos/seed/cook/800/400', '厨房小白入门：10道零失败的家常菜',
        '<p>厨房小白也能做出美味家常菜。推荐10道零失败菜品：西红柿炒蛋、酸辣土豆丝、蒜蓉西兰花、可乐鸡翅、红烧肉、宫保鸡丁、麻婆豆腐、清蒸鲈鱼、蛋炒饭、番茄蛋汤。</p><p>新手建议从简单的炒菜开始，掌握火候和调味的基本技巧。做饭是一种生活技能，也是一种治愈心灵的方式。</p>',
        1, '2026-07-05 18:20:00', 58, 920);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (28, 1, 'https://picsum.photos/seed/sleep/800/400', '睡眠质量提升攻略：如何拥有高质量的睡眠',
        '<p>睡眠质量直接影响生活质量和工作效率。改善睡眠的方法：保持规律作息（即使周末也不要相差太大）、睡前1小时远离电子屏幕、保持卧室凉爽黑暗安静。</p><p>助眠小技巧：睡前泡脚、喝热牛奶、听白噪音、做深呼吸练习。避免睡前饮酒、喝咖啡、剧烈运动。如果长期失眠，建议就医咨询。</p>',
        1, '2026-07-06 22:00:00', 27, 460);

-- 分类 2: 摄影
INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (29, 3, 'https://picsum.photos/seed/landscape/800/400', '风光摄影入门：如何拍出震撼的风景照',
        '<p>风光摄影是最受欢迎的摄影类型之一。要拍出令人震撼的风景照，首先要学会观察光线。黄金时段（日出后和日落前一小时）的柔和光线能为照片增添温暖的色调。</p><p>构图方面，善用前景引导线、三分法则和框架构图，能让画面更有层次感和深度。记得使用三脚架保证画面清晰，小光圈获取大景深。</p>',
        2, '2026-07-07 07:30:00', 63, 1050);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (30, 2, 'https://picsum.photos/seed/macro/800/400', '微距摄影指南：探索微观世界的奇妙细节',
        '<p>微距摄影为我们打开了一个全新的视觉世界。一朵花的花蕊、一片叶子的脉络、一只昆虫的复眼，在微距镜头下都展现出令人惊叹的细节。</p><p>拍摄微距需要稳定的支撑（三脚架或独脚架）、精确的对焦（手动对焦配合焦点堆叠）、以及适当的景深控制。环形闪光灯是微距拍摄的好帮手。</p>',
        2, '2026-07-07 14:00:00', 36, 610);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (31, 4, 'https://picsum.photos/seed/bw/800/400', '黑白摄影的魅力：用光影讲述故事',
        '<p>黑白摄影剥离了色彩的干扰，让观者更专注于光影、质感和构图。它是一种纯粹的视觉表达方式。</p><p>拍摄黑白照片时，要学会用灰度的眼光观察世界。高对比度场景、丰富的纹理、强烈的明暗对比都是黑白摄影的好题材。后期处理中，调整各色通道的明暗关系可以创造出不同的影调效果。</p>',
        2, '2026-07-08 16:40:00', 41, 730);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (32, 3, 'https://picsum.photos/seed/wildlife/800/400', '野生动物摄影：耐心与技术的完美结合',
        '<p>野生动物摄影是极具挑战性的摄影类型。它需要超乎寻常的耐心、扎实的摄影技术、以及对动物行为的深入了解。</p><p>器材方面，长焦镜头（至少300mm）是必备装备。拍摄时要注意伪装和隐蔽，保持安全距离，尊重动物的自然栖息地。了解动物的习性和活动规律，能大大提高拍摄成功率。</p>',
        2, '2026-07-09 10:15:00', 52, 890);

-- 分类 3: AI
INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (33, 1, 'https://picsum.photos/seed/llm/800/400', '大语言模型的涌现能力：从GPT到Claude的演进',
        '<p>大语言模型展现出的涌现能力一直是AI领域最令人惊叹的现象。当模型参数达到一定规模后，会突然具备之前不存在的能力，如逻辑推理、代码生成、数学计算等。</p><p>从GPT-3到GPT-4，再到Claude 3，每一代模型都在涌现能力上带来了新的惊喜。研究人员正在努力理解这一现象背后的机制。</p>',
        3, '2026-07-09 15:30:00', 67, 1200);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (34, 4, 'https://picsum.photos/seed/ai/800/400', 'AI绘画工具对比：Midjourney vs Stable Diffusion',
        '<p>AI绘画工具已经成为设计师和创作者的得力助手。本文对比两款最受欢迎的AI绘画工具：Midjourney和Stable Diffusion。</p><p>Midjourney以其出色的美学风格著称，生成的图像艺术感强；Stable Diffusion则以开源、可定制化为优势，用户可以在本地部署并进行深度定制。</p>',
        3, '2026-07-10 11:00:00', 48, 850);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (35, 2, 'https://picsum.photos/seed/transformer/800/400', 'Transformer架构详解：注意力机制的革命',
        '<p>2017年Google提出的Transformer架构彻底改变了深度学习的格局。其核心的自注意力机制允许模型捕捉序列中任意位置之间的依赖关系，解决了RNN的长距离依赖问题。</p><p>本文将深入解析Transformer的编码器-解码器结构、多头注意力机制、位置编码等核心概念。</p>',
        3, '2026-07-10 17:20:00', 55, 980);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (36, 3, 'https://picsum.photos/seed/edge/800/400', '边缘AI：让智能走进终端设备',
        '<p>边缘AI是将人工智能算法部署在终端设备上的技术。相比云端AI，边缘AI具有低延迟、高隐私、低带宽消耗等优势。</p><p>随着专用AI芯片的发展，智能手机、智能摄像头、工业传感器等设备都能运行复杂的AI模型，实现真正的分布式智能。</p>',
        3, '2026-07-11 09:45:00', 39, 670);

-- 分类 4: 科技
INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (37, 1, 'https://picsum.photos/seed/brain/800/400', '脑机接口技术取得重大突破',
        '<p>Neuralink等脑机接口公司近期公布了令人振奋的研究成果。高带宽、低侵入式的脑机接口设备已成功植入人体，帮助瘫痪患者恢复部分运动能力。</p><p>未来脑机接口有望在医疗康复、人机交互、认知增强等领域发挥重要作用。</p>',
        4, '2026-07-11 14:10:00', 72, 1350);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (38, 2, 'https://picsum.photos/seed/riscv/800/400', 'RISC-V架构在物联网领域的应用',
        '<p>开源指令集架构RISC-V在物联网领域展现出强大潜力。相比ARM架构，RISC-V具有更高的灵活性和更低的授权成本。</p><p>众多芯片厂商开始推出基于RISC-V的物联网处理器，在智能家居、工业控制、可穿戴设备等领域得到广泛应用。</p>',
        4, '2026-07-12 10:30:00', 33, 590);

-- 分类 5: 数码
INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (39, 2, 'https://picsum.photos/seed/phone/800/400', '2024年旗舰手机横评：iPhone 16 Pro Max vs 三星S24 Ultra',
        '<p>2024年旗舰手机市场竞争激烈。iPhone 16 Pro Max搭载A18 Pro芯片，影像系统全面升级，新增拍照按钮和5倍光学变焦。三星S24 Ultra则配备骁龙8 Gen3处理器，S Pen手写笔和Galaxy AI功能是其亮点。</p><p>两款手机在性能、拍照、续航方面各有千秋。iOS和Android生态的选择也是重要考量因素。</p>',
        5, '2026-07-12 16:00:00', 85, 1620);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (40, 3, 'https://picsum.photos/seed/earbuds/800/400', '真无线耳机对比：AirPods Pro 2 vs 索尼WF-1000XM5',
        '<p>AirPods Pro 2搭载H2芯片，主动降噪效果出色，自适应通透模式体验极佳，与苹果生态无缝衔接。索尼WF-1000XM5在音质方面更胜一筹，LDAC编码支持高解析度音频。</p><p>两款耳机各有优势：追求生态体验选AirPods，追求音质选索尼。佩戴舒适度和续航也是选购时需要考虑的因素。</p>',
        5, '2026-07-13 09:15:00', 46, 810);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (41, 4, 'https://picsum.photos/seed/monitor/800/400', '4K显示器选购推荐：设计师和游戏玩家的最佳选择',
        '<p>4K显示器已经成为专业工作和游戏的标配。设计师应关注色域覆盖（Adobe RGB 99%以上）、色准（Delta E<2）和出厂校色报告。</p><p>游戏玩家则应关注刷新率（144Hz以上）、响应时间（1ms）和HDR支持。推荐产品包括戴尔U2723QE、LG 27GP950等。</p>',
        5, '2026-07-13 13:45:00', 38, 690);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (42, 1, 'https://picsum.photos/seed/watch/800/400', '智能手表选购：Apple Watch Ultra 2 vs 佳明Fenix 8',
        '<p>Apple Watch Ultra 2专为极限运动设计，钛合金表壳坚固耐用，双频GPS定位精准，潜水深度可达40米。佳明Fenix 8则是专业运动手表的标杆。</p><p>如果你是苹果用户且热爱户外运动，Ultra 2是最佳选择。如果你是专业运动员或需要超长续航，佳明Fenix 8更适合。</p>',
        5, '2026-07-14 11:20:00', 53, 940);

-- 分类 6: 经济
INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (43, 4, 'https://picsum.photos/seed/stock/800/400', '股票投资基础知识：新手必读指南',
        '<p>股票投资是普通人参与资本市场的重要方式。入门需要了解基本概念：市盈率、市净率、股息率等估值指标，以及资产负债表、利润表等财务报表。</p><p>投资策略方面，价值投资和成长投资是两大主流流派。建议新手从指数基金开始，逐步学习个股分析。记住：投资有风险，入市需谨慎。</p>',
        6, '2026-07-14 15:30:00', 61, 1100);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (44, 2, 'https://picsum.photos/seed/crypto/800/400', '数字货币的未来：比特币与央行数字货币',
        '<p>比特币作为首个去中心化数字货币，已成为一种另类投资资产。其总量有限的特性使其被部分投资者视为数字黄金。</p><p>与此同时，各国央行积极研发央行数字货币（CBDC），数字人民币已在多个城市试点。未来，私人数字货币和央行数字货币将共同构建新的数字金融生态。</p>',
        6, '2026-07-15 10:00:00', 49, 880);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (45, 3, 'https://picsum.photos/seed/fund/800/400', '基金定投攻略：懒人理财的最佳方式',
        '<p>基金定投是最适合普通投资者的理财方式之一。通过定期定额投资，可以平滑市场波动，降低择时风险。</p><p>定投策略建议：选择宽基指数基金、坚持长期投资（3年以上）、设置合理的止盈目标、市场低迷时适当加大投入。定投的核心是纪律和耐心。</p>',
        6, '2026-07-15 14:15:00', 57, 1020);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (46, 1, 'https://picsum.photos/seed/pension/800/400', '养老金规划：如何为退休生活做好准备',
        '<p>养老规划越早开始越好。假设60岁退休，如果从25岁开始每月定投1000元，按年化7%收益计算，退休时可积累约200万元。</p><p>养老资金来源包括：社保养老金、企业年金、个人储蓄和投资。建议建立多元化的养老资产组合，确保退休后的生活质量。</p>',
        6, '2026-07-16 09:30:00', 43, 760);

-- 分类 7: 教程
INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (47, 3, 'https://picsum.photos/seed/xiaomi/800/400', '小米手机刷机完全教程：MIUI到第三方ROM',
        '<p>小米手机刷机相对简单。首先在官网申请解锁权限，绑定账号等待7天后使用小米解锁工具解锁。然后刷入第三方Recovery（推荐TWRP），通过TWRP刷入第三方ROM。</p><p>推荐ROM包括：Pixel Experience（类原生）、LineageOS（稳定流畅）、crDroid（功能丰富）。刷机前务必备份数据和当前系统。</p>',
        7, '2026-07-16 16:20:00', 39, 680);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (48, 4, 'https://picsum.photos/seed/magisk/800/400', 'Magisk Root教程：获取安卓手机最高权限',
        '<p>Magisk是目前最主流的Root解决方案。它采用Systemless方式获取Root权限，不会修改系统分区，支持SafetyNet检测绕过。</p><p>安装步骤：解锁BL → 刷入TWRP → 在TWRP中刷入Magisk ZIP → 重启后安装Magisk App。通过Magisk模块可以实现各种系统级功能扩展。</p>',
        7, '2026-07-17 11:00:00', 47, 830);

-- 分类 8: 音乐
INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (49, 3, 'https://picsum.photos/seed/earbuds/800/400', '发烧友入门：无损音频与高解析度音乐格式解析',
        '<p>音乐发烧友常说的无损音质究竟是什么？简单科普一下音频格式：</p><p><strong>有损格式</strong> — MP3、AAC，通过丢弃人耳不敏感的高频信号压缩体积，适合流媒体播放。</p><p><strong>无损格式</strong> — FLAC、ALAC，完全还原录音室母带数据，无音质损耗。</p><p><strong>Hi-Res 高解析度</strong> — 采样率达到 24bit/96kHz 及以上，动态范围和细节层次远超 CD 标准（16bit/44.1kHz）。</p><p>搭配一副好耳机和一个小尾巴（便携解码耳放），你就能在熟悉的歌曲里听到以前未曾注意过的乐器细节与空间感。</p>',
        8, '2026-07-17 15:30:00', 54, 960);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (50, 2, 'https://picsum.photos/seed/speaker/800/400', '黑胶唱片回潮：为什么年轻人都开始玩黑胶了？',
        '<p>在流媒体触手可及的数字时代，实体黑胶唱片（Vinyl）却迎来了全球性的复兴潮：</p><p><strong>1. 仪式感</strong> — 从封套中抽出黑胶，放在唱盘上，轻轻落下唱针，这种物理交互带来了数字音乐无法替代的专注聆听体验。</p><p><strong>2. 模拟味的温暖音色</strong> — 黑胶唱片记录的是连续的模拟声波，高音温润不刺耳，中频饱满，有一种独特的空气感与岁月沉淀。</p><p><strong>3. 封面艺术与收藏价值</strong> — 12英寸的巨大画幅是绝佳的视觉艺术品，很多限量版彩胶更是乐迷心头的至宝。</p><p>放上一张 Miles Davis 的爵士乐，伴着唱针轻微的沙沙声，这就是属于夜晚的治愈时光。</p>',
        8, '2026-07-18 10:45:00', 68, 1220);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (51, 4, 'https://picsum.photos/seed/alone/800/400', '吉他自学指南：零基础如何弹唱你的第一首歌',
        '<p>很多朋友想学吉他但被大横按劝退，其实掌握方法自学吉他并不难：</p><p><strong>第一阶段：熟悉基本和弦</strong> — 优先练习 Em、C、G、D 这四个万能和弦，手指立起来，指尖垂直按弦，避免碰到其他琴弦。</p><p><strong>第二阶段：右手节奏型</strong> — 从简单的 4/4 拍扫弦和分解和弦练起，打节拍器保持匀速至关重要。</p><p><strong>第三阶段：和弦顺畅转换</strong> — 每天练习和弦盲按转换，形成肌肉记忆，不要停顿。</p><p>只要坚持每天练习 20 分钟，两周时间你就能弹唱《平凡之路》或《晴天》了！</p>',
        8, '2026-07-18 17:15:00', 73, 1310);

-- 分类 9: 历史
INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (52, 2, 'https://picsum.photos/seed/travel/800/400', '大唐盛世的市井生活：长安城的一日',
        '<p>如果穿越回一千多年前的大唐长安，你的一天会如何度过？</p><p><strong>清晨：晨钟敲响</strong> — 景耀门鼓声响起，一百零八坊坊门开启，朱雀大街车水马龙，各国使节与胡商络绎不绝。</p><p><strong>午间：东市与西市</strong> — 西市是国际贸易中心，胡姬酒肆、波斯香料、罗马琉璃琳琅满目；东市则是文人墨客和达官贵人的聚集地。</p><p><strong>傍晚：曲江流饮</strong> — 文人聚集在曲江池畔，饮酒作诗，各展才华。</p><p><strong>入夜：宵禁与夜市</strong> — 虽然有夜禁制度，但上元灯节等节日全城通宵狂欢，火树银花不夜天。</p><p>包容开放的气度，才是大唐真正迷人的地方。</p>',
        9, '2026-07-19 11:30:00', 82, 1480);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (53, 3, 'https://picsum.photos/seed/landscape/800/400', '丝绸之路上的文明交融：从敦煌莫高窟看世界',
        '<p>莫高窟坐落在河西走廊的尽头，是东西方文明交汇的千年见证：</p><p><strong>多元艺术风格</strong> — 从早期北魏的犍陀罗艺术风格，到盛唐雍容华贵的飞天形象，再到西夏与元代的密宗壁画，壁画见证了希腊、印度、波斯与中原文化的交融。</p><p><strong>经贸与文化动脉</strong> — 丝绸之路不仅运送丝绸、瓷器与香料，更传播了造纸术、冶金术、佛教与哲学思想。</p><p>站在鸣沙山下仰望九层楼，仿佛依然能听到当年驼铃悠悠的回响。</p>',
        9, '2026-07-19 16:00:00', 65, 1140);

-- 分类 11: 游戏
INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (54, 1, 'https://picsum.photos/seed/vr/800/400', '从《黑神话：悟空》聊聊国产 3A 游戏的工业化之路',
        '<p>《黑神话：悟空》的发售是中国游戏工业发展史上的里程碑：</p><p><strong>技术积淀</strong> — 采用虚幻引擎 5（UE5），结合 Nanite 虚拟几何体与 Lumen 全局光照技术，配合中国古建筑的高精度实景扫描，呈现了惊艳的东方奇幻世界。</p><p><strong>文化输出</strong> — 陕北说书、山西古刹、西游神话的现代化重构，让全球玩家领略到了中国传统文化的独特魅力。</p><p><strong>产业意义</strong> — 它证明了国内团队完全有能力制作顶级的单机 3A 大作，为中国游戏工业的成熟与转型指明了方向。</p>',
        11, '2026-07-20 10:20:00', 96, 1850);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (55, 4, 'https://picsum.photos/seed/landscape/800/400', '开放世界游戏设计哲学：自由度与叙事的平衡艺术',
        '<p>从《塞尔达传说：旷野之息》到《艾尔登法环》，优秀的开放世界游戏究竟做对了什么？</p><p><strong>1. 三角法则与视线引导</strong> — 通过地形高低差和地标建筑自然吸引玩家探索，而不是用满地图的问号强迫打卡。</p><p><strong>2. 涌现式玩法（Emergent Gameplay）</strong> — 建立统一且逻辑严密的物理和化学引擎（如风、火、雷、重力），让玩家用创意解决问题。</p><p><strong>3. 碎片化叙事与环境叙事</strong> — 将故事融入遗迹、物品说明和 NPC 的寥寥数语中，让玩家在探索中拼凑出世界的全貌。</p><p>真正的开放世界，不是地图有多大，而是给予了玩家多大的探索乐趣与想象空间。</p>',
        11, '2026-07-20 15:45:00', 78, 1420);

INSERT INTO `post` (`id`, `user_id`, `cover`, `title`, `content`, `category_id`, `create_time`, `like_count`, `view_count`)
VALUES (56, 2, 'https://picsum.photos/seed/pc/800/400', '掌机复兴：为什么我们依然热爱便携式游戏设备？',
        '<p>从任天堂 Switch 到 Steam Deck 以及各种 Windows 掌机，便携式游戏设备在近几年迎来了爆发式增长：</p><p><strong>碎片化时间利用</strong> — 在通勤地铁上、出差飞机上，甚至睡前靠在床头，随时随地拿起即玩，按电源键即休眠的便利性是台式机无法比拟的。</p><p><strong>独立游戏与复古模拟的绝配</strong> — 很多精巧的 2D 独立游戏在掌机的小屏幕上反而拥有比 4K 大屏更细腻、更舒适的沉浸体验。</p><p>随身携带一个属于自己的游戏小世界，是成年人最解压的日常消遣之一。</p>',
        11, '2026-07-20 20:30:00', 59, 1080);

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
