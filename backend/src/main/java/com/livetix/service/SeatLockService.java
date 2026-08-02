package com.livetix.service;

import java.util.List;
import java.util.Map;

public interface SeatLockService {

    Map<String, Object> getSeats(Long sessionId);

    Map<String, Object> lockSeats(Long sessionId, List<String> seatIds, Long userId);

    void releaseSeats(Long sessionId, List<String> seatIds, Long userId);

    void confirmSeats(Long sessionId, List<String> seatIds, Long userId);
}