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
@EnableConfigurationProperties(AppConfig.class)
public class ArtifactResolverService {
    private final Environment env;
    private final AppConfig appConfig;
    private final RemoteRepository newCentralRepository;

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
            AbstractEventsCrawlerRepositoryListener eventsCrawlerRepositoryListener = getAbstractEventsCrawlerRepositoryListener();
            Booter booter = new Booter(eventsCrawlerRepositoryListener);
            RepositorySystem system = Booter.newRepositorySystem();
            DefaultRepositorySystemSession session = booter.newRepositorySystemSession(system);
            List<org.eclipse.aether.artifact.Artifact> managedArtifacts = getArtifactsListsFromArtifact(originalArtifact, system, session);
            managedArtifacts.addAll(eventsCrawlerRepositoryListener.getAllDeps());
            return getZipRemoteEntryFlux(managedArtifacts);
        }).onFailure(Throwable::printStackTrace).get();
    }

    /**
     * Resolve artifacts without import scope. This isn't a full nor traditional Maven strategy,
     * but this not-pom-friendly strategy turned out to be useful in many cases.
     */
    private List<ZipRemoteEntry> jarsBasedLevelResolvingStrategy(Artifact originalArtifact) {
        return Try.of(() -> {
            Booter booter = new Booter();
            RepositorySystem system = Booter.newRepositorySystem();
            DefaultRepositorySystemSession session = booter.newRepositorySystemSession(system);
            List<org.eclipse.aether.artifact.Artifact> managedArtifacts = getArtifactsListsFromArtifact(originalArtifact, system, session);
            return getZipRemoteEntryFlux(managedArtifacts);
        }).onFailure(Throwable::printStackTrace).get();
    }

    /**
     * Resolve artifacts exactly like a local maven client.
     */
    private List<ZipRemoteEntry> exactlyLevelResolvingStrategy(Artifact originalArtifact) {
        return Try.of(() -> {
            AbstractEventsCrawlerRepositoryListener eventsCrawlerRepositoryListener = getAbstractEventsCrawlerRepositoryListener();
            Booter booter = new Booter(eventsCrawlerRepositoryListener);
            RepositorySystem system = Booter.newRepositorySystem();
            DefaultRepositorySystemSession session = booter.newRepositorySystemSession(system);
            getArtifactsListsFromArtifact(originalArtifact, system, session);
            Set<org.eclipse.aether.artifact.Artifact> managedArtifacts = eventsCrawlerRepositoryListener.getAllDeps();
            return getZipRemoteEntryFlux(managedArtifacts.stream().toList());
        }).onFailure(Throwable::printStackTrace).get();
    }

    private AbstractEventsCrawlerRepositoryListener getAbstractEventsCrawlerRepositoryListener() {
        AbstractEventsCrawlerRepositoryListener eventsCrawlerRepositoryListener;
        if (Arrays.asList(env.getActiveProfiles()).contains("dev")) {
            eventsCrawlerRepositoryListener = new ConsoleRepositoryListener();
        } else {
            eventsCrawlerRepositoryListener = new EventsCrawlerRepositoryListener();
        }
        return eventsCrawlerRepositoryListener;
    }

    /**
     * Resolve one artifact.
     */
    private List<ZipRemoteEntry> singleLevelResolvingStrategy(Artifact originalArtifact) {
        return Try.of(() -> {
            RepositorySystem system = Booter.newRepositorySystem();
            DefaultRepositorySystemSession session = new Booter().newRepositorySystemSession(system);
            List<org.eclipse.aether.artifact.Artifact> managedArtifacts = Collections.singletonList(singleArtifactResolving(originalArtifact, system, session));
            return getZipRemoteEntryFlux(managedArtifacts);
        }).onFailure(Throwable::printStackTrace).get();
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
                .flatMap(dependency -> getRemoteEntryFromLibrary(
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

    private List<ZipRemoteEntry> getRemoteEntryFromLibrary(Artifact artifact) {
        List<ZipRemoteEntry> zipEntries = new ArrayList<>();
        String path = String.format("%s/%s/%s/%s-%s",
                artifact.getGroupId().replace(".", "/"),
                artifact.getArtifactId(),
                artifact.getVersion(),
                artifact.getArtifactId(),
                artifact.getVersion());
        String libPath = String.format("%s.%s", path, artifact.getPackagingType());
        String libUrl = String.format("%s/%s", appConfig.getMavenUrl(), libPath);
        zipEntries.add(new ZipRemoteEntry(libPath, libUrl));
        addEquivalentPomUrlForEveryJar(artifact, zipEntries, path);
        return zipEntries;
    }

    private void addEquivalentPomUrlForEveryJar(Artifact artifact, List<ZipRemoteEntry> zipEntries, String path) {
        if (!artifact.getPackagingType().equals("pom")) {
            String pomPath = String.format("%s.%s", path, "pom");
            String pomUrl = String.format("%s/%s", appConfig.getMavenUrl(), pomPath);
            zipEntries.add(new ZipRemoteEntry(pomPath, pomUrl));
        }
    }
}
