-- ============================================
-- LiveTix Database Initialization Script
-- Charset: utf8mb4
-- ============================================

CREATE DATABASE IF NOT EXISTS livetix DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE livetix;

-- ============================================
-- 1. User Table
-- ============================================
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `password` VARCHAR(128) NOT NULL COMMENT '密码(BCrypt)',
    `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    `avatar` VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `gender` TINYINT DEFAULT 0 COMMENT '性别 0未知 1男 2女',
    `role` VARCHAR(32) NOT NULL DEFAULT 'user' COMMENT '角色: admin/user',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
    `member_level` TINYINT DEFAULT 0 COMMENT '会员等级 0普通 1银卡 2金卡 3钻石',
    `member_expire` DATETIME DEFAULT NULL COMMENT '会员到期时间',
    `balance` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '账户余额',
    `points` INT NOT NULL DEFAULT 0 COMMENT '积分',
    `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `last_login_ip` VARCHAR(64) DEFAULT NULL COMMENT '最后登录IP',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_phone` (`phone`),
    KEY `idx_email` (`email`),
    KEY `idx_role` (`role`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================
-- 2. Show Category Table
-- ============================================
DROP TABLE IF EXISTS `t_category`;
CREATE TABLE `t_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `name` VARCHAR(64) NOT NULL COMMENT '分类名称',
    `icon` VARCHAR(512) DEFAULT NULL COMMENT '图标URL',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_sort` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演出分类表';

-- ============================================
-- 3. Venue Table
-- ============================================
DROP TABLE IF EXISTS `t_venue`;
CREATE TABLE `t_venue` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '场馆ID',
    `name` VARCHAR(128) NOT NULL COMMENT '场馆名称',
    `city` VARCHAR(64) NOT NULL COMMENT '所在城市',
    `address` VARCHAR(256) NOT NULL COMMENT '详细地址',
    `seat_map` JSON DEFAULT NULL COMMENT '座位图JSON',
    `total_seats` INT NOT NULL DEFAULT 0 COMMENT '总座位数',
    `contact_phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_city` (`city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场馆表';

-- ============================================
-- 4. Show (Performance) Table
-- ============================================
DROP TABLE IF EXISTS `t_show`;
CREATE TABLE `t_show` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '演出ID',
    `title` VARCHAR(256) NOT NULL COMMENT '演出标题',
    `category_id` BIGINT NOT NULL COMMENT '分类ID',
    `venue_id` BIGINT NOT NULL COMMENT '场馆ID',
    `cover_image` VARCHAR(512) DEFAULT NULL COMMENT '封面图URL',
    `images` JSON DEFAULT NULL COMMENT '详情图列表JSON',
    `description` TEXT COMMENT '演出描述',
    `artists` VARCHAR(256) DEFAULT NULL COMMENT '艺人/演出者',
    `show_time` DATETIME NOT NULL COMMENT '演出时间',
    `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
    `sale_start_time` DATETIME NOT NULL COMMENT '开售时间',
    `sale_end_time` DATETIME NOT NULL COMMENT '停售时间',
    `total_stock` INT NOT NULL DEFAULT 0 COMMENT '总库存(票数)',
    `available_stock` INT NOT NULL DEFAULT 0 COMMENT '可用库存',
    `price_min` DECIMAL(10,2) DEFAULT NULL COMMENT '最低票价',
    `price_max` DECIMAL(10,2) DEFAULT NULL COMMENT '最高票价',
    `ticket_types` JSON DEFAULT NULL COMMENT '票种JSON [{name,price,stock}]',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0待上架 1在售 2售罄 3已结束 4已取消',
    `is_hot` TINYINT NOT NULL DEFAULT 0 COMMENT '是否热门 0否 1是',
    `is_recommend` TINYINT NOT NULL DEFAULT 0 COMMENT '是否推荐 0否 1是',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览量',
    `buy_limit` INT NOT NULL DEFAULT 0 COMMENT '限购数量 0不限购',
    `is_real_name` TINYINT NOT NULL DEFAULT 0 COMMENT '是否实名制 0否 1是',
    `allow_refund` TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许退票 0否 1是',
    `refund_deadline_hours` INT DEFAULT 48 COMMENT '退票时限(距开演小时数)',
    `refund_fee_percent` DECIMAL(5,2) DEFAULT 0.00 COMMENT '退票手续费比例',
    `allow_transfer` TINYINT NOT NULL DEFAULT 0 COMMENT '是否支持转赠 0否 1是',
    `pay_timeout_minutes` INT NOT NULL DEFAULT 15 COMMENT '支付超时(分钟)',
    `enable_reminder` TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许开售提醒 0否 1是',
    `show_status` VARCHAR(20) DEFAULT 'upcoming' COMMENT '演出状态 upcoming/presale/onsale/soldout/ended',
    `rules` TEXT COMMENT '票务规则说明',
    `notice` TEXT COMMENT '观演须知',
    `refund_policy` TEXT COMMENT '退票政策',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category_id`),
    KEY `idx_venue` (`venue_id`),
    KEY `idx_show_time` (`show_time`),
    KEY `idx_status` (`status`),
    KEY `idx_hot` (`is_hot`),
    KEY `idx_recommend` (`is_recommend`),
    KEY `idx_show_status` (`show_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演出表';

-- ============================================
-- 5. Order Table
-- ============================================
DROP TABLE IF EXISTS `t_order`;
CREATE TABLE `t_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单编号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `show_id` BIGINT NOT NULL COMMENT '演出ID',
    `show_title` VARCHAR(256) DEFAULT NULL COMMENT '演出标题(冗余)',
    `venue_name` VARCHAR(128) DEFAULT NULL COMMENT '场馆名称(冗余)',
    `show_time` DATETIME DEFAULT NULL COMMENT '演出时间(冗余)',
    `cover_image` VARCHAR(512) DEFAULT NULL COMMENT '封面图(冗余)',
    `ticket_type` VARCHAR(64) DEFAULT NULL COMMENT '票种名称',
    `ticket_price` DECIMAL(10,2) NOT NULL COMMENT '票价',
    `quantity` INT NOT NULL DEFAULT 1 COMMENT '购买数量',
    `total_amount` DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
    `pay_amount` DECIMAL(10,2) NOT NULL COMMENT '实付金额',
    `status` VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '状态: pending待支付/paid已支付/cancelled已取消/refunded已退款',
    `pay_method` VARCHAR(32) DEFAULT NULL COMMENT '支付方式: alipay/wechat/balance',
    `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
    `cancel_time` DATETIME DEFAULT NULL COMMENT '取消时间',
    `refund_time` DATETIME DEFAULT NULL COMMENT '退款时间',
    `coupon_id` BIGINT DEFAULT NULL COMMENT '使用的优惠券ID',
    `session_id` BIGINT DEFAULT NULL COMMENT '关联场次ID',
    `real_name_ids` VARCHAR(500) DEFAULT NULL COMMENT '观演人ID列表(JSON数组)',
    `pay_expire_time` DATETIME DEFAULT NULL COMMENT '支付超时时间',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_show_id` (`show_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- ============================================
-- 6. Coupon Table
-- ============================================
DROP TABLE IF EXISTS `t_coupon`;
CREATE TABLE `t_coupon` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '优惠券ID',
    `name` VARCHAR(128) NOT NULL COMMENT '优惠券名称',
    `type` VARCHAR(32) NOT NULL COMMENT '类型: full_reduce满减/discount折扣',
    `threshold` DECIMAL(10,2) NOT NULL COMMENT '使用门槛金额',
    `reduce_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '减免金额(full_reduce)',
    `discount_rate` DECIMAL(3,2) DEFAULT NULL COMMENT '折扣率(discount, 如0.85)',
    `total_count` INT NOT NULL COMMENT '发放总量',
    `received_count` INT NOT NULL DEFAULT 0 COMMENT '已领取数量',
    `used_count` INT NOT NULL DEFAULT 0 COMMENT '已使用数量',
    `per_user_limit` INT NOT NULL DEFAULT 1 COMMENT '每人限领',
    `start_time` DATETIME NOT NULL COMMENT '生效时间',
    `end_time` DATETIME NOT NULL COMMENT '过期时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券表';

-- ============================================
-- 7. User-Coupon Relationship
-- ============================================
DROP TABLE IF EXISTS `t_user_coupon`;
CREATE TABLE `t_user_coupon` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `coupon_id` BIGINT NOT NULL COMMENT '优惠券ID',
    `status` VARCHAR(32) NOT NULL DEFAULT 'unused' COMMENT 'unused未使用/used已使用/expired已过期',
    `order_id` BIGINT DEFAULT NULL COMMENT '使用的订单ID',
    `use_time` DATETIME DEFAULT NULL COMMENT '使用时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_coupon_id` (`coupon_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户优惠券关联表';

-- ============================================
-- 8. Carousel (Banner) Table
-- ============================================
DROP TABLE IF EXISTS `t_banner`;
CREATE TABLE `t_banner` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(128) DEFAULT NULL COMMENT '标题',
    `image_url` VARCHAR(512) NOT NULL COMMENT '图片URL',
    `link_url` VARCHAR(512) DEFAULT NULL COMMENT '跳转链接',
    `show_id` BIGINT DEFAULT NULL COMMENT '关联演出ID',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_sort` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='轮播图表';

-- ============================================
-- 9. System Config Table
-- ============================================
DROP TABLE IF EXISTS `t_sys_config`;
CREATE TABLE `t_sys_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `config_key` VARCHAR(64) NOT NULL COMMENT '配置键',
    `config_value` TEXT COMMENT '配置值',
    `description` VARCHAR(256) DEFAULT NULL COMMENT '描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- ============================================
-- 10. Real Name Info Table (实名信息)
-- ============================================
DROP TABLE IF EXISTS `t_real_name_info`;
CREATE TABLE `t_real_name_info` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名',
    `id_card_type` VARCHAR(20) NOT NULL DEFAULT 'ID_CARD' COMMENT '证件类型 ID_CARD/PASSPORT/HK_MACAU_PASS',
    `id_card_number` VARCHAR(200) NOT NULL COMMENT '证件号码(加密存储)',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认观演人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实名信息表';

-- ============================================
-- 11. Show Session Table (演出场次)
-- ============================================
DROP TABLE IF EXISTS `t_show_session`;
CREATE TABLE `t_show_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `show_id` BIGINT NOT NULL COMMENT '演出ID',
    `session_name` VARCHAR(128) NOT NULL COMMENT '场次名称',
    `show_time` DATETIME NOT NULL COMMENT '场次时间',
    `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
    `total_stock` INT NOT NULL DEFAULT 0 COMMENT '总库存',
    `available_stock` INT NOT NULL DEFAULT 0 COMMENT '可用库存',
    `ticket_types` JSON DEFAULT NULL COMMENT '票种JSON',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_show_id` (`show_id`),
    KEY `idx_show_time` (`show_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演出场次表';

-- ============================================
-- 12. Refund Request Table (退票申请)
-- ============================================
DROP TABLE IF EXISTS `t_refund_request`;
CREATE TABLE `t_refund_request` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `reason` VARCHAR(512) DEFAULT NULL COMMENT '退票原因',
    `refund_amount` DECIMAL(10,2) NOT NULL COMMENT '退款金额',
    `fee_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '手续费',
    `status` VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT 'pending待审核/approved已通过/rejected已拒绝',
    `reviewer_id` BIGINT DEFAULT NULL COMMENT '审核人ID',
    `review_comment` VARCHAR(512) DEFAULT NULL COMMENT '审核意见',
    `review_time` DATETIME DEFAULT NULL COMMENT '审核时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退票申请表';

-- ============================================
-- 13. Show Favorite Table (用户收藏)
-- ============================================
DROP TABLE IF EXISTS `t_show_favorite`;
CREATE TABLE `t_show_favorite` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `show_id` BIGINT NOT NULL COMMENT '演出ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_show` (`user_id`, `show_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_show_id` (`show_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏表';

-- ============================================
-- 14. Show Reminder Table (开售提醒)
-- ============================================
DROP TABLE IF EXISTS `t_show_reminder`;
CREATE TABLE `t_show_reminder` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `show_id` BIGINT NOT NULL COMMENT '演出ID',
    `is_reminded` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已提醒 0否 1是',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_show` (`user_id`, `show_id`),
    KEY `idx_show_id` (`show_id`),
    KEY `idx_is_reminded` (`is_reminded`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开售提醒表';

-- ============================================
-- 15. Notification Table (消息通知)
-- ============================================
DROP TABLE IF EXISTS `t_notification`;
CREATE TABLE `t_notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `type` VARCHAR(32) NOT NULL COMMENT '类型 order_status/refund_result/show_remind/system',
    `title` VARCHAR(256) NOT NULL COMMENT '标题',
    `content` TEXT COMMENT '内容',
    `related_id` BIGINT DEFAULT NULL COMMENT '关联业务ID',
    `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读 0否 1是',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id_read` (`user_id`, `is_read`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息通知表';

-- ============================================
-- 16. Admin Operation Log Table (操作日志)
-- ============================================
DROP TABLE IF EXISTS `t_admin_log`;
CREATE TABLE `t_admin_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `admin_id` BIGINT NOT NULL COMMENT '操作人ID',
    `admin_name` VARCHAR(64) DEFAULT NULL COMMENT '操作人用户名',
    `module` VARCHAR(64) NOT NULL COMMENT '操作模块 show/order/user/coupon/category/refund/system',
    `action` VARCHAR(32) NOT NULL COMMENT '操作类型 create/update/delete/approve/reject',
    `target_id` BIGINT DEFAULT NULL COMMENT '操作对象ID',
    `detail` TEXT COMMENT '操作详情JSON',
    `ip` VARCHAR(64) DEFAULT NULL COMMENT '操作IP',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_admin_id` (`admin_id`),
    KEY `idx_module` (`module`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员操作日志表';

-- ============================================
-- 17. Wallet Transaction Table (钱包交易流水)
-- ============================================
DROP TABLE IF EXISTS `t_wallet_transaction`;
CREATE TABLE `t_wallet_transaction` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `type` VARCHAR(32) NOT NULL COMMENT '类型 recharge/purchase/refund/withdraw',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '金额(正数入账/负数出账)',
    `balance_after` DECIMAL(10,2) NOT NULL COMMENT '交易后余额',
    `order_id` BIGINT DEFAULT NULL COMMENT '关联订单ID',
    `remark` VARCHAR(256) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_type` (`type`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包交易流水表';

-- ============================================
-- 18-20. RBAC Tables (角色权限)
-- ============================================
DROP TABLE IF EXISTS `t_admin_role`;
CREATE TABLE `t_admin_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `role_code` VARCHAR(32) NOT NULL COMMENT '角色编码 admin/operator/finance/cs',
    `role_name` VARCHAR(64) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(256) DEFAULT NULL COMMENT '描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员角色表';

DROP TABLE IF EXISTS `t_admin_permission`;
CREATE TABLE `t_admin_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `perm_code` VARCHAR(64) NOT NULL COMMENT '权限编码',
    `perm_name` VARCHAR(64) NOT NULL COMMENT '权限名称',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父权限ID',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

DROP TABLE IF EXISTS `t_admin_role_permission`;
CREATE TABLE `t_admin_role_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- ============================================
-- Initialize Seed Data
-- ============================================

-- 51 修复: 默认管理员账号 — 首次登录后务必修改密码
--        用户名: admin，默认密码不记录在此文件中
INSERT INTO `t_user` (`username`, `password`, `nickname`, `role`, `status`, `member_level`, `balance`, `points`)
VALUES ('admin', '$2a$10$kuWpHvYtKI0umj99TnTuA.p0w9eYvWrF98t65LtvpJFjlqtCYXTOa', '系统管理员', 'admin', 1, 3, 0.00, 0);

-- 57 修复: 测试用户余额设为 0（防止生产环境误用泄漏的测试账号刷票）
-- Insert a test user (密码不在此注释中记录)
INSERT INTO `t_user` (`username`, `password`, `nickname`, `role`, `status`, `member_level`, `balance`, `points`)
VALUES ('testuser', '$2a$10$0fnp58mBdjvZ1eyjuGEo..60mLOtbDYccnfm.aECAnX3GIV4UzjqW', '测试用户', 'user', 1, 1, 0.00, 0);

-- Insert categories (演出分类)
INSERT INTO `t_category` (`name`, `icon`, `sort`) VALUES
('演唱会', '/icons/concert.svg', 1),
('音乐节', '/icons/festival.svg', 2),
('话剧歌剧', '/icons/drama.svg', 3),
('体育赛事', '/icons/sports.svg', 4),
('儿童亲子', '/icons/kids.svg', 5),
('展览展会', '/icons/exhibition.svg', 6);

-- Insert categories (校园闲置交易分类)
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

-- Insert venues
INSERT INTO `t_venue` (`name`, `city`, `address`, `total_seats`) VALUES
('国家体育场（鸟巢）', '北京', '北京市朝阳区国家体育场南路1号', 91000),
('上海梅赛德斯-奔驰文化中心', '上海', '上海市浦东新区世博大道1200号', 18000),
('广州天河体育中心', '广州', '广州市天河区天河路299号', 60000),
('华熙LIVE·五棵松', '北京', '北京市海淀区复兴路69号', 18000),
('深圳湾体育中心', '深圳', '深圳市南山区滨海大道3001号', 30000),
('成都凤凰山体育公园', '成都', '成都市金牛区北星大道一段', 60000),
('武汉体育中心', '武汉', '武汉市蔡甸区车城北路58号', 80000),
('杭州奥体中心', '杭州', '杭州市滨江区飞虹路', 80000);

-- Insert sample shows
INSERT INTO `t_show` (`title`, `category_id`, `venue_id`, `cover_image`, `description`, `artists`, `show_time`, `end_time`, `sale_start_time`, `sale_end_time`, `total_stock`, `available_stock`, `price_min`, `price_max`, `ticket_types`, `status`, `is_hot`, `is_recommend`, `sort`)
VALUES
('周杰伦「嘉年华」世界巡回演唱会-北京站', 1, 1, '/covers/jay_chou.jpg', '周杰伦2026嘉年华世界巡回演唱会北京站', '周杰伦', '2026-08-15 19:30:00', '2026-08-15 22:30:00', '2026-07-01 10:00:00', '2026-08-15 18:00:00', 50000, 50000, 580.00, 1680.00,
 '[{"name":"看台票","price":580.00,"stock":20000},{"name":"内场票","price":1280.00,"stock":20000},{"name":"VIP","price":1680.00,"stock":10000}]', 1, 1, 1, 1),
('五月天「回到那一天」巡回演唱会-上海站', 1, 2, '/covers/mayday.jpg', '五月天2026巡回演唱会上海站', '五月天', '2026-09-01 19:00:00', '2026-09-01 22:00:00', '2026-07-15 10:00:00', '2026-09-01 17:00:00', 15000, 15000, 480.00, 1380.00,
 '[{"name":"看台票A","price":480.00,"stock":5000},{"name":"看台票B","price":680.00,"stock":5000},{"name":"内场票","price":1380.00,"stock":5000}]', 1, 1, 1, 2),
('草莓音乐节2026-成都站', 2, 6, '/covers/strawberry.jpg', '2026草莓音乐节成都站，两天狂欢', '陈粒、新裤子、万能青年旅店', '2026-08-20 14:00:00', '2026-08-21 22:00:00', '2026-07-10 10:00:00', '2026-08-20 12:00:00', 40000, 40000, 280.00, 680.00,
 '[{"name":"单日票","price":280.00,"stock":20000},{"name":"双日通票","price":480.00,"stock":15000},{"name":"VIP双日","price":680.00,"stock":5000}]', 1, 0, 1, 3),
('《只此青绿》舞剧-广州站', 3, 3, '/covers/qinglv.jpg', '《只此青绿》——舞绘《千里江山图》', '中国东方演艺集团', '2026-08-25 19:30:00', '2026-08-25 21:30:00', '2026-07-20 10:00:00', '2026-08-25 18:00:00', 8000, 8000, 280.00, 880.00,
 '[{"name":"C区","price":280.00,"stock":3000},{"name":"B区","price":480.00,"stock":3000},{"name":"A区","price":880.00,"stock":2000}]', 1, 0, 1, 4),
('CBA全明星赛2026-深圳站', 4, 5, '/covers/cba.jpg', '2026年CBA全明星周末', 'CBA全明星球员', '2026-08-10 18:00:00', '2026-08-10 21:00:00', '2026-07-05 10:00:00', '2026-08-10 16:00:00', 25000, 25000, 180.00, 1280.00,
 '[{"name":"看台票","price":180.00,"stock":15000},{"name":"内场票","price":680.00,"stock":7000},{"name":"VIP","price":1280.00,"stock":3000}]', 1, 1, 1, 5);

-- Insert banners
INSERT INTO `t_banner` (`title`, `image_url`, `show_id`, `sort`, `status`) VALUES
('周杰伦嘉年华巡回演唱会', '/banners/banner1.jpg', 1, 1, 1),
('五月天回到那一天巡回演唱会', '/banners/banner2.jpg', 2, 2, 1),
('草莓音乐节2026', '/banners/banner3.jpg', 3, 3, 1),
('CBA全明星赛2026', '/banners/banner4.jpg', 5, 4, 1);

-- Insert coupons
INSERT INTO `t_coupon` (`name`, `type`, `threshold`, `reduce_amount`, `discount_rate`, `total_count`, `received_count`, `used_count`, `per_user_limit`, `start_time`, `end_time`, `status`)
VALUES
('新人专享满200减20', 'full_reduce', 200.00, 20.00, NULL, 1000, 0, 0, 1, '2026-07-01 00:00:00', '2026-12-31 23:59:59', 1),
('暑期狂欢满500减50', 'full_reduce', 500.00, 50.00, NULL, 500, 0, 0, 1, '2026-07-01 00:00:00', '2026-09-30 23:59:59', 1),
('VIP专享9折', 'discount', 0.00, NULL, 0.90, 200, 0, 0, 1, '2026-07-01 00:00:00', '2026-12-31 23:59:59', 1);

-- Insert system configs
INSERT INTO `t_sys_config` (`config_key`, `config_value`, `description`) VALUES
('site_name', 'LiveTix', '网站名称'),
('site_logo', '/logo.png', '网站Logo'),
('contact_phone', '400-888-9999', '客服电话'),
('order_timeout_minutes', '15', '订单超时未支付自动取消(分钟)');

-- Insert RBAC roles
INSERT INTO `t_admin_role` (`role_code`, `role_name`, `description`) VALUES
('super_admin', '超级管理员', '拥有所有权限，可管理角色和权限'),
('operator', '运营人员', '管理演出、订单、用户、优惠券、页面'),
('finance', '财务人员', '查看财务数据、审核退票'),
('cs', '客服人员', '查看订单和用户信息（只读）');

-- Insert RBAC permissions (parent-child hierarchy)
INSERT INTO `t_admin_permission` (`id`, `perm_code`, `perm_name`, `parent_id`, `sort`) VALUES
(1, 'dashboard', '数据概览', 0, 1),
(2, 'shows', '演出管理', 0, 2),
(3, 'shows:list', '演出列表', 2, 1),
(4, 'shows:create', '创建演出', 2, 2),
(5, 'shows:edit', '编辑演出', 2, 3),
(6, 'shows:delete', '删除演出', 2, 4),
(7, 'shows:sessions', '场次管理', 2, 5),
(8, 'shows:seats', '座位管理', 2, 6),
(9, 'categories', '分类管理', 0, 3),
(10, 'orders', '订单管理', 0, 4),
(11, 'orders:list', '订单列表', 10, 1),
(12, 'orders:refund', '退款操作', 10, 2),
(13, 'refunds', '退票审核', 0, 5),
(14, 'refunds:review', '审核退票', 13, 1),
(15, 'users', '用户管理', 0, 6),
(16, 'users:list', '用户列表', 15, 1),
(17, 'users:toggle', '封禁/解封用户', 15, 2),
(18, 'coupons', '优惠券管理', 0, 7),
(19, 'finance', '财务管理', 0, 8),
(20, 'finance:transactions', '交易流水', 19, 1),
(21, 'finance:refunds', '退款记录', 19, 2),
(22, 'banners', '页面管理', 0, 9),
(23, 'settings', '系统设置', 0, 10),
(24, 'roles', '权限管理', 0, 11),
(25, 'logs', '操作日志', 0, 12),
(26, 'logs:view', '查看日志', 25, 1);

-- Assign all permissions to super_admin role (role_id=1)
INSERT INTO `t_admin_role_permission` (`role_id`, `permission_id`)
SELECT 1, id FROM `t_admin_permission`;

-- Assign operator permissions (role_id=2): dashboard, shows, categories, orders, users, coupons, banners, settings
INSERT INTO `t_admin_role_permission` (`role_id`, `permission_id`) VALUES
(2, 1), (2, 2), (2, 3), (2, 4), (2, 5), (2, 6), (2, 7), (2, 8),
(2, 9), (2, 10), (2, 11), (2, 15), (2, 16), (2, 17), (2, 18), (2, 22), (2, 23);

-- Assign finance permissions (role_id=3): dashboard, orders:list, refunds, finance
INSERT INTO `t_admin_role_permission` (`role_id`, `permission_id`) VALUES
(3, 1), (3, 10), (3, 11), (3, 12), (3, 13), (3, 14), (3, 19), (3, 20), (3, 21);

-- Assign CS permissions (role_id=4): dashboard, orders:list(readonly), users:list(readonly), refunds:list(readonly)
INSERT INTO `t_admin_role_permission` (`role_id`, `permission_id`) VALUES
(4, 1), (4, 10), (4, 11), (4, 13), (4, 15), (4, 16);

-- ============================================
-- 21. Product Table (校园闲置交易商品)
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
    `images` JSON DEFAULT NULL COMMENT '图片列表JSON',
    `cover_image` VARCHAR(512) DEFAULT NULL COMMENT '封面图URL',
    `condition_level` TINYINT NOT NULL DEFAULT 1 COMMENT '新旧程度: 1全新 2几乎全新 3轻微使用 4明显使用',
    `trade_location` VARCHAR(256) DEFAULT NULL COMMENT '交易地点',
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

-- Insert sample products (测试商品种子数据)
INSERT INTO `t_product` (`title`, `description`, `price`, `original_price`, `category_id`, `user_id`, `cover_image`, `condition_level`, `trade_location`, `status`, `is_negotiable`)
VALUES
('iPhone 15 Pro 256GB 黑色', '去年12月买的，一直带壳使用，无划痕无维修，盒子配件齐全', 5999.00, 8999.00, 10, 2, '/uploads/2026/07/placeholder.jpg', 2, 'XX大学东校区3号宿舍楼', 1, 1),
('《数据结构》严蔚敏 全新', '考研买多了，全新未拆封，半价出', 25.00, 49.80, 11, 2, '/uploads/2026/07/placeholder.jpg', 1, 'XX大学图书馆', 1, 0),
('宿舍用小冰箱 50L', '毕业清仓，用了两年，制冷正常，静音款', 150.00, 399.00, 12, 2, '/uploads/2026/07/placeholder.jpg', 3, 'XX大学西校区12号楼', 1, 1),
('Nike Air Force 1 白色 42码', '只穿过两次，码数买小了，几乎全新', 399.00, 799.00, 13, 2, '/uploads/2026/07/placeholder.jpg', 2, 'XX大学体育馆', 1, 0),
('蓝牙耳机 AirPods Pro 2', '用了半年，续航正常，配件齐全，换新出', 899.00, 1899.00, 16, 2, '/uploads/2026/07/placeholder.jpg', 2, 'XX大学北校区', 1, 1);
