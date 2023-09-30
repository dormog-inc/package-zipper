package com.dor.package_zipper.maven.resolver;

import com.dor.package_zipper.models.RepositoryAwareAetherArtifact;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A simplistic {@link org.eclipse.aether.RepositorySystem} listener that extracts dependencies from events.
 * This repository listener prevents Jars from getting downloaded, since it is made to help to extract the dependencies list, and not downloading pom files.
 */
@Slf4j
public class EventsCrawlerRepositoryListener extends AbstractEventsCrawlerRepositoryListener {
    private final Set<RepositoryAwareAetherArtifact> allDeps = new HashSet<>();
    private final Set<String> allDepsString = new HashSet<>();

    @Override
    public Set<Artifact> getAllDeps() {
        return allDeps.stream().distinct().map(RepositoryAwareAetherArtifact::getArtifact).collect(Collectors.toSet());
    }

    public Set<RepositoryAwareAetherArtifact> getAllRepositoryAwareDeps() {
        return allDeps;
    }

    @Override
    public void artifactDownloaded(RepositoryEvent event) {
        Optional<File> optionalFile = Optional.ofNullable(event.getFile());
        optionalFile.ifPresent(file -> {
            log.info("The event's path is {}", event.getFile().getAbsolutePath());
        });
    }

    @Override
    public void artifactDownloading(RepositoryEvent event) {
        // Return null to prevent the artifact from being installed
//        if (!event.getArtifact().getExtension().equals("jar")) {
//            super.artifactDownloading(event);
//        }
    }
    @Override
    public void artifactInstalling(RepositoryEvent event) {
    }
    @Override
    public void metadataInstalling(RepositoryEvent event) {
    }
    @Override
    public void metadataDownloading(RepositoryEvent event) {
    }

    @Override
    public void artifactResolved(RepositoryEvent event) {
        if (event.getRepository() instanceof RemoteRepository remoteRepository && !allDepsString.contains(event.getArtifact().toString())) {
            allDeps.add(new RepositoryAwareAetherArtifact(event.getArtifact(), remoteRepository.getUrl()));
            allDepsString.add(event.getArtifact().toString());
        }
        super.artifactResolved(event);
    }
}
