package com.example.tutorconnect.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService userDetailsService(JdbcTemplate jdbc) {
    return email ->
        jdbc
            .query(
                "SELECT user_id,name,email,password_hash,role FROM users WHERE email=?",
                (rs, n) ->
                    new Account(
                        rs.getLong("user_id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getString("role")),
                email)
            .stream()
            .findFirst()
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
  }

  @Bean
  @org.springframework.core.annotation.Order(2)
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/",
                        "/login",
                        "/slots",
                        "/assets/**",
                        "/api/home",
                        "/api/slots",
                        "/api/slots/*",
                        "/api/catalog",
                        "/api/session",
                        "/health",
                        "/error")
                    .permitAll()
                    .requestMatchers("/customer/**", "/api/customer/**")
                    .hasRole("CUSTOMER")
                    .requestMatchers("/provider/**", "/api/provider/**")
                    .hasRole("PROVIDER")
                    .anyRequest()
                    .authenticated())
        .formLogin(
            f ->
                f.loginPage("/login")
                    .successHandler(loginDestination())
                    .failureHandler(
                        (req, res, ex) -> {
                          if (wantsJson(req)) {
                            res.setStatus(401);
                            res.setContentType("application/json");
                            res.getWriter()
                                .write(
                                    "{\"status\":401,\"message\":\"Invalid email or password.\"}");
                          } else res.sendRedirect("/login?error");
                        })
                    .permitAll())
        .logout(
            l ->
                l.logoutSuccessHandler(
                    (req, res, auth) -> {
                      if (wantsJson(req)) res.setStatus(204);
                      else res.sendRedirect("/login?logout");
                    }))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          if (req.getRequestURI().startsWith("/api/") || wantsJson(req)) {
                            res.setStatus(401);
                            res.setContentType("application/json");
                            res.getWriter()
                                .write("{\"status\":401,\"message\":\"Please log in.\"}");
                          } else res.sendRedirect("/login");
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          res.setStatus(403);
                          if (req.getRequestURI().startsWith("/api/") || wantsJson(req)) {
                            res.setContentType("application/json");
                            res.getWriter()
                                .write("{\"status\":403,\"message\":\"Access denied.\"}");
                          } else {
                            res.setContentType("text/html;charset=UTF-8");
                            res.getWriter()
                                .write(
                                    "<!DOCTYPE html><html lang=\"en\"><title>Access"
                                        + " denied</title><h1>Access denied</h1><p>This action is"
                                        + " not available for your account.</p><a"
                                        + " href=\"/slots\">Back to slots</a></html>");
                          }
                        }));
    return http.build();
  }

  private SavedRequestAwareAuthenticationSuccessHandler loginDestination() {
    var handler =
        new SavedRequestAwareAuthenticationSuccessHandler() {
          @Override
          public void onAuthenticationSuccess(
              HttpServletRequest request,
              HttpServletResponse response,
              Authentication authentication)
              throws IOException, ServletException {
            if (wantsJson(request)) {
              clearAuthenticationAttributes(request);
              request.getSession().removeAttribute("SPRING_SECURITY_SAVED_REQUEST");
              response.setStatus(204);
            } else super.onAuthenticationSuccess(request, response, authentication);
          }

          @Override
          protected String determineTargetUrl(
              HttpServletRequest request,
              HttpServletResponse response,
              Authentication authentication) {
            return authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_PROVIDER"))
                ? "/provider/dashboard"
                : "/slots";
          }
        };
    handler.setAlwaysUseDefaultTargetUrl(true);
    return handler;
  }

  private static boolean wantsJson(HttpServletRequest request) {
    String accept = request.getHeader("Accept");
    return accept != null && accept.contains("application/json");
  }
}
