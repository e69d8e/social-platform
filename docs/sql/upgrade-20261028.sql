-- ============================================================
-- 2026-10-28 功能升级迁移脚本（增量执行，不破坏存量数据）
-- 涉及功能：
--   1. 用户主页自定义背景图        -> user.background_url
--   2. 帖子私密                    -> post.is_private
--   3. 帖子收藏                    -> post.star_count + star_record 表
--   4. 评论点赞                    -> comment.like_count + comment_like_record 表
-- ============================================================
USE `social_platform`;

-- 1. 用户表：主页背景图 URL（空/NULL 表示使用默认背景）
ALTER TABLE `user`
    ADD COLUMN `background_url` VARCHAR(255) NULL DEFAULT NULL COMMENT '主页背景图URL（NULL 使用默认背景）' AFTER `avatar`;

-- 2. 帖子表：私密标记 + 收藏数
ALTER TABLE `post`
    ADD COLUMN `is_private` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否私密（0: 公开, 1: 私密仅自己可见）' AFTER `enabled`,
    ADD COLUMN `star_count` INT NOT NULL DEFAULT 0 COMMENT '收藏数' AFTER `like_count`;

-- 3. 评论表：点赞数
ALTER TABLE `comment`
    ADD COLUMN `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数' AFTER `content`;

-- 4. 帖子收藏记录表
CREATE TABLE IF NOT EXISTS `star_record`
(
    `id`          BIGINT   NOT NULL COMMENT '收藏记录ID',
    `post_id`     BIGINT   NOT NULL COMMENT '被收藏的帖子ID',
    `user_id`     BIGINT   NOT NULL COMMENT '收藏用户ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='帖子收藏记录表';

-- 5. 评论点赞记录表
CREATE TABLE IF NOT EXISTS `comment_like_record`
(
    `id`          BIGINT   NOT NULL COMMENT '记录ID',
    `comment_id`  BIGINT   NOT NULL COMMENT '被点赞的评论ID',
    `user_id`     BIGINT   NOT NULL COMMENT '点赞用户ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_comment_user` (`comment_id`, `user_id`),
    KEY `idx_comment_id` (`comment_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='评论点赞记录表';
