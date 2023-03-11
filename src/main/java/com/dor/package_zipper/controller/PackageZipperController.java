package com.dor.package_zipper.controller;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.models.*;
import com.dor.package_zipper.services.ArtifactResolverService;
import com.dor.package_zipper.services.GradlePluginsHandler;
import com.dor.package_zipper.services.streaming.PackageStreamsManager;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.repository.RemoteRepository;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import java.util.*;

import static com.dor.package_zipper.configuration.RepositoryConfig.GRADLE_PLUGINS_REPOSITORY;

@RestController
@Slf4j
@AllArgsConstructor
public class PackageZipperController {
    private final AppConfig appConfig;
    private final ArtifactResolverService artifactResolverService;
    private final List<RemoteRepository> remoteRepositoryList;
    private final PackageStreamsManager packageStreamsManager;
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
            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level,
            @RequestParam(defaultValue = "false") boolean shouldBringClassifiers) {
        return packageStreamsManager.streamZippedArtifact(new Artifact(groupId, artifactId, version), level, shouldBringClassifiers);
    }

    @Tag(name = "artifacts")
    @GetMapping(value = "/zip/artifact/{artifact}")
    public ResponseEntity<Flux<DataBuffer>> getStreamArtifactZip(
            @Schema(type = "string", example = "org.jetbrains:annotations:23.0.0")
            @PathVariable String artifact,
            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level,
            @RequestParam(defaultValue = "false") boolean shouldBringClassifiers,
            @Schema(type = "list", example = "https://maven.ceon.pl/artifactory/repo")
            @RequestParam(name = "customRepositoriesList", required = false) List<String> optionalCustomRepositoriesList) {
        List<String> sessionsRemoteRepositoryList = getSessionsRemoteRepositoryList(optionalCustomRepositoriesList);
        return packageStreamsManager.streamZippedArtifact(new Artifact(artifact), level, shouldBringClassifiers, sessionsRemoteRepositoryList);
    }

    private List<String> getSessionsRemoteRepositoryList(List<String> optionalCustomRepositoriesList) {
        List<String> sessionsRemoteRepositoryList = new ArrayList<String>(this.remoteRepositoryList.stream().map(RemoteRepository::getUrl).distinct().toList());
        Optional.ofNullable(optionalCustomRepositoriesList).ifPresent(sessionsRemoteRepositoryList::addAll);
        return sessionsRemoteRepositoryList;
    }

    @Tag(name = "artifacts")
    @PostMapping("/zip/artifact/multi")
    public ResponseEntity<Flux<DataBuffer>> streamMultipleArtifactsZip(
            @Schema(type = "list", example = "[\"org.jetbrains:annotations:23.0.0\", \"org/jetbrains/annotations/22.0.0\"]")
            @RequestBody List<String> artifactStringList,
            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level,
            @RequestParam(defaultValue = "false") boolean shouldBringClassifiers,
            @RequestParam(name = "customRepositoriesList", required = false) List<String> optionalCustomRepositoriesList) {
        List<String> sessionRemoteRepositoryList = getSessionsRemoteRepositoryList(optionalCustomRepositoriesList);
        var artifactsList = artifactStringList.stream().map(Artifact::new).toList();
        return packageStreamsManager.getMultiFileStreamResponse(artifactsList, level, sessionRemoteRepositoryList, shouldBringClassifiers);
    }

    @GetMapping(value = "/zip/plugin/")
    @Tag(name = "plugins")
    public ResponseEntity<Flux<DataBuffer>> streamGradlePlugins(@RequestParam String artifact,
                                                                @RequestParam String version,
                                                                @RequestParam(defaultValue = "HEAVY") ShipmentLevel level,
                                                                @RequestParam(defaultValue = "false") boolean shouldBringClassifiers) {
        return packageStreamsManager.streamZippedArtifact(GradlePluginsHandler.formatGradlePluginPomName(artifact, version), level, shouldBringClassifiers);
    }

    @Tag(name = "plugins")
    @PostMapping("/zip/plugin/multi")
    public ResponseEntity<Flux<DataBuffer>> streamMultipleArtifactsZip(
            @Schema(type = "object", example = "{\"io.github.bla.plugin-id\": \"1.0.3\"}")
            @RequestBody Map<String, String> mapOfArtifactAndVersion,
            @RequestParam(defaultValue = "false") boolean shouldBringClassifiers,
            @RequestParam(defaultValue = "HEAVY") ShipmentLevel level) {
        List<Artifact> artifactsList = mapOfArtifactAndVersion
                .entrySet()
                .stream()
                .map((entry) -> GradlePluginsHandler.formatGradlePluginPomName(entry.getKey(), entry.getValue())).toList();
        return packageStreamsManager.getMultiFileStreamResponse(artifactsList, level, defaultRemoteRepositoriesUrls, shouldBringClassifiers);
    }
}
