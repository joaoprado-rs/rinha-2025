package com.joaoprado.rinha.worker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

  @Bean("metricsExecutor")
  public Executor metricsExecutor() {
    // Aumentar de 2 para 16 threads para suportar 20 workers sem gargalo
    return Executors.newFixedThreadPool(16);
  }
}
