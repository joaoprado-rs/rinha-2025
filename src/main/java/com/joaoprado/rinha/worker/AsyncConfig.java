package com.joaoprado.rinha.worker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

  @Bean("metricsExecutor")
  public Executor metricsExecutor() {
    return Executors.newFixedThreadPool(16);
  }
}
