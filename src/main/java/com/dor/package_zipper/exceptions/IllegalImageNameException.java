package com.dor.package_zipper.exceptions;

public class IllegalImageNameException extends Exception {
    // Parameterless Constructor
    public IllegalImageNameException() {}

    // Constructor that accepts a message
    public IllegalImageNameException(String message)
    {
        super(message);
    }
}
