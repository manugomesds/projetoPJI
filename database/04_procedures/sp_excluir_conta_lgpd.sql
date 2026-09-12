create or replace procedure sp_excluir_conta_lgpd(
    p_usuario_id bigint,
    p_motivo text,
    p_comprovante_hash char(64)
)
language plpgsql as $$
begin
    -- Proteção contra exclusão do usuário fantasma de sistema (ID 0)
    if p_usuario_id = 0 then
        raise exception 'Operação inválida: o usuário fantasma de sistema (ID 0) não pode ser excluído.';
    end if;

    if not exists (select 1 from usuarios where id = p_usuario_id) then
        raise exception 'Usuário (ID: %) não encontrado.', p_usuario_id;
    end if;

    -- =========================================================================
    -- 1. RETER (Obrigação legal / Comprovante sem PII - RF22)
    -- =========================================================================
    insert into log_exclusoes_lgpd (motivo_opcional, comprovante_hash)
    values (p_motivo, p_comprovante_hash);

    -- =========================================================================
    -- 2. ANONIMIZAR / REATRIBUIR (Preservação de histórico e trilha de auditoria)
    -- =========================================================================

    -- Candidaturas: reatribui para o usuário fantasma (ID 0)
    update candidaturas 
    set artista_id = 0 
    where artista_id = p_usuario_id;

    -- Vagas: transição segura de estado. Encerra apenas vagas 'ABERTA' ou 'PAUSADA'.
    -- Vagas 'CANCELADA' ou 'ENCERRADA' preservam seu estado imutável original.
    update vagas 
    set status = case 
            when status in ('ABERTA'::status_vaga_enum, 'PAUSADA'::status_vaga_enum) then 'ENCERRADA'::status_vaga_enum
            else status 
        end,
        contratante_id = 0 
    where contratante_id = p_usuario_id;

    -- Mensagens de Chat: remove autor para exibir "Usuário Removido" na interface
    update mensagens_chat 
    set remetente_id = null 
    where remetente_id = p_usuario_id;

    -- Denúncias de Plágio: desvincula o denunciante e o denunciado sem apagar o registro
    update denuncias_plagio 
    set denunciante_id = 0 
    where denunciante_id = p_usuario_id;

    update denuncias_plagio 
    set perfil_denunciado_id = 0 
    where perfil_denunciado_id = p_usuario_id;

    -- Reportes e Moderação: anonimiza autores mantendo a trilha de auditoria (RNF09)
    update reportes_usuario 
    set denunciante_id = 0 
    where denunciante_id = p_usuario_id;

    update moderacao_conteudo 
    set autor_id = 0 
    where autor_id = p_usuario_id;

    update moderacao_conteudo 
    set moderador_id = null 
    where moderador_id = p_usuario_id;

    -- Log de Vagas Canceladas e Entidades públicas/comunitárias
    update log_vagas_canceladas 
    set cancelado_por_id = 0 
    where cancelado_por_id = p_usuario_id;

    update comunidades 
    set criador_id = 0 
    where criador_id = p_usuario_id;

    update editais 
    set publicador_id = 0 
    where publicador_id = p_usuario_id;

    -- =========================================================================
    -- 3. LIMPEZA DE DADOS POLIMÓRFICOS E INTERAÇÕES
    -- =========================================================================

    -- Remove itens salvos pelo usuário OU salvos por outros usuários apontando para o perfil excluído
    delete from itens_salvos 
    where usuario_id = p_usuario_id 
       or (tipo_alvo = 'PERFIL_ARTISTA' and alvo_id = p_usuario_id);

    -- Remove histórico de visualizações recebidas do perfil
    delete from visualizacoes_perfil 
    where perfil_visitado_id = p_usuario_id;

    -- =========================================================================
    -- 4. DELEÇÃO FÍSICA (Ativos, PIIs, Mídias e Relacionamentos Diretos)
    -- =========================================================================

    -- Tokens de autenticação e sessões
    delete from refresh_tokens where usuario_id = p_usuario_id;

    -- Mídias e Portfólio
    delete from portfolio_arquivos where artista_id = p_usuario_id;
    delete from embeds_externos where artista_id = p_usuario_id;

    -- Agenda, Conquistas e Gamificação
    delete from agenda_artista where artista_id = p_usuario_id;
    delete from conquistas_desbloqueadas where artista_id = p_usuario_id;
    delete from historico_medalhas where artista_id = p_usuario_id;
    delete from ranking_top_da_semana where artista_id = p_usuario_id;

    -- Vinculações e Dados de Autodeclaração / Responsável Legal
    delete from responsaveis_legais where usuario_id = p_usuario_id;
    delete from membros_comunidade where usuario_id = p_usuario_id;
    delete from autodeclaracoes where usuario_id = p_usuario_id;
    delete from perfil_artista_area where perfil_artista_id = p_usuario_id;

    -- Perfis de domínio
    delete from perfis_artistas where usuario_id = p_usuario_id;
    delete from perfis_contratantes where usuario_id = p_usuario_id;

    -- =========================================================================
    -- 5. EXCLUSÃO FÍSICA DA CONTA (usuarios)
    -- =========================================================================
    delete from usuarios where id = p_usuario_id;

end;
$$;