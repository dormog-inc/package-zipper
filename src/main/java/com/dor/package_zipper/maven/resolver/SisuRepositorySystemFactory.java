package com.dor.package_zipper.maven.resolver;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import com.google.inject.Guice;
import com.google.inject.Module;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.ModelBuilder;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.sisu.launch.Main;
import org.eclipse.sisu.space.BeanScanning;

/**
 * A factory for repository system instances that employs Eclipse Sisu to wire up the system's components.
 */
@Named
public class SisuRepositorySystemFactory
{
    @Inject
    private RepositorySystem repositorySystem;

    public static RepositorySystem newRepositorySystem()
    {
        final Module app = Main.wire(
            BeanScanning.INDEX,
            new SisuRepositorySystemDemoModule()
        );
        return Guice.createInjector( app ).getInstance( SisuRepositorySystemFactory.class ).repositorySystem;
    }

    @Named
    private static class ModelBuilderProvider
        implements Provider<ModelBuilder>
    {
        public ModelBuilder get()
        {
            return new DefaultModelBuilderFactory().newInstance();
        }
    }
}
