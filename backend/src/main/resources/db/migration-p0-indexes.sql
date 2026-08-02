-- ============================================================
-- P0-5: 高并发优化 — 数据库联合索引迁移脚本
-- ============================================================
-- 执行前请先备份数据库
-- MySQL 8.0+ 验证: EXPLAIN SELECT * FROM t_order WHERE ...;

-- -----------------------------------------------------------
-- 1. t_order: 用户重复下单防重查询
--    查询模式: SELECT * FROM t_order WHERE user_id=? AND show_id=? AND status='pending'
--    用途: 防止同一用户在同一演出下重复下单
-- -----------------------------------------------------------
-- 先检查索引是否存在，避免重复创建报错
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'livetix' AND TABLE_NAME = 't_order' AND INDEX_NAME = 'idx_user_show_status');

SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE t_order ADD INDEX idx_user_show_status (user_id, show_id, status) COMMENT ''用户+演出+状态联合索引: 防重查重''',
    'SELECT ''idx_user_show_status already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------
-- 2. t_order: 定时任务扫描待支付订单
--    查询模式: SELECT * FROM t_order WHERE status='pending' ORDER BY create_time ASC
--    用途: 替代全表扫描 + OFFSET 分页，提升定时任务效率
-- -----------------------------------------------------------
SET @idx_exists2 = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'livetix' AND TABLE_NAME = 't_order' AND INDEX_NAME = 'idx_status_create_time');

SET @sql2 = IF(@idx_exists2 = 0,
    'ALTER TABLE t_order ADD INDEX idx_status_create_time (status, create_time) COMMENT ''状态+创建时间联合索引: 超时订单扫描''',
    'SELECT ''idx_status_create_time already exists'' AS msg');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- -----------------------------------------------------------
-- 3. t_show: 库存扣减优化（如果不存在单独索引）
--    deductStock 已使用 WHERE id=? AND available_stock >= N（主键索引）
--    不需要额外索引，但需确认主键为 id 列
-- -----------------------------------------------------------

-- -----------------------------------------------------------
-- 4. t_user: 余额扣减（乐观锁 WHERE id=? AND balance >= ?）
--    主键索引已覆盖，无需额外索引
-- -----------------------------------------------------------

-- ============================================================
-- 验证索引是否创建成功:
--   SHOW INDEX FROM t_order;
--   EXPLAIN SELECT * FROM t_order WHERE user_id=1 AND show_id=1 AND status='pending';
--   EXPLAIN SELECT * FROM t_order WHERE status='pending' ORDER BY create_time ASC LIMIT 100;
-- ============================================================
