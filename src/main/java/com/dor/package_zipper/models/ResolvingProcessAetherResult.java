package com.dor.package_zipper.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.eclipse.aether.artifact.Artifact;

import java.util.List;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class ResolvingProcessAetherResult {
    private final List<RepositoryAwareAetherArtifact> artifactList;
    private String exceptionMessage;
}
