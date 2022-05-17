package com.dor.package_zipper.maven.resolver.guice;

import org.eclipse.aether.RepositorySystem;

import com.google.inject.Guice;

/**
 * A factory for repository system instances that employs JSR-330 via Guice to wire up the system's components.
 */
public class GuiceRepositorySystemFactory
{

    public static RepositorySystem newRepositorySystem()
    {
        return Guice.createInjector( new DemoResolverModule() ).getInstance( RepositorySystem.class );
    }

}
