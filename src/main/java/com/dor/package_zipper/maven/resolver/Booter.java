package com.dor.package_zipper.maven.resolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * A helper to boot the repository system and a repository system session.
 */
public class Booter
{
    public static final String SERVICE_LOCATOR = "serviceLocator";

    public static final String GUICE = "guice";

    public static final String SISU = "sisu";

    public static String selectFactory( String[] args )
    {
        if ( args == null || args.length == 0 )
        {
            return SERVICE_LOCATOR;
        }
        else
        {
            return args[0];
        }
    }

    public static RepositorySystem newRepositorySystem( final String factory )
    {
        switch ( factory ) 
        {
            case SERVICE_LOCATOR:
                return org.apache.maven.resolver.examples.manual.ManualRepositorySystemFactory.newRepositorySystem();
            case GUICE:
                return org.apache.maven.resolver.examples.guice.GuiceRepositorySystemFactory.newRepositorySystem();
            case SISU:
                return org.apache.maven.resolver.examples.sisu.SisuRepositorySystemFactory.newRepositorySystem();
            default:
                throw new IllegalArgumentException( "Unknown factory: " + factory );
        }
    }

    public static DefaultRepositorySystemSession newRepositorySystemSession( RepositorySystem system )
    {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        LocalRepository localRepo = new LocalRepository( "target/local-repo" );
        session.setLocalRepositoryManager( system.newLocalRepositoryManager( session, localRepo ) );

        session.setTransferListener( new ConsoleTransferListener() );
        session.setRepositoryListener( new ConsoleRepositoryListener() );

        // uncomment to generate dirty trees
        // session.setDependencyGraphTransformer( null );

        return session;
    }

    public static List<RemoteRepository> newRepositories( RepositorySystem system, RepositorySystemSession session )
    {
        return new ArrayList<>( Collections.singletonList( newCentralRepository() ) );
    }

    private static RemoteRepository newCentralRepository()
    {
        return new RemoteRepository.Builder( "central", "default", "https://repo.maven.apache.org/maven2/" ).build();
    }

}
