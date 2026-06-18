drop database if exists `social_platform`;
CREATE DATABASE if not exists `social_platform` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `social_platform`;

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
        '{bcrypt}$2a$10$Zzd1nV8xK0wYC3337/SCD.hH39iDgWUzDbBZ5ew5AhMrILnm.8Eqm',
        '管理员', 2, 1, 1);
insert into `user` (`id`, `username`, `nickname`, `password`, `bio`, `authority_id`)
values (2, 'dufu', 'dufu',
        '{bcrypt}$2a$10$Zzd1nV8xK0wYC3337/SCD.hH39iDgWUzDbBZ5ew5AhMrILnm.8Eqm',
        '呵呵呵', 1);
insert into `user` (`id`, `username`, `nickname`, `password`, `bio`, `authority_id`)
values (3, 'wangwei', 'wangwei',
        '{bcrypt}$2a$10$Zzd1nV8xK0wYC3337/SCD.hH39iDgWUzDbBZ5ew5AhMrILnm.8Eqm',
        '哈哈哈', 1);
insert into `user` (`id`, `username`, `nickname`, `password`, `bio`, `authority_id`, `fans_private`, `follow_private`)
values (4, 'baijvyi', 'baijvyi',
        '{bcrypt}$2a$10$Zzd1nV8xK0wYC3337/SCD.hH39iDgWUzDbBZ5ew5AhMrILnm.8Eqm',
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
#     FOREIGN KEY (`follower_id`) REFERENCES `user` (`id`),
#     FOREIGN KEY (`followee_id`) REFERENCES `user` (`id`),
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
#     FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    KEY `idx_user_time` (`user_id`, `create_time` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='帖子表';

# drop table if exists `post_image`;
# # 帖子图片表
# CREATE TABLE if not exists `post_image` (
#                                             `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '图片记录ID',
#                                             `post_id` BIGINT NOT NULL COMMENT '帖子ID',
#                                             `url` VARCHAR(255) NOT NULL COMMENT '图片URL地址',
#                                             PRIMARY KEY (`id`),
# #     FOREIGN KEY (`post_id`) REFERENCES `post` (`id`),
#                                             KEY `idx_post_id` (`post_id`)
# ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子图片表';

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
#     FOREIGN KEY (`post_id`) REFERENCES `post` (`id`),
#     FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
#     FOREIGN KEY (`parent_id`) REFERENCES `comment` (`id`),
#     FOREIGN KEY (`reply_to`) REFERENCES `user` (`id`),
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
    UNIQUE KEY `uk_post_user` (`post_id`, `user_id`), -- 确保一个用户不能重复点赞同一帖子
#     FOREIGN KEY (`post_id`) REFERENCES `post` (`id`),
#     FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
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