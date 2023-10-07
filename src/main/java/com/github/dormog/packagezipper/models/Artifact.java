package com.github.dormog.packagezipper.models;

import lombok.*;

import java.io.Serializable;
import java.util.Arrays;

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
        if (fullNameArtifact.contains(":")) {
            String[] split = fullNameArtifact.split(":");
            if (split.length < 3) {
                throw new IllegalArgumentException("The artifact name should have format: groupId:artifactId:version");
            }
            this.groupId = split[0];
            this.artifactId = split[1];
            this.version = split[2];
        } else if (fullNameArtifact.contains("/")) {
            String[] split = fullNameArtifact.split("/");
            if (split.length < 3) {
                throw new IllegalArgumentException("The artifact name should have format: group/id/artifact-id/version");
            }
            this.groupId = String.join("/" , Arrays.asList(split).subList(0, split.length - 2));
            this.artifactId = split[split.length - 2];
            this.version = split[split.length - 1];
        } else {
            throw new IllegalArgumentException("The artifact name should look like this: \"org/springframework/boot/spring-boot/2.5.0\" " +
                    "or this: \"org.springframework.boot:spring-boot:2.5.0\"");
        }
    }

    public String getPackagingType() {
        return this.packagingType == null ? "jar" : this.packagingType;
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

    public String getArtifactGradleFashionedName() {
        return String.format("%s:%s:%s", groupId, artifactId, version);
    }
}
