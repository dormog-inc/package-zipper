package com.dor.package_zipper.controller;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.models.Artifact;
import com.dor.package_zipper.models.ShipmentLevel;
import com.dor.package_zipper.models.ZipRemoteEntry;
import com.dor.package_zipper.models.ZipStreamerBody;
import com.dor.package_zipper.services.ArtifactResolverService;
import com.google.gson.Gson;
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

import java.util.List;

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
        final Flux<DataBuffer> dataBufferFlux = getZipStream(
                artifactResolverService.resolveArtifacts(artifacts, level));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=multi.zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(dataBufferFlux);
    }

    public ResponseEntity<Flux<DataBuffer>> streamZippedArtifact(Artifact artifact, ShipmentLevel level) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + artifact.getArtifactFullName() + ".zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(getZipStream(artifactResolverService.resolveArtifact(artifact, level)));
    }

    private Flux<DataBuffer> getZipStream(List<ZipRemoteEntry> zipRemoteEntries) {
        Gson gson = new Gson();
        Mono<ZipStreamerBody> body = Mono.just(new ZipStreamerBody(zipRemoteEntries.stream().distinct().toList()))
                .doOnNext(a -> log.info("zip streamer request body: {}", gson.toJson(a)));
        log.info("the stream zipper url is: " + appConfig.getStreamZipperUrl());
        return WebClient.create(appConfig.getStreamZipperUrl())
                .post().uri("/download")
                .body(BodyInserters.fromProducer(body, ZipRemoteEntry.class))
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .bodyToFlux(DataBuffer.class);
    }
}
