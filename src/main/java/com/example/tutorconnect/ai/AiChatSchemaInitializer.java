package com.example.tutorconnect.ai;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
class AiChatSchemaInitializer implements ApplicationRunner {
  private final DataSource dataSource;

  AiChatSchemaInitializer(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public void run(ApplicationArguments args) {
    new ResourceDatabasePopulator(new ClassPathResource("ai-chat-schema.sql")).execute(dataSource);
  }
}
