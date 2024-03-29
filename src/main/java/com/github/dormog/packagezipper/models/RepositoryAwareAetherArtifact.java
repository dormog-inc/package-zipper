package com.github.dormog.packagezipper.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.eclipse.aether.artifact.Artifact;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class RepositoryAwareAetherArtifact {
    private final Artifact artifact;
    private String repository;
}
