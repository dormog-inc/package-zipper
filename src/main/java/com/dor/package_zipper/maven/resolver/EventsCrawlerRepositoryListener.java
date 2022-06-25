package com.dor.package_zipper.maven.resolver;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.artifact.Artifact;

import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/**
 * A simplistic repository listener that logs events to the console.
 */
@Slf4j
public class EventsCrawlerRepositoryListener extends AbstractEventsCrawlerRepositoryListener {
    private final Set<Artifact> allDeps = new HashSet<>();

    @Override
    public Set<Artifact> getAllDeps() {
        return allDeps;
    }

    @Override
    public void artifactResolving(RepositoryEvent event) {
        allDeps.add(event.getArtifact());
    }

    @Override
    public void artifactDownloaded(RepositoryEvent event) {
        File file = event.getFile();
        log.info("The event's path is {}", event.getFile().getAbsolutePath());
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
    }
}
