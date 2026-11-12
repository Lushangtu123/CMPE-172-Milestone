package com.example.tutorconnect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootTest(
    properties = {"notifications.enabled=false", "notifications.recovery.enabled=false"})
class AiSchedulingTests {
  @Autowired ApplicationContext context;

  @Test
  void aiSchedulingDoesNotDependOnNotificationRecovery() {
    assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isNotEmpty();
  }

  @Test
  void slowAiDoesNotOccupyOnlyRecoverySchedulerThread() {
    assertThat(context.getBeansOfType(ThreadPoolTaskScheduler.class).values())
        .anyMatch(s -> s.getScheduledThreadPoolExecutor().getCorePoolSize() >= 2);
  }
}
