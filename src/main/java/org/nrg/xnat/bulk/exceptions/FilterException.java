package org.nrg.xnat.bulk.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class FilterException extends Exception {
    public FilterException(final String message) {
        super(message);
    }

    public FilterException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public FilterException(final Throwable cause) {
            super(cause);
        }
}
