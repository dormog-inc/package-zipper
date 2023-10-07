package com.github.dormog.packagezipper.services;

import com.github.dormog.packagezipper.configuration.AppConfig;
import com.github.dormog.packagezipper.models.Artifact;
import com.github.dormog.packagezipper.models.ZipRemoteEntry;
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

    public List<ZipRemoteEntry> getRemoteEntryFromLibrary(Artifact artifact, String repositoryUrl) {
        List<ZipRemoteEntry> zipEntries = new ArrayList<>();
        boolean isClassifierArtifact = !artifact.getClassifier().equals("");
        String classifierConcatenation = isClassifierArtifact ? "-" + artifact.getClassifier() : "";
        String path = String.format("%s/%s/%s/%s-%s",
                artifact.getGroupId().replace(".", "/"),
                artifact.getArtifactId(),
                artifact.getVersion(),
                artifact.getArtifactId(),
                artifact.getVersion() + classifierConcatenation);
        String libPath = String.format("%s.%s", path, artifact.getPackagingType());
        String libUrl = String.format("%s/%s", repositoryUrl, libPath);
        zipEntries.add(new ZipRemoteEntry(libPath, libUrl));
        if (!isClassifierArtifact) addEquivalentPomUrlForEveryJar(artifact, zipEntries, path, repositoryUrl);
        return zipEntries;
    }

    private void addEquivalentPomUrlForEveryJar(Artifact artifact, List<ZipRemoteEntry> zipEntries, String path, String repositoryUrl) {
        if (!artifact.getPackagingType().equals("pom")) {
            String pomPath = String.format("%s.%s", path, "pom");
            String pomUrl = String.format("%s/%s", repositoryUrl, pomPath);
            zipEntries.add(new ZipRemoteEntry(pomPath, pomUrl));
        }
    }
}
