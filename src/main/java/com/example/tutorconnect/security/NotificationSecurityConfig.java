package com.example.tutorconnect.security;

import com.example.tutorconnect.notification.NotificationSettings;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class NotificationSecurityConfig {
  @Bean
  @Order(1)
  SecurityFilterChain notificationSecurity(HttpSecurity http, NotificationSettings settings)
      throws Exception {
    return http.securityMatcher("/internal/**")
        .csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .addFilterBefore(
            new OncePerRequestFilter() {
              protected void doFilterInternal(
                  HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                  throws ServletException, IOException {
                String key = request.getHeader("X-Notification-Key");
                if (key == null
                    || !MessageDigest.isEqual(
                        settings.token().getBytes(StandardCharsets.UTF_8),
                        key.getBytes(StandardCharsets.UTF_8))) {
                  response.setStatus(401);
                  response.setContentType("application/json");
                  response
                      .getWriter()
                      .write(
                          "{\"status\":401,\"message\":\"Notification service authentication"
                              + " required.\"}");
                  return;
                }
                chain.doFilter(request, response);
              }
            },
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
