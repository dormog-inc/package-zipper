//package com.dor.package_zipper.controller;
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.HttpHeaders;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//import reactor.core.publisher.Mono;
//import reactor.netty.http.client.HttpClient;
//import reactor.netty.http.client.HttpClientResponse;
//import reactor.util.function.Tuple2;
//import reactor.util.function.Tuples;
//
//import java.util.Arrays;
//import java.util.Base64;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//@RestController
//@Slf4j
//class SyncDockerPullController {
//    private static final String DOCKER_DEFAULT_AUTH_URL = "auth.docker.io/token";
//    private static final String DOCKER_DEFAULT_SERVER_URL = "registry-1.docker.io";
//    private static final String DOCKER_DEFAULT_REPO = "library";
//    private static final Pattern REALM_PATTERN = Pattern.compile("realm=\"([^\"]*)\"");
//
//    @GetMapping("/pull")
//    public Mono<String> pullImage(
//            @RequestParam String image,
//            @RequestParam(required = false) String username,
//            @RequestParam(required = false) String password) {
//
//        String[] imgParts = image.split("/");
//        String registryUrl, repository, img, tag;
//
//        if (imgParts.length > 1 && (imgParts[0].contains(".") || imgParts[0].contains(":"))) {
//            registryUrl = imgParts[0];
//            repository = imgParts[1];
//
//            String[] remainingParts = (String.join("/", Arrays.copyOfRange(imgParts, 2, imgParts.length))).split(":");
//            img = remainingParts[0];
//            tag = remainingParts[1];
//        } else {
//            registryUrl = DOCKER_DEFAULT_SERVER_URL;
//
//            if (imgParts.length > 1) {
//                repository = imgParts[0];
//                String[] splitData = imgParts[1].split(":");
//                img = ""; // FIXME: Image name on docker hub is actually the repository url
//                tag = splitData[1];
//            } else {
//                img = ""; // FIXME: Image name on docker hub is actually the repository url
//                String[] splitData = imgParts[0].split(":");
//                tag = splitData[1];
//                repository = "library/" + splitData[0];
//            }
//        }
//
//        log.info("Docker image: {}", img);
//        log.info("Docker tag: {}", tag);
//        log.info("Repository: {}", repository);
//        log.info("Server URL: https://{}", registryUrl);
//
//        return getEndpointRegistry(registryUrl, repository)
//                .flatMap(endpoint -> {
//                    log.info("Registry endpoint: {}", endpoint);
//                    return getAuthHeader(endpoint, username, password)
//                            .map(header -> Tuples.of(endpoint, header));
//                })
//                .flatMap(tuple -> {
//                    String endpoint = tuple.getT1();
//                    HttpHeaders header = tuple.getT2();
//
//                    String manifestRequest = String.format("https://%s/v2/%s/%s/manifests/%s", registryUrl, repository, img, tag);
//                    return downloadImage(header, manifestRequest)
//                            .flatMap((r) -> {
//                                var response = r.getT1();
//                                var body = r.getT1();
//                                if(response.status().code() != 200) {
//                                    log.error("[-] Cannot fetch manifest for {} [HTTP {}]", image, response.status().code());
//                                    return getAuthHeader(endpoint, username, password)
//                                            .flatMap(retryHeader -> {
//                                                if(log.isDebugEnabled()) {
//                                                    log.debug("generated_req: {}", manifestRequest);
//                                                    log.debug("auth headers: {}", retryHeader);
//                                                }
//                                                return downloadImage(retryHeader, manifestRequest)
//                                                        .flatMap(retryR -> {
//                                                            var retryResponse = retryR.getT1();
//                                                            var retryBody = retryR.getT2();
//
//                                                            if(retryResponse.status().code() == 200) {
//                                                                ObjectMapper mapper = new ObjectMapper();
//                                                                JsonNode jsonNode = null;
//                                                                try {
//                                                                    jsonNode = mapper.readTree(retryBody);
//                                                                } catch (JsonProcessingException e) {
//                                                                    throw new RuntimeException(e);
//                                                                }
//                                                                return processManifests(jsonNode);
//                                                            } else if (retryResponse.status().code() == 401) {
//                                                                log.error("Authentication needed!");
//                                                                return Mono.error(new RuntimeException("Authentication required!"));
//                                                            } else {
//                                                                log.error("Error when getting manifest response status code : {}", retryResponse.status().code());
//                                                                return Mono.error(new RuntimeException("Error when getting manifest!"));
//                                                            }
//                                                        });
//                                            });
//                                }
//                                return Mono.just("Image pull successful!");
//                            });
//                });
//    }
//
//    private Mono<String> processManifests(JsonNode jsonNode) {
//        JsonNode manifests = jsonNode.get("manifests");
//        if (manifests != null) {
//            for (JsonNode manifest : manifests) {
//                JsonNode platform = manifest.get("platform");
//                StringBuilder sb = new StringBuilder();
//                platform.fields().forEachRemaining(field -> {
//                    sb.append(field.getKey()).append(": ").append(field.getValue()).append(", ");
//                });
//                sb.append("digest: ").append(manifest.get("digest"));
//                log.info(sb.toString());
//            }
//        }
//        return Mono.just("Manifests processed!");
//    }
//
//    private Mono<String> getEndpointRegistry(String url, String repository) {
//        return HttpClient.create().get().uri("https://" + url + "/v2/")
//                .responseSingle((response, content) -> {
//                    if (response.status().code() == 401) {
//                        String wwwAuthenticate = response.responseHeaders().get("WWW-Authenticate");
//                        Matcher matcher = REALM_PATTERN.matcher(wwwAuthenticate);
//                        if (matcher.find()) {
//                            String realmAddress = matcher.group(1);
//                            return Mono.just(realmAddress);
//                        }
//                    }
//                    return Mono.error(new RuntimeException("Unable to find the realm address"));
//                });
//    }
//
//    private Mono<HttpHeaders> getAuthHeader(String registryEndpoint, String username, String password) {
//        return HttpClient.create()
//                .headers(headers -> {
//                    if (username != null && password != null) {
//                        String encoded = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
//                        headers.add("Authorization", "Basic " + encoded);
//                    }
//                })
//                .get()
//                .uri(registryEndpoint)
//                .responseSingle((response, content) -> {
//                    if (response.status().code() == 200) {
//                        return content.asString().flatMap(body -> {
//                            // Extract token and setup header.
//                            String token = body;  // Parsing logic should be implemented for JSON responses.
//                            HttpHeaders headers = new HttpHeaders();
//                            headers.set("Authorization", "Bearer " + token);
//                            return Mono.just(headers);
//                        });
//                    }
//                    return Mono.error(new RuntimeException("Failed to get authorization"));
//                });
//    }
//
//    private Mono<Tuple2<HttpClientResponse, String>> downloadImage(HttpHeaders headers, String image) {
//        return HttpClient.create()
//                .headers(httpHeaders -> httpHeaders.add("Authorization", headers.getFirst("Authorization")))
//                .get()
//                .uri(DOCKER_DEFAULT_SERVER_URL + "/v2/" + image + "/manifests/latest")
//                .responseSingle((response, content) ->
//                        content.asString()
//                                .map(body -> Tuples.of(response, body))
//                );
//    }
//
//}
