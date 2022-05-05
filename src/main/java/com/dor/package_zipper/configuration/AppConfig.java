package com.dor.package_zipper.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "package-zipper")
@Data
public class AppConfig {
    
    private String mavenUrl;
    private String streamZipperUrl;
}