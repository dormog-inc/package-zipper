package com.dor.package_zipper.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.models.ArtifactDTO;
import com.dor.package_zipper.models.ZipRemoteEntry;

import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.MavenStrategyStage;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ArtifactResolverService {
    private AppConfig appConfig;

    public List<ZipRemoteEntry> resolveArtifacrt(ArtifactDTO artifact, boolean withTransitivity) {
        MavenStrategyStage mavenStrategyStage = Maven.resolver().resolve(artifact.getArtifactFullName());
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
            zipRemoteEntries.addAll(getRemoteEntryFromLibary(artifact));

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
        zipEntries.add(new ZipRemoteEntry(libPath, String.format("%s/%s", appConfig.getMavenUrl() ,libPath)));
        zipEntries.add(new ZipRemoteEntry(pomPath, String.format("%s/%s", appConfig.getMavenUrl() ,libPath)));
        return zipEntries;
  }
    
}
