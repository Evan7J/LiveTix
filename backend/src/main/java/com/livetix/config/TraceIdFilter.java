package com.livetix.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 9: MDC TraceId 过滤器 — 全链路日志追踪
 * 每个请求自动生成 traceId 放入 MDC，日志中通过 %X{traceId} 输出
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            // 优先使用上游传入的 traceId，否则生成新的
            String traceId = null;
            if (request instanceof HttpServletRequest httpReq) {
                traceId = httpReq.getHeader(TRACE_ID_HEADER);
            }
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }
            MDC.put(MDC_KEY, traceId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
