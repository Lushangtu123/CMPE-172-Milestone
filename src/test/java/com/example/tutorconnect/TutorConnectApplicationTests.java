package com.example.tutorconnect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TutorConnectApplicationTests {

    @LocalServerPort
    private int port;

    private final RestClient restClient = RestClient.create();

    @Test
    void homeReadsProvidersFromDatabase() {
        String body = get("/");

        assertThat(body).contains("TutorConnect");
        assertThat(body).contains("\"providerCount\":2");
        assertThat(body).contains("\"serviceCount\":3");
        assertThat(body).contains("\"openSlotCount\":4");
        assertThat(body).contains("Jordan Lee");
        assertThat(body).contains("Priya Shah");
    }

    @Test
    void slotsReadsAvailabilityFromDatabase() {
        String body = get("/slots");

        assertThat(body).contains("Algebra Coaching");
        assertThat(body).contains("Physics Problem Solving");
        assertThat(body).contains("Essay Coaching");
        assertThat(body).contains("\"status\":\"OPEN\"");
        assertThat(body).contains("\"status\":\"BOOKED\"");
    }

    private String get(String path) {
        String body = restClient.get()
                .uri("http://localhost:" + port + path)
                .retrieve()
                .body(String.class);
        assertThat(body).isNotNull();
        return body;
    }
}
