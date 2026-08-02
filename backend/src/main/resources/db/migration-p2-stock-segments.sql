-- ============================================================
-- P2: 库存分段拆分 — 分散热点行锁竞争
-- ============================================================
-- 原理: 将单行库存拆分为 N 个分片（如 10 个）
--      扣减时随机选一片，UPDATE 分散到不同行，减少 InnoDB 行锁等待
--      查询总库存 = SUM(segment_stock)
--
-- 适用场景: 热门演唱会/赛事（单场 >1000 并发下单）
-- ============================================================

-- -----------------------------------------------------------
-- 1. 创建库存分段表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_stock_segment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    show_id BIGINT NOT NULL COMMENT '演出ID',
    segment_index INT NOT NULL COMMENT '分片序号 0~N-1',
    segment_stock INT NOT NULL DEFAULT 0 COMMENT '分片库存',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_show_segment (show_id, segment_index),
    KEY idx_show_id (show_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存分段表（P2热点行锁分散）';

-- -----------------------------------------------------------
-- 2. 初始化分片存储过程（管理员预热库存时调用）
--    CALL init_stock_segments(showId, totalStock, 10);
-- -----------------------------------------------------------
DELIMITER //
CREATE PROCEDURE init_stock_segments(
    IN p_show_id BIGINT,
    IN p_total_stock INT,
    IN p_segment_count INT
)
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE base_stock INT;
    DECLARE remainder INT;

    SET base_stock = FLOOR(p_total_stock / p_segment_count);
    SET remainder = p_total_stock - (base_stock * p_segment_count);

    -- 先清除旧分片
    DELETE FROM t_stock_segment WHERE show_id = p_show_id;

    -- 分配库存到各分片（余数分配到前几个分片）
    WHILE i < p_segment_count DO
        INSERT INTO t_stock_segment (show_id, segment_index, segment_stock)
        VALUES (p_show_id, i, base_stock + IF(i < remainder, 1, 0))
        ON DUPLICATE KEY UPDATE segment_stock = VALUES(segment_stock);
        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;

-- -----------------------------------------------------------
-- 3. 原子扣减分片库存（随机选一片，乐观锁）
--    SELECT deduct_segment_stock(showId, quantity);
-- -----------------------------------------------------------
DELIMITER //
CREATE FUNCTION deduct_segment_stock(
    p_show_id BIGINT,
    p_quantity INT
) RETURNS INT
READS SQL DATA
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE s_index INT;
    DECLARE s_stock INT;
    DECLARE total_remaining INT;

    -- 随机选一个分片
    SET s_index = FLOOR(RAND() * 10);

    -- 乐观锁扣减
    UPDATE t_stock_segment
    SET segment_stock = segment_stock - p_quantity, version = version + 1
    WHERE show_id = p_show_id
      AND segment_index = s_index
      AND segment_stock >= p_quantity;

    IF ROW_COUNT() = 0 THEN
        -- 当前分片不足，尝试其他分片
        SET s_index = (s_index + 1) % 10;
        UPDATE t_stock_segment
        SET segment_stock = segment_stock - p_quantity, version = version + 1
        WHERE show_id = p_show_id
          AND segment_index = s_index
          AND segment_stock >= p_quantity;

        IF ROW_COUNT() = 0 THEN
            RETURN 0;  -- 所有分片都库存不足
        END IF;
    END IF;

    -- 返回总剩余库存
    SELECT COALESCE(SUM(segment_stock), 0) INTO total_remaining
    FROM t_stock_segment WHERE show_id = p_show_id;

    RETURN total_remaining;
END //
DELIMITER ;

-- ============================================================
-- 验证:
--   CALL init_stock_segments(1, 1000, 10);
--   SELECT * FROM t_stock_segment WHERE show_id = 1;
--   SELECT deduct_segment_stock(1, 5);
-- ============================================================
