package com.dor.package_zipper.controller;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.models.Artifact;
import com.dor.package_zipper.models.ShipmentLevel;
import com.dor.package_zipper.services.ArtifactResolverService;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.maven.index.*;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.expr.SourcedSearchExpression;
import org.eclipse.aether.repository.RemoteRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;

import static com.dor.package_zipper.configuration.RepositoryConfig.GRADLE_PLUGINS_REPOSITORY;

@RestController
@Slf4j
@AllArgsConstructor
public class QueriesController {
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

//    @Tag(name = "artifacts")
//    @GetMapping(value = "/zip/artifact/all_versions")
//    public ResponseEntity<Flux<DataBuffer>> getAllVersionsOfStreamArtifactZip(
//            @Schema(type = "string", example = "org.apache.solr")
//            @RequestParam String groupId,
//            @Schema(type = "string", example = "solr-core")
//            @RequestParam String artifactId,
//            @Schema(type = "string", example = "8.5.1")
//            @RequestParam String version,
//            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level) {
//        return streamZippedArtifact(new Artifact(groupId, artifactId, version), level);
//    }

    @Tag(name = "artifacts")
    @GetMapping(value = "/zip/group/all_last_version_of_all_deps")
    public Mono<String> getAllLastVersionsOfAllDepsOfGroup(
            @Schema(type = "string", example = "org.apache.solr")
            @RequestParam String groupId,
            @Schema(type = "string", example = "solr-core")
            @RequestParam String artifactId,
            @Schema(type = "string", example = "8.5.1")
            @RequestParam String version,
            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level) {
        var artifact = new Artifact(groupId, artifactId, version);
        String baseUrl = "https://search.maven.org/solrsearch/select?q=%22" + artifact.getGroupId() + "%22&rows=200&wt=json";
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build()
                .get()
                .retrieve()
                .bodyToMono(String.class);
//        return streamZippedArtifact(new Artifact(groupId, artifactId, version), level);
    }

    @Tag(name = "artifacts")
    @GetMapping(value = "/zip/group/all_versions_of_all_deps")
    public Mono<String> getAllVersionsOfAllDeps(
            @Schema(type = "string", example = "org.apache.solr")
            @RequestParam String groupId,
            @Schema(type = "string", example = "solr-core")
            @RequestParam String artifactId,
            @Schema(type = "string", example = "8.5.1")
            @RequestParam String version,
            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level) {
        var artifact = new Artifact(groupId, artifactId, version);
        String baseUrl = "https://repo1.maven.org/maven2/" + artifact.getGroupId().replace(".", "/") + "/" + artifact.getArtifactId() + "/";
        PlexusContainer plexusContainer = new DefaultPlexusContainer(config);

        // lookup the indexer components from plexus
        Indexer indexer = plexusContainer.lookup(Indexer.class);

        IndexingContext centralContext =
                indexer.createIndexingContext("central-context", "central", centralLocalCache, centralIndexDir,
                        "https://repo1.maven.org/maven2", null, true, true, indexers);

        Query gidQ =
                indexer.constructQuery(MAVEN.GROUP_ID, new SourcedSearchExpression("org.apache.maven.indexer"));
        Query aidQ = indexer.constructQuery(MAVEN.ARTIFACT_ID, new SourcedSearchExpression("indexer-core"));

        BooleanQuery bq = new BooleanQuery.Builder()
                .add(gidQ, BooleanClause.Occur.MUST)
                .add(aidQ, BooleanClause.Occur.MUST)
                .build();

        searchAndDump(indexer, "all artifacts under GA org.apache.maven.indexer:indexer-core", bq);


        return WebClient.builder()
                .baseUrl(baseUrl)
                .build()
                .get()
                .retrieve()
                .bodyToMono(String.class)
                .map(string -> {
                    Document doc = null;
                    try {
                        doc = Jsoup.connect("https://en.wikipedia.org/").get();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    log.info(doc.title());
                    Elements newsHeadlines = doc.select("#mp-itn b a");
                    for (Element headline : newsHeadlines) {
                        log.info("%s\n\t%s",
                                headline.attr("title"), headline.absUrl("href"));
                    }
                    return string;
                });
    }

    public void searchAndDump(Indexer nexusIndexer, String descr, Query q)
            throws IOException {
        System.out.println("Searching for " + descr);

        FlatSearchResponse response = nexusIndexer.searchFlat(new FlatSearchRequest(q, centralContext));

        for (ArtifactInfo ai : response.getResults()) {
            System.out.println(ai.toString());
        }

        System.out.println("------");
        System.out.println("Total: " + response.getTotalHitsCount());
        System.out.println();
    }

//    @Tag(name = "artifacts")
//    @GetMapping(value = "/zip/artifact")
//    public ResponseEntity<Flux<DataBuffer>> getStreamArtifactZip(
//            @Schema(type = "string", example = "org.apache.solr")
//            @RequestParam String groupId,
//            @Schema(type = "string", example = "solr-core")
//            @RequestParam String artifactId,
//            @Schema(type = "string", example = "8.5.1")
//            @RequestParam String version,
//            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level) {
//        return streamZippedArtifact(new Artifact(groupId, artifactId, version), level);
//    }
//
//    @Tag(name = "artifacts")
//    @GetMapping(value = "/zip/artifact/{artifact}")
//    public ResponseEntity<Flux<DataBuffer>> getStreamArtifactZip(
//            @Schema(type = "string", example = "org.jetbrains:annotations:23.0.0")
//            @PathVariable String artifact,
//            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level,
//            @Schema(type = "list", example = "https://maven.ceon.pl/artifactory/repo")
//            @RequestParam(name = "customRepositoriesList", required = false) List<String> optionalCustomRepositoriesList) {
//        List<String> sessionsRemoteRepositoryList = getSessionsRemoteRepositoryList(optionalCustomRepositoriesList);
//        return streamZippedArtifact(new Artifact(artifact), level, sessionsRemoteRepositoryList);
//    }
//
//    private List<String> getSessionsRemoteRepositoryList(List<String> optionalCustomRepositoriesList) {
//        List<String> sessionsRemoteRepositoryList = new ArrayList<String>(this.remoteRepositoryList.stream().map(RemoteRepository::getUrl).distinct().toList());
//        Optional.ofNullable(optionalCustomRepositoriesList).ifPresent(sessionsRemoteRepositoryList::addAll);
//        return sessionsRemoteRepositoryList;
//    }
//
//    @Tag(name = "artifacts")
//    @PostMapping("/zip/artifact/multi")
//    public ResponseEntity<Flux<DataBuffer>> streamArtifactsZip(
//            @Schema(type = "list", example = "[\"org.jetbrains:annotations:23.0.0\", \"org/jetbrains/annotations/22.0.0\"]")
//            @RequestBody List<String> artifactStringList,
//            @RequestParam(defaultValue = "EXACTLY") ShipmentLevel level,
//            @RequestParam(name = "customRepositoriesList", required = false) List<String> optionalCustomRepositoriesList) {
//        List<String> sessionsRemoteRepositoryList = getSessionsRemoteRepositoryList(optionalCustomRepositoriesList);
//        var artifactsList = artifactStringList.stream().map(Artifact::new).toList();
//        List<ResolvingProcessServiceResult> resolvingProcessServiceResults = artifactResolverService.resolveArtifacts(artifactsList,
//                level,
//                sessionsRemoteRepositoryList);
//        List<String> exceptions = new ArrayList<>();
//        final Flux<DataBuffer> dataBufferFlux = parseResolvingProcessResults(resolvingProcessServiceResults, exceptions);
//        var response = ResponseEntity.ok()
//                .header(HttpHeaders.CONTENT_DISPOSITION,
//                        "attachment; filename=multi-%s.zip".formatted(artifactsList.get(0).getArtifactId()))
//                .contentType(MediaType.APPLICATION_OCTET_STREAM);
//        if (!exceptions.isEmpty()) {
//            response.header(HttpHeaders.WARNING, exceptions.toArray(String[]::new));
//        }
//        return response
//                .body(dataBufferFlux);
//    }
//
//    @GetMapping(value = "/zip/plugin/")
//    @Tag(name = "plugins")
//    public ResponseEntity<Flux<DataBuffer>> streamGradlePlugins(@RequestParam String artifact,
//                                                                @RequestParam String version,
//                                                                @RequestParam(defaultValue = "HEAVY") ShipmentLevel level) {
//        return streamZippedArtifact(GradlePluginsHandler.formatGradlePluginPomName(artifact, version), level);
//    }
//
//    @Tag(name = "plugins")
//    @PostMapping("/zip/plugin/multi")
//    public ResponseEntity<Flux<DataBuffer>> streamArtifactsZip(
//            @Schema(type = "object", example = "{\"io.github.bla.plugin-id\": \"1.0.3\"}")
//            @RequestBody Map<String, String> mapOfArtifactAndVersion,
//            @RequestParam(defaultValue = "HEAVY") ShipmentLevel level) {
//        List<ResolvingProcessServiceResult> resolvingProcessServiceResults = artifactResolverService.resolveArtifacts(
//                mapOfArtifactAndVersion.entrySet().stream().map((entry) -> GradlePluginsHandler.formatGradlePluginPomName(entry.getKey(), entry.getValue())).toList(),
//                level,
//                defaultRemoteRepositoriesUrls);
//        List<String> exceptions = new ArrayList<>();
//        final Flux<DataBuffer> dataBufferFlux = parseResolvingProcessResults(resolvingProcessServiceResults, exceptions);
//        var response = ResponseEntity.ok()
//                .header(HttpHeaders.CONTENT_DISPOSITION,
//                        "attachment; filename=multi-%s.zip".formatted(mapOfArtifactAndVersion.keySet().stream().toList().get(0)))
//                .contentType(MediaType.APPLICATION_OCTET_STREAM);
//        if (!exceptions.isEmpty()) {
//            response.header(HttpHeaders.WARNING, exceptions.toArray(String[]::new));
//        }
//        return response
//                .body(dataBufferFlux);
//    }
//
//    private Flux<DataBuffer> parseResolvingProcessResults(List<ResolvingProcessServiceResult> resolvingProcessServiceResults, List<String> exceptions) {
//        List<ZipRemoteEntry> zipRemoteEntries = new ArrayList<>();
//        resolvingProcessServiceResults.forEach(resolvingProcessServiceResult -> {
//            zipRemoteEntries.addAll(resolvingProcessServiceResult.getZipRemoteEntries());
//            exceptions.addAll(resolvingProcessServiceResult.getExceptionMessages());
//        });
//        return getZipStream(zipRemoteEntries);
//    }
//
//
//    private ResponseEntity<Flux<DataBuffer>> streamZippedArtifact(Artifact artifact, ShipmentLevel level, List<String> sessionsRemoteRepositoryUrls) {
//        ResolvingProcessServiceResult resolvingProcessServiceResult = artifactResolverService.resolveArtifact(artifact, level, sessionsRemoteRepositoryUrls);
//        var response = ResponseEntity.ok()
//                .header(HttpHeaders.CONTENT_DISPOSITION,
//                        "attachment; filename=" + artifact.getArtifactFullName() + ".zip")
//                .contentType(MediaType.APPLICATION_OCTET_STREAM);
//        Optional.ofNullable(resolvingProcessServiceResult.getExceptionMessages()).ifPresent(exceptionMessages -> {
//            response.header(HttpHeaders.WARNING, exceptionMessages.toArray(String[]::new));
//        });
//        return response
//                .body(getZipStream(resolvingProcessServiceResult.getZipRemoteEntries()));
//    }
//
//    public ResponseEntity<Flux<DataBuffer>> streamZippedArtifact(Artifact artifact, ShipmentLevel level) {
//        return streamZippedArtifact(artifact, level, defaultRemoteRepositoriesUrls);
//    }
//
//    private Flux<DataBuffer> getZipStream(List<ZipRemoteEntry> zipRemoteEntries) {
//        Mono<ZipStreamerBody> body = Mono.just(new ZipStreamerBody(zipRemoteEntries.stream().distinct().toList()))
//                .doOnNext(zipStreamerBody -> {
//                    try {
//                        log.info("zip streamer request body: {}", new ObjectMapper().writeValueAsString(zipStreamerBody));
//                    } catch (JsonProcessingException e) {
//                        e.printStackTrace();
//                    }
//                });
//        log.info("the stream zipper url is: " + appConfig.getStreamZipperUrl());
//        return WebClient.create(appConfig.getStreamZipperUrl())
//                .post().uri("/download")
//                .body(BodyInserters.fromProducer(body, ZipRemoteEntry.class))
//                .accept(MediaType.APPLICATION_OCTET_STREAM)
//                .retrieve()
//                .bodyToFlux(DataBuffer.class);
//    }
}
