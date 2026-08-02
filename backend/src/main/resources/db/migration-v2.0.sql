-- ============================================
-- LiveTix 数据库迁移脚本 v2.0
-- 目的：为已有数据库增量添加新字段，避免 DROP TABLE 导致数据丢失
-- 执行方式：mysql -u root -p livetix < this_file.sql
-- 日期：2026-07-14
-- ============================================

USE livetix;

-- ============================================
-- 1. t_show 表新增业务属性字段
-- ============================================
ALTER TABLE `t_show`
    ADD COLUMN IF NOT EXISTS `buy_limit` INT NOT NULL DEFAULT 0 COMMENT '限购数量 0不限购' AFTER `view_count`,
    ADD COLUMN IF NOT EXISTS `is_real_name` TINYINT NOT NULL DEFAULT 0 COMMENT '是否实名制 0否 1是' AFTER `buy_limit`,
    ADD COLUMN IF NOT EXISTS `allow_refund` TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许退票 0否 1是' AFTER `is_real_name`,
    ADD COLUMN IF NOT EXISTS `refund_deadline_hours` INT DEFAULT 48 COMMENT '退票时限(距开演小时数)' AFTER `allow_refund`,
    ADD COLUMN IF NOT EXISTS `refund_fee_percent` DECIMAL(5,2) DEFAULT 0.00 COMMENT '退票手续费比例' AFTER `refund_deadline_hours`,
    ADD COLUMN IF NOT EXISTS `allow_transfer` TINYINT NOT NULL DEFAULT 0 COMMENT '是否支持转赠 0否 1是' AFTER `refund_fee_percent`,
    ADD COLUMN IF NOT EXISTS `pay_timeout_minutes` INT NOT NULL DEFAULT 15 COMMENT '支付超时(分钟)' AFTER `allow_transfer`,
    ADD COLUMN IF NOT EXISTS `enable_reminder` TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许开售提醒 0否 1是' AFTER `pay_timeout_minutes`,
    ADD COLUMN IF NOT EXISTS `show_status` VARCHAR(20) DEFAULT 'upcoming' COMMENT '演出状态 upcoming/presale/onsale/soldout/ended' AFTER `enable_reminder`,
    ADD COLUMN IF NOT EXISTS `rules` TEXT COMMENT '票务规则说明' AFTER `show_status`,
    ADD COLUMN IF NOT EXISTS `notice` TEXT COMMENT '观演须知' AFTER `rules`,
    ADD COLUMN IF NOT EXISTS `refund_policy` TEXT COMMENT '退票政策' AFTER `notice`;

-- ============================================
-- 2. t_order 表新增字段
-- ============================================
ALTER TABLE `t_order`
    ADD COLUMN IF NOT EXISTS `session_id` BIGINT DEFAULT NULL COMMENT '关联场次ID' AFTER `coupon_id`,
    ADD COLUMN IF NOT EXISTS `real_name_ids` VARCHAR(500) DEFAULT NULL COMMENT '观演人ID列表(JSON数组)' AFTER `session_id`,
    ADD COLUMN IF NOT EXISTS `pay_expire_time` DATETIME DEFAULT NULL COMMENT '支付超时时间' AFTER `real_name_ids`;

-- ============================================
-- 3. 更新已有演出的业务属性（设置合理默认值）
-- ============================================
UPDATE `t_show` SET
    `buy_limit` = 4,
    `is_real_name` = 1,
    `allow_refund` = 1,
    `refund_deadline_hours` = 48,
    `refund_fee_percent` = 10.00,
    `allow_transfer` = 0,
    `pay_timeout_minutes` = 15,
    `enable_reminder` = 1,
    `show_status` = 'onsale',
    `rules` = '每人限购4张，本演出为实名制演出，请携带本人有效身份证件入场。',
    `notice` = '请于演出开始前30分钟入场，禁止携带食品饮料，请将手机调至静音模式。',
    `refund_policy` = '距开演48小时前可申请退票，退票收取10%手续费。开演前48小时内不支持退票。'
WHERE `status` = 1;
