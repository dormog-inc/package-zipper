package com.github.dormog.packagezipper.maven.resolver.shrinkwrap;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * -----------------------------------------------------NOTE-------------------------------------------------------
 * This class is a local instance of the same class from ShrinkWrap. By the time this should be replaced of removed
 * No need for CRs for this class
 */
public enum GetTcclAction implements PrivilegedAction<ClassLoader> {
    INSTANCE;

    @Override
    public ClassLoader run() {
        return Thread.currentThread().getContextClassLoader();
    }
}