package com.dor.package_zipper.services;

import com.dor.package_zipper.configuration.AppConfig;
import com.dor.package_zipper.maven.resolver.Booter;
import com.dor.package_zipper.maven.resolver.ConsoleDependencyGraphDumper;
import com.dor.package_zipper.models.Artifact;
import com.dor.package_zipper.models.ZipRemoteEntry;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenArtifactInfo;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.MavenStrategyStage;
import org.jboss.shrinkwrap.resolver.api.maven.ScopeType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

@Service
@AllArgsConstructor
@Slf4j
public class ArtifactResolverService {
    private final AppConfig appConfig;

    public Flux<ZipRemoteEntry> resolveArtifact(Artifact artifact, boolean withTransitivity) {
        MavenStrategyStage mavenStrategyStage = Maven.resolver().resolve(artifact.getArtifactFullName());
        Flux<ZipRemoteEntry> zipRemoteEntries = resolveMavenStrategy(artifact);
        return Flux.concat(zipRemoteEntries, getRemoteEntryFromLibrary(artifact)).distinct();
    }

//    public Flux<ZipRemoteEntry> resolveArtifacts(List<Artifact> artifacts, boolean withTransitivity) {
//        MavenResolverSystem mavenResolverSystem = Maven.resolver();
//        MavenStrategyStage mavenStrategyStage = mavenResolverSystem.resolve(
//                artifacts.stream().map(Artifact::getArtifactFullName).collect(Collectors.toList()));
//        return Flux.concat(
//                resolveMavenStrategy(mavenStrategyStage),
//                Flux.fromIterable(artifacts).map(artifact -> getRemoteEntryFromLibrary(artifact)).flatMap(a -> a))
//                .distinct();
//    }

//    public Flux<ZipRemoteEntry> resolveArtifactFromPom(MultipartFile pomFile, boolean withTransitivity) {
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

    private Flux<ZipRemoteEntry> resolveMavenStrategy(Artifact artifactOriginal) {
        try {

        RepositorySystem system = Booter.newRepositorySystem( Booter.selectFactory(null) );

        DefaultRepositorySystemSession session = Booter.newRepositorySystemSession( system );

        session.setConfigProperty( ConflictResolver.CONFIG_PROP_VERBOSE, true );
        session.setConfigProperty( DependencyManagerUtils.CONFIG_PROP_VERBOSE, true );

        org.eclipse.aether.artifact.Artifact artifact = new DefaultArtifact( artifactOriginal.toString() );

        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact( artifact );
        descriptorRequest.setRepositories( Booter.newRepositories( system, session ) );
        ArtifactDescriptorResult descriptorResult = null;
            descriptorResult = system.readArtifactDescriptor( session, descriptorRequest );

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRootArtifact( descriptorResult.getArtifact() );
        collectRequest.setDependencies( descriptorResult.getDependencies() );
        collectRequest.setManagedDependencies( descriptorResult.getManagedDependencies() );
        collectRequest.setRepositories( descriptorRequest.getRepositories() );

        DependencyFilter classpathFilter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE, JavaScopes.PROVIDED);
        DependencyRequest dependencyRequest = new DependencyRequest( collectRequest, classpathFilter );

        List<ArtifactResult> collectResult = system.resolveDependencies( session, dependencyRequest ).getArtifactResults();
        return Flux.fromIterable(collectResult)
                .map(dependency -> getRemoteEntryFromLibrary(
                                new Artifact(
                                        dependency.getArtifact().getGroupId(),
                                        dependency.getArtifact().getArtifactId(),
                                        dependency.getArtifact().getVersion())))
                        .flatMap(a -> a).distinct();
        } catch (ArtifactDescriptorException | DependencyResolutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Flux<ZipRemoteEntry> getRemoteEntryFromLibrary(Artifact artifact) {
        Flux<ZipRemoteEntry> zipEntriesFlux = null;
        List<ZipRemoteEntry> zipEntries = new ArrayList<>();
        String path = String.format("%s/%s/%s/%s-%s",
                artifact.getGroupId().replace(".", "/"),
                artifact.getArtifactId(),
                artifact.getVersion(),
                artifact.getArtifactId(),
                artifact.getVersion());
        String libPath = String.format("%s.%s", path, artifact.getPackagingType());
        String libUrl = String.format("%s/%s", appConfig.getMavenUrl(), libPath);
        String pomUrl = libUrl;
        zipEntries.add(new ZipRemoteEntry(libPath, libUrl));

        if (!artifact.getPackagingType().equals("pom")) {
            String pomPath = String.format("%s.%s", path, "pom");
            pomUrl = String.format("%s/%s", appConfig.getMavenUrl(), pomPath);
            zipEntries.add(new ZipRemoteEntry(pomPath, pomUrl));
        }

        Flux<ZipRemoteEntry> pomEntries = getParentPomEntries(pomUrl);
        if (pomEntries != null) {
            zipEntriesFlux = Flux.concat(pomEntries, Flux.fromIterable(zipEntries)).distinct();
        }
        return zipEntriesFlux.distinct();
    }

    private Flux<ZipRemoteEntry> getParentPomEntries(String pomUrl) {
        return WebClient.create(pomUrl).get().retrieve().bodyToMono(String.class)
                .filter(pomStirng -> {
                    return pomStirng.contains("<parent>");
                })
                .map(this::loadXMLFromString)
                .filter(doc -> {
                    XPathFactory xPathfactory = XPathFactory.newInstance();
                    XPath xpath = xPathfactory.newXPath();
                    XPathExpression expr;
                    String artifactId = "";
                    try {
                        expr = xpath.compile("/project/parent/artifactId/text()");
                        artifactId = expr.evaluate(doc, XPathConstants.STRING).toString();
                    } catch (XPathExpressionException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    return !artifactId.equals("");
                })
                .map(doc -> {
                    List<String> a = Arrays.asList("groupId", "artifactId", "version");
                    List<String> b = new ArrayList<>();
                    for(int i = 0; i< a.size(); i++) {
                        XPathFactory xPathfactory = XPathFactory.newInstance();
                        XPath xpath = xPathfactory.newXPath();
                        XPathExpression expr;
                        try {
                            String bla = pomUrl;
                            expr = xpath.compile(String.format("/project/parent/%s/text()", a.get(i)));
                            b.add(expr.evaluate(doc, XPathConstants.STRING).toString());
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }          

                    Artifact parent = new Artifact(b.get(0), b.get(1), b.get(2));
                    parent.setPackagingType("pom");
                    return parent;
                })
                .map(artifact -> getRemoteEntryFromLibrary(artifact)).flux().flatMap(a -> a).distinct();
    }

    private Document loadXMLFromString(String xml) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        Document document = null;
        try {
            builder = factory.newDocumentBuilder();
            InputSource is = new InputSource(new StringReader(xml));
            document = builder.parse(is);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return document;
    }

}
