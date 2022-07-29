package com.dor.package_zipper.services;

import com.dor.package_zipper.maven.resolver.EventsCrawlerRepositoryListener;
import com.dor.package_zipper.models.*;
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
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.dor.package_zipper.configuration.RepositoryConfig.GRADLE_PLUGINS_REPOSITORY;
import static com.dor.package_zipper.configuration.RepositoryConfig.MAVEN_REPOSITORY;
import static com.dor.package_zipper.services.GradlePluginsHandler.GRADLE_PLUGIN;

@Service
@AllArgsConstructor
@Slf4j
public class ArtifactResolverService {
    private final ObjectFactory<SessionManager> sessionManager;
    private final ObjectFactory<CleanBooterSessionManager> cleanBooterSessionManager;
    private final List<RemoteRepository> remoteRepositories;
    private final RemoteEntriesService remoteEntriesService;



    public ResolvingProcessServiceResult resolveArtifact(Artifact artifact, ShipmentLevel level) {
        return switch (level) {
            case HEAVY -> heavyLevelResolvingStrategy(artifact);
            case JAR_BASED -> jarsBasedLevelResolvingStrategy(artifact);
            case EXACTLY -> exactlyLevelResolvingStrategy(artifact);
            case SINGLE -> singleLevelResolvingStrategy(artifact);
        };
    }

    public List<ResolvingProcessServiceResult> resolveArtifacts(List<Artifact> artifacts, ShipmentLevel level) {
        return switch (level) {
            case HEAVY -> getZipRemoteEntries(artifacts, this::heavyLevelResolvingStrategy);
            case JAR_BASED -> getZipRemoteEntries(artifacts, this::jarsBasedLevelResolvingStrategy);
            case EXACTLY -> getZipRemoteEntries(artifacts, this::exactlyLevelResolvingStrategy);
            case SINGLE -> getZipRemoteEntries(artifacts, this::singleLevelResolvingStrategy);
        };
    }

    private ResolvingProcessServiceResult heavyLevelResolvingStrategy(Artifact originalArtifact) {
        return Try.of(() -> {
            SessionManager sessionManagerObject = sessionManager.getObject();
            ResolvingProcessAetherResult resolvingProcessAetherResult = getArtifactsListsFromArtifact(originalArtifact,
                    sessionManagerObject.getSystem(),
                    sessionManagerObject.getSession());
            List<RepositoryAwareAetherArtifact> managedArtifacts = new ArrayList<>(resolvingProcessAetherResult.getArtifactList());
            managedArtifacts.addAll(((EventsCrawlerRepositoryListener) sessionManagerObject.getEventsCrawlerRepositoryListener()).getAllRepositoryAwareDeps());
            List<ZipRemoteEntry> zipRemoteEntryFlux = getZipRemoteEntryFlux(managedArtifacts);
            return new ResolvingProcessServiceResult(zipRemoteEntryFlux, resolvingProcessAetherResult.getExceptionMessage());
        }).onFailure(Throwable::printStackTrace).get();
    }

    /**
     * Resolve artifacts exactly like a local maven client.
     */
    private ResolvingProcessServiceResult exactlyLevelResolvingStrategy(Artifact originalArtifact) {
        return Try.of(() -> {
            SessionManager sessionManagerObject = sessionManager.getObject();
            ResolvingProcessAetherResult resolvingProcessAetherResult = getArtifactsListsFromArtifact(originalArtifact,
                    sessionManagerObject.getSystem(), sessionManagerObject.getSession());
            Set<RepositoryAwareAetherArtifact> managedArtifacts =
                    ((EventsCrawlerRepositoryListener) sessionManagerObject.getEventsCrawlerRepositoryListener()).getAllRepositoryAwareDeps();
            return new ResolvingProcessServiceResult(getZipRemoteEntryFlux(managedArtifacts.stream().toList()), resolvingProcessAetherResult.getExceptionMessage());
        }).onFailure(Throwable::printStackTrace).get();
    }

    /**
     * Resolve artifacts without import scope. This isn't a full nor traditional Maven strategy,
     * but this not-pom-friendly strategy turned out to be useful in many cases.
     */
    private ResolvingProcessServiceResult jarsBasedLevelResolvingStrategy(Artifact originalArtifact) {
        return Try.of(() -> {
            CleanBooterSessionManager sessionManagerObject = cleanBooterSessionManager.getObject();
            ResolvingProcessAetherResult resolvingProcessAetherResult = getArtifactsListsFromArtifact(originalArtifact,
                    sessionManagerObject.getSystem(),
                    sessionManagerObject.getSession());
            return new ResolvingProcessServiceResult(getZipRemoteEntryFlux(new ArrayList<>(resolvingProcessAetherResult
                    .getArtifactList())),
                    resolvingProcessAetherResult.getExceptionMessage());
        }).onFailure(Throwable::printStackTrace).get();
    }

    /**
     * Resolve one artifact.
     */
    private ResolvingProcessServiceResult singleLevelResolvingStrategy(Artifact originalArtifact) {
        return Try.of(() -> {
            ResolvingProcessAetherResult singleArtifactResolving = singleArtifactResolving(originalArtifact,
                    cleanBooterSessionManager.getObject().getSystem(),
                    cleanBooterSessionManager.getObject().getSession());
            return new ResolvingProcessServiceResult(getZipRemoteEntryFlux(new ArrayList<>(singleArtifactResolving.getArtifactList())));
        }).onFailure(Throwable::printStackTrace).get();
    }

    private List<ResolvingProcessServiceResult> getZipRemoteEntries(List<Artifact> artifacts, Function<Artifact, ResolvingProcessServiceResult> resolvingStrategy) {
        return artifacts.stream()
                .map(resolvingStrategy)
                .toList();
    }

    private List<ZipRemoteEntry> getZipRemoteEntryFlux(List<RepositoryAwareAetherArtifact> managedArtifacts) {
        return managedArtifacts
                .stream()
                .flatMap(dependency -> remoteEntriesService.getRemoteEntryFromLibrary(
                        new Artifact(
                                dependency.getArtifact().getGroupId(),
                                dependency.getArtifact().getArtifactId(),
                                dependency.getArtifact().getVersion(),
                                dependency.getArtifact().getExtension(),
                                dependency.getArtifact().getClassifier()),
                        dependency.getRepository() == null ? MAVEN_REPOSITORY : dependency.getRepository()
                ).stream())
                .distinct()
                .toList();
    }

    private ResolvingProcessAetherResult getArtifactsListsFromArtifact(Artifact originalArtifact, RepositorySystem system, DefaultRepositorySystemSession session) throws ArtifactDescriptorException, DependencyResolutionException, ArtifactResolutionException {
        session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
        session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);

        org.eclipse.aether.artifact.Artifact aetherArtifact = new DefaultArtifact(originalArtifact.getArtifactGradleFashionedName());

        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact(aetherArtifact);
        descriptorRequest.setRepositories(remoteRepositories);
        ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);

        DependencyRequest dependencyRequest = getDependencyRequest(descriptorRequest, descriptorResult);

        try {
            DependencyResult dependencyResult = system.resolveDependencies(session, dependencyRequest);
            List<RepositoryAwareAetherArtifact> managedArtifacts = calculateArtifactsList(originalArtifact, system, session, dependencyResult);
            return new ResolvingProcessAetherResult(managedArtifacts);
        } catch (DependencyResolutionException e) {
            e.printStackTrace();
            DependencyResult dependencyResult = e.getResult();
            List<RepositoryAwareAetherArtifact> managedArtifacts = calculateArtifactsList(originalArtifact, system, session, dependencyResult);
            return new ResolvingProcessAetherResult(managedArtifacts, e.getMessage());
        }
    }

    private List<RepositoryAwareAetherArtifact> calculateArtifactsList(Artifact originalArtifact, RepositorySystem system, DefaultRepositorySystemSession session, DependencyResult dependencyResult) {
        List<RepositoryAwareAetherArtifact> managedArtifacts = dependencyResult.getRequest()
                .getCollectRequest()
                .getManagedDependencies()
                .stream()
                .map(Dependency::getArtifact)
                .map(RepositoryAwareAetherArtifact::new)
                .collect(Collectors.toList());
        Try.of(() -> managedArtifacts.addAll(singleArtifactResolving(originalArtifact, system, session).getArtifactList()
                        .stream().toList()))
                .onFailure(err -> {
                    log.error(err.getMessage());
                    if (originalArtifact.getArtifactId().contains(GRADLE_PLUGIN)) {
                        managedArtifacts.add(new RepositoryAwareAetherArtifact(new DefaultArtifact(
                                originalArtifact.getGroupId(),
                                originalArtifact.getArtifactId(),
                                originalArtifact.getClassifier(),
                                "jar",
                                originalArtifact.getVersion()), GRADLE_PLUGINS_REPOSITORY));
                    }
                });
        return managedArtifacts.stream().distinct().toList();
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

    private ResolvingProcessAetherResult singleArtifactResolving(Artifact originalArtifact, RepositorySystem system, DefaultRepositorySystemSession session) throws ArtifactResolutionException {
        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(new DefaultArtifact(originalArtifact.getArtifactGradleFashionedName()));
        artifactRequest.setRepositories(remoteRepositories);
        ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
        return new ResolvingProcessAetherResult(Collections.singletonList(new RepositoryAwareAetherArtifact(artifactResult.getArtifact())));
    }
}
