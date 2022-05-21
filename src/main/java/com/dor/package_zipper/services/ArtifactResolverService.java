package com.dor.package_zipper.services;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.maven.resolver.AbstractEventsCrawlerRepositoryListener;
import com.dor.package_zipper.maven.resolver.Booter;
import com.dor.package_zipper.maven.resolver.ConsoleRepositoryListener;
import com.dor.package_zipper.maven.resolver.EventsCrawlerRepositoryListener;
import com.dor.package_zipper.models.Artifact;
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
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class ArtifactResolverService {
    private final Environment env;
    private final AppConfig appConfig;
    private final RemoteRepository newCentralRepository;

    public Flux<ZipRemoteEntry> resolveArtifact(Artifact artifact, boolean thin) {
        if (thin) {
            return thinResolvingStrategy(artifact);
        } else {
            return completeResolvingStrategy(artifact);
        }
    }

    /**
     * Resolve artifacts without import scope. This isn't a full nor traditional Maven strategy,
     * but this not-pom-friendly strategy turned out to be useful in many cases.
     */
    private Flux<ZipRemoteEntry> thinResolvingStrategy(Artifact originalArtifact) {
        return Try.of(() -> {
            RepositorySystem system = Booter.newRepositorySystem();
            DefaultRepositorySystemSession session = new Booter().newRepositorySystemSession(system);

            session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
            session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);

            org.eclipse.aether.artifact.Artifact artifact = new DefaultArtifact(originalArtifact.getArtifactGradleFashionedName());

            ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
            descriptorRequest.setArtifact(artifact);
            descriptorRequest.setRepositories(Collections.singletonList(newCentralRepository));
            // TODO: prevent this line from downloading jars locally:
            ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);

            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRootArtifact(descriptorResult.getArtifact());
            collectRequest.setDependencies(descriptorResult.getDependencies());
            collectRequest.setManagedDependencies(descriptorResult.getManagedDependencies());
            collectRequest.setRepositories(descriptorRequest.getRepositories());

            DependencyFilter classpathFilter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE,
                    JavaScopes.RUNTIME,
                    JavaScopes.TEST,
                    JavaScopes.SYSTEM,
                    JavaScopes.PROVIDED);
            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFilter);

            DependencyResult dependencyResult = system.resolveDependencies(session, dependencyRequest);
            List<org.eclipse.aether.artifact.Artifact> managedArtifacts = dependencyResult.getRequest().getCollectRequest().getManagedDependencies().stream().map(Dependency::getArtifact).collect(Collectors.toList());
            managedArtifacts.add(singleArtifactResolving(system, session, artifact));
            return Flux.fromIterable(managedArtifacts)
                    .flatMap(dependency -> getRemoteEntryFromLibrary(
                            new Artifact(
                                    dependency.getGroupId(),
                                    dependency.getArtifactId(),
                                    dependency.getVersion(),
                                    dependency.getExtension(),
                                    dependency.getClassifier())))
                    .distinct();
        }).onFailure(Throwable::printStackTrace).get();
    }

    public Flux<ZipRemoteEntry> resolveArtifacts(List<Artifact> artifacts, boolean thin) {
        if (thin) {
            return Flux.fromIterable(artifacts)
                    .flatMap(this::thinResolvingStrategy);
        } else {
            return Flux.fromIterable(artifacts)
                    .flatMap(this::completeResolvingStrategy);
        }
    }

//    public Flux<ZipRemoteEntry> resolveArtifactFromPom(MultipartFile pomFile, boolean thin) {
//        Flux<ZipRemoteEntry> zipRemoteEntries = null;
//        Path path = Paths.get(
//                pomFile.getOriginalFilename().replace(".pom", "") + "_" + System.currentTimeMillis() / 1000L
//                        + "_pom.xml");
//        try {
//            pomFile.transferTo(path);
//            MavenResolverSystem mavenResolverSystem = Maven.resolver();
//            MavenStrategyStage mavenStrategyStage = mavenResolverSystem.loadPomFromFile(path.toFile())
//                    .importCompileAndRuntimeDependencies()
//                    .importDependencies(ScopeType.IMPORT)
//                    .importTestDependencies().resolve();
//            zipRemoteEntries = resolveMavenStrategy(mavenStrategyStage);
////            TODO: return this option:
////            zipRemoteEntries = resolveMavenStrategy(withTransitivity, mavenStrategyStage);
//        } catch (Exception e) {
//            log.error("error create tmp pom file", e);
//        } finally {
//            path.toFile().delete();
//        }
//        return zipRemoteEntries;
//    }
//
//    public MavenWorkingSessionz loadPomFromFile(File pomFile, Properties userProperties, String... profiles)
//            throws IllegalArgumentException {
//
//        final DefaultModelBuildingRequest request = new DefaultModelBuildingRequest()
//                .setSystemProperties(SecurityActions.getProperties()).setProfiles(this.getSettingsDefinedProfiles())
//                .setPomFile(pomFile).setActiveProfileIds(SettingsXmlProfileSelector.explicitlyActivatedProfiles(profiles))
//                .setInactiveProfileIds(SettingsXmlProfileSelector.explicitlyDisabledProfiles(profiles));
//
//        final DefaultModelBuildingRequest request = new DefaultArtifactDescriptorReader()
//                .
//                .setPomFile(pomFile).setActiveProfileIds(SettingsXmlProfileSelector.explicitlyActivatedProfiles(profiles))
//                .setInactiveProfileIds(SettingsXmlProfileSelector.explicitlyDisabledProfiles(profiles));
//
//        if (userProperties != null){
//            request.setUserProperties(userProperties);
//        }
//
//        ModelBuilder builder = new DefaultModelBuilderFactory().newInstance();
//        ModelBuildingResult result;
//        try {
//            request.setModelResolver(new MavenModelResolver(getSystem(), getSession(), getRemoteRepositories()));
//            result = builder.build(request);
//        }
//        // wrap exception message
//        catch (ModelBuildingException e) {
//            String pomPath = request.getPomFile().getAbsolutePath();
//            StringBuilder sb = new StringBuilder("Found ").append(e.getProblems().size())
//                    .append(" problems while building POM model from ").append(pomPath).append("\n");
//
//            int counter = 1;
//            for (ModelProblem problem : e.getProblems()) {
//                sb.append(counter++).append("/ ").append(problem).append("\n");
//            }
//
//            throw new IllegalArgumentException(sb.toString());
//        }
//
//        // get and update model
//        Model model = result.getEffectiveModel();
//
//
//        model.getDependencies()
//        return this;
//    }

    private Flux<ZipRemoteEntry> completeResolvingStrategy(Artifact originalArtifact) {
        return Try.of(() -> {
            RepositorySystem system = Booter.newRepositorySystem();

            AbstractEventsCrawlerRepositoryListener eventsCrawlerRepositoryListener;
            if (Arrays.asList(env.getActiveProfiles()).contains("dev")) {
                eventsCrawlerRepositoryListener = new ConsoleRepositoryListener();
            } else {
                eventsCrawlerRepositoryListener = new EventsCrawlerRepositoryListener();
            }
            DefaultRepositorySystemSession session = new Booter(eventsCrawlerRepositoryListener).newRepositorySystemSession(system);

            session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
            session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);

            org.eclipse.aether.artifact.Artifact artifact = new DefaultArtifact(originalArtifact.getArtifactGradleFashionedName());


            ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
            descriptorRequest.setArtifact(artifact);
            descriptorRequest.setRepositories(Collections.singletonList(newCentralRepository));
            // TODO: prevent this line from downloading jars locally:
            ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);

            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRootArtifact(descriptorResult.getArtifact());
            collectRequest.setDependencies(descriptorResult.getDependencies());
            collectRequest.setManagedDependencies(descriptorResult.getManagedDependencies());
            collectRequest.setRepositories(descriptorRequest.getRepositories());

            DependencyFilter classpathFilter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE,
                    JavaScopes.RUNTIME,
                    JavaScopes.TEST,
                    JavaScopes.SYSTEM,
                    JavaScopes.PROVIDED);
            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFilter);

            DependencyResult dependencyResult = system.resolveDependencies(session, dependencyRequest);
            List<org.eclipse.aether.artifact.Artifact> managedArtifacts = dependencyResult.getRequest().getCollectRequest().getManagedDependencies().stream().map(Dependency::getArtifact).collect(Collectors.toList());
            managedArtifacts.addAll(eventsCrawlerRepositoryListener.getAllDeps());
            managedArtifacts.add(singleArtifactResolving(system, session, artifact));
            return Flux.fromIterable(managedArtifacts)
                    .flatMap(dependency -> getRemoteEntryFromLibrary(
                            new Artifact(
                                    dependency.getGroupId(),
                                    dependency.getArtifactId(),
                                    dependency.getVersion(),
                                    dependency.getExtension(),
                                    dependency.getClassifier())))
                    .distinct();
        }).onFailure(Throwable::printStackTrace).get();
    }

    private org.eclipse.aether.artifact.Artifact singleArtifactResolving(RepositorySystem system, DefaultRepositorySystemSession session, org.eclipse.aether.artifact.Artifact artifact) throws ArtifactResolutionException {
        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(artifact);
        artifactRequest.setRepositories(Collections.singletonList(newCentralRepository));
        ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
        return artifactResult.getArtifact();
    }

    private Flux<ZipRemoteEntry> getRemoteEntryFromLibrary(Artifact artifact) {
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
        if (!artifact.getPackagingType().equals("pom")) {
            String pomPath = String.format("%s.%s", path, "pom");
            String pomUrl = String.format("%s/%s", appConfig.getMavenUrl(), pomPath);
            zipEntries.add(new ZipRemoteEntry(pomPath, pomUrl));
        }
        return Flux.fromIterable(zipEntries);
    }

    private String getPomUrl(Artifact artifact) {
        String path = String.format("%s/%s/%s/%s-%s",
                artifact.getGroupId().replace(".", "/"),
                artifact.getArtifactId(),
                artifact.getVersion(),
                artifact.getArtifactId(),
                artifact.getVersion());
        String pomPath = String.format("%s.%s", path, "pom");
        return String.format("%s/%s", appConfig.getMavenUrl(), pomPath);
    }
}
