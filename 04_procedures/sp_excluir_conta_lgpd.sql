CREATE OR REPLACE PROCEDURE sp_excluir_conta_lgpd(
    p_usuario_id bigint,
    p_motivo text,
    p_comprovante_hash char(64)
)
LANGUAGE plpgsql
AS $$
BEGIN

    -- Registra comprovante sem manter o ID do usuário excluído.
    INSERT INTO log_exclusoes_lgpd (
        motivo_opcional,
        comprovante_hash
    )
    VALUES (
        p_motivo,
        p_comprovante_hash
    );

    -- Vagas publicadas permanecem no histórico, mas não ficam ativas.
    UPDATE vagas
    SET status = 'ENCERRADA'
    WHERE contratante_id = p_usuario_id
      AND status NOT IN ('ENCERRADA', 'CANCELADA');

    -- Remove o usuário.
    --
    -- A partir daqui:
    -- perfis, refresh tokens, portfolio, salvos, agenda etc.
    -- são removidos pelas cascatas dos dados próprios.
    --
    -- candidaturas/vagas/mensagens/denúncias que precisam
    -- permanecer utilizam SET NULL nas referências pessoais.
    DELETE FROM usuarios
    WHERE id = p_usuario_id;

END;
$$;
