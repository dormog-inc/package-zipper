package com.dor.package_zipper.controller;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.models.*;
import com.dor.package_zipper.services.ArtifactResolverService;
import com.dor.package_zipper.services.GradlePluginsHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@Slf4j
@AllArgsConstructor
public class PackageZipperController {
    private final AppConfig appConfig;
    private final ArtifactResolverService artifactResolverService;

    @GetMapping(value = "/zip/stream")
    public ResponseEntity<Flux<DataBuffer>> getStreamArtifactZip(@RequestParam String groupId,
                                                                 @RequestParam String artifactId,
                                                                 @RequestParam String version,
                                                                 @RequestParam(defaultValue = "exactly") ShipmentLevel level) {
        return streamZippedArtifact(new Artifact(groupId, artifactId, version), level);
    }

    @GetMapping(value = "/zip/stream/{artifact}")
    public ResponseEntity<Flux<DataBuffer>> getStreamArtifactZip(@PathVariable String artifact,
                                                                 @RequestParam(defaultValue = "exactly") ShipmentLevel level) {
        return streamZippedArtifact(new Artifact(artifact), level);
    }

    @PostMapping("/zip/stream")
    public ResponseEntity<Flux<DataBuffer>> streamArtifactZip(@RequestBody Artifact artifact,
                                                              @RequestParam(defaultValue = "exactly") ShipmentLevel level) {
        return streamZippedArtifact(artifact, level);
    }

    @PostMapping("/zip/stream/multi")
    public ResponseEntity<Flux<DataBuffer>> streamArtifactsZip(@RequestBody List<Artifact> artifacts,
                                                               @RequestParam(defaultValue = "exactly") ShipmentLevel level) {
        List<ResolvingProcessServiceResult> resolvingProcessServiceResults = artifactResolverService.resolveArtifacts(artifacts, level);
        List<ZipRemoteEntry> zipRemoteEntries = new ArrayList<>();
        List<String> exceptions = new ArrayList<>();
        resolvingProcessServiceResults.forEach(resolvingProcessServiceResult -> {
            zipRemoteEntries.addAll(resolvingProcessServiceResult.getZipRemoteEntries());
            exceptions.add(resolvingProcessServiceResult.getException());
        });
        final Flux<DataBuffer> dataBufferFlux = getZipStream(zipRemoteEntries);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=multi.zip")
                .header("exceptions", exceptions.toArray(String[]::new))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(dataBufferFlux);
    }

    @GetMapping(value = "/zip/stream/plugin/")
    public ResponseEntity<Flux<DataBuffer>> streamGradlePlugins(@RequestParam String artifact,
                                                                @RequestParam String version,
                                                                @RequestParam(defaultValue = "heavy") ShipmentLevel level) {
        return streamZippedArtifact(GradlePluginsHandler.formatGradlePluginPomName(artifact, version), level);
    }

    @PostMapping("/zip/stream/plugin/multi")
    public ResponseEntity<Flux<DataBuffer>> streamArtifactsZip(
            @Schema( type = "string", example = "{\"io.github.bla.plugin-id\": \"1.0.3\"}")
            @RequestBody Map<String, String> mapOfArtifactAndVersion,
                                                               @RequestParam(defaultValue = "heavy") ShipmentLevel level) {
        List<ResolvingProcessServiceResult> resolvingProcessServiceResults = artifactResolverService.resolveArtifacts(
                mapOfArtifactAndVersion.entrySet().stream().map((entry) -> GradlePluginsHandler.formatGradlePluginPomName(entry.getKey(), entry.getValue())).toList(), level);
        List<ZipRemoteEntry> zipRemoteEntries = new ArrayList<>();
        List<String> exceptions = new ArrayList<>();
        resolvingProcessServiceResults.forEach(resolvingProcessServiceResult -> {
            zipRemoteEntries.addAll(resolvingProcessServiceResult.getZipRemoteEntries());
            exceptions.add(resolvingProcessServiceResult.getException());
        });
        final Flux<DataBuffer> dataBufferFlux = getZipStream(zipRemoteEntries);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=multi.zip")
                .header("exceptions", exceptions.toArray(String[]::new))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(dataBufferFlux);
    }

    public ResponseEntity<Flux<DataBuffer>> streamZippedArtifact(Artifact artifact, ShipmentLevel level) {
        ResolvingProcessServiceResult resolvingProcessServiceResult = artifactResolverService.resolveArtifact(artifact, level);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + artifact.getArtifactFullName() + ".zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("exception", resolvingProcessServiceResult.getException())
                .body(getZipStream(resolvingProcessServiceResult.getZipRemoteEntries()));
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
