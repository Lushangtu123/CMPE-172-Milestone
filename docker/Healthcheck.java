import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class Healthcheck {
  public static void main(String[] args) {
    try {
      var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
      var request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/health"))
              .timeout(Duration.ofSeconds(2))
              .GET()
              .build();
      if (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() != 200)
        System.exit(1);
    } catch (Exception e) {
      System.exit(1);
    }
  }
}
