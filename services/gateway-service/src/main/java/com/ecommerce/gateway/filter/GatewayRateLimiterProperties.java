package com.ecommerce.gateway.filter;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.gateway.rate-limiter")
@Validated
@Getter
@Setter
public class GatewayRateLimiterProperties {

    private boolean enabled = true;
    @Min(value = 1, message = "capacity must be greater than zero")
    private int capacity = 60;
    @Min(value = 1, message = "refillTokens must be greater than zero")
    private int refillTokens = 60;
    @NotNull(message = "refillDuration is required")
    private Duration refillDuration = Duration.ofSeconds(1);
}
