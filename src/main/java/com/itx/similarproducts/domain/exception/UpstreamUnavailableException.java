package com.itx.similarproducts.domain.exception;

/**
 * El servicio del que dependemos no ha respondido correctamente (error 5xx, timeout, conexión...).
 */
public class UpstreamUnavailableException extends RuntimeException {

    public UpstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
