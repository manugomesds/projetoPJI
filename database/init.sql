-- =============================================================================
-- SCRIPT MESTRE DE INICIALIZAÇÃO DO BANCO DE DADOS (init.sql)
-- Ordem obrigatória: Enums -> Tabelas -> Funções -> Procedures
-- =============================================================================

\echo '--------------------------------------------------'
\echo '1. Criando ENUMs e Tipos Customizados...'
\echo '--------------------------------------------------'
\i 01_types/01_enums.sql

\echo '--------------------------------------------------'
\echo '2. Criando Tabelas e Estruturas...'
\echo '--------------------------------------------------'
\i 02_tables/01_tables.sql

\echo '--------------------------------------------------'
\echo '3. Criando Funções (Cálculos e Consultas)...'
\echo '--------------------------------------------------'
\i 03_functions/01_fn_calcular_idade.sql
\i 03_functions/02_fn_consultas_e_dashboards.sql

\echo '--------------------------------------------------'
\echo '4. Criando Stored Procedures (Escrita)...'
\echo '--------------------------------------------------'
\i 04_procedures/01_sp_cadastrar_usuario.sql
\i 04_procedures/02_sp_realizar_candidatura.sql
\i 04_procedures/03_sp_autenticacao_e_senha.sql

\echo '=================================================='
\echo '  BANCO DE DADOS INICIALIZADO COM SUCESSO! '
\echo '=================================================='