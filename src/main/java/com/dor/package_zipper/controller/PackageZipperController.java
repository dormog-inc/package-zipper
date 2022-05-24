package com.dor.package_zipper.controller;

import java.util.List;
import java.util.stream.Collectors;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.models.Artifact;
import com.dor.package_zipper.models.ShipmentLevel;
import com.dor.package_zipper.models.ZipRemoteEntry;
import com.dor.package_zipper.models.ZipStreamerBody;
import com.dor.package_zipper.services.ArtifactResolverService;
import com.google.gson.Gson;

import org.springframework.core.codec.ByteBufferEncoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
@AllArgsConstructor
public class PackageZipperController {
    private final AppConfig appConfig;
    private final ArtifactResolverService artifactResolverService;

    @GetMapping(value = "/zip/stream")
    public ResponseEntity<StreamingResponseBody> getStreamArtifactZip(@RequestParam String groupId,
                                                                      @RequestParam String artifactId, @RequestParam String version,
                                                                      @RequestParam(defaultValue = "exactly") ShipmentLevel level) {
        Artifact artifact = new Artifact(groupId, artifactId, version);
        return streamZippedArtifact(artifact, level);
    }

    @GetMapping(value = "/zip/stream/{artifact}")
    public ResponseEntity<StreamingResponseBody> getStreamArtifactZip(@PathVariable String artifact,
                                                                      @RequestParam(defaultValue = "exactly") ShipmentLevel level) {
        Artifact artifactObj = new Artifact(artifact);

        return streamZippedArtifact(artifactObj, level);
    }

    @GetMapping(value = "/zip/stream/{artifact}/paginated")
    public ResponseEntity<StreamingResponseBody> getPaginatedStreamArtifactZip(@PathVariable String artifact,
                                                                      @RequestParam(defaultValue = "3") int pages,
                                                                      @RequestParam(defaultValue = "exactly") ShipmentLevel level) {
        Artifact artifactObj = new Artifact(artifact);

        return streamZippedArtifact(artifactObj, level);
    }

    @PostMapping("/zip/stream/multi")
    public ResponseEntity<StreamingResponseBody> streamArtifactsZip(@RequestBody List<Artifact> artifacts,
                                                                    @RequestParam(defaultValue = "exactly") ShipmentLevel level) {
        final Flux<DataBuffer> dataBufferFlux = getZipStream(
            artifactResolverService.resolveArtifacts(artifacts, level));
        StreamingResponseBody stream = output -> DataBufferUtils
            .write(dataBufferFlux,
                output)
            .doOnError(e -> log.error("Error writing to stream", e))
            .doOnComplete(() -> log.info("Streaming artifact zip complete artifact: {}",
                artifacts))
            .blockLast();
        // TODO maybe need use DataBufferUtils.releaseConsumer()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=multi.zip")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(stream);

    }

    // TODO: enable pom requests
//    @PostMapping(value = "/zip/stream/pom", consumes = {
//        "multipart/form-data"
//    })
//    public ResponseEntity<StreamingResponseBody> getPomArtifactsZip(@RequestParam("file") MultipartFile pomFile,
//                                                                    @RequestParam(defaultValue = "exactly") ShipmentLevel level) {
//        final Flux<DataBuffer> dataBufferFlux = getZipStream(
//            artifactResolverService.resolveArtifactFromPom(pomFile, level));
//        StreamingResponseBody stream = output -> DataBufferUtils
//            .write(dataBufferFlux,
//                output)
//            .doOnError(e -> log.error("Error writing to stream", e))
//            .doOnComplete(() -> log.info("Streaming pom artifact zip complete artifact: {}",
//                pomFile.getOriginalFilename()))
//            .blockLast();
//        // TODO maybe need use DataBufferUtils.releaseConsumer()
//        return ResponseEntity.ok()
//            .header(HttpHeaders.CONTENT_DISPOSITION,
//                "attachment; filename=" + pomFile.getOriginalFilename().replace(".pom", ".zip"))
//            .contentType(MediaType.APPLICATION_OCTET_STREAM)
//            .body(stream);
//
//    }

    @PostMapping("/zip/stream")
    public ResponseEntity<Flux<DataBuffer>> streamArtifactZip(@RequestBody Artifact artifact,
                                                                   @RequestParam(defaultValue = "exactly") ShipmentLevel level) {
        return alternativeStreamZippedArtifact(artifact, level);
    }

    private ResponseEntity<Flux<DataBuffer>> alternativeStreamZippedArtifact(Artifact artifact,
                                                                       ShipmentLevel level) {
        final Flux<DataBuffer> dataBufferFlux = getZipStream(
            artifactResolverService.resolveArtifact(artifact, level));

//        DataBufferUtils.release(dataBufferFlux);
//        StreamingResponseBody stream = output -> DataBufferUtils
//            .write(dataBufferFlux,
//                output)
//            .doOnError(e -> log.error("Error writing to stream", e))
//            .doOnComplete(() -> log.info("Streaming artifact zip complete artifact: {}",
//                artifact.getArtifactFullName()))
//            .blockLast();
        // TODO maybe need use DataBufferUtils.releaseConsumer()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + artifact.getArtifactFullName() + ".zip")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
//            .contentType(MediaType.TEXT_EVENT_STREAM_VALUE)
                .body(dataBufferFlux);
//                .body
//                .body(stream);
    }

    private ResponseEntity<StreamingResponseBody> streamZippedArtifact(Artifact artifact,
                                                                       ShipmentLevel level) {
        final Flux<DataBuffer> dataBufferFlux = getZipStream(
            artifactResolverService.resolveArtifact(artifact, level));
        StreamingResponseBody stream = output -> DataBufferUtils
            .write(dataBufferFlux,
                output)
            .doOnError(e -> log.error("Error writing to stream", e))
            .doOnComplete(() -> log.info("Streaming artifact zip complete artifact: {}",
                artifact.getArtifactFullName()))
            .blockLast();
        // TODO maybe need use DataBufferUtils.releaseConsumer()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + artifact.getArtifactFullName() + ".zip")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(stream);
    }

    private Flux<DataBuffer> getZipStream(Flux<ZipRemoteEntry> zipRemoteEntries) {
        Mono<ZipStreamerBody> body = zipRemoteEntries.collectList()
                .map( list -> new ZipStreamerBody(list.stream().distinct().collect(Collectors.toList())))
                .doOnNext( a -> log.info("zip streamer request body: {}", new Gson().toJson(a)));
        log.info("the stream zipper url is: " + appConfig.getStreamZipperUrl());
        return WebClient.create(appConfig.getStreamZipperUrl())
            .post().uri("/download")
            .body(BodyInserters.fromProducer(body, ZipRemoteEntry.class))
            .accept(MediaType.APPLICATION_OCTET_STREAM)
            .retrieve()
            .bodyToFlux(DataBuffer.class);
    }
}
