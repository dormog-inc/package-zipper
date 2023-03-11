package com.dor.package_zipper.maven.resolver;

import com.dor.package_zipper.maven.resolver.manual.ManualRepositorySystemFactory;
import com.dor.package_zipper.maven.resolver.shrinkwrap.MavenSettingsBuilder;
import lombok.Data;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.LocalRepository;

import java.util.Optional;

/**
 * A helper to boot the repository system and a repository system session.
 */
@Data
public class Booter {
    private AbstractRepositoryListener abstractRepositoryListener;
    private String repository;

    public Booter(AbstractRepositoryListener abstractRepositoryListener) {
        this.abstractRepositoryListener = abstractRepositoryListener;
        repository = new MavenSettingsBuilder().buildDefaultSettings().getLocalRepository();
    }

    public Booter() {
        repository = new MavenSettingsBuilder().buildDefaultSettings().getLocalRepository();
    }

    public static RepositorySystem newRepositorySystem() {
        return ManualRepositorySystemFactory.newRepositorySystem();
    }

    public DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository(repository);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        Optional.ofNullable(abstractRepositoryListener).ifPresent(session::setRepositoryListener);
        return session;
    }

}
