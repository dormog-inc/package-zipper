package com.dor.package_zipper.models;

import lombok.*;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
public class Artifact implements Serializable {
    @NonNull
    private String groupId;
    @NonNull
    private String artifactId;
    @NonNull
    private String version;
    @Builder.Default
    private String packagingType = "jar";
    private String classifier;

    public Artifact(String fullNameArtifact) {
        String[] split = fullNameArtifact.split(":");
        if (split.length < 3) {
            throw new RuntimeException("The artifact name should have format: groupId:artifactId:version");
        }
        this.groupId = split[0];
        this.artifactId = split[1];
        this.version = split[2];
    }

    public String getArtifactFullName() {
        String fullName = String.format("%s:%s", groupId, artifactId);
        if (packagingType != null) {
            fullName += String.format(":%s", packagingType);
        }
        if (classifier != null) {
            fullName += String.format(":%s", classifier);
        }
        if (version != null) {
            fullName += String.format(":%s", version);
        }
        return fullName;
    }
}
