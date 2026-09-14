package com.example.appstore.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * Two stateless chains ({@code docs/architecture/security.md}): the management server as a whole (ADR-0044), protected
 * by the network, and the public port with the JWT resource server and a catch-all {@code denyAll}.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    static final String REQUIRED_AUTHORITY = "SCOPE_" + TokenService.SCOPE;

    @Bean
    @Order(1)
    SecurityFilterChain managementChain(HttpSecurity http) throws Exception {
        http.securityMatcher(SecurityConfiguration::isManagementRequest)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http, JwtDecoder jwtDecoder, JsonMapper jsonMapper) throws Exception {
        ProblemResponses problems = new ProblemResponses(jsonMapper);
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/auth/token")
                        .permitAll()
                        .requestMatchers("/livez", "/readyz", "/error")
                        .permitAll()
                        // disabled entirely in the prod profile
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/api/v1/**")
                        .hasAuthority(REQUIRED_AUTHORITY)
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .addFilterAfter(new ClientIdMdcFilter(), BearerTokenAuthenticationFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /** True for requests on the separate management server; with a shared port nothing matches (safe default). */
    static boolean isManagementRequest(HttpServletRequest request) {
        WebApplicationContext context =
                WebApplicationContextUtils.getWebApplicationContext(request.getServletContext());
        return context != null && WebServerApplicationContext.hasServerNamespace(context, "management");
    }
}
