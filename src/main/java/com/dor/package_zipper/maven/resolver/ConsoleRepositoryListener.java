package com.dor.package_zipper.maven.resolver;

import java.io.PrintStream;

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;

import static java.util.Objects.requireNonNull;

/**
 * A simplistic repository listener that logs events to the console.
 */
public class ConsoleRepositoryListener
    extends AbstractRepositoryListener
{

    private final PrintStream out;

    public ConsoleRepositoryListener()
    {
        this( null );
    }

    public ConsoleRepositoryListener( PrintStream out )
    {
        this.out = ( out != null ) ? out : System.out;
    }

    public void artifactDeployed( RepositoryEvent event )
    {
        requireNonNull( event, "event cannot be null" );
        out.println( "Deployed " + event.getArtifact() + " to " + event.getRepository() );
    }

    public void artifactDeploying( RepositoryEvent event )
    {
        requireNonNull( event, "event cannot be null" );
        out.println( "Deploying " + event.getArtifact() + " to " + event.getRepository() );
    }

    public void artifactDescriptorInvalid( RepositoryEvent event )
    {
        requireNonNull( event, "event cannot be null" );
        out.println( "Invalid artifact descriptor for " + event.getArtifact() + ": "
            + event.getException().getMessage() );
    }

    public void artifactDescriptorMissing( RepositoryEvent event )
    {
        requireNonNull( event, "event cannot be null" );
        out.println( "Missing artifact descriptor for " + event.getArtifact() );
    }

    public void artifactInstalled( RepositoryEvent event )
    {
        requireNonNull( event, "event cannot be null" );
        out.println( "Installed " + event.getArtifact() + " to " + event.getFile() );
    }

    public void artifactInstalling( RepositoryEvent event )
    {
        requireNonNull( event, "event cannot be null" );
        out.println( "Installing " + event.getArtifact() + " to " + event.getFile() );
    }

    public void artifactResolved( RepositoryEvent event )
    {
        requireNonNull( event, "event cannot be null" );
        out.println( "Resolved artifact " + event.getArtifact() + " from " + event.getRepository() );
    }

    public void artifactDownloading( RepositoryEvent event )
    {
        requireNonNull( event, "event cannot be null" );
        out.println( "Downloading artifact " + event.getArtifact() + " from " + event.getRepository() );
    }

    public void artifactDownloaded( RepositoryEvent event )
    {
        requireNonNull( event, "event cannot be null" );
        out.println( "Downloaded artifact " + event.getArtifact() + " from " + event.getRepository() );
    }

    public void artifactResolving( RepositoryEvent event )
    {
        requireNonNull( event, "event cannot be null" );
        out.println( "Resolving artifact " + event.getArtifact() );
    }

    public void metadataDeployed( RepositoryEvent event )
    {
        requireNonNull( event, "event cannot be null" );
        out.println( "Deployed " + event.getMetadata() + " to " + event.getRepository() );
    }

    public void metadataDeploying( RepositoryEvent event )
    {
        requireNonNull( event, "event cannot be null" );
        out.println( "Deploying " + event.getMetadata() + " to " + event.getRepository() );
    }

    public void metadataInstalled( RepositoryEvent event )
    {
        requireNonNull( event, "event cannot be null" );
        out.println( "Installed " + event.getMetadata() + " to " + event.getFile() );
    }

    public void metadataInstalling( RepositoryEvent event )
    {
        requireNonNull( event, "event cannot be null" );
        out.println( "Installing " + event.getMetadata() + " to " + event.getFile() );
    }

    public void metadataInvalid( RepositoryEvent event )
    {
        requireNonNull( event, "event cannot be null" );
        out.println( "Invalid metadata " + event.getMetadata() );
    }

    public void metadataResolved( RepositoryEvent event )
    {
        requireNonNull( event, "event cannot be null" );
        out.println( "Resolved metadata " + event.getMetadata() + " from " + event.getRepository() );
    }

    public void metadataResolving( RepositoryEvent event )
    {
        requireNonNull( event, "event cannot be null" );
        out.println( "Resolving metadata " + event.getMetadata() + " from " + event.getRepository() );
    }

}
