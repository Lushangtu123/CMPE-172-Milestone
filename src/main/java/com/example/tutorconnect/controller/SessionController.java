package com.example.tutorconnect.controller;

import com.example.tutorconnect.security.Account;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {
  public record PublicAccount(long id, String name, String email, String role) {}

  public record Csrf(String headerName, String token) {}

  public record SessionView(PublicAccount account, Csrf csrf) {}

  @GetMapping("/api/session")
  public ResponseEntity<SessionView> session(
      @AuthenticationPrincipal Account account, CsrfToken csrf) {
    var user =
        account == null
            ? null
            : new PublicAccount(account.id(), account.name(), account.email(), account.role());
    // Resolving the deferred token creates a session; call again after login/logout rotation.
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new SessionView(user, new Csrf(csrf.getHeaderName(), csrf.getToken())));
  }
}
