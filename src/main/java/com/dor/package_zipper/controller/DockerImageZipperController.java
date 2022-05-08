package com.dor.package_zipper.controller;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.models.ImageDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.api.DockerClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

@RestController
@Slf4j
@AllArgsConstructor
public class DockerImageZipperController {
    private final AppConfig appConfig;
    private final DockerClient dockerClient;

    @PostMapping("/zip/stream")
    public ResponseEntity<StreamingResponseBody> StreamDockerImageTar(@RequestBody String image) {
        return null;
    }

    // Get Docker authentication endpoint when it is required
    public Mono<String> getDockerAuthenticationEndpoint(ImageDetails imageDetails) {
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create(String.format("https://%s/v2/", imageDetails.getRegistryUrl()))).build();
//                .build();
        return WebClient.create(String.format("https://%s/v2/", imageDetails.getRegistryUrl()))
                .get()
                .retrieve()
                .toBodilessEntity()
                .map(response -> {
//                    Map.entry(response.getStatusCode(), response.getHeaders())
                    String regService, authUrl = "";
                    if (response.getStatusCodeValue() == 401) {
                        authUrl = response.getHeaders().get("WWW-Authenticate").get(0).split("\"")[1];
                    }
                    try {
                        // TODO: verify that the response from the header() func is not already split
                        regService = response.getHeaders().get("WWW-Authenticate").get(0).split("\"")[3];
                    } catch (Exception e) {
                        regService = "";
                    }
                    return String.format("%s?service=%s&scope=repository:%s:pull", authUrl, regService, imageDetails.getRepository());
                });
    }

    // Get Docker token (this function is useless for unauthenticated registries like Microsoft)
    public Mono<Mono<Map<String, String>>> getAuthHead(ImageDetails imageDetails, String type) {
//        ClientRequest request = ClientRequest.create(HttpMethod.GET, url).build();
        return getDockerAuthenticationEndpoint(imageDetails)
                .flatMap(uri -> WebClient.create(imageDetails.getRegistryUrl())
                        .get()
                        .uri(uri)
                        .exchangeToMono(clientResponse ->
                                clientResponse.bodyToMono(String.class)
                                        .map(body -> {
                                            try {
                                                final ObjectNode node = new ObjectMapper().readValue(body, ObjectNode.class);
                                                String accessToken = node.get("token").asText();
                                                return Mono.just(Map.of("Authorization", "Bearer " + accessToken, "Accept", type));
                                            } catch (JsonProcessingException e) {
                                                e.printStackTrace();
                                                return Mono.empty();
                                            }
                                        })));
    }
}
