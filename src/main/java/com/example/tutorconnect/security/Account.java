package com.example.tutorconnect.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record Account(long id, String name, String email, String passwordHash, String role)
    implements UserDetails {
  public String getUsername() {
    return email;
  }

  public String getPassword() {
    return passwordHash;
  }

  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role));
  }
}
