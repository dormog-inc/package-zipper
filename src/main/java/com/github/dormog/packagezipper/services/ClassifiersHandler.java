package com.github.dormog.packagezipper.services;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClassifiersHandler {
    public static void addClassifierArtifacts(ArtifactDescriptorResult descriptorResult, List<Dependency> dependencies, org.eclipse.aether.artifact.Artifact artifact) {
        if (artifact.getExtension().equals("jar")) {
            List<String> classifiers = getAvailableClassifiers(artifact, descriptorResult.getRepositories().stream().map(RemoteRepository::getUrl).toList());
            classifiers.forEach(classifier -> {
                var classifierArtifact = new DefaultArtifact(artifact.getGroupId(),
                        artifact.getArtifactId(),
                        classifier,
                        artifact.getExtension(),
                        artifact.getVersion());
                dependencies.add(new Dependency(classifierArtifact, JavaScopes.COMPILE));
            });
        }
    }

    public static List<String> getAvailableClassifiers(org.eclipse.aether.artifact.Artifact artifact, List<String> repositories) {
        Set<String> classifiers = new HashSet<>();

        // Construct the URL to retrieve the available files for the artifact
        for (String baseURL : repositories) {
            try {
                String url = String.format("%s/%s/%s/%s/", baseURL, artifact.getGroupId().replace(".", "/"), artifact.getArtifactId(), artifact.getVersion());
                Document doc = Jsoup.connect(url).get();

                Elements links = doc.select("a[href]");
                for (Element link : links) {
                    String fileName = link.attr("href");
                    if (fileName.startsWith(artifact.getArtifactId()) && fileName.endsWith(".jar")) {
                        int startIndex = artifact.getArtifactId().length() + artifact.getVersion().length() + 2;
                        int endIndex = fileName.lastIndexOf(".jar");
                        if (endIndex > startIndex) {
                            String classifier = fileName.substring(startIndex, endIndex);
                            classifiers.add(classifier);
                        }
                    }
                }
                break;
            } catch (IOException ignored) {}
        }

        return classifiers.stream().toList();
    }
}
