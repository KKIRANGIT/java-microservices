package com.ecommerce.gateway.filter;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GlobalInMemoryRateLimiterFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalInMemoryRateLimiterFilter.class);

    private final GatewayRateLimiterProperties properties;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public GlobalInMemoryRateLimiterFilter(
            GatewayRateLimiterProperties properties,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route == null ? "unresolved-route" : route.getId();
        String key = routeId + "|" + resolveClientKey(exchange);

        TokenBucket bucket = buckets.computeIfAbsent(
                key,
                ignored -> new TokenBucket(
                        properties.getCapacity(),
                        properties.getRefillTokens(),
                        properties.getRefillDuration()));

        if (bucket.tryConsume()) {
            meterRegistry.counter(
                            "ecommerce.gateway.ratelimit.requests",
                            "route",
                            routeId,
                            "result",
                            "allowed")
                    .increment();
            return chain.filter(exchange);
        }

        meterRegistry.counter(
                        "ecommerce.gateway.ratelimit.requests",
                        "route",
                        routeId,
                        "result",
                        "blocked")
                .increment();
        LOGGER.warn("Gateway rate limit exceeded. routeId={}, client={}", routeId, key);
        return tooManyRequests(exchange, routeId);
    }

    @Override
    public int getOrder() {
        return -200;
    }

    private String resolveClientKey(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown-client";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, String routeId) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"timestamp":"%s","status":429,"error":"Too Many Requests","message":"Gateway rate limit exceeded for route %s"}
                """
                .formatted(Instant.now(), routeId);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    private static final class TokenBucket {
        private final int capacity;
        private final int refillTokens;
        private final long refillNanos;
        private final AtomicInteger tokens;
        private final AtomicLong lastRefillNanos;

        private TokenBucket(int capacity, int refillTokens, Duration refillDuration) {
            this.capacity = Math.max(capacity, 1);
            this.refillTokens = Math.max(refillTokens, 1);
            this.refillNanos = Math.max(refillDuration.toNanos(), 1L);
            this.tokens = new AtomicInteger(this.capacity);
            this.lastRefillNanos = new AtomicLong(System.nanoTime());
        }

        private boolean tryConsume() {
            refillIfNeeded();
            while (true) {
                int current = tokens.get();
                if (current <= 0) {
                    return false;
                }
                if (tokens.compareAndSet(current, current - 1)) {
                    return true;
                }
            }
        }

        private void refillIfNeeded() {
            long now = System.nanoTime();
            long previous = lastRefillNanos.get();
            long elapsed = now - previous;
            if (elapsed < refillNanos) {
                return;
            }

            long periods = elapsed / refillNanos;
            if (periods <= 0) {
                return;
            }

            if (lastRefillNanos.compareAndSet(previous, previous + (periods * refillNanos))) {
                int tokensToAdd = Math.toIntExact(Math.min((long) refillTokens * periods, Integer.MAX_VALUE));
                tokens.updateAndGet(current -> Math.min(capacity, current + tokensToAdd));
            }
        }
    }
}
