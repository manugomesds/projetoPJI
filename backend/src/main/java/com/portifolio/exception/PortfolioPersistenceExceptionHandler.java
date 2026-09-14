package com.portifolio.exception;

import com.portifolio.controller.PortfolioController;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;

@Order(-1) @RestControllerAdvice(assignableTypes = PortfolioController.class)
public class PortfolioPersistenceExceptionHandler {
    @ExceptionHandler({DataAccessException.class, TransactionException.class, PortfolioOperationException.class})
    public ResponseEntity<ErroResposta> falha(RuntimeException ex) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Falha na operação de portfólio", ex);
        return ResponseEntity.internalServerError().body(ErroResposta.builder().timestamp(LocalDateTime.now()).status(500)
                .mensagem("Não foi possível processar o portfólio. Tente novamente.").detalhes(List.of()).build());
    }
}
