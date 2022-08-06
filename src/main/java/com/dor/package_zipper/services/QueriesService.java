package com.dor.package_zipper.services;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.models.Artifact;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.dor.package_zipper.configuration.RepositoryConfig.GRADLE_PLUGINS_REPOSITORY;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueriesService {
    private final AppConfig appConfig;
    private List<String> defaultRemoteRepositoriesUrls;

    @PostConstruct
    public void init() {
        defaultRemoteRepositoriesUrls = List.of(
                appConfig.getMavenUrl(),
                GRADLE_PLUGINS_REPOSITORY
        );
    }

    public Flux<List<String>> getAllArtifactsOfGroup(String solrReqUrl) {
        return Flux.from(WebClient.builder()
                        .baseUrl(solrReqUrl)
                        .build()
                        .get()
                        .retrieve()
                        .bodyToMono(String.class))
                .map(solrResponse -> {
                    List<String> artifactIds = new ArrayList<>();
                    try {
                        ObjectNode jsonNode = new ObjectMapper().readValue(solrResponse, ObjectNode.class);
                        Optional<JsonNode> responseNode = Optional.ofNullable(jsonNode.get("response"));
                        if (responseNode.isPresent()) {
                            Optional<ArrayNode> docs = Optional.ofNullable(responseNode.get().withArray("docs"));
                            if (docs.isPresent()) {
                                for (var doc : docs.get()) {
                                    String artifactId = doc.get("id").asText().split(":")[1];
                                    artifactIds.add(artifactId);
                                }
                            }
                        }
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                    return artifactIds;
                });
    }

    public String getSolrReqUrl(String groupId) {
        return "https://search.maven.org/solrsearch/select?q=g:\"%s\"&rows=1000&fl=\"id\"&wt=json".formatted(groupId);
    }

    public String getMavenMetadataUrl(String repo, String groupId, String artifactId) {
        return "%s/%s/%s/maven-metadata.xml".formatted(repo, groupId.replace(".", "/"), artifactId);
    }

    public List<Artifact> flattenListsToList(List<List<Artifact>> lists) {
        return lists.stream().flatMap(List::stream).toList();
    }

    public Mono<List<Artifact>> getAllVersionsOfAnArtifact(String groupId, String baseUrl, String artifactId) {
        return getVersionsOfAnArtifact(groupId, baseUrl, artifactId, true, null);
    }

    public Mono<List<Artifact>> getLastVersionsOfAnArtifact(String groupId, String baseUrl, String artifactId, Integer numberOfVersions) {
        return getVersionsOfAnArtifact(groupId, baseUrl, artifactId, false, numberOfVersions);
    }

    public Mono<List<Artifact>> getVersionsOfAnArtifact(String groupId, String baseUrl, String artifactId, boolean areAllVersionsRequired, Integer numberOfVersions) {
        return Mono.from(Flux.fromIterable(defaultRemoteRepositoriesUrls)
                .flatMap(remoteRepository -> WebClient.builder()
                        .baseUrl(baseUrl)
                        .build()
                        .get()
                        .retrieve()
                        .bodyToMono(String.class)
                        .map(moduleMetadata -> {
                            try {
                                Metadata metadata = new MetadataXpp3Reader().read(new ByteArrayInputStream(moduleMetadata.getBytes(StandardCharsets.UTF_8)), false);
                                List<Artifact> artifacts = metadata.getVersioning().getVersions().stream()
                                        .map(version -> new Artifact(groupId, artifactId, version))
                                        .toList();
                                if (areAllVersionsRequired) {
                                    return artifacts;
                                } else {
                                    int fromIndex = Math.max((artifacts.size() - numberOfVersions), 0);
                                    return artifacts
                                            .subList(fromIndex, artifacts.size());
                                }
                            } catch (IOException | XmlPullParserException e) {
                                e.printStackTrace();
                            }
                            return new ArrayList<Artifact>();
                        })
                        .onErrorResume(WebClientResponseException.class,
                                ex -> Mono.just(new ArrayList<>()))
                        .defaultIfEmpty(new ArrayList<>()))
                .buffer()
                .map(this::flattenListsToList));
    }
}
