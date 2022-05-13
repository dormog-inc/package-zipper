package com.dor.package_zipper.services;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.models.Artifact;
import com.dor.package_zipper.models.ZipRemoteEntry;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.jboss.shrinkwrap.resolver.api.maven.Maven;
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

    public List<ZipRemoteEntry> resolveArtifact(Artifact artifact, boolean withTransitivity) {
        MavenStrategyStage mavenStrategyStage = Maven.resolver().resolve(artifact.getArtifactFullName());
        List<ZipRemoteEntry> zipRemoteEntries = resolveMavenStrategy(withTransitivity, mavenStrategyStage);
        zipRemoteEntries.addAll(getRemoteEntryFromLibrary(artifact));
        return zipRemoteEntries;
    }

    public List<ZipRemoteEntry> resolveArtifacts(List<Artifact> artifacts, boolean withTransitivity) {
        MavenResolverSystem mavenResolverSystem = Maven.resolver();
        MavenStrategyStage mavenStrategyStage = mavenResolverSystem.resolve(
                artifacts.stream().map(Artifact::getArtifactFullName).collect(Collectors.toList()));
        List<ZipRemoteEntry> zipRemoteEntries = resolveMavenStrategy(withTransitivity, mavenStrategyStage);
        artifacts.forEach(artifact -> {
            zipRemoteEntries.addAll(getRemoteEntryFromLibrary(artifact));
        });
        return zipRemoteEntries;
    }

    public List<ZipRemoteEntry> resolveArtifactFromPom(MultipartFile pomFile, boolean withTransitivity) {
        List<ZipRemoteEntry> zipRemoteEntries = null;
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

    private List<ZipRemoteEntry> resolveMavenStrategy(boolean withTransitivity,
            MavenStrategyStage mavenStrategyStage) {
        List<MavenResolvedArtifact> mavenArtifacts = new ArrayList<MavenResolvedArtifact>();
        if (withTransitivity) {
            mavenArtifacts.addAll(mavenStrategyStage
                    .withTransitivity().asList(MavenResolvedArtifact.class));
        } else {
            mavenArtifacts.addAll(mavenStrategyStage
                    .withoutTransitivity().asList(MavenResolvedArtifact.class));
        }
        //TODO remove duplicates (see artifact in example txt for example (shrinkwrap-resolver-parent-3.1.4.pom))
        return mavenArtifacts.stream()
                .map(mavenArtifact -> Arrays
                        .stream(mavenArtifact.getDependencies()).map(dependency -> getRemoteEntryFromLibrary(
                                new Artifact(
                                        dependency.getCoordinate().getGroupId(),
                                        dependency.getCoordinate().getArtifactId(),
                                        dependency.getCoordinate().getVersion())))
                        .flatMap(List::stream)
                        .distinct()
                        .collect(Collectors.toList()))
                .flatMap(List::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<ZipRemoteEntry> getRemoteEntryFromLibrary(Artifact artifact) {
        List<ZipRemoteEntry> zipEntries = new ArrayList<>();
        String path = String.format("%s/%s/%s/%s-%s",
                artifact.getGroupId().replace(".", "/"),
                artifact.getArtifactId(),
                artifact.getVersion(),
                artifact.getArtifactId(),
                artifact.getVersion());
        String libPath = String.format("%s.%s", path, artifact.getPackagingType());
        zipEntries.add(new ZipRemoteEntry(libPath, String.format("%s/%s", appConfig.getMavenUrl(), libPath)));
        if (!artifact.getPackagingType().equals("pom")) {
            String pomPath = String.format("%s.%s", path, "pom");
            String pomUrl = String.format("%s/%s", appConfig.getMavenUrl(), pomPath);
            zipEntries.add(new ZipRemoteEntry(pomPath, pomUrl));
            List<ZipRemoteEntry> pomEntries = getParentPomEntries(pomUrl);
            if (pomEntries != null) {
                zipEntries.addAll(pomEntries);
            }
        }
        return zipEntries;
    }

    private List<ZipRemoteEntry> getParentPomEntries(String pomUrl) {
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
                .map(artifact -> getRemoteEntryFromLibrary(artifact))
                .block();
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
