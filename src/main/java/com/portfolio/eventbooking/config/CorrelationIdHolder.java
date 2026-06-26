package com.portfolio.eventbooking.config;

/**
 * ThreadLocal carrier for the current request's correlation ID. Exists so the
 * service layer (AuditLogService, BookingService) can read the correlation ID
 * without taking a dependency on HttpServletRequest/MDC directly — keeps the
 * service layer testable without a servlet context.
 *
 * Webhook processing and the scheduled expiry sweep run outside any HTTP
 * request, so they set their own correlation ID explicitly (a fresh UUID per
 * webhook delivery, or a per-sweep-run ID) rather than reading this holder.
 */
public final class CorrelationIdHolder {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationIdHolder() {
    }

    public static void set(String correlationId) {
        CURRENT.set(correlationId);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
