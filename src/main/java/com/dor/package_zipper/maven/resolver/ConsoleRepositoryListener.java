package com.dor.package_zipper.maven.resolver;

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.artifact.Artifact;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * A simplistic repository listener that logs events to the console.
 */
public class ConsoleRepositoryListener extends AbstractEventsCrawlerRepositoryListener {
    private final Set<Artifact> allDeps = new HashSet<>();
    private final PrintStream out;

    public ConsoleRepositoryListener() {
        this(null);
    }

    public ConsoleRepositoryListener(PrintStream out) {
        this.out = (out != null) ? out : System.out;
    }

    @Override
    public Set<Artifact> getAllDeps() {
        return allDeps;
    }

    @Override
    public void artifactDeployed(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        out.println("Deployed " + event.getArtifact() + " to " + event.getRepository());
    }

    @Override
    public void artifactDeploying(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        out.println("Deploying " + event.getArtifact() + " to " + event.getRepository());
    }

    @Override
    public void artifactDescriptorInvalid(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        out.println("Invalid artifact descriptor for " + event.getArtifact() + ": "
                + event.getException().getMessage());
    }

    @Override
    public void artifactDescriptorMissing(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        out.println("Missing artifact descriptor for " + event.getArtifact());
    }

    @Override
    public void artifactInstalled(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        out.println("Installed " + event.getArtifact() + " to " + event.getFile());
    }

    @Override
    public void artifactInstalling(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        out.println("Installing " + event.getArtifact() + " to " + event.getFile());
    }

    @Override
    public void artifactResolved(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        out.println("Resolved artifact " + event.getArtifact() + " from " + event.getRepository());
    }

    @Override
    public void artifactDownloading(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        out.println("Downloading artifact " + event.getArtifact() + " from " + event.getRepository());
    }

    @Override
    public void artifactDownloaded(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        out.println("Downloaded artifact " + event.getArtifact() + " from " + event.getRepository());
    }

    @Override
    public void artifactResolving(RepositoryEvent event) {
        allDeps.add(event.getArtifact());
        requireNonNull(event, "event cannot be null");
        out.println("Resolving artifact " + event.getArtifact());
    }

    @Override
    public void metadataDeployed(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        out.println("Deployed " + event.getMetadata() + " to " + event.getRepository());
    }

    @Override
    public void metadataDeploying(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        out.println("Deploying " + event.getMetadata() + " to " + event.getRepository());
    }

    @Override
    public void metadataInstalled(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        out.println("Installed " + event.getMetadata() + " to " + event.getFile());
    }

    @Override
    public void metadataInstalling(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        out.println("Installing " + event.getMetadata() + " to " + event.getFile());
    }

    @Override
    public void metadataInvalid(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        out.println("Invalid metadata " + event.getMetadata());
    }

    @Override
    public void metadataResolved(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        out.println("Resolved metadata " + event.getMetadata() + " from " + event.getRepository());
    }

    @Override
    public void metadataResolving(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        out.println("Resolving metadata " + event.getMetadata() + " from " + event.getRepository());
    }

}
