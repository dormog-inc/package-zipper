package com.github.dormog.packagezipper.maven.resolver.shrinkwrap;

import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Properties;

/**
 * -----------------------------------------------------NOTE-------------------------------------------------------
 * This class is a local instance of the same class from ShrinkWrap. By the time this should be replaced of removed
 * No need for CRs for this class
 */
public final class SecurityActions {

    // -------------------------------------------------------------------------------||
    // Constructor -------------------------------------------------------------------||
    // -------------------------------------------------------------------------------||

    /**
     * No instantiation
     */
    private SecurityActions() {
        throw new UnsupportedOperationException("No instantiation");
    }

    // -------------------------------------------------------------------------------||
    // Utility Methods ---------------------------------------------------------------||
    // -------------------------------------------------------------------------------||

    static String getProperty(final String key) {
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> System.getProperty(key));
        }
        // Unwrap
        catch (final PrivilegedActionException pae) {
            final Throwable t = pae.getCause();
            // Rethrow
            if (t instanceof SecurityException) {
                throw (SecurityException) t;
            }
            if (t instanceof NullPointerException) {
                throw (NullPointerException) t;
            } else if (t instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) t;
            } else {
                // No other checked Exception thrown by System.getProperty
                try {
                    throw (RuntimeException) t;
                }
                // Just in case we've really messed up
                catch (final ClassCastException cce) {
                    throw new RuntimeException("Obtained unchecked Exception; this code should never be reached", t);
                }
            }
        }
    }

    static String getEnvProperty(final String key) {
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> System.getenv(key));
        }
        // Unwrap
        catch (final PrivilegedActionException pae) {
            final Throwable t = pae.getCause();
            // Rethrow
            if (t instanceof SecurityException e) {
                throw e;
            }
            if (t instanceof NullPointerException e) {
                throw e;
            } else if (t instanceof IllegalArgumentException e) {
                throw e;
            } else {
                // No other checked Exception thrown by System.getProperty
                try {
                    throw (RuntimeException) t;
                }
                catch (final ClassCastException cce) {
                    throw new RuntimeException("Obtained unchecked Exception; this code should never be reached", t);
                }
            }
        }
    }

    public static Properties getProperties() {
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<Properties>() {
                @Override
                public Properties run() {
                    return System.getProperties();
                }
            });
        }
        // Unwrap
        catch (final PrivilegedActionException pae) {
            final Throwable t = pae.getCause();
            // Rethrow
            if (t instanceof SecurityException) {
                throw (SecurityException) t;
            }
            if (t instanceof NullPointerException) {
                throw (NullPointerException) t;
            } else if (t instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) t;
            } else {
                // No other checked Exception thrown by System.getProperty
                try {
                    throw (RuntimeException) t;
                }
                // Just in case we've really messed up
                catch (final ClassCastException cce) {
                    throw new RuntimeException("Obtained unchecked Exception; this code should never be reached", t);
                }
            }
        }
    }

    /**
     * Obtains the Constructor specified from the given Class and argument types
     *
     * @param clazz
     * @param argumentTypes
     * @return
     * @throws NoSuchMethodException
     */
    static <T> Constructor<T> getConstructor(final Class<T> clazz, final Class<?>... argumentTypes)
            throws NoSuchMethodException {
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<Constructor<T>>() {
                public Constructor<T> run() throws NoSuchMethodException {
                    return clazz.getDeclaredConstructor(argumentTypes);
                }
            });
        }
        // Unwrap
        catch (final PrivilegedActionException pae) {
            final Throwable t = pae.getCause();
            // Rethrow
            if (t instanceof NoSuchMethodException) {
                throw (NoSuchMethodException) t;
            } else {
                // No other checked Exception thrown by Class.getConstructor
                try {
                    throw (RuntimeException) t;
                }
                // Just in case we've really messed up
                catch (final ClassCastException cce) {
                    throw new RuntimeException("Obtained unchecked Exception; this code should never be reached", t);
                }
            }
        }
    }
}
