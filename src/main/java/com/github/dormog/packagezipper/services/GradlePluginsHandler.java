package com.github.dormog.packagezipper.services;

import com.github.dormog.packagezipper.models.Artifact;

import java.util.Arrays;
import java.util.List;

public class GradlePluginsHandler {

    public static final String GRADLE_PLUGIN = ".gradle.plugin";

    /**
     * Gradle plugins have the following POM names format: plugin.id:plugin.id.gradle.plugin:plugin.version.
     * Given a plugin id (dotted or slashed) and a version, this method will return the relative POM name.
     */
    public static Artifact formatGradlePluginPomName(String pluginId, String version) {
        if (pluginId.contains(".")) {
            return new Artifact(pluginId, pluginId + GRADLE_PLUGIN, version);
        } else if (pluginId.contains("/")) {
            List<String> pluginIdElements = Arrays.stream(pluginId.split("/")).toList();
            return new Artifact(String.join(".", pluginIdElements), pluginId + GRADLE_PLUGIN, version);
        } else {
            throw new IllegalArgumentException();
        }
    }
}
