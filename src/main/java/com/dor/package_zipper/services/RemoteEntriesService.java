package com.dor.package_zipper.services;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.models.Artifact;
import com.dor.package_zipper.models.ZipRemoteEntry;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
@EnableConfigurationProperties(AppConfig.class)
public class RemoteEntriesService {
    private final AppConfig appConfig;

    public List<ZipRemoteEntry> getRemoteEntryFromLibrary(Artifact artifact) {
        List<ZipRemoteEntry> zipEntries = new ArrayList<>();
        String path = String.format("%s/%s/%s/%s-%s",
                artifact.getGroupId().replace(".", "/"),
                artifact.getArtifactId(),
                artifact.getVersion(),
                artifact.getArtifactId(),
                artifact.getVersion());
        String libPath = String.format("%s.%s", path, artifact.getPackagingType());
        String libUrl = String.format("%s/%s", appConfig.getMavenUrl(), libPath);
        zipEntries.add(new ZipRemoteEntry(libPath, libUrl));
        addEquivalentPomUrlForEveryJar(artifact, zipEntries, path);
        return zipEntries;
    }

    private void addEquivalentPomUrlForEveryJar(Artifact artifact, List<ZipRemoteEntry> zipEntries, String path) {
        if (!artifact.getPackagingType().equals("pom")) {
            String pomPath = String.format("%s.%s", path, "pom");
            String pomUrl = String.format("%s/%s", appConfig.getMavenUrl(), pomPath);
            zipEntries.add(new ZipRemoteEntry(pomPath, pomUrl));
        }
    }
}
