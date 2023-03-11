package com.dor.package_zipper.services.streaming;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.models.*;
import com.dor.package_zipper.services.ArtifactResolverService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.repository.RemoteRepository;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.dor.package_zipper.configuration.RepositoryConfig.GRADLE_PLUGINS_REPOSITORY;

@RestController
@Slf4j
@RequiredArgsConstructor
public class PackageStreamsManager {
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

    public ResponseEntity<Flux<DataBuffer>> streamZippedArtifact(Artifact artifact, ShipmentLevel level, boolean shouldBringClassifiers) {
        return streamZippedArtifact(artifact, level, shouldBringClassifiers, defaultRemoteRepositoriesUrls);
    }

    public ResponseEntity<Flux<DataBuffer>> streamZippedArtifact(Artifact artifact, ShipmentLevel level, boolean shouldBringClassifiers, List<String> sessionsRemoteRepositoryUrls) {
        ResolvingProcessServiceResult resolvingProcessServiceResult = artifactResolverService.resolveArtifact(artifact, level, sessionsRemoteRepositoryUrls, shouldBringClassifiers);
        String fileName = artifact.getArtifactFullName();
        var response = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=%s.zip".formatted(fileName))
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        Optional.ofNullable(resolvingProcessServiceResult.getExceptionMessages()).ifPresent(exceptionMessages -> {
            response.header(HttpHeaders.WARNING, exceptionMessages.toArray(String[]::new));
        });
        return response
                .body(getZipStream(resolvingProcessServiceResult.getZipRemoteEntries()));
    }

    public ResponseEntity<Flux<DataBuffer>> getMultiFileStreamResponse(List<Artifact> artifactsList,
                                                                       ShipmentLevel level,
                                                                       List<String> sessionRemoteRepositoryList,
                                                                       boolean shouldBringClassifiers) {
        List<String> exceptions = new ArrayList<>();
        final Flux<DataBuffer> dataBufferFlux = getDataBufferFlux(artifactsList, level, sessionRemoteRepositoryList, exceptions, shouldBringClassifiers);
        String fileName = "multi-" + artifactsList.get(0).getArtifactId();
        var response = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=%s.zip".formatted(fileName))
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        if (!exceptions.isEmpty()) {
            response.header(HttpHeaders.WARNING, exceptions.toArray(String[]::new));
        }
        return response
                .body(dataBufferFlux);
    }

    public ResponseEntity<Flux<DataBuffer>> getResponseFromDataBufferFlux(Flux<DataBuffer> dataBufferFlux, String fileName, List<String> exceptions) {
        var response = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=%s.zip".formatted(fileName))
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        if (!exceptions.isEmpty()) {
            response.header(HttpHeaders.WARNING, exceptions.toArray(String[]::new));
        }
        return response
                .body(dataBufferFlux);
    }

    public Flux<DataBuffer> getDataBufferFlux(List<Artifact> artifactsList, ShipmentLevel level, List<String> sessionRemoteRepositoryList, List<String> exceptions, boolean shouldBringClassifiers) {
        List<ResolvingProcessServiceResult> resolvingProcessServiceResults = artifactResolverService.resolveArtifacts(artifactsList,
                level,
                sessionRemoteRepositoryList,
                shouldBringClassifiers);
        return parseResolvingProcessResults(resolvingProcessServiceResults, exceptions);
    }

    private Flux<DataBuffer> parseResolvingProcessResults(List<ResolvingProcessServiceResult> resolvingProcessServiceResults, List<String> exceptions) {
        List<ZipRemoteEntry> zipRemoteEntries = new ArrayList<>();
        resolvingProcessServiceResults.forEach(resolvingProcessServiceResult -> {
            zipRemoteEntries.addAll(resolvingProcessServiceResult.getZipRemoteEntries());
            if (Optional.ofNullable(exceptions).isPresent() && Optional.ofNullable(resolvingProcessServiceResult.getExceptionMessages()).isPresent()) {
                exceptions.addAll(resolvingProcessServiceResult.getExceptionMessages());
            }
        });
        return getZipStream(zipRemoteEntries);
    }

    public Flux<DataBuffer> getZipStream(List<ZipRemoteEntry> zipRemoteEntries) {
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
