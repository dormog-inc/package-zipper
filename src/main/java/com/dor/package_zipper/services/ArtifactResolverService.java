package com.dor.package_zipper.services;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.maven.resolver.AbstractEventsCrawlerRepositoryListener;
import com.dor.package_zipper.maven.resolver.Booter;
import com.dor.package_zipper.maven.resolver.ConsoleRepositoryListener;
import com.dor.package_zipper.maven.resolver.EventsCrawlerRepositoryListener;
import com.dor.package_zipper.models.Artifact;
import com.dor.package_zipper.models.ShipmentLevel;
import com.dor.package_zipper.models.ZipRemoteEntry;
import io.vavr.control.Try;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class ArtifactResolverService {
    private final SessionManager sessionManager;
    private final CleanBooterSessionManager cleanBooterSessionManager;
    private final RemoteRepository newCentralRepository;
    private final RemoteEntriesService remoteEntriesService;

    public List<ZipRemoteEntry> resolveArtifact(Artifact artifact, ShipmentLevel level) {
        return switch (level) {
            case HEAVY -> heavyLevelResolvingStrategy(artifact);
            case JAR_BASED ->  jarsBasedLevelResolvingStrategy(artifact);
            case EXACTLY ->  exactlyLevelResolvingStrategy(artifact);
            case SINGLE ->  singleLevelResolvingStrategy(artifact);
        };
    }

    private List<ZipRemoteEntry> heavyLevelResolvingStrategy(Artifact originalArtifact) {
        return Try.of(() -> {
            List<org.eclipse.aether.artifact.Artifact> managedArtifacts = getArtifactsListsFromArtifact(originalArtifact,
                    sessionManager.getSystem(),
                    sessionManager.getSession());
            managedArtifacts.addAll(sessionManager.getEventsCrawlerRepositoryListener().getAllDeps());
            return getZipRemoteEntryFlux(managedArtifacts);
        }).onFailure(Throwable::printStackTrace).get();
    }

    /**
     * Resolve artifacts exactly like a local maven client.
     */
    private List<ZipRemoteEntry> exactlyLevelResolvingStrategy(Artifact originalArtifact) {
        return Try.of(() -> {
            getArtifactsListsFromArtifact(originalArtifact, sessionManager.getSystem(), sessionManager.getSession());
            Set<org.eclipse.aether.artifact.Artifact> managedArtifacts = sessionManager.getEventsCrawlerRepositoryListener().getAllDeps();
            return getZipRemoteEntryFlux(managedArtifacts.stream().toList());
        }).onFailure(Throwable::printStackTrace).get();
    }

    /**
     * Resolve artifacts without import scope. This isn't a full nor traditional Maven strategy,
     * but this not-pom-friendly strategy turned out to be useful in many cases.
     */
    private List<ZipRemoteEntry> jarsBasedLevelResolvingStrategy(Artifact originalArtifact) {
        return Try.of(() -> getZipRemoteEntryFlux(getArtifactsListsFromArtifact(originalArtifact,
                cleanBooterSessionManager.getSystem(),
                cleanBooterSessionManager.getSession()))).onFailure(Throwable::printStackTrace).get();
    }

    /**
     * Resolve one artifact.
     */
    private List<ZipRemoteEntry> singleLevelResolvingStrategy(Artifact originalArtifact) {
        return Try.of(() -> getZipRemoteEntryFlux(Collections.singletonList(singleArtifactResolving(originalArtifact,
                cleanBooterSessionManager.getSystem(),
                cleanBooterSessionManager.getSession())))).onFailure(Throwable::printStackTrace).get();
    }

    public List<ZipRemoteEntry> resolveArtifacts(List<Artifact> artifacts, ShipmentLevel level) {
        return switch (level) {
            case HEAVY -> getZipRemoteEntries(artifacts, this::heavyLevelResolvingStrategy);
            case JAR_BASED -> getZipRemoteEntries(artifacts, this::jarsBasedLevelResolvingStrategy);
            case EXACTLY -> getZipRemoteEntries(artifacts, this::exactlyLevelResolvingStrategy);
            case SINGLE -> getZipRemoteEntries(artifacts, this::singleLevelResolvingStrategy);
        };
    }

    private List<ZipRemoteEntry> getZipRemoteEntries(List<Artifact> artifacts, Function<Artifact, List<ZipRemoteEntry>> resolvingStrategy) {
        return artifacts.stream()
                .map(resolvingStrategy)
                .flatMap(Collection::stream)
                .toList();
    }

    private List<ZipRemoteEntry> getZipRemoteEntryFlux(List<org.eclipse.aether.artifact.Artifact> managedArtifacts) {
        return managedArtifacts
                .stream()
                .flatMap(dependency -> remoteEntriesService.getRemoteEntryFromLibrary(
                        new Artifact(
                                dependency.getGroupId(),
                                dependency.getArtifactId(),
                                dependency.getVersion(),
                                dependency.getExtension(),
                                dependency.getClassifier())).stream())
                .distinct()
                .toList();
    }

    private List<org.eclipse.aether.artifact.Artifact> getArtifactsListsFromArtifact(Artifact originalArtifact, RepositorySystem system, DefaultRepositorySystemSession session) throws ArtifactDescriptorException, DependencyResolutionException, ArtifactResolutionException {
        session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
        session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);

        org.eclipse.aether.artifact.Artifact aetherArtifact = new DefaultArtifact(originalArtifact.getArtifactGradleFashionedName());

        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact(aetherArtifact);
        descriptorRequest.setRepositories(Collections.singletonList(newCentralRepository));
        ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);

        DependencyRequest dependencyRequest = getDependencyRequest(descriptorRequest, descriptorResult);

        DependencyResult dependencyResult = system.resolveDependencies(session, dependencyRequest);
        List<org.eclipse.aether.artifact.Artifact> managedArtifacts = dependencyResult.getRequest().getCollectRequest().getManagedDependencies().stream().map(Dependency::getArtifact).collect(Collectors.toList());

        Try.of(() -> managedArtifacts.add(singleArtifactResolving(originalArtifact, system, session)))
                .onFailure(err -> log.error(err.getMessage()));
        return managedArtifacts;
    }

    private DependencyRequest getDependencyRequest(ArtifactDescriptorRequest descriptorRequest, ArtifactDescriptorResult descriptorResult) {
        CollectRequest collectRequest = getCollectRequest(descriptorRequest, descriptorResult);

        DependencyFilter classpathFilter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE,
                JavaScopes.RUNTIME,
                JavaScopes.TEST,
                JavaScopes.SYSTEM,
                JavaScopes.PROVIDED);
        return new DependencyRequest(collectRequest, classpathFilter);
    }

    private CollectRequest getCollectRequest(ArtifactDescriptorRequest descriptorRequest, ArtifactDescriptorResult descriptorResult) {
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRootArtifact(descriptorResult.getArtifact());
        collectRequest.setDependencies(descriptorResult.getDependencies());
        collectRequest.setManagedDependencies(descriptorResult.getManagedDependencies());
        collectRequest.setRepositories(descriptorRequest.getRepositories());
        return collectRequest;
    }

    private org.eclipse.aether.artifact.Artifact singleArtifactResolving(Artifact originalArtifact, RepositorySystem system, DefaultRepositorySystemSession session) throws ArtifactResolutionException {
        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(new DefaultArtifact(originalArtifact.getArtifactGradleFashionedName()));
        artifactRequest.setRepositories(Collections.singletonList(newCentralRepository));
        ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
        return artifactResult.getArtifact();
    }
}
