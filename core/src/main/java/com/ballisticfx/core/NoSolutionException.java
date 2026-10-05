package com.ballisticfx.core;

/** Thrown when the target is beyond what the load can reach. */
public class NoSolutionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NoSolutionException(String message) {
        super(message);
    }
}
