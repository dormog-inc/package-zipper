package com.github.dormog.packagezipper.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class ResolvingProcessAetherResult {
    private final List<RepositoryAwareAetherArtifact> artifactList;
    private List<String> exceptionMessages;
}
