package com.github.dormog.packagezipper.maven.resolver;

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.artifact.Artifact;

import java.util.Set;

/**
 * A skeleton for custom event crawlers repository listeners.
 */
public abstract class AbstractEventsCrawlerRepositoryListener extends AbstractRepositoryListener {
    private Set<Artifact> allDeps;

    public Set<Artifact> getAllDeps() {
        return allDeps;
    }

    /**
     * Enables subclassing.
     */
    protected AbstractEventsCrawlerRepositoryListener()
    {
    }
}
