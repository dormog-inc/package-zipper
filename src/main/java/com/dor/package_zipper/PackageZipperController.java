package com.dor.package_zipper;

import java.util.List;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.models.ArtifactDTO;
import com.dor.package_zipper.models.ZipRemoteEntry;
import com.dor.package_zipper.models.ZipStreamerBody;
import com.dor.package_zipper.services.ArtifactResolverService;
import com.google.gson.Gson;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
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
      private AppConfig appConfig;
      private ArtifactResolverService artifactResolverService;

      @PostMapping("/zip/stream")
      public ResponseEntity<StreamingResponseBody> StreamArtifactZip(@RequestBody ArtifactDTO artifact,
                  @RequestParam(defaultValue = "true") boolean transitivity) {
            return StreamZippedArtifact(artifact, transitivity);
      }

      @PostMapping("/zip/stream/multi")
      public ResponseEntity<StreamingResponseBody> StreamArtifactsZip(@RequestBody List<ArtifactDTO> artifacts,
                  @RequestParam(defaultValue = "true") boolean transitivity) {
            final Flux<DataBuffer> dataBufferFlux = getZipStream(
                        artifactResolverService.resolveArtifacrts(artifacts, transitivity));
            StreamingResponseBody stream = output -> DataBufferUtils
                        .write(dataBufferFlux,
                                    output)
                        .doOnError(e -> log.error("Error writing to stream", e))
                        .doOnComplete(() -> log.info("Streaming artifact zip complete artifact: {}",
                                    artifacts))
                        .blockLast();
            // TODO mabye need use DataBufferUtils.releaseConsumer()
            return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=multi.zip")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(stream);

      }

      @PostMapping(value = "/zip/stream/pom", consumes = {
            "multipart/form-data"
         })
      public ResponseEntity<StreamingResponseBody> getPOMArtifactsZip(@RequestParam("file") MultipartFile pomFile,
                  @RequestParam(defaultValue = "true", name = "transitivity") boolean transitivity) {
            final Flux<DataBuffer> dataBufferFlux = getZipStream(
                        artifactResolverService.resolveArtifacrtFromPom(pomFile, transitivity));
            StreamingResponseBody stream = output -> DataBufferUtils
                        .write(dataBufferFlux,
                                    output)
                        .doOnError(e -> log.error("Error writing to stream", e))
                        .doOnComplete(() -> log.info("Streaming pom artifact zip complete artifact: {}",
                                    pomFile.getOriginalFilename()))
                        .blockLast();
            // TODO mabye need use DataBufferUtils.releaseConsumer()
            return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=" + pomFile.getOriginalFilename().replace(".pom","") +".zip")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(stream);

      }

      @GetMapping(value = "/zip/stream")
      public ResponseEntity<StreamingResponseBody> getStreamArtifactZip(@RequestParam String groupId,
                  @RequestParam String artifactId, @RequestParam String version,
                  @RequestParam(defaultValue = "true") boolean transitivity) {
            ArtifactDTO artifact = new ArtifactDTO(groupId, artifactId, version);
            return StreamZippedArtifact(artifact, transitivity);
      }

      @GetMapping(value = "/zip/stream/{artifact}")
      public ResponseEntity<StreamingResponseBody> getStreamArtifactZip(@PathVariable String artifact,
                  @RequestParam(defaultValue = "true") boolean transitivity) {
            ArtifactDTO artifactObj = new ArtifactDTO(artifact);

            return StreamZippedArtifact(artifactObj, transitivity);
      }

      private ResponseEntity<StreamingResponseBody> StreamZippedArtifact(ArtifactDTO artifact,
                  boolean withTransitivity) {
            final Flux<DataBuffer> dataBufferFlux = getZipStream(
                        artifactResolverService.resolveArtifacrt(artifact, withTransitivity));
            StreamingResponseBody stream = output -> DataBufferUtils
                        .write(dataBufferFlux,
                                    output)
                        .doOnError(e -> log.error("Error writing to stream", e))
                        .doOnComplete(() -> log.info("Streaming artifact zip complete artifact: {}",
                                    artifact.getArtifactFullName()))
                        .blockLast();
            // TODO mabye need use DataBufferUtils.releaseConsumer()
            return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=" + artifact.getArtifactFullName() + ".zip")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(stream);
      }

      private Flux<DataBuffer> getZipStream(List<ZipRemoteEntry> zipRemoteEntries) {
            log.info("Artifacts to package: {}", zipRemoteEntries);
            ZipStreamerBody body = new ZipStreamerBody(zipRemoteEntries);
            log.info("zip streamer request body: {}", new Gson().toJson(body));
            final Flux<DataBuffer> dataBufferFlux = WebClient.create(appConfig.getStreamZipperUrl())
                        .post().uri("/download")
                        .body(BodyInserters.fromValue(body))
                        .accept(MediaType.APPLICATION_OCTET_STREAM)
                        .retrieve()
                        .bodyToFlux(DataBuffer.class);
            return dataBufferFlux;
      }

}
