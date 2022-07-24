package com.dor.package_zipper.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.eclipse.aether.artifact.Artifact;

import java.util.List;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class ResolvingProcessServiceResult {
    private final List<ZipRemoteEntry> zipRemoteEntries;
    private String exception;
}
