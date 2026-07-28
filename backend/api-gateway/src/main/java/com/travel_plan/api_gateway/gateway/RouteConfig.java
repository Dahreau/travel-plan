package com.travel_plan.api_gateway.gateway;

import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class RouteConfig {

    @Bean
    public RouterFunction<ServerResponse> authServiceRoutes(JwtGatewayFilterFunction jwtFilter) {
        RouterFunction<ServerResponse> login = route("auth-service-login")
                .POST("/api/auth/login", http())
                .filter(lb("auth-service"))
                .build();

        RouterFunction<ServerResponse> protectedRoutes = route("auth-service")
                .route(path("/api/auth/**"), http())
                .filter(lb("auth-service"))
                .filter(jwtFilter)
                .build();

        return login.and(protectedRoutes);
    }

    @Bean
    public RouterFunction<ServerResponse> userServiceRoutes(JwtGatewayFilterFunction jwtFilter) {
        return route("user-service")
                .route(path("/api/users/**"), http())
                .filter(lb("user-service"))
                .filter(jwtFilter)
                .build();
    }
}
