package com.github.dormog.packagezipper.configuration;

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
                        .title("Package Zipper API")
                        .version(appVersion)
                        .description(
                                "Package Zipper is a Java project for downloading and streaming java packages and there " +
                                        "dependencies as zip files from maven repository.<br><br>" +
                                "The project is intended to download dependencies and let the user choose the resolution level. The available levels are: " +
                                "<br>EXACTLY is the default and brings all relevant dependencies the same as Maven \\ Gradle would (including classifiers). " +
                                "<br>SINGLE is for bringing a jar & pom only." +
                                "<br>HEAVY is for bringing all jars, even optional or jars that their version was just referenced in the bom.")
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")));
    }
}
