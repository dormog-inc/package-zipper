package com.dor.package_zipper.maven.resolver;

import com.dor.package_zipper.maven.resolver.manual.ManualRepositorySystemFactory;
import com.dor.package_zipper.maven.resolver.shrinkwrap.MavenSettingsBuilder;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A helper to boot the repository system and a repository system session.
 */
public class Booter {
    private AbstractRepositoryListener abstractRepositoryListener;

    public Booter(AbstractRepositoryListener abstractRepositoryListener) {
        this.abstractRepositoryListener = abstractRepositoryListener;
    }

    public Booter() {
    }

    public static RepositorySystem newRepositorySystem() {
        return ManualRepositorySystemFactory.newRepositorySystem();
    }

    public DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository(new MavenSettingsBuilder().buildDefaultSettings().getLocalRepository());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        Optional.ofNullable(abstractRepositoryListener).ifPresent(session::setRepositoryListener);

        return session;
    }

}
