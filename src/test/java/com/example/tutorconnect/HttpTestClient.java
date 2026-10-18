package com.example.tutorconnect;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.*;
import java.net.http.*;
import java.util.regex.Pattern;

class HttpTestClient {
  final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
  final int port;
  String csrf;

  HttpTestClient(int port) {
    this.port = port;
  }

  HttpResponse<String> get(String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> post(String path, String body) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("X-CSRF-TOKEN", csrf == null ? "" : csrf)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  void token(String path) throws Exception {
    var response = get(path);
    assertThat(response.statusCode()).isEqualTo(200);
    var m = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(response.body());
    assertThat(m.find()).isTrue();
    csrf = m.group(1);
  }

  void login(String email) throws Exception {
    token("/login");
    assertThat(post("/login", "username=" + email + "&password=TutorDemo123%21").statusCode())
        .isEqualTo(302);
    token("/slots");
  }
}
