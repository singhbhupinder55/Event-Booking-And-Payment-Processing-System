package com.portfolio.eventbooking.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Generates (or propagates, if the caller supplied one) a correlation ID for
 * every inbound request and:
 *  - puts it in the MDC so every log line for this request includes it
 *  - puts it on the response header so a client (or Postman, or you in an
 *    interview) can copy it and grep the logs for one request's full lifecycle
 *  - makes it available via CorrelationIdHolder for the service layer to
 *    stamp onto audit_log rows
 *
 * Runs first in the filter chain (lowest order value) so every downstream
 * filter and the request itself can rely on the MDC already being populated.
 */
@Component
@Order(Integer.MIN_VALUE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incoming = request.getHeader(CORRELATION_ID_HEADER);
        String correlationId = (incoming != null && !incoming.isBlank()) ? incoming : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, correlationId);
        CorrelationIdHolder.set(correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clean up — threads are reused by the servlet container's
            // thread pool, so leaving this in MDC/ThreadLocal would leak the
            // correlation ID from this request into whatever the next request
            // on this thread does.
            MDC.remove(MDC_KEY);
            CorrelationIdHolder.clear();
        }
    }
}
