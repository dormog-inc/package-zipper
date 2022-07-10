package com.dor.package_zipper.maven.resolver.manual;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factory for repository system instances that employs Maven Artifact Resolver's built-in service locator
 * infrastructure to wire up the system's components.
 */
public class ManualRepositorySystemFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManualRepositorySystemFactory.class);

    public static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                LOGGER.error("Service creation failed for {} with implementation {}",
                        type, impl, exception);
            }
        });

        return locator.getService(RepositorySystem.class);
    }

}