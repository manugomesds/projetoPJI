
\i '01_types/01_enums.sql' 

\i '02_tables/01_usuarios.sql'
\i '02_tables/02_perfis.sql'
\i '02_tables/03_tags.sql'
\i '02_tables/04_vagas.sql'
\i '02_tables/05_portfolio_e_midias.sql'
\i '02_tables/06_comunidades_e_editais.sql'
\i '02_tables/07_galerias.sql'
\i '02_tables/08_chat_e_notificacoes.sql'
\i '02_tables/09_moderacao_e_engajamento.sql'
\i '02_tables/10_agenda_gamificacao_e_logs.sql'


\i '03_functions/fn_buscar_vagas.sql'
\i '03_functions/fn_calcular_engajamento_perfil.sql'
\i '03_functions/fn_filtrar_banco_talentos.sql'
\i '03_functions/fn_moderar_conteudo.sql'
\i '03_functions/fn_sugerir_artistas_vaga.sql'
\i '03_functions/fn_verificar_perfil_completo.sql'
\i '03_functions/fn_verificar_status_moderacao.sql'

\i '04_procedures/sp_atualizar_perfil.sql'
\i '04_procedures/sp_atualizar_vaga.sql'
\i '04_procedures/sp_cadastrar_usuario.sql'
\i '04_procedures/sp_cancelar_vaga.sql'
\i '04_procedures/sp_enviar_candidatura.sql'
\i '04_procedures/sp_excluir_conta_lgpd.sql'
\i '04_procedures/sp_publicar_vaga.sql'
\i '04_procedures/sp_redefinir_senha.sql'
\i '04_procedures/sp_salvar_item.sql'
\i '04_procedures/sp_solicitar_recuperacao_senha.sql'

\i '06_triggers/trg_moderacao_automatica.sql'
\i '06_triggers/trg_perfil_completo.sql'
\i '06_triggers/trg_timestamps.sql'

\i 'seed.sql'

\echo 'Banco de dados configurado por completo com sucesso!'