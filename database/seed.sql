BEGIN;

-- =========================================================================
-- 1. CONTA RESERVADA DE SISTEMA (USUÁRIO FANTASMA - ID 0)
-- DOCUMENTAÇÃO LGPD (RF22):
-- Conta especial com ID fixo 0 reservada para a qual são reatribuídas todas
-- as referências de histórico (candidaturas, denúncias, logs, comunidades)
-- quando um usuário solicita a exclusão definitiva de sua conta.
-- Esta conta permanece com 'perfil_completo = false' para não figurar em
-- buscas, recomendações ou no banco de talentos públicos.
-- =========================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM usuarios WHERE id = 0) THEN
        INSERT INTO usuarios (
            id, nome, data_nascimento, telefone, email, senha_hash, 
            tipo_usuario, perfil_completo, status_conta, cpf_cnpj
        ) VALUES (
            0, 'Usuário Removido', '2000-01-01', '00000000000', 
            'anonimo@sosartistas.local', '$2a$12$GhostUserDummyPasswordHash123456789012345', 
            'ARTISTA'::tipo_usuario_enum, false, 'ATIVO'::status_conta_enum, '00000000000'
        );

        INSERT INTO perfis_artistas (usuario_id, biografia) 
        VALUES (0, 'Perfil mantido anonimamente para preservação de histórico do sistema (RF22).');

        INSERT INTO perfis_contratantes (usuario_id, nome_empresa, tipo_perfil) 
        VALUES (0, 'Entidade Removida', 'PESSOA_FISICA'::tipo_contratante_enum);
    END IF;
END $$;


-- =========================================================================
-- 2. CATÁLOGO OFICIAL DE TAXONOMIA (RF04 / RF08)
-- =========================================================================
INSERT INTO areas_artisticas (id, nome, descricao) VALUES
(1, 'Artes Visuais', 'Pintura, escultura, ilustração e arte digital'),
(2, 'Música', 'Performance musical, composição e produção fonográfica'),
(3, 'Artes Cênicas', 'Teatro, dança e performances corporais');

INSERT INTO funcoes (id, area_id, nome) VALUES
(1, 1, 'Pintor / Muralista'),
(2, 1, 'Ilustrador Digital'),
(3, 2, 'Compositor'),
(4, 2, 'Instrumentista');

INSERT INTO especializacoes (id, nome) VALUES
(1, 'Aparelhagem e Tintas'),
(2, 'Vetor e Concept Art'),
(3, 'Trilhas Sonoras'),
(4, 'Violão Cordas de Aço');

INSERT INTO funcao_especializacao (funcao_id, especializacao_id) VALUES
(1, 1),
(2, 2),
(3, 3),
(4, 4);


-- =========================================================================
-- 3. USUÁRIOS E RESPONSÁVEIS LEGAIS (RF01 / RF02 / RF06)
-- =========================================================================
INSERT INTO usuarios (
    id, nome, data_nascimento, telefone, email, senha_hash, 
    tipo_usuario, perfil_completo, status_conta, cpf_cnpj
) VALUES 
(1, 'Ana Silva', '2002-05-10', '11999991111', 'ana@email.com', '$2a$12$e0MYzXyjpJS7Pd0RVvHwHeF5aXgM88qO88fJqfH8Jm6wM.wVfM2K2', 'ARTISTA'::tipo_usuario_enum, true, 'ATIVO'::status_conta_enum, '11122233344'),
(2, 'Bruno Souza', '2001-08-15', '11999992222', 'bruno@email.com', '$2a$12$e0MYzXyjpJS7Pd0RVvHwHeF5aXgM88qO88fJqfH8Jm6wM.wVfM2K2', 'ARTISTA'::tipo_usuario_enum, true, 'ATIVO'::status_conta_enum, '22233344455'),
(3, 'Carla Dias', '2009-02-20', '11999993333', 'carla@email.com', '$2a$12$e0MYzXyjpJS7Pd0RVvHwHeF5aXgM88qO88fJqfH8Jm6wM.wVfM2K2', 'ARTISTA'::tipo_usuario_enum, true, 'ATIVO'::status_conta_enum, '33344455566'),
(4, 'Alpha Cultural LTDA', '1985-03-12', '11888884444', 'alpha@email.com', '$2a$12$e0MYzXyjpJS7Pd0RVvHwHeF5aXgM88qO88fJqfH8Jm6wM.wVfM2K2', 'CONTRATANTE'::tipo_usuario_enum, true, 'ATIVO'::status_conta_enum, '12345678000190'),
(5, 'Beta Studio Mídias', '1990-07-22', '11888885555', 'beta@email.com', '$2a$12$e0MYzXyjpJS7Pd0RVvHwHeF5aXgM88qO88fJqfH8Jm6wM.wVfM2K2', 'CONTRATANTE'::tipo_usuario_enum, true, 'ATIVO'::status_conta_enum, '98765432000110'),
(6, 'Gama Associação Cultural', '1982-11-30', '11888886666', 'gama@email.com', '$2a$12$e0MYzXyjpJS7Pd0RVvHwHeF5aXgM88qO88fJqfH8Jm6wM.wVfM2K2', 'CONTRATANTE'::tipo_usuario_enum, true, 'ATIVO'::status_conta_enum, '45678912000130');

INSERT INTO responsaveis_legais (usuario_id, nome_responsavel, telefone_responsavel, email_responsavel) VALUES 
(3, 'Mãe da Carla Dias', '11977773333', 'mae.carla@email.com');

INSERT INTO refresh_tokens (usuario_id, token_hash, expiracao) VALUES 
(1, '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', current_timestamp + interval '1 day');


-- =========================================================================
-- 4. PERFIS DE ARTISTA E TAXONOMIA VINCULADA (RF08)
-- =========================================================================
INSERT INTO perfis_artistas (
    usuario_id, biografia, localizacao, url_portfolio, 
    nivel_medalha, score_engajamento, raio_atuacao, disponivel_oportunidades
) VALUES 
(1, 'Artista visual e pintora de murais urbanos.', 'São Paulo, SP', 'https://portfolio.com/ana', 2, 85.00, 'REGIONAL', true),
(2, 'Ilustrador digital focado em concept art.', 'Rio de Janeiro, RJ', 'https://portfolio.com/bruno', 3, 90.00, 'NACIONAL', true),
(3, 'Compositora jovem e instrumentista.', 'Curitiba, PR', 'https://portfolio.com/carla', 1, 70.00, 'LOCAL', true);

-- Vínculos de Área Principal e Secundária
INSERT INTO perfil_artista_area (perfil_artista_id, area_id, is_principal, nivel_experiencia) VALUES
(1, 1, true, 'INTERMEDIARIO'::nivel_experiencia_enum),
(2, 1, true, 'AVANCADO'::nivel_experiencia_enum),
(3, 2, true, 'INICIANTE'::nivel_experiencia_enum);

INSERT INTO perfil_artista_funcao (perfil_artista_id, area_id, funcao_id) VALUES
(1, 1, 1),
(2, 1, 2),
(3, 2, 3);

INSERT INTO perfil_artista_especializacao (perfil_artista_id, area_id, especializacao_id) VALUES
(1, 1, 1),
(2, 1, 2),
(3, 2, 3);

INSERT INTO autodeclaracoes (usuario_id, categoria, exibicao_publica) VALUES
(1, 'MULHER', true),
(2, 'PCD', false);


-- =========================================================================
-- 5. PERFIS DE CONTRATANTE (SUBTIPOS OFICIAIS RF01)
-- =========================================================================
INSERT INTO perfis_contratantes (usuario_id, nome_empresa, tipo_perfil, biografia, localizacao) VALUES 
(4, 'Alpha Cultural LTDA', 'SETOR_PRIVADO'::tipo_contratante_enum, 'Fomento a projetos culturais e urbanos', 'São Paulo, SP'),
(5, 'Beta Studio Mídias', 'SETOR_PRIVADO'::tipo_contratante_enum, 'Produtora de conteúdo audiovisual e mídias virtuais', 'Rio de Janeiro, RJ'),
(6, 'Gama Associação Cultural', 'ONG'::tipo_contratante_enum, 'Organização não governamental voltada a editais públicos', 'Belo Horizonte, MG');


-- =========================================================================
-- 6. VAGAS E TAXONOMIA ASSOCIADA (RF03 / RF04)
-- =========================================================================
INSERT INTO vagas (
    id, contratante_id, titulo, descricao, requisitos, 
    remunera_valor, forma_pagamento, cidade, estado, 
    modelo_trabalho, tipo_contrato, categoria, status, ultima_atualizacao
) VALUES 
(1, 4, 'Pintura de Mural Urbano', 'Criar e executar mural artístico em fachada comercial', 'Experiência prévia em pintura externa', 2500.00, 'Pix', 'São Paulo', 'SP', 'PRESENCIAL'::modelo_trabalho_enum, 'PJ', 'Pintura', 'ABERTA'::status_vaga_enum, current_timestamp);

INSERT INTO vaga_funcao (vaga_id, funcao_id) VALUES (1, 1);
INSERT INTO vaga_especializacao (vaga_id, especializacao_id) VALUES (1, 1);

INSERT INTO fotos_vaga (vaga_id, ordem, url) VALUES 
(1, 1, 'https://img.com/vaga1.png');


-- =========================================================================
-- 7. CANDIDATURAS E INTERAÇÕES DO SISTEMA
-- =========================================================================
INSERT INTO candidaturas (vaga_id, artista_id, mensagem_apresentacao, link_portfolio_candidatura, status, data_candidatura) VALUES 
(1, 1, 'Tenho grande interesse na pintura deste mural.', 'https://portfolio.com/ana', 'PENDENTE'::status_candidatura_enum, current_timestamp);

INSERT INTO log_vagas_canceladas (vaga_id, cancelado_por_id, motivo) VALUES 
(1, 4, 'Reestruturação de orçamento do projeto');

INSERT INTO portfolio_arquivos (id, artista_id, url_arquivo, nome_original, tamanho_bytes, tipo_mime, possui_selo) VALUES 
(1, 1, 'https://img.com/obra1.png', 'mural_obra1.png', 1048576, 'image/png', true);

INSERT INTO embeds_externos (artista_id, url_original, codigo_iframe, tipo_midia, legenda) VALUES 
(3, 'https://youtube.com/watch?v=1', '<iframe src="https://youtube.com/embed/1"></iframe>', 'AUDIO', 'Demonstração de composição autoral');


-- =========================================================================
-- 8. COMUNIDADES, EDITAIS E GALERIAS
-- =========================================================================
INSERT INTO comunidades (id, criador_id, nome, descricao, categoria_artistica, privacidade) VALUES 
(1, 1, 'Artistas Unidos de SP', 'Comunidade de troca de experiências em artes visuais', 'Pintura', 'PUBLICA');

INSERT INTO membros_comunidade (comunidade_id, usuario_id, papel) VALUES 
(1, 1, 'ADMIN'), 
(1, 2, 'MEMBRO');

INSERT INTO editais (id, comunidade_id, publicador_id, titulo, descricao, url_arquivo_oficial, data_inicio_inscricao, data_fim_inscricao, data_resultado) VALUES 
(1, 1, 4, 'Edital de Fomento das Artes 2026', 'Apoio a projetos de intervenção urbana', 'https://edital.pdf', '2026-03-01', '2026-04-01', '2026-04-15');

INSERT INTO retificacoes_edital (edital_id, titulo_retificacao, descricao_alteracoes, url_arquivo_aditivo) VALUES 
(1, 'Retificação 01 - Ajuste de Cronograma', 'Prorrogação das inscrições por mais 5 dias', 'https://retif01.pdf');

INSERT INTO galerias_virtuais (id, dono_id, comunidade_id, titulo, descricao, categoria, tipo_galeria, status) VALUES 
(1, 1, 1, 'Mostra Virtual de Murais', 'Exposição de obras urbanas paulistas', 'Pintura', 'COMUNITARIA', 'ATIVA');

INSERT INTO itens_galeria (galeria_id, arquivo_id) VALUES (1, 1);
INSERT INTO interacoes_galeria (usuario_id, arquivo_id, curtiu, comentario) VALUES (2, 1, true, 'Excelente trabalho com as cores!');


-- =========================================================================
-- 9. COMUNICAÇÃO, MODERAÇÃO E LOGS LGPD (RF09 / RF22)
-- =========================================================================
INSERT INTO notificacoes (usuario_destino_id, tipo_notificacao, mensagem_alerta, link_contexto) VALUES 
(1, 'CANDIDATURA', 'Você recebeu uma atualização na sua candidatura.', '/candidaturas/1');

INSERT INTO salas_chat (id) VALUES (1);
INSERT INTO participantes_chat (sala_id, usuario_id) VALUES (1, 1), (1, 4);
INSERT INTO mensagens_chat (sala_id, remetente_id, texto_mensagem) VALUES (1, 4, 'Olá Ana, recebemos seu portfólio!');

INSERT INTO denuncias_plagio (denunciante_id, perfil_denunciado_id, tipo_violacao, descricao_detalhada) VALUES 
(1, 2, 'OUTRO', 'Suspeita de uso não autorizado de arte conceitual.');

INSERT INTO moderacao_conteudo (tipo_conteudo, conteudo_id, autor_id, status_moderacao) VALUES 
('GALERIA', 1, 1, 'APROVADO');

INSERT INTO reportes_usuario (denunciante_id, tipo_conteudo, conteudo_id, motivo_reporte) VALUES 
(2, 'GALERIA', 1, 'Conteúdo em desacordo com as regras da comunidade');

INSERT INTO itens_salvos (usuario_id, tipo_alvo, alvo_id) VALUES (1, 'VAGA', 1);

INSERT INTO log_exclusoes_lgpd (motivo_opcional, comprovante_hash) VALUES 
('Solicitação voluntária do usuário via painel de privacidade', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');


-- =========================================================================
-- 10. REAJUSTE DE SEQUÊNCIAS DO POSTGRESQL (PREVINE CONFLITO DE PRIMARY KEY)
-- =========================================================================
SELECT setval('usuarios_id_seq', coalesce((SELECT max(id) FROM usuarios), 1), true);
SELECT setval('areas_artisticas_id_seq', coalesce((SELECT max(id) FROM areas_artisticas), 1), true);
SELECT setval('funcoes_id_seq', coalesce((SELECT max(id) FROM funcoes), 1), true);
SELECT setval('especializacoes_id_seq', coalesce((SELECT max(id) FROM especializacoes), 1), true);
SELECT setval('vagas_id_seq', coalesce((SELECT max(id) FROM vagas), 1), true);
SELECT setval('portfolio_arquivos_id_seq', coalesce((SELECT max(id) FROM portfolio_arquivos), 1), true);
SELECT setval('comunidades_id_seq', coalesce((SELECT max(id) FROM comunidades), 1), true);
SELECT setval('editais_id_seq', coalesce((SELECT max(id) FROM editais), 1), true);
SELECT setval('galerias_virtuais_id_seq', coalesce((SELECT max(id) FROM galerias_virtuais), 1), true);
SELECT setval('salas_chat_id_seq', coalesce((SELECT max(id) FROM salas_chat), 1), true);

COMMIT;