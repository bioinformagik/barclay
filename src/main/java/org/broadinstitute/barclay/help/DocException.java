package org.broadinstitute.barclay.help;

import org.broadinstitute.barclay.utils.BarclayException;

public class DocException extends BarclayException {
    private static final long serialVersionUID = 1L;

    public DocException( String msg ) {
        super(msg);
    }

    public DocException( String message, Throwable throwable ) {
        super(message, throwable);
    }

}
