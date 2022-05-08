package com.dor.package_zipper.exceptions;

public class ArtifactNameIncludesTooManyColonsException extends Exception {
    // Parameterless Constructor
    public ArtifactNameIncludesTooManyColonsException() {}

    // Constructor that accepts a message
    public ArtifactNameIncludesTooManyColonsException(String message)
    {
        super(message);
    }
}