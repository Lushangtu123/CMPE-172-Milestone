package com.example.tutorconnect.notification;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
public class NotificationSchemaInitializer implements ApplicationRunner {
  private final DataSource dataSource;

  public NotificationSchemaInitializer(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public void run(ApplicationArguments args) {
    new ResourceDatabasePopulator(new ClassPathResource("notification-schema.sql"))
        .execute(dataSource);
  }
}
