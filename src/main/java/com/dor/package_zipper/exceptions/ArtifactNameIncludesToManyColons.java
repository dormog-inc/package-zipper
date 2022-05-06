package com.dor.package_zipper.exceptions;

public class ArtifactNameIncludesToManyColons extends Exception {
    // Parameterless Constructor
    public ArtifactNameIncludesToManyColons() {}

    // Constructor that accepts a message
    public ArtifactNameIncludesToManyColons(String message)
    {
        super(message);
    }
}