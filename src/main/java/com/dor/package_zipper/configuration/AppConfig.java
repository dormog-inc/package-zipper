package com.dor.package_zipper.configuration;

import org.eclipse.aether.repository.RemoteRepository;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@ConfigurationProperties(prefix = "package-zipper")
@Data
public class AppConfig {
    private String mavenUrl;
    private String streamZipperUrl;
}