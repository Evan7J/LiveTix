package com.livetix.config;

import com.livetix.service.AdminLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

/**
 * AOP aspect for automatically logging admin operations.
 * Intercepts all write operations in admin controllers.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final AdminLogService adminLogService;

    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        HttpServletRequest req = attrs.getRequest();
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = req.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
        return ip;
    }

    private void log(String module, String action, JoinPoint jp, Long targetId) {
        // 52 修复: 敏感参数脱敏后再记录到日志
        String detail = jp.getSignature().toShortString() + " " + maskArgs(jp.getArgs());
        String ip = getClientIp();
        adminLogService.record(module, action, targetId, detail, ip);
    }

    /**
     * 52: 对敏感字段脱敏 — password/idNumber/phone 等替换为 ***
     */
    private String maskArgs(Object[] args) {
        if (args == null || args.length == 0) return "[]";
        String raw = Arrays.toString(args);
        // 脱敏常见敏感字段
        return raw
            .replaceAll("\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"***\"")
            .replaceAll("\"oldPassword\"\\s*:\\s*\"[^\"]*\"", "\"oldPassword\":\"***\"")
            .replaceAll("\"newPassword\"\\s*:\\s*\"[^\"]*\"", "\"newPassword\":\"***\"")
            .replaceAll("\"idCardNumber\"\\s*:\\s*\"[^\"]*\"", "\"idCardNumber\":\"***\"")
            .replaceAll("\"phone\"\\s*:\\s*\"[^\"]*\"", "\"phone\":\"***\"");
    }

    // === Admin Show operations ===
    @AfterReturning("execution(* com.livetix.controller.admin.AdminShowController.create*(..))")
    public void logShowCreate(JoinPoint jp) { log("show", "create", jp, null); }
    @AfterReturning("execution(* com.livetix.controller.admin.AdminShowController.update*(..))")
    public void logShowUpdate(JoinPoint jp) { log("show", "update", jp, null); }
    @AfterReturning("execution(* com.livetix.controller.admin.AdminShowController.delete*(..))")
    public void logShowDelete(JoinPoint jp) { log("show", "delete", jp, null); }

    // === Admin Order (refund) operations ===
    @AfterReturning("execution(* com.livetix.controller.admin.AdminOrderController.refund*(..))")
    public void logOrderRefund(JoinPoint jp) { log("order", "update", jp, null); }

    // === Admin User operations ===
    @AfterReturning("execution(* com.livetix.controller.admin.AdminUserController.toggle*(..))")
    public void logUserToggle(JoinPoint jp) { log("user", "update", jp, null); }

    // === Admin Category operations ===
    @AfterReturning("execution(* com.livetix.controller.admin.AdminCategoryController.create*(..))")
    public void logCategoryCreate(JoinPoint jp) { log("category", "create", jp, null); }
    @AfterReturning("execution(* com.livetix.controller.admin.AdminCategoryController.update*(..))")
    public void logCategoryUpdate(JoinPoint jp) { log("category", "update", jp, null); }
    @AfterReturning("execution(* com.livetix.controller.admin.AdminCategoryController.delete*(..))")
    public void logCategoryDelete(JoinPoint jp) { log("category", "delete", jp, null); }

    // === Admin Refund operations ===
    @AfterReturning("execution(* com.livetix.controller.admin.AdminRefundController.approve*(..))")
    public void logRefundApprove(JoinPoint jp) { log("refund", "approve", jp, null); }
    @AfterReturning("execution(* com.livetix.controller.admin.AdminRefundController.reject*(..))")
    public void logRefundReject(JoinPoint jp) { log("refund", "reject", jp, null); }

    // === Admin Config operations ===
    @AfterReturning("execution(* com.livetix.controller.admin.AdminSysConfigController.update*(..))")
    public void logConfigUpdate(JoinPoint jp) { log("system", "update", jp, null); }
}
