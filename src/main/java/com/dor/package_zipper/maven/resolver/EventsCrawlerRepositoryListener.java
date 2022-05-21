package com.dor.package_zipper.maven.resolver;

import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.artifact.Artifact;

import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * A simplistic repository listener that logs events to the console.
 */
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

}
