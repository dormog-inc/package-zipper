package com.dor.package_zipper.services;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.models.Artifact;
import com.dor.package_zipper.models.ZipRemoteEntry;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenArtifactInfo;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.MavenStrategyStage;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

@Service
@AllArgsConstructor
@Slf4j
public class ArtifactResolverService {
    private final AppConfig appConfig;

    public Flux<ZipRemoteEntry> resolveArtifact(Artifact artifact, boolean withTransitivity) {
        MavenStrategyStage mavenStrategyStage = Maven.resolver().resolve(artifact.getArtifactFullName());
        Flux<ZipRemoteEntry> zipRemoteEntries = resolveMavenStrategy(withTransitivity, mavenStrategyStage);
        return Flux.concat(zipRemoteEntries, getRemoteEntryFromLibrary(artifact)).distinct();
    }

    public Flux<ZipRemoteEntry> resolveArtifacts(List<Artifact> artifacts, boolean withTransitivity) {
        MavenResolverSystem mavenResolverSystem = Maven.resolver();
        MavenStrategyStage mavenStrategyStage = mavenResolverSystem.resolve(
                artifacts.stream().map(Artifact::getArtifactFullName).collect(Collectors.toList()));
        return Flux.concat(
            resolveMavenStrategy(withTransitivity, mavenStrategyStage),
            Flux.fromIterable(artifacts).map(artifact -> getRemoteEntryFromLibrary(artifact)).flatMap(a -> a)
        ).distinct();
    }

    public Flux<ZipRemoteEntry> resolveArtifactFromPom(MultipartFile pomFile, boolean withTransitivity) {
        Flux<ZipRemoteEntry> zipRemoteEntries = null;
        Path path = Paths.get(
                pomFile.getOriginalFilename().replace(".pom", "") + "_" + System.currentTimeMillis() / 1000L
                        + "_pom.xml");
        try {
            pomFile.transferTo(path);
            MavenResolverSystem mavenResolverSystem = Maven.resolver();
            MavenStrategyStage mavenStrategyStage = mavenResolverSystem.loadPomFromFile(path.toFile())
                    .importCompileAndRuntimeDependencies()
                    .importTestDependencies().resolve();
            zipRemoteEntries = resolveMavenStrategy(withTransitivity, mavenStrategyStage);
        } catch (Exception e) {
            log.error("error create tmp pom file", e);
        } finally {
            path.toFile().delete();
        }
        return zipRemoteEntries;
    }

    private Flux<ZipRemoteEntry> resolveMavenStrategy(boolean withTransitivity,
            MavenStrategyStage mavenStrategyStage) {
        List<MavenResolvedArtifact> mavenArtifacts = new ArrayList<MavenResolvedArtifact>();
        if (withTransitivity) {
            mavenArtifacts.addAll(mavenStrategyStage
                    .withTransitivity().asList(MavenResolvedArtifact.class));
        } else {
            mavenArtifacts.addAll(mavenStrategyStage
                    .withoutTransitivity().asList(MavenResolvedArtifact.class));
        }

        return Flux.fromIterable(mavenArtifacts)
                .map(mavenArtifact -> Flux.fromArray(mavenArtifact.getDependencies())
                        .map(dependency -> getRemoteEntryFromLibrary(
                                new Artifact(
                                        dependency.getCoordinate().getGroupId(),
                                        dependency.getCoordinate().getArtifactId(),
                                        dependency.getCoordinate().getVersion()))
                            ).flatMap(a -> a).distinct()
                    ).flatMap(a -> a).distinct();
    }

    private Flux<ZipRemoteEntry> getRemoteEntryFromLibrary(Artifact artifact) {
        Flux<ZipRemoteEntry> zipEntriesFlux = null;
        List<ZipRemoteEntry> zipEntries = new ArrayList<>();
        String path = String.format("%s/%s/%s/%s-%s",
                artifact.getGroupId().replace(".", "/"),
                artifact.getArtifactId(),
                artifact.getVersion(),
                artifact.getArtifactId(),
                artifact.getVersion());
        String libPath = String.format("%s.%s", path, artifact.getPackagingType());
        String libUrl = String.format("%s/%s", appConfig.getMavenUrl(), libPath);
        String pomUrl = libUrl;
        zipEntries.add(new ZipRemoteEntry(libPath, libUrl));

        if (!artifact.getPackagingType().equals("pom")) {
            String pomPath = String.format("%s.%s", path, "pom");
            pomUrl = String.format("%s/%s", appConfig.getMavenUrl(), pomPath);
            zipEntries.add(new ZipRemoteEntry(pomPath, pomUrl));
        }

        Flux<ZipRemoteEntry> pomEntries = getParentPomEntries(pomUrl);
        if (pomEntries != null) {
            zipEntriesFlux = Flux.concat(pomEntries, Flux.fromIterable(zipEntries)).distinct();
        }
        return zipEntriesFlux.distinct();
    }

    private Flux<ZipRemoteEntry> getParentPomEntries(String pomUrl) {
        return WebClient.create(pomUrl).get().retrieve().bodyToMono(String.class)
                .filter(pomStirng -> {
                    return pomStirng.contains("<parent>");
                })
                .map(this::loadXMLFromString)
                .map(doc -> {
                    Artifact parent = new Artifact(doc.getElementsByTagName("groupId").item(0).getTextContent(),
                            doc.getElementsByTagName("artifactId").item(0).getTextContent(),
                            doc.getElementsByTagName("version").item(0).getTextContent());
                    parent.setPackagingType("pom");
                    return parent;
                })
                .map(artifact -> Flux.concat(getRemoteEntryFromLibrary(artifact), resolveArtifact(artifact, true).distinct())).flux().flatMap(a -> a).distinct();
    }

    private Document loadXMLFromString(String xml) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        Document document = null;
        try {
            builder = factory.newDocumentBuilder();
            InputSource is = new InputSource(new StringReader(xml));
            document = builder.parse(is);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return document;
    }

}
