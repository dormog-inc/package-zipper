package com.dor.package_zipper.configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.repository.RemoteRepository;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AllArgsConstructor
@EnableConfigurationProperties(AppConfig.class)
@Slf4j
public class RepositoryConfig {
    private final AppConfig appConfig;

    @Bean
    public RemoteRepository newCentralRepository() {
        return new RemoteRepository.Builder("central", "default", appConfig.getMavenUrl()).build();
    }
}