package com.livetix.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetix.entity.ShowSession;
import com.livetix.mapper.ShowSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 选座锁定服务实现 — Redis SET NX 细粒度锁
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatLockServiceImpl implements SeatLockService {

    private final RedisTemplate<String, Object> redisTemplate;

    private final ShowSessionMapper sessionMapper;

    private static final int MAX_SEATS_PER_ORDER = 6;
    private static final long SEAT_LOCK_TTL_MINUTES = 5;
    private static final String SESSION_SEATS_KEY = "livetix:session:seats:";
    private static final String SEAT_LOCK_PREFIX = "livetix:seat:lock:";

    @Override
    public Map<String, Object> getSeats(Long sessionId) {
        ShowSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return Map.of("error", "场次不存在");
        }

        String hashKey = SESSION_SEATS_KEY + sessionId;
        Boolean exists = redisTemplate.hasKey(hashKey);
        if (Boolean.FALSE.equals(exists)) {
            initSeatsFromDB(session);
        }

        Map<Object, Object> seats = redisTemplate.opsForHash().entries(hashKey);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("sessionName", session.getSessionName());
        result.put("venueId", session.getShowId());
        result.put("totalStock", session.getTotalStock());
        result.put("availableStock", session.getAvailableStock());

        List<Map<String, Object>> seatList = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : seats.entrySet()) {
            Map<String, Object> seat = new HashMap<>();
            seat.put("seatId", entry.getKey().toString());
            seat.put("status", entry.getValue().toString());
            seatList.add(seat);
        }
        result.put("seats", seatList);
        return result;
    }

    @Override
    public Map<String, Object> lockSeats(Long sessionId, List<String> seatIds, Long userId) {
        if (seatIds == null || seatIds.isEmpty()) {
            return Map.of("success", false, "message", "请选择座位");
        }
        if (seatIds.size() > MAX_SEATS_PER_ORDER) {
            return Map.of("success", false, "message", "单次最多选择" + MAX_SEATS_PER_ORDER + "个座位");
        }

        List<String> acquiredLocks = new ArrayList<>();
        List<String> locked = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        try {
            for (String seatId : seatIds) {
                String lockKey = SEAT_LOCK_PREFIX + sessionId + ":" + seatId;
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, "1", SEAT_LOCK_TTL_MINUTES, TimeUnit.MINUTES);

                if (Boolean.TRUE.equals(acquired)) {
                    acquiredLocks.add(lockKey);
                } else {
                    failed.add(seatId);
                    break;
                }
            }

            if (failed.isEmpty()) {
                String hashKey = SESSION_SEATS_KEY + sessionId;
                for (String seatId : seatIds) {
                    redisTemplate.opsForHash().put(hashKey, seatId, "locked:" + userId);
                    locked.add(seatId);
                }
            } else {
                for (String lockKey : acquiredLocks) {
                    redisTemplate.delete(lockKey);
                }
            }
        } catch (Exception e) {
            log.error("Failed to lock seats: sessionId={}, seatIds={}", sessionId, seatIds, e);
            failed.addAll(seatIds);
            for (String lockKey : acquiredLocks) {
                redisTemplate.delete(lockKey);
            }
        }

        String message;
        if (locked.size() == seatIds.size()) {
            message = "已锁定 " + locked.size() + " 个座位，请在" + SEAT_LOCK_TTL_MINUTES + "分钟内完成支付";
        } else if (!locked.isEmpty()) {
            message = "部分座位已被他人选择";
        } else {
            message = "所选座位已被他人选中，请重新选择";
        }

        return Map.of(
                "success", !locked.isEmpty(),
                "message", message,
                "locked", locked,
                "failed", failed
        );
    }

    @Override
    public void releaseSeats(Long sessionId, List<String> seatIds, Long userId) {
        if (seatIds == null || seatIds.isEmpty()) return;

        String hashKey = SESSION_SEATS_KEY + sessionId;
        for (String seatId : seatIds) {
            String status = (String) redisTemplate.opsForHash().get(hashKey, seatId);
            if (status != null && status.equals("locked:" + userId)) {
                redisTemplate.opsForHash().put(hashKey, seatId, "available");
                String lockKey = SEAT_LOCK_PREFIX + sessionId + ":" + seatId;
                redisTemplate.delete(lockKey);
                log.info("Seat released: session={}, seat={}, userId={}", sessionId, seatId, userId);
            }
        }
    }

    @Override
    public void confirmSeats(Long sessionId, List<String> seatIds, Long userId) {
        if (seatIds == null || seatIds.isEmpty()) return;

        String hashKey = SESSION_SEATS_KEY + sessionId;
        for (String seatId : seatIds) {
            String status = (String) redisTemplate.opsForHash().get(hashKey, seatId);
            if (status != null && status.equals("locked:" + userId)) {
                redisTemplate.opsForHash().put(hashKey, seatId, "sold");
                log.info("Seat confirmed: session={}, seat={}, userId={}", sessionId, seatId, userId);
            }
        }

        ShowSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setAvailableStock(session.getAvailableStock() - seatIds.size());
            sessionMapper.updateById(session);
        }
    }

    private void initSeatsFromDB(ShowSession session) {
        String hashKey = SESSION_SEATS_KEY + session.getId();
        try {
            var ticketTypes = parseTicketTypes(session.getTicketTypes());
            if (ticketTypes != null && !ticketTypes.isEmpty()) {
                for (var ticket : ticketTypes) {
                    String name = (String) ticket.get("name");
                    int stock = ((Number) ticket.get("stock")).intValue();
                    for (int i = 1; i <= stock; i++) {
                        String seatId = name + "-" + String.format("%03d", i);
                        redisTemplate.opsForHash().put(hashKey, seatId, "available");
                    }
                }
            }
            redisTemplate.expire(hashKey, 30, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("Failed to init seats from DB for session {}", session.getId(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseTicketTypes(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}