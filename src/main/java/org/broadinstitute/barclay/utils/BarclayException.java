package org.broadinstitute.barclay.utils;

/**
 * Base class for runtime exceptions thrown by Barclay code
 */
public class BarclayException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BarclayException( String msg ) {
        super(msg);
    }

    public BarclayException( String message, Throwable throwable ) {
        super(message, throwable);
    }

}
