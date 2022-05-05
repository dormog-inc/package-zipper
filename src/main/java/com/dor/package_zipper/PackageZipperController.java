package com.dor.package_zipper;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.websocket.server.PathParam;

import com.dor.package_zipper.models.Artifact;
import com.dor.package_zipper.models.ZipRemoteEntry;
import com.dor.package_zipper.models.ZipStreamerBody;
import com.google.gson.Gson;

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
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;

import org.apache.maven.model.Dependency;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenArtifactInfo;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.MavenStrategyStage;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependency;
import org.jboss.shrinkwrap.resolver.api.maven.strategy.MavenResolutionStrategy;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
public class PackageZipperController {
      private static final String MAVEN_URL = "https://repo1.maven.org/maven2/";

      @PostMapping("/package")
      public ResponseEntity<StreamingResponseBody> packageArtifacts(@RequestBody Artifact artifact,
                  @RequestParam(defaultValue = "true") boolean transitivity) {
            final Flux<DataBuffer> dataBufferFlux = resolveArtifacrtToZipStream(artifact, transitivity);

            StreamingResponseBody stream = output -> DataBufferUtils.write(dataBufferFlux, output)
                        .doOnError(e -> log.error("Error writing to stream", e)).blockLast();
            return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=myzip.zip")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(stream);
      }

      private Flux<DataBuffer> resolveArtifacrtToZipStream(Artifact artifact, boolean withTransitivity) {
            MavenStrategyStage mavenStrategyStage = Maven.resolver().resolve(artifact.getArtifactFullName());
            List<MavenResolvedArtifact> mavenArtifacts = new ArrayList<MavenResolvedArtifact>();
            if (withTransitivity) {
                  mavenArtifacts.addAll((Collection<? extends MavenResolvedArtifact>) mavenStrategyStage
                              .withTransitivity().asList(MavenResolvedArtifact.class));
            } else {
                  mavenArtifacts.addAll((Collection<? extends MavenResolvedArtifact>) mavenStrategyStage
                              .withoutTransitivity().asList(MavenResolvedArtifact.class));
            }
            List<ZipRemoteEntry> a = mavenArtifacts.stream().map(mavenArtifact -> {
                  return Arrays.stream(mavenArtifact.getDependencies()).map(dependency -> getRemoteEntryFromLibary(
                              new Artifact(
                                          dependency.getCoordinate().getGroupId(),
                                          dependency.getCoordinate().getArtifactId(),
                                          dependency.getCoordinate().getVersion())))
                              .flatMap(List::stream)
                              .collect(Collectors.toList());
            })
                        .flatMap(List::stream)
                        .collect(Collectors.toList());
            a.addAll(getRemoteEntryFromLibary(artifact));
            log.info("Artifacts to package: {}", a);
            ZipStreamerBody body = new ZipStreamerBody(a);
            log.info("zip streamer request body: {}", new Gson().toJson(body));
            final Flux<DataBuffer> dataBufferFlux = WebClient.create("https://gradlezipstreamer.herokuapp.com")
                        .post().uri("/download")
                        .body(BodyInserters.fromValue(body))
                        .accept(MediaType.APPLICATION_OCTET_STREAM)
                        .retrieve()
                        .bodyToFlux(DataBuffer.class);
            return dataBufferFlux;
      }

      private List<ZipRemoteEntry> getRemoteEntryFromLibary(Artifact artifact) {
            List<ZipRemoteEntry> zipEntries = new ArrayList<>();
            String path = String.format("%s/%s/%s/%s-%s",
                        artifact.getGroupId().replace(".", "/"),
                        artifact.getArtifactId(),
                        artifact.getVersion(),
                        artifact.getArtifactId(),
                        artifact.getVersion());
            String libPath = path + "." + artifact.getPackagingType();
            String pomPath = path + ".pom";
            zipEntries.add(new ZipRemoteEntry(libPath, MAVEN_URL + libPath));
            zipEntries.add(new ZipRemoteEntry(pomPath, MAVEN_URL + pomPath));
            return zipEntries;
      }

      @GetMapping(value = "/zip")
      public ResponseEntity<StreamingResponseBody> getZipFileStream(@RequestParam String groupId,
                  @RequestParam String artifactId, @RequestParam String version,
                  @RequestParam(defaultValue = "true") boolean transitivity) {
            Artifact artifact = new Artifact(groupId, artifactId, version);
            final Flux<DataBuffer> dataBufferFlux = resolveArtifacrtToZipStream(artifact, transitivity);

            StreamingResponseBody stream = output -> DataBufferUtils.write(dataBufferFlux, output)
                        .doOnError(e -> log.error("Error writing to stream", e)).blockLast();
            return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=myzip.zip")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(stream);
      }

      @GetMapping(value = "/zip/stream/{artifact}")
      public ResponseEntity<StreamingResponseBody> getZipFileStream(@PathVariable String artifact,
                  @RequestParam(defaultValue = "true") boolean transitivity) {
            Artifact artifactObj = new Artifact(artifact);
            final Flux<DataBuffer> dataBufferFlux = resolveArtifacrtToZipStream(artifactObj, transitivity);

            StreamingResponseBody stream = output -> DataBufferUtils.write(dataBufferFlux, output)
                        .doOnError(e -> log.error("Error writing to stream", e)).blockLast();
            return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=" + artifactObj.getArtifactFullName() + ".zip")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(stream);
      }

}
