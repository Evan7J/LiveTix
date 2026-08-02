-- ============================================
-- 商品模块迁移脚本
-- 校园闲置交易平台 — t_product 商品表
-- ============================================

DROP TABLE IF EXISTS `t_product`;
CREATE TABLE `t_product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
    `title` VARCHAR(256) NOT NULL COMMENT '商品标题',
    `description` TEXT COMMENT '商品描述',
    `price` DECIMAL(10,2) NOT NULL COMMENT '售价',
    `original_price` DECIMAL(10,2) DEFAULT NULL COMMENT '原价（用于展示折扣）',
    `category_id` BIGINT NOT NULL COMMENT '分类ID',
    `user_id` BIGINT NOT NULL COMMENT '卖家用户ID',
    `images` JSON DEFAULT NULL COMMENT '图片列表JSON ["url1","url2"]',
    `cover_image` VARCHAR(512) DEFAULT NULL COMMENT '封面图URL',
    `condition_level` TINYINT NOT NULL DEFAULT 1 COMMENT '新旧程度: 1全新 2几乎全新 3轻微使用 4明显使用',
    `trade_location` VARCHAR(256) DEFAULT NULL COMMENT '交易地点（如：XX校区XX楼）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1在售 2已售 3下架',
    `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览量',
    `favorite_count` INT NOT NULL DEFAULT 0 COMMENT '收藏数',
    `is_negotiable` TINYINT NOT NULL DEFAULT 0 COMMENT '是否可议价 0否 1是',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_price` (`price`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_condition` (`condition_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表（校园闲置交易）';

-- 插入校园闲置交易分类种子数据
INSERT INTO `t_category` (`name`, `icon`, `sort`) VALUES
('电子产品', '/icons/electronics.svg', 10),
('书籍教材', '/icons/books.svg', 11),
('生活用品', '/icons/daily.svg', 12),
('服饰鞋包', '/icons/fashion.svg', 13),
('运动户外', '/icons/sports.svg', 14),
('文具办公', '/icons/stationery.svg', 15),
('数码配件', '/icons/digital.svg', 16),
('其他', '/icons/other.svg', 17)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);