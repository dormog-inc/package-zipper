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
    private final RemoteEntriesService remoteEntriesService;


    public ResolvingProcessServiceResult resolveArtifact(Artifact artifact, ShipmentLevel level, List<String> sessionsRemoteRepositoryList, boolean shouldBringClassifiers) {
        List<RemoteRepository> remoteRepositories = getRemoteRepositories(sessionsRemoteRepositoryList);
        return switch (level) {
            case HEAVY -> heavyLevelResolvingStrategy(artifact, remoteRepositories, shouldBringClassifiers);
            case JAR_BASED -> jarsBasedLevelResolvingStrategy(artifact, remoteRepositories, shouldBringClassifiers);
            case EXACTLY -> exactlyLevelResolvingStrategy(artifact, remoteRepositories, shouldBringClassifiers);
            case SINGLE -> singleLevelResolvingStrategy(artifact, remoteRepositories, shouldBringClassifiers);
        };
    }

    private List<RemoteRepository> getRemoteRepositories(List<String> sessionsRemoteRepositoryList) {
        List<RemoteRepository> remoteRepositories = new ArrayList<>();
        for (int i = 0; i < sessionsRemoteRepositoryList.size(); i++) {
            remoteRepositories.add(new RemoteRepository.Builder(String.valueOf(i), "default", sessionsRemoteRepositoryList.get(i)).build());
        }
        return remoteRepositories;
    }

    public List<ResolvingProcessServiceResult> resolveArtifacts(List<Artifact> artifacts, ShipmentLevel level, List<String> sessionsRemoteRepositoryList, boolean shouldBringClassifiers) {
        List<RemoteRepository> remoteRepositories = getRemoteRepositories(sessionsRemoteRepositoryList);
        return switch (level) {
            case HEAVY -> getZipRemoteEntries(artifacts, originalArtifact -> heavyLevelResolvingStrategy(originalArtifact, remoteRepositories, shouldBringClassifiers));
            case JAR_BASED -> getZipRemoteEntries(artifacts, originalArtifact -> jarsBasedLevelResolvingStrategy(originalArtifact, remoteRepositories, shouldBringClassifiers));
            case EXACTLY -> getZipRemoteEntries(artifacts, originalArtifact -> exactlyLevelResolvingStrategy(originalArtifact, remoteRepositories, shouldBringClassifiers));
            case SINGLE -> getZipRemoteEntries(artifacts, originalArtifact -> singleLevelResolvingStrategy(originalArtifact, remoteRepositories, shouldBringClassifiers));
        };
    }

    private ResolvingProcessServiceResult heavyLevelResolvingStrategy(Artifact originalArtifact, List<RemoteRepository> sessionsRemoteRepositoryList, boolean shouldBringClassifiers) {
        return Try.of(() -> {
            SessionManager sessionManagerObject = sessionManager.getObject();
            ResolvingProcessAetherResult resolvingProcessAetherResult = getArtifactsListsFromArtifact(originalArtifact,
                    sessionManagerObject.getSystem(),
                    sessionManagerObject.getSession(),
                    sessionsRemoteRepositoryList,
                    shouldBringClassifiers);
            List<RepositoryAwareAetherArtifact> managedArtifacts = new ArrayList<>(resolvingProcessAetherResult.getArtifactList());
            managedArtifacts.addAll(((EventsCrawlerRepositoryListener) sessionManagerObject.getEventsCrawlerRepositoryListener()).getAllRepositoryAwareDeps());
            List<ZipRemoteEntry> zipRemoteEntryFlux = getZipRemoteEntryFlux(managedArtifacts);
            return new ResolvingProcessServiceResult(zipRemoteEntryFlux, resolvingProcessAetherResult.getExceptionMessages());
        }).onFailure(Throwable::printStackTrace).get();
    }

    /**
     * Resolve artifacts exactly like a local maven client.
     */
    private ResolvingProcessServiceResult exactlyLevelResolvingStrategy(Artifact originalArtifact, List<RemoteRepository> sessionsRemoteRepositoryList, boolean shouldBringClassifiers) {
        try {
            SessionManager sessionManagerObject = sessionManager.getObject();
            ResolvingProcessAetherResult resolvingProcessAetherResult = getArtifactsListsFromArtifact(originalArtifact,
                    sessionManagerObject.getSystem(), sessionManagerObject.getSession(), sessionsRemoteRepositoryList, shouldBringClassifiers);
            Set<RepositoryAwareAetherArtifact> managedArtifacts =
                    ((EventsCrawlerRepositoryListener) sessionManagerObject.getEventsCrawlerRepositoryListener()).getAllRepositoryAwareDeps();
            return new ResolvingProcessServiceResult(getZipRemoteEntryFlux(managedArtifacts.stream().toList()), resolvingProcessAetherResult.getExceptionMessages());
        } catch (Exception e) {
            e.printStackTrace();
            return createErrorResolvingProcessServiceResult(e);
        }
    }

    private ResolvingProcessServiceResult createErrorResolvingProcessServiceResult(Exception e) {
        return new ResolvingProcessServiceResult(new ArrayList<>(), List.of(e.getMessage()));
    }

    /**
     * Resolve artifacts without import scope. This isn't a full nor traditional Maven strategy,
     * but this not-pom-friendly strategy turned out to be useful in many cases.
     */
    private ResolvingProcessServiceResult jarsBasedLevelResolvingStrategy(Artifact originalArtifact, List<RemoteRepository> sessionsRemoteRepositoryList, boolean shouldBringClassifiers) {
        return Try.of(() -> {
            CleanBooterSessionManager sessionManagerObject = cleanBooterSessionManager.getObject();
            ResolvingProcessAetherResult resolvingProcessAetherResult = getArtifactsListsFromArtifact(originalArtifact,
                    sessionManagerObject.getSystem(),
                    sessionManagerObject.getSession(),
                    sessionsRemoteRepositoryList,
                    shouldBringClassifiers);
            return new ResolvingProcessServiceResult(getZipRemoteEntryFlux(new ArrayList<>(resolvingProcessAetherResult
                    .getArtifactList())),
                    resolvingProcessAetherResult.getExceptionMessages());
        }).onFailure(Throwable::printStackTrace).get();
    }

    /**
     * Resolve one artifact.
     */
    private ResolvingProcessServiceResult singleLevelResolvingStrategy(Artifact originalArtifact, List<RemoteRepository> sessionsRemoteRepositoryList, boolean shouldBringClassifiers) {
        return Try.of(() -> {
            ResolvingProcessAetherResult singleArtifactResolving = singleArtifactResolving(originalArtifact,
                    cleanBooterSessionManager.getObject().getSystem(),
                    cleanBooterSessionManager.getObject().getSession(),
                    sessionsRemoteRepositoryList);
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

    private ResolvingProcessAetherResult getArtifactsListsFromArtifact(Artifact originalArtifact,
                                                                       RepositorySystem system,
                                                                       DefaultRepositorySystemSession session,
                                                                       List<RemoteRepository> sessionsRemoteRepositoryList,
                                                                       boolean shouldBringClassifiers
    ) throws ArtifactDescriptorException {
        session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
        session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);

        org.eclipse.aether.artifact.Artifact aetherArtifact = new DefaultArtifact(originalArtifact.getArtifactGradleFashionedName());

        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact(aetherArtifact);
        descriptorRequest.setRepositories(sessionsRemoteRepositoryList);
        ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);

        DependencyRequest dependencyRequest = getDependencyRequest(descriptorRequest, descriptorResult, shouldBringClassifiers);

        try {
            DependencyResult dependencyResult = system.resolveDependencies(session, dependencyRequest);
            return calculateArtifactsList(originalArtifact, system, session, dependencyResult, sessionsRemoteRepositoryList);
        } catch (DependencyResolutionException e) {
            e.printStackTrace();
            DependencyResult dependencyResult = e.getResult();
            return calculateArtifactsList(originalArtifact, system, session, dependencyResult, sessionsRemoteRepositoryList);
        }
    }

    private ResolvingProcessAetherResult calculateArtifactsList(Artifact originalArtifact,
                                                                RepositorySystem system,
                                                                DefaultRepositorySystemSession session,
                                                                DependencyResult dependencyResult,
                                                                List<RemoteRepository> remoteRepositories) {
        List<RepositoryAwareAetherArtifact> managedArtifacts = dependencyResult.getRequest()
                .getCollectRequest()
                .getManagedDependencies()
                .stream()
                .map(Dependency::getArtifact)
                .map(RepositoryAwareAetherArtifact::new)
                .collect(Collectors.toList());
        try {
            managedArtifacts.addAll(singleArtifactResolving(originalArtifact, system, session, remoteRepositories).getArtifactList()
                    .stream().toList());
            return new ResolvingProcessAetherResult(managedArtifacts.stream().distinct().toList(), List.of());
        } catch (Exception e) {
            if (originalArtifact.getArtifactId().contains(GRADLE_PLUGIN)) {
                managedArtifacts.add(new RepositoryAwareAetherArtifact(new DefaultArtifact(
                        originalArtifact.getGroupId(),
                        originalArtifact.getArtifactId(),
                        originalArtifact.getClassifier(),
                        "jar",
                        originalArtifact.getVersion()), GRADLE_PLUGINS_REPOSITORY));
            } else {
                log.error(e.getMessage());
            }
            return new ResolvingProcessAetherResult(managedArtifacts.stream().distinct().toList(), List.of(e.getMessage()));
        }
    }

    private DependencyRequest getDependencyRequest(ArtifactDescriptorRequest descriptorRequest, ArtifactDescriptorResult descriptorResult, boolean shouldBringClassifiers) {
        CollectRequest collectRequest = getCollectRequest(descriptorRequest, descriptorResult, shouldBringClassifiers);

        DependencyFilter classpathFilter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE,
                JavaScopes.RUNTIME,
                JavaScopes.TEST,
                JavaScopes.SYSTEM,
                JavaScopes.PROVIDED);
        return new DependencyRequest(collectRequest, classpathFilter);
    }

    private CollectRequest getCollectRequest(ArtifactDescriptorRequest descriptorRequest, ArtifactDescriptorResult descriptorResult, boolean shouldBringClassifiers) {
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRootArtifact(descriptorResult.getArtifact());
        List<Dependency> dependencies = new ArrayList<>(descriptorResult.getDependencies());
        if (shouldBringClassifiers) {
            for (var dep : descriptorResult.getDependencies()) {
                ClassifiersHandler.addClassifierArtifacts(descriptorResult, dependencies, dep.getArtifact());
            }
            ClassifiersHandler.addClassifierArtifacts(descriptorResult, dependencies, descriptorResult.getArtifact());
        }
        collectRequest.setDependencies(dependencies);
        collectRequest.setManagedDependencies(descriptorResult.getManagedDependencies());
        collectRequest.setRepositories(descriptorRequest.getRepositories());
        return collectRequest;
    }

    private ResolvingProcessAetherResult singleArtifactResolving(Artifact originalArtifact,
                                                                 RepositorySystem system,
                                                                 DefaultRepositorySystemSession session,
                                                                 List<RemoteRepository> remoteRepositories) throws ArtifactResolutionException {
        try {
            ArtifactRequest artifactRequest = new ArtifactRequest();
            artifactRequest.setArtifact(new DefaultArtifact(originalArtifact.getArtifactGradleFashionedName()));
            artifactRequest.setRepositories(remoteRepositories);
            ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
            return new ResolvingProcessAetherResult(Collections.singletonList(new RepositoryAwareAetherArtifact(artifactResult.getArtifact())));
        } catch (Exception e) {
            return new ResolvingProcessAetherResult(new ArrayList<>(), List.of(e.getMessage()));
        }
    }
}
