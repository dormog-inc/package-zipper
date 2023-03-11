package com.dor.package_zipper.controller;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.models.ShipmentLevel;
import com.dor.package_zipper.services.QueriesService;
import com.dor.package_zipper.services.streaming.PackageStreamsManager;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.dor.package_zipper.configuration.RepositoryConfig.GRADLE_PLUGINS_REPOSITORY;

@RestController
@Slf4j
@RequiredArgsConstructor
public class QueriesController {
    private final AppConfig appConfig;
    private final PackageStreamsManager packageStreamsManager;
    private final QueriesService queriesService;
    private List<String> defaultRemoteRepositoriesUrls;

    @PostConstruct
    public void init() {
        defaultRemoteRepositoriesUrls = List.of(
                appConfig.getMavenUrl(),
                GRADLE_PLUGINS_REPOSITORY
        );
    }

    @Tag(name = "queries")
    @GetMapping(value = "/zip/group/all_versions")
    public ResponseEntity<Flux<DataBuffer>> getAllVersionsOfGroup(
            @Schema(type = "string", example = "org.apache.solr")
            @RequestParam String groupId,
            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level,
            @RequestParam(defaultValue = "false") boolean shouldBringClassifiers) {
        List<String> exceptions = new ArrayList<>();
        String solrReqUrl = queriesService.getSolrReqUrl(groupId);
        var dataBufferFlux = queriesService.getAllArtifactsOfGroup(solrReqUrl)
                .flatMap(artifactsOfRequestedGroup -> Flux.fromIterable(artifactsOfRequestedGroup)
                        .flatMap(artifactId -> queriesService.getAllVersionsOfAnArtifact(groupId,
                                queriesService.getMavenMetadataUrl(appConfig.getMavenUrl(), groupId, artifactId),
                                artifactId))
                        .buffer()
                        .map(queriesService::flattenListsToList))
                .flatMap(artifacts -> packageStreamsManager.getDataBufferFlux(artifacts, level, defaultRemoteRepositoriesUrls, exceptions, shouldBringClassifiers));
        String fileName = "all-versions-of-" + groupId;
        return packageStreamsManager.getResponseFromDataBufferFlux(dataBufferFlux, fileName, exceptions);
    }

    @Tag(name = "queries")
    @GetMapping(value = "/zip/group/last_{number}_versions")
    public ResponseEntity<Flux<DataBuffer>> getLastVersionOfGroup(
            @Schema(type = "int", example = "5")
            @PathVariable(name = "number") Integer numberOfVersions,
            @Schema(type = "string", example = "org.apache.solr")
            @RequestParam String groupId,
            @RequestParam(defaultValue = "false") boolean shouldBringClassifiers,
            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level) {
        List<String> exceptions = new ArrayList<>();
        String solrReqUrl = queriesService.getSolrReqUrl(groupId);
        var dataBufferFlux = queriesService.getAllArtifactsOfGroup(solrReqUrl)
                .flatMap(artifactsOfRequestedGroup -> Flux.fromIterable(artifactsOfRequestedGroup)
                        .flatMap(artifactId -> queriesService.getLastVersionsOfAnArtifact(groupId,
                                queriesService.getMavenMetadataUrl(appConfig.getMavenUrl(), groupId, artifactId),
                                artifactId,
                                numberOfVersions))
                        .buffer()
                        .map(queriesService::flattenListsToList))
                .flatMap(artifacts -> packageStreamsManager.getDataBufferFlux(artifacts, level, defaultRemoteRepositoriesUrls, exceptions, shouldBringClassifiers));
        String fileName = "last-versions-of-" + groupId;
        return packageStreamsManager.getResponseFromDataBufferFlux(dataBufferFlux, fileName, exceptions);
    }


    @Tag(name = "queries")
    @PostMapping(value = "/zip/group/multi/all_versions")
    public ResponseEntity<Flux<DataBuffer>> getAllVersionsOfMultipleGroups(
            @Schema(type = "list", example = "[\"org.jetbrains\"]")
            @RequestBody List<String> groups,
            @RequestParam(defaultValue = "false") boolean shouldBringClassifiers,
            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level) {
        List<String> exceptions = new ArrayList<>();
        Flux<DataBuffer> dataBufferFlux = Flux.fromIterable(groups)
                .flatMap(group -> {
                    String solrReqUrl = queriesService.getSolrReqUrl(group);
                    return queriesService.getAllArtifactsOfGroup(solrReqUrl)
                            .map(artifactIds -> Map.entry(group, artifactIds));
                })
                .flatMap(entryOfGroupAndArtifacts -> Flux.fromIterable(entryOfGroupAndArtifacts.getValue())
                        .flatMap(artifactId -> {
                            var groupId = entryOfGroupAndArtifacts.getKey();
                            var mavenMetadataUrl = queriesService.getMavenMetadataUrl(appConfig.getMavenUrl(), groupId, artifactId);
                            return queriesService.getAllVersionsOfAnArtifact(groupId, mavenMetadataUrl,artifactId);
                }))
                .buffer()
                .map(queriesService::flattenListsToList)
                .flatMap(artifacts -> packageStreamsManager.getDataBufferFlux(artifacts, level, defaultRemoteRepositoriesUrls, new ArrayList<>(), shouldBringClassifiers));
        String fileName = "multi-all-versions-of-" + groups.get(0);
        return packageStreamsManager.getResponseFromDataBufferFlux(dataBufferFlux, fileName, exceptions);
    }


    @Tag(name = "queries")
    @PostMapping(value = "/zip/group/multi/last_{number}_versions")
    public ResponseEntity<Flux<DataBuffer>> getLastVersionsOfMultipleGroups(
            @Schema(type = "int", example = "5")
            @PathVariable(name = "number") Integer numberOfVersions,
            @Schema(type = "list", example = "[\"org.jetbrains\"]")
            @RequestBody List<String> groups,
            @RequestParam(defaultValue = "false") boolean shouldBringClassifiers,
            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level) {
        List<String> exceptions = new ArrayList<>();
        Flux<DataBuffer> dataBufferFlux = Flux.fromIterable(groups)
                .flatMap(group -> {
                    String solrReqUrl = queriesService.getSolrReqUrl(group);
                    return queriesService.getAllArtifactsOfGroup(solrReqUrl)
                            .map(artifactIds -> Map.entry(group, artifactIds));
                })
                .flatMap(entryOfGroupAndArtifacts -> Flux.fromIterable(entryOfGroupAndArtifacts.getValue())
                        .flatMap(artifactId -> {
                            var groupId = entryOfGroupAndArtifacts.getKey();
                            var mavenMetadataUrl = queriesService.getMavenMetadataUrl(appConfig.getMavenUrl(), groupId, artifactId);
                            return queriesService.getLastVersionsOfAnArtifact(groupId, mavenMetadataUrl,artifactId, numberOfVersions);
                }))
                .buffer()
                .map(queriesService::flattenListsToList)
                .flatMap(artifacts -> packageStreamsManager.getDataBufferFlux(artifacts, level, defaultRemoteRepositoriesUrls, new ArrayList<>(), shouldBringClassifiers));
        String fileName = "multi-last-versions-of-" + groups.get(0);
        return packageStreamsManager.getResponseFromDataBufferFlux(dataBufferFlux, fileName, exceptions);
    }

    @Tag(name = "queries")
    @GetMapping(value = "/zip/artifact/all_versions")
    public ResponseEntity<Flux<DataBuffer>> getAllVersionsOfArtifact(
            @Schema(type = "string", example = "org.apache.solr")
            @RequestParam String groupId,
            @Schema(type = "string", example = "solr-core")
            @RequestParam String artifactId,
            @RequestParam(defaultValue = "false") boolean shouldBringClassifiers,
            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level) {
        String mavenMetadataUrl = queriesService.getMavenMetadataUrl(appConfig.getMavenUrl(), groupId, artifactId);
        List<String> exceptions = new ArrayList<>();
        Flux<DataBuffer> dataBufferFlux = Flux.from(queriesService.getAllVersionsOfAnArtifact(groupId, mavenMetadataUrl,artifactId))
                .flatMap(artifacts -> packageStreamsManager.getDataBufferFlux(artifacts, level, defaultRemoteRepositoriesUrls, new ArrayList<>(), shouldBringClassifiers));
        String fileName = "last-versions-of-" + artifactId;
        return packageStreamsManager.getResponseFromDataBufferFlux(dataBufferFlux, fileName, exceptions);
    }

    @Tag(name = "queries")
    @GetMapping(value = "/zip/artifact/last_{number}_versions")
    public ResponseEntity<Flux<DataBuffer>> getAllVersionsOfStreamArtifactZip(
            @Schema(type = "int", example = "5")
            @PathVariable(name = "number") Integer numberOfVersions,
            @Schema(type = "string", example = "org.apache.solr")
            @RequestParam String groupId,
            @Schema(type = "string", example = "solr-core")
            @RequestParam String artifactId,
            @RequestParam(defaultValue = "false") boolean shouldBringClassifiers,
            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level) {
        String mavenMetadataUrl = queriesService.getMavenMetadataUrl(appConfig.getMavenUrl(), groupId, artifactId);
        List<String> exceptions = new ArrayList<>();
        var dataBufferFlux = Flux.from(queriesService.getLastVersionsOfAnArtifact(groupId, mavenMetadataUrl, artifactId, numberOfVersions))
                .flatMap(artifacts -> packageStreamsManager.getDataBufferFlux(artifacts, level, defaultRemoteRepositoriesUrls, exceptions, shouldBringClassifiers));
        String fileName = "last-versions-of-" + artifactId;
        return packageStreamsManager.getResponseFromDataBufferFlux(dataBufferFlux, fileName, exceptions);
    }

    @Tag(name = "queries")
    @PostMapping(value = "/zip/artifact/multi/all_versions")
    public ResponseEntity<Flux<DataBuffer>> getAllVersionsOfMultipleArtifacts(
            @Schema(type = "list", example = "[\"org.jetbrains:annotations\"]")
            @RequestBody List<String> artifactList,
            @RequestParam(defaultValue = "false") boolean shouldBringClassifiers,
            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level) {
        List<String> exceptions = new ArrayList<>();
        Flux<DataBuffer> dataBufferFlux = Flux.fromIterable(artifactList)
                .flatMap(artifactString -> {
                    var artifactParts = artifactString.split(":");
                    var groupId = artifactParts[0];
                    var artifactId = artifactParts[1];
                    var mavenMetadataUrl = queriesService.getMavenMetadataUrl(appConfig.getMavenUrl(), groupId, artifactId);
                    return queriesService.getAllVersionsOfAnArtifact(groupId, mavenMetadataUrl,artifactId);
                })
                .buffer()
                .map(queriesService::flattenListsToList)
                .flatMap(artifacts -> packageStreamsManager.getDataBufferFlux(artifacts, level, defaultRemoteRepositoriesUrls, new ArrayList<>(), shouldBringClassifiers));
        String fileName = "multi-all-versions-of-" + artifactList.get(0);
        return packageStreamsManager.getResponseFromDataBufferFlux(dataBufferFlux, fileName, exceptions);
    }

    @Tag(name = "queries")
    @PostMapping(value = "/zip/artifact/multi/last_{number}_versions")
    public ResponseEntity<Flux<DataBuffer>> getLastVersionsOfMultipleArtifacts(
            @Schema(type = "int", example = "5")
            @PathVariable(name = "number") Integer numberOfVersions,
            @Schema(type = "list", example = "[\"org.jetbrains:annotations\"]")
            @RequestBody List<String> artifactList,
            @RequestParam(defaultValue = "false") boolean shouldBringClassifiers,
            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level) {
        List<String> exceptions = new ArrayList<>();
        Flux<DataBuffer> dataBufferFlux = Flux.fromIterable(artifactList)
                .flatMap(artifactString -> {
                    var artifactParts = artifactString.split(":");
                    var groupId = artifactParts[0];
                    var artifactId = artifactParts[1];
                    var mavenMetadataUrl = queriesService.getMavenMetadataUrl(appConfig.getMavenUrl(), groupId, artifactId);
                    return queriesService.getLastVersionsOfAnArtifact(groupId, mavenMetadataUrl,artifactId, numberOfVersions);
                })
                .buffer()
                .map(queriesService::flattenListsToList)
                .flatMap(artifacts -> packageStreamsManager.getDataBufferFlux(artifacts, level, defaultRemoteRepositoriesUrls, new ArrayList<>(), shouldBringClassifiers));
        String fileName = "multi-last-versions-of-" + artifactList.get(0);
        return packageStreamsManager.getResponseFromDataBufferFlux(dataBufferFlux, fileName, exceptions);
    }
}
