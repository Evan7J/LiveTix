package com.livetix.controller.admin;

import com.livetix.common.Result;
import com.livetix.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminRefundController {

    private final RefundService refundService;

    @GetMapping("/refunds")
    public Result<?> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status) {
        return refundService.listRefunds(page, pageSize, status);
    }

    @GetMapping("/refunds/{id}")
    public Result<?> detail(@PathVariable Long id) {
        var refund = refundService.getById(id);
        if (refund == null) {
            return Result.fail("退票申请不存在");
        }
        return Result.ok(refund);
    }

    @PutMapping("/refunds/{id}/approve")
    public Result<?> approve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return refundService.approveRefund(id, body.getOrDefault("comment", ""));
    }

    @PutMapping("/refunds/{id}/reject")
    public Result<?> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return refundService.rejectRefund(id, body.getOrDefault("comment", ""));
    }
}
