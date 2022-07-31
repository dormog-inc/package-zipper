package com.dor.package_zipper.maven.resolver;

import com.dor.package_zipper.models.RepositoryAwareAetherArtifact;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A simplistic {@link org.eclipse.aether.RepositorySystem} listener that extracts dependencies from events.
 */
@Slf4j
public class EventsCrawlerRepositoryListener extends AbstractEventsCrawlerRepositoryListener {
    private final Set<RepositoryAwareAetherArtifact> allDeps = new HashSet<>();

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
            ArtifactRepository repository = event.getRepository();
            if (repository instanceof RemoteRepository remoteRepository) {
                allDeps.add(new RepositoryAwareAetherArtifact(event.getArtifact(), remoteRepository.getUrl()));
            }
            if (event.getArtifact().getExtension().equals("jar")) {
                if (file.exists()) {
                    try {
                        Files.delete(file.toPath());
                    } catch (Exception e) {
                        log.error("Could not delete file from FileSystem. Reason was {}", e.getCause().toString());
                    }
                } else {
                    log.info("Note - the file {} does not exists on the system, even downloading operation was considered a success.", event.getArtifact().toString());
                }
            }
        });
    }
}
