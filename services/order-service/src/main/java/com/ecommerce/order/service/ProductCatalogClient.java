package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class ProductCatalogClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductCatalogClient.class);

    private final RestTemplate restTemplate;

    public ProductCatalogClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Retry(name = "productServiceRetry")
    @RateLimiter(name = "productServiceRateLimiter")
    @Bulkhead(name = "productServiceBulkhead", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "productServiceCircuitBreaker", fallbackMethod = "productServiceFallback")
    public ProductResponse getProductBySku(String skuCode) {
        try {
            ProductResponse product = restTemplate.getForObject(
                    "http://product-service/api/products/sku/{skuCode}",
                    ProductResponse.class,
                    skuCode);
            if (product == null) {
                throw new ProductNotFoundException("Product not found for sku " + skuCode);
            }
            return product;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ProductNotFoundException("Product not found for sku " + skuCode);
        } catch (RestClientException ex) {
            throw new ProductServiceUnavailableException(
                    "Product service request failed for sku " + skuCode,
                    ex);
        }
    }

    private ProductResponse productServiceFallback(String skuCode, Throwable throwable) {
        LOGGER.warn("Product service fallback triggered for skuCode={}. reason={}", skuCode, throwable.toString());
        throw new ProductServiceUnavailableException(
                "Product service is temporarily unavailable for sku " + skuCode,
                throwable);
    }
}
