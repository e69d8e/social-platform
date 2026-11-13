-- ============================================================
-- 增量升级脚本：首页推荐系统重构 + 分类增强 + 帖子标签系统 (2026-11-13)
-- 适用对象：已初始化旧版 schema 的 MySQL 8.x 库（可幂等重复执行）
-- ============================================================

-- 1. 扩展 category 表字段（description / icon / color / sort_order / enabled）
DROP PROCEDURE IF EXISTS `sp_add_column_if_missing`;
DELIMITER $$
CREATE PROCEDURE `sp_add_column_if_missing`(
    IN tbl VARCHAR(64),
    IN col VARCHAR(64),
    IN col_def VARCHAR(512)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = tbl
          AND column_name = col
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', col_def);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL sp_add_column_if_missing('category', 'description', "VARCHAR(200) NOT NULL DEFAULT '' COMMENT '分类简介'");
CALL sp_add_column_if_missing('category', 'icon', "VARCHAR(32) NULL COMMENT '分类图标/Emoji'");
CALL sp_add_column_if_missing('category', 'color', "VARCHAR(16) NULL COMMENT '分类主题色 HEX'");
CALL sp_add_column_if_missing('category', 'sort_order', "INT NOT NULL DEFAULT 100 COMMENT '排序权重（越小越靠前）'");
CALL sp_add_column_if_missing('category', 'enabled', "TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用 (1启用, 0禁用)'");

-- 2. 为 post 表增加 comment_count 并从 comment 表回填
CALL sp_add_column_if_missing('post', 'comment_count', "INT NOT NULL DEFAULT 0 COMMENT '评论数'");

UPDATE `post` p
    LEFT JOIN (SELECT `post_id`, COUNT(*) AS `cnt` FROM `comment` GROUP BY `post_id`) c
    ON p.`id` = c.`post_id`
SET p.`comment_count` = COALESCE(c.`cnt`, 0);

-- 3. 为 user_interest_score 增加 update_time 用于时间衰减
CALL sp_add_column_if_missing('user_interest_score', 'update_time', "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新时间'");

DROP PROCEDURE IF EXISTS `sp_add_column_if_missing`;

-- 4. 更新默认分类的图标、颜色、简介与排序
UPDATE `category` SET `description` = '生活日常、灵感随笔与综合交流', `icon` = '✨', `color` = '#8C7D72', `sort_order` = 999, `enabled` = 1 WHERE `id` = 1 AND `icon` IS NULL;
UPDATE `category` SET `description` = '光影构图、旅拍扫街与器材心得', `icon` = '📷', `color` = '#D97757', `sort_order` = 10,  `enabled` = 1 WHERE `id` = 2 AND `icon` IS NULL;
UPDATE `category` SET `description` = '大模型、AI 工具、提示词与前沿探索', `icon` = '🤖', `color` = '#3A6F85', `sort_order` = 20,  `enabled` = 1 WHERE `id` = 3 AND `icon` IS NULL;
UPDATE `category` SET `description` = '工程架构、技术趋势与科学前沿', `icon` = '🔬', `color` = '#2F6F58', `sort_order` = 30,  `enabled` = 1 WHERE `id` = 4 AND `icon` IS NULL;
UPDATE `category` SET `description` = '手机电脑、外设测评与效率装备', `icon` = '💻', `color` = '#C75E5E', `sort_order` = 40,  `enabled` = 1 WHERE `id` = 5 AND `icon` IS NULL;
UPDATE `category` SET `description` = '宏观产业、理财科普与投资思考', `icon` = '📈', `color` = '#B9842E', `sort_order` = 80,  `enabled` = 1 WHERE `id` = 6 AND `icon` IS NULL;
UPDATE `category` SET `description` = '实战指南、开发笔记与避坑经验', `icon` = '📘', `color` = '#2E7466', `sort_order` = 50,  `enabled` = 1 WHERE `id` = 7 AND `icon` IS NULL;
UPDATE `category` SET `description` = '无损音质、黑胶唱盘与乐器弹唱', `icon` = '🎵', `color` = '#A95A52', `sort_order` = 70,  `enabled` = 1 WHERE `id` = 8 AND `icon` IS NULL;
UPDATE `category` SET `description` = '人文史话、古今风貌与文明交融', `icon` = '🏛️', `color` = '#7E6050', `sort_order` = 90,  `enabled` = 1 WHERE `id` = 9 AND `icon` IS NULL;
UPDATE `category` SET `description` = '独立佳作、3A 大作、掌机与游戏设计', `icon` = '🎮', `color` = '#567085', `sort_order` = 60,  `enabled` = 1 WHERE `id` = 11 AND `icon` IS NULL;

-- 5. 新增标签表 & 帖子标签关联表
CREATE TABLE IF NOT EXISTS `tag`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '标签ID',
    `name`        VARCHAR(32) NOT NULL COMMENT '标签名称（不含#）',
    `category_id` INT         NULL COMMENT '关联分类ID（可选）',
    `use_count`   INT         NOT NULL DEFAULT 0 COMMENT '公开帖子引用计数',
    `is_hot`      TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否推荐热门标签',
    `enabled`     TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '是否启用',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_tag_name` (`name`),
    KEY `idx_tag_category_hot` (`category_id`, `is_hot`, `use_count` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='帖子标签表';

CREATE TABLE IF NOT EXISTS `post_tag`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `post_id`     BIGINT   NOT NULL COMMENT '帖子ID',
    `tag_id`      BIGINT   NOT NULL COMMENT '标签ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_post_tag` (`post_id`, `tag_id`),
    KEY `idx_tag_post` (`tag_id`, `post_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='帖子-标签关联表';

-- 6. 新增用户标签兴趣评分表 & 推荐负反馈表
CREATE TABLE IF NOT EXISTS `user_tag_interest`
(
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
    `tag_id`      BIGINT   NOT NULL COMMENT '标签ID',
    `score`       INT      NOT NULL DEFAULT 0 COMMENT '用户对标签的兴趣分',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新时间',
    UNIQUE KEY `uk_user_tag` (`user_id`, `tag_id`),
    KEY `idx_uti_user_score` (`user_id`, `score` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户标签兴趣评分表';

CREATE TABLE IF NOT EXISTS `user_recommend_feedback`
(
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`       BIGINT      NOT NULL COMMENT '操作用户ID',
    `target_type`   VARCHAR(16) NOT NULL COMMENT '反馈维度: POST / CATEGORY / TAG / AUTHOR',
    `target_id`     BIGINT      NOT NULL COMMENT '目标ID（帖子/分类/标签/作者ID）',
    `feedback_type` VARCHAR(16) NOT NULL DEFAULT 'DISLIKE' COMMENT '反馈类型: DISLIKE',
    `reason`        VARCHAR(64) NULL COMMENT '原因描述',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_user_feedback` (`user_id`, `target_type`, `target_id`),
    KEY `idx_urf_user_type` (`user_id`, `target_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户推荐负反馈表';

-- 7. 清理历史遗漏进 home_post 的私密帖（若有）
DELETE hp FROM `home_post` hp
    INNER JOIN `post` p ON hp.`post_id` = p.`id`
WHERE p.`is_private` = 1 OR p.`enabled` = 0;

-- 8. 幂等种子热门标签（INSERT IGNORE）
INSERT IGNORE INTO `tag` (`id`, `name`, `category_id`, `use_count`, `is_hot`, `enabled`) VALUES
(1, 'Java', 7, 0, 1, 1),
(2, 'Spring Boot', 7, 0, 1, 1),
(3, 'MyBatis-Plus', 7, 0, 0, 1),
(4, 'Redis', 4, 0, 1, 1),
(5, 'Elasticsearch', 4, 0, 1, 1),
(6, '大语言模型', 3, 0, 1, 1),
(7, 'Vue3', 7, 0, 1, 1),
(8, 'Docker', 7, 0, 1, 1),
(9, '网络安全', 4, 0, 0, 1),
(10, '夜景摄影', 2, 0, 1, 1),
(11, '独立游戏', 11, 0, 1, 1),
(12, 'MacBook', 5, 0, 1, 1),
(13, '架构设计', 7, 0, 1, 1),
(14, 'WebSocket', 7, 0, 0, 1),
(15, '效率工具', 4, 0, 1, 1),
(16, '唐诗', 9, 0, 0, 1),
(17, '前端趋势', 4, 0, 1, 1),
(18, '半导体', 6, 0, 1, 1),
(19, '极简生活', 1, 0, 1, 1),
(20, '租房指南', 1, 0, 1, 1),
(21, '家常美食', 1, 0, 1, 1),
(22, '健康睡眠', 1, 0, 0, 1),
(23, '风光摄影', 2, 0, 1, 1),
(24, '微距摄影', 2, 0, 0, 1),
(25, '黑白影像', 2, 0, 1, 1),
(26, '野生动物', 2, 0, 0, 1),
(27, 'AI绘画', 3, 0, 1, 1),
(28, 'Transformer', 3, 0, 1, 1),
(29, '边缘计算', 3, 0, 0, 1),
(30, '脑机接口', 4, 0, 1, 1),
(31, 'RISC-V', 4, 0, 0, 1),
(32, '旗舰手机', 5, 0, 1, 1),
(33, '无线耳机', 5, 0, 1, 1),
(34, '4K显示器', 5, 0, 0, 1),
(35, '智能手表', 5, 0, 0, 1),
(36, '股票投资', 6, 0, 1, 1),
(37, '数字货币', 6, 0, 1, 1),
(38, '基金定投', 6, 0, 1, 1),
(39, '养老规划', 6, 0, 0, 1),
(40, '安卓玩机', 7, 0, 0, 1),
(41, 'Hi-Res音频', 8, 0, 1, 1),
(42, '黑胶唱片', 8, 0, 1, 1),
(43, '吉他弹唱', 8, 0, 1, 1),
(44, '大唐长安', 9, 0, 1, 1),
(45, '丝绸之路', 9, 0, 1, 1),
(46, '黑神话悟空', 11, 0, 1, 1),
(47, '开放世界', 11, 0, 1, 1),
(48, '掌机', 11, 0, 1, 1),
(49, '社区新手', 1, 0, 0, 1),
(50, '全栈开发', 7, 0, 1, 1);

-- 仅当种子帖子尚存在且无标签时回填（避免污染新增业务数据）
INSERT IGNORE INTO `post_tag` (`post_id`, `tag_id`)
SELECT p.`id`, t.`tag_id`
FROM `post` p
INNER JOIN (
    SELECT 1 AS pid, 49 AS tag_id UNION ALL SELECT 1, 19 UNION ALL
    SELECT 2, 1 UNION ALL SELECT 2, 2 UNION ALL SELECT 2, 3 UNION ALL
    SELECT 3, 4 UNION ALL SELECT 3, 13 UNION ALL SELECT 3, 50 UNION ALL
    SELECT 4, 5 UNION ALL SELECT 4, 1 UNION ALL
    SELECT 5, 6 UNION ALL SELECT 5, 50 UNION ALL
    SELECT 6, 7 UNION ALL SELECT 6, 17 UNION ALL SELECT 6, 14 UNION ALL
    SELECT 7, 8 UNION ALL SELECT 7, 50 UNION ALL
    SELECT 8, 9 UNION ALL SELECT 8, 2 UNION ALL
    SELECT 9, 16 UNION ALL
    SELECT 10, 4 UNION ALL SELECT 10, 13 UNION ALL SELECT 10, 9 UNION ALL
    SELECT 11, 10 UNION ALL SELECT 11, 23 UNION ALL
    SELECT 12, 11 UNION ALL SELECT 12, 48 UNION ALL
    SELECT 13, 12 UNION ALL SELECT 13, 8 UNION ALL SELECT 13, 1 UNION ALL
    SELECT 14, 2 UNION ALL SELECT 14, 1 UNION ALL
    SELECT 15, 14 UNION ALL SELECT 15, 7 UNION ALL SELECT 15, 2 UNION ALL
    SELECT 16, 15 UNION ALL SELECT 16, 8 UNION ALL
    SELECT 17, 3 UNION ALL SELECT 17, 1 UNION ALL
    SELECT 18, 16 UNION ALL SELECT 18, 44 UNION ALL
    SELECT 19, 17 UNION ALL SELECT 19, 7 UNION ALL
    SELECT 20, 18 UNION ALL SELECT 20, 31 UNION ALL
    SELECT 21, 2 UNION ALL SELECT 21, 9 UNION ALL SELECT 21, 1 UNION ALL
    SELECT 22, 4 UNION ALL SELECT 22, 13 UNION ALL
    SELECT 23, 50 UNION ALL SELECT 23, 9 UNION ALL
    SELECT 24, 13 UNION ALL SELECT 24, 1 UNION ALL
    SELECT 25, 19 UNION ALL
    SELECT 26, 20 UNION ALL SELECT 26, 19 UNION ALL
    SELECT 27, 21 UNION ALL
    SELECT 28, 22 UNION ALL SELECT 28, 19 UNION ALL
    SELECT 29, 23 UNION ALL SELECT 29, 10 UNION ALL
    SELECT 30, 24 UNION ALL SELECT 30, 23 UNION ALL
    SELECT 31, 25 UNION ALL SELECT 31, 23 UNION ALL
    SELECT 32, 26 UNION ALL SELECT 32, 23 UNION ALL
    SELECT 33, 6 UNION ALL SELECT 33, 28 UNION ALL
    SELECT 34, 27 UNION ALL SELECT 34, 6 UNION ALL
    SELECT 35, 28 UNION ALL SELECT 35, 6 UNION ALL
    SELECT 36, 29 UNION ALL SELECT 36, 6 UNION ALL
    SELECT 37, 30 UNION ALL
    SELECT 38, 31 UNION ALL SELECT 38, 18 UNION ALL
    SELECT 39, 32 UNION ALL
    SELECT 40, 33 UNION ALL SELECT 40, 41 UNION ALL
    SELECT 41, 34 UNION ALL SELECT 41, 15 UNION ALL
    SELECT 42, 35 UNION ALL SELECT 42, 32 UNION ALL
    SELECT 43, 36 UNION ALL SELECT 43, 38 UNION ALL
    SELECT 44, 37 UNION ALL SELECT 44, 36 UNION ALL
    SELECT 45, 38 UNION ALL SELECT 45, 36 UNION ALL
    SELECT 46, 39 UNION ALL SELECT 46, 38 UNION ALL
    SELECT 47, 40 UNION ALL SELECT 47, 32 UNION ALL
    SELECT 48, 40 UNION ALL
    SELECT 49, 41 UNION ALL SELECT 49, 33 UNION ALL
    SELECT 50, 42 UNION ALL SELECT 50, 41 UNION ALL
    SELECT 51, 43 UNION ALL
    SELECT 52, 44 UNION ALL SELECT 52, 16 UNION ALL
    SELECT 53, 45 UNION ALL SELECT 53, 44 UNION ALL
    SELECT 54, 46 UNION ALL SELECT 54, 47 UNION ALL
    SELECT 55, 47 UNION ALL SELECT 55, 11 UNION ALL
    SELECT 56, 48 UNION ALL SELECT 56, 11
) t ON p.`id` = t.`pid`;

UPDATE `tag` tg
    LEFT JOIN (
        SELECT pt.`tag_id`, COUNT(*) AS `cnt`
        FROM `post_tag` pt
        INNER JOIN `post` p ON pt.`post_id` = p.`id`
        WHERE p.`enabled` = 1 AND (p.`is_private` IS NULL OR p.`is_private` = 0)
        GROUP BY pt.`tag_id`
    ) agg ON tg.`id` = agg.`tag_id`
SET tg.`use_count` = COALESCE(agg.`cnt`, 0);
