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
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

@RestController("/docker")
@Slf4j
@AllArgsConstructor
public class DockerImageZipperController {
    private final AppConfig appConfig;
    private final DockerClient dockerClient;

    @PostMapping("/zip/docker-stream")
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
    public Mono<Map<String, String>> getAuthHead(ImageDetails imageDetails, String type) {
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
                                                return Map.of("Authorization", "Bearer " + accessToken, "Accept", type);
                                            } catch (JsonProcessingException e) {
                                                e.printStackTrace();
                                                return Map.of();
                                            }
                                        })));
    }

    // Fetch manifest v2 and get image layer digests
    public Mono<ClientResponse> fetchManifest(ImageDetails imageDetails) {
        return getAuthHead(imageDetails, "application/vnd.docker.distribution.manifest.v2+json")
                .flatMap(map -> WebClient.create(String.format("https://%s/v2/%s/manifests/%s", imageDetails.getRegistryUrl(),
                                imageDetails.getRepository(),
                                imageDetails.getTag())).get()
                        .header("Authorization", map.get("Authorization"))
                        .header("Accept", map.get("Accept"))
                        .exchangeToMono(clientResponse -> {
                            if (clientResponse.statusCode().value() != 200) {
                                // TODO: we can add here a fallback call to fetch manifests by tag (with @digest)
                                return Mono.error(new ManifestsCouldNotBeFetchedException("[-] Cannot fetch manifest"));
                            } else {
                                return Mono.just(clientResponse);
                            }
                        }));
    }
}
