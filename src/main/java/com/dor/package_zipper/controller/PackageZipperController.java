package com.dor.package_zipper.controller;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.models.*;
import com.dor.package_zipper.services.ArtifactResolverService;
import com.dor.package_zipper.services.GradlePluginsHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.repository.RemoteRepository;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

import static com.dor.package_zipper.configuration.RepositoryConfig.GRADLE_PLUGINS_REPOSITORY;

@RestController
@Slf4j
@AllArgsConstructor
public class PackageZipperController {
    private final AppConfig appConfig;
    private final ArtifactResolverService artifactResolverService;
    private final List<RemoteRepository> remoteRepositoryList;
    private List<String> defaultRemoteRepositoriesUrls;

    @PostConstruct
    public void init() {
        defaultRemoteRepositoriesUrls = List.of(
                appConfig.getMavenUrl(),
                GRADLE_PLUGINS_REPOSITORY
        );
    }

    @Tag(name = "artifacts")
    @GetMapping(value = "/zip/artifact")
    public ResponseEntity<Flux<DataBuffer>> getStreamArtifactZip(
            @Schema(type = "string", example = "org.apache.solr")
            @RequestParam String groupId,
            @Schema(type = "string", example = "solr-core")
            @RequestParam String artifactId,
            @Schema(type = "string", example = "8.5.1")
            @RequestParam String version,
            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level) {
        return streamZippedArtifact(new Artifact(groupId, artifactId, version), level);
    }

    @Tag(name = "artifacts")
    @GetMapping(value = "/zip/artifact/{artifact}")
    public ResponseEntity<Flux<DataBuffer>> getStreamArtifactZip(
            @Schema(type = "string", example = "org.jetbrains:annotations:23.0.0")
            @PathVariable String artifact,
            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level,
            @RequestParam(required = false) List<String> customRepositoriesList) {
        List<String> sessionsRemoteRepositoryList = this.remoteRepositoryList.stream().map(RemoteRepository::getUrl).distinct().toList();
        sessionsRemoteRepositoryList.addAll(customRepositoriesList);
        return streamZippedArtifact(new Artifact(artifact), level, sessionsRemoteRepositoryList);
    }

    @Tag(name = "artifacts")
    @PostMapping("/zip/artifact/multi")
    public ResponseEntity<Flux<DataBuffer>> streamArtifactsZip(
            @Schema(type = "list", example = "[\"org.jetbrains:annotations:23.0.0\", \"org/jetbrains/annotations/22.0.0\"]")
            @RequestBody List<String> artifactStringList,
            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level,
            @RequestBody List<String> customRepositoriesList) {
        List<String> sessionsRemoteRepositoryList = this.remoteRepositoryList.stream().map(RemoteRepository::getUrl).distinct().toList();
        sessionsRemoteRepositoryList.addAll(customRepositoriesList);
        var artifactsList = artifactStringList.stream().map(Artifact::new).toList();
        List<ResolvingProcessServiceResult> resolvingProcessServiceResults = artifactResolverService.resolveArtifacts(artifactsList,
                level,
                sessionsRemoteRepositoryList);
        List<String> exceptions = new ArrayList<>();
        final Flux<DataBuffer> dataBufferFlux = parseResolvingProcessResults(resolvingProcessServiceResults, exceptions);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=multi-%s.zip".formatted(artifactsList.get(0).getArtifactId()))
                .header(HttpHeaders.WARNING, exceptions.toArray(String[]::new))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(dataBufferFlux);
    }

    @GetMapping(value = "/zip/plugin/")
    @Tag(name = "plugins")
    public ResponseEntity<Flux<DataBuffer>> streamGradlePlugins(@RequestParam String artifact,
                                                                @RequestParam String version,
                                                                @RequestParam(defaultValue = "heavy") ShipmentLevel level,
                                                                @RequestParam(defaultValue = "heavy") List<RemoteRepository> remoteRepositories) {
        return streamZippedArtifact(GradlePluginsHandler.formatGradlePluginPomName(artifact, version), level);
    }

    @Tag(name = "plugins")
    @PostMapping("/zip/plugin/multi")
    public ResponseEntity<Flux<DataBuffer>> streamArtifactsZip(
            @Schema(type = "object", example = "{\"io.github.bla.plugin-id\": \"1.0.3\"}")
            @RequestBody Map<String, String> mapOfArtifactAndVersion,
            @RequestParam(defaultValue = "HEAVY") ShipmentLevel level) {
        List<ResolvingProcessServiceResult> resolvingProcessServiceResults = artifactResolverService.resolveArtifacts(
                mapOfArtifactAndVersion.entrySet().stream().map((entry) -> GradlePluginsHandler.formatGradlePluginPomName(entry.getKey(), entry.getValue())).toList(),
                level,
                defaultRemoteRepositoriesUrls);
        List<String> exceptions = new ArrayList<>();
        final Flux<DataBuffer> dataBufferFlux = parseResolvingProcessResults(resolvingProcessServiceResults, exceptions);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=multi-%s.zip".formatted(mapOfArtifactAndVersion.keySet().stream().toList().get(0)))
                .header(HttpHeaders.WARNING, exceptions.toArray(String[]::new))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(dataBufferFlux);
    }

    private Flux<DataBuffer> parseResolvingProcessResults(List<ResolvingProcessServiceResult> resolvingProcessServiceResults, List<String> exceptions) {
        List<ZipRemoteEntry> zipRemoteEntries = new ArrayList<>();
        resolvingProcessServiceResults.forEach(resolvingProcessServiceResult -> {
            zipRemoteEntries.addAll(resolvingProcessServiceResult.getZipRemoteEntries());
            exceptions.addAll(resolvingProcessServiceResult.getExceptionMessages());
        });
        return getZipStream(zipRemoteEntries);
    }


    private ResponseEntity<Flux<DataBuffer>> streamZippedArtifact(Artifact artifact, ShipmentLevel level, List<String> sessionsRemoteRepositoryUrls) {
        ResolvingProcessServiceResult resolvingProcessServiceResult = artifactResolverService.resolveArtifact(artifact, level, sessionsRemoteRepositoryUrls);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + artifact.getArtifactFullName() + ".zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.WARNING, resolvingProcessServiceResult.getExceptionMessages().toArray(String[]::new))
                .body(getZipStream(resolvingProcessServiceResult.getZipRemoteEntries()));
    }

    public ResponseEntity<Flux<DataBuffer>> streamZippedArtifact(Artifact artifact, ShipmentLevel level) {
        return streamZippedArtifact(artifact, level, defaultRemoteRepositoriesUrls);
    }

    private Flux<DataBuffer> getZipStream(List<ZipRemoteEntry> zipRemoteEntries) {
        Mono<ZipStreamerBody> body = Mono.just(new ZipStreamerBody(zipRemoteEntries.stream().distinct().toList()))
                .doOnNext(zipStreamerBody -> {
                    try {
                        log.info("zip streamer request body: {}", new ObjectMapper().writeValueAsString(zipStreamerBody));
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                });
        log.info("the stream zipper url is: " + appConfig.getStreamZipperUrl());
        return WebClient.create(appConfig.getStreamZipperUrl())
                .post().uri("/download")
                .body(BodyInserters.fromProducer(body, ZipRemoteEntry.class))
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .bodyToFlux(DataBuffer.class);
    }
}
