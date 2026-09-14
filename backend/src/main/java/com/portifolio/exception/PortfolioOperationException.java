package com.portifolio.exception;

public class PortfolioOperationException extends RuntimeException {
    public PortfolioOperationException(Throwable cause) {
        super("Não foi possível processar o portfólio. Tente novamente.", cause);
    }
}
