package com.dor.package_zipper.configuration;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.repository.RemoteRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@AllArgsConstructor
@EnableConfigurationProperties(AppConfig.class)
@Slf4j
public class RepositoryConfig {
    public static final String GRADLE_PLUGINS_REPOSITORY = "https://plugins.gradle.org/m2";
    public static final String MAVEN_REPOSITORY = "https://repo1.maven.org/maven2";
    private final AppConfig appConfig;

//    @Bean
//    public RemoteRepository newCentralRepository() {
//        return new RemoteRepository.Builder("central", "default", appConfig.getMavenUrl()).build();
//    }
    @Bean
    public List<RemoteRepository> repositoriesList() {
        return List.of(
                new RemoteRepository.Builder("central", "default", appConfig.getMavenUrl()).build(),
                new RemoteRepository.Builder("gradlePlugins", "default", GRADLE_PLUGINS_REPOSITORY).build()
        );
    }
}