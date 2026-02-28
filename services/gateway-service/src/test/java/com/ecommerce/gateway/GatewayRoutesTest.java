package com.ecommerce.gateway;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

@SpringBootTest(
        classes = GatewayServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "logging.file.name=target/gateway-test.log"
        })
class GatewayRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void routesAreConfiguredForAllBackendServices() {
        Map<String, String> routes = routeLocator.getRoutes()
                .collectList()
                .block()
                .stream()
                .collect(Collectors.toMap(Route::getId, route -> route.getUri().toString()));

        assertNotNull(routes);
        assertTrue(routes.size() >= 4);
        assertTrue(routes.get("product-service").startsWith("lb://product-service"));
        assertTrue(routes.get("inventory-service").startsWith("lb://inventory-service"));
        assertTrue(routes.get("order-service").startsWith("lb://order-service"));
        assertTrue(routes.get("notification-service").startsWith("lb://notification-service"));
    }
}
