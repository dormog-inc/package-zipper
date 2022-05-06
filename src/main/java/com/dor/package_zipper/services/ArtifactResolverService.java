package com.dor.package_zipper.services;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.models.ArtifactDTO;
import com.dor.package_zipper.models.ZipRemoteEntry;

import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.MavenStrategyStage;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@AllArgsConstructor
@Slf4j
public class ArtifactResolverService {
    private AppConfig appConfig;

    public List<ZipRemoteEntry> resolveArtifacrt(ArtifactDTO artifact, boolean withTransitivity) {
        MavenStrategyStage mavenStrategyStage = Maven.resolver().resolve(artifact.getArtifactFullName());
        List<ZipRemoteEntry> zipRemoteEntries = resolveMavenStrategy(withTransitivity, mavenStrategyStage);
        zipRemoteEntries.addAll(getRemoteEntryFromLibary(artifact));
        return zipRemoteEntries;
    }

    public List<ZipRemoteEntry> resolveArtifacrts(List<ArtifactDTO> artifacts, boolean withTransitivity) {
        MavenResolverSystem mavenResolverSystem = Maven.resolver();
        MavenStrategyStage mavenStrategyStage = mavenResolverSystem.resolve(
                artifacts.stream().map(artifact -> artifact.getArtifactFullName()).collect(Collectors.toList()));
        List<ZipRemoteEntry> zipRemoteEntries = resolveMavenStrategy(withTransitivity, mavenStrategyStage);
        artifacts.forEach(artifact -> {
            zipRemoteEntries.addAll(getRemoteEntryFromLibary(artifact));
        });
        return zipRemoteEntries;
    }

    public List<ZipRemoteEntry> resolveArtifacrtFromPom(MultipartFile pomFile, boolean withTransitivity) {
        List<ZipRemoteEntry> zipRemoteEntries = null;
        Path path = Paths.get(
                pomFile.getOriginalFilename().replace(".pom", "") + "_" + System.currentTimeMillis() / 1000L + "_pom.xml");
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
        List<ZipRemoteEntry> zipRemoteEntries = mavenArtifacts.stream().map(mavenArtifact -> {
            return Arrays.stream(mavenArtifact.getDependencies()).map(dependency -> getRemoteEntryFromLibary(
                    new ArtifactDTO(
                            dependency.getCoordinate().getGroupId(),
                            dependency.getCoordinate().getArtifactId(),
                            dependency.getCoordinate().getVersion())))
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
        })
                .flatMap(List::stream)
                .collect(Collectors.toList());

        return zipRemoteEntries;
    }

    private List<ZipRemoteEntry> getRemoteEntryFromLibary(ArtifactDTO artifact) {
        List<ZipRemoteEntry> zipEntries = new ArrayList<>();
        String path = String.format("%s/%s/%s/%s-%s",
                artifact.getGroupId().replace(".", "/"),
                artifact.getArtifactId(),
                artifact.getVersion(),
                artifact.getArtifactId(),
                artifact.getVersion());
        String libPath = String.format("%s.%s", path, artifact.getPackagingType());
        String pomPath = String.format("%s.%s", path, "pom");
        zipEntries.add(new ZipRemoteEntry(libPath, String.format("%s/%s", appConfig.getMavenUrl(), libPath)));
        zipEntries.add(new ZipRemoteEntry(pomPath, String.format("%s/%s", appConfig.getMavenUrl(), pomPath)));
        return zipEntries;
    }

}
