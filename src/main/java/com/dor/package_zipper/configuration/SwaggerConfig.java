package com.dor.package_zipper.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI(@Value("${springdoc.version}") String appVersion) {
        return new OpenAPI()
                .info(new Info()
                        .title("Package zipper API")
                        .version(appVersion)
                        .description(
                                "package-zipper is a java project for downloading and streaming java packages and there " +
                                        "dependencies as zip files from maven repository.")
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")));
    }
}
