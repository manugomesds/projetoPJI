BEGIN;

INSERT INTO usuarios (id, nome, data_nascimento, telefone, email, senha, tipo_usuario, perfil_completo) VALUES 
(1, 'Ana Silva', '2002-05-10', '11999991111', 'ana@email.com', 'pass', 'ARTISTA', true),
(2, 'Bruno Souza', '2001-08-15', '11999992222', 'bruno@email.com', 'pass', 'ARTISTA', true),
(3, 'Carla Dias', '2003-02-20', '11999993333', 'carla@email.com', 'pass', 'ARTISTA', true),
(4, 'Empresa Alpha', '1985-03-12', '11888884444', 'alpha@email.com', 'pass', 'CONTRATANTE', true),
(5, 'Studio Beta', '1990-07-22', '11888885555', 'beta@email.com', 'pass', 'CONTRATANTE', true),
(6, 'Produções Gama', '1982-11-30', '11888886666', 'gama@email.com', 'pass', 'CONTRATANTE', true);

SELECT setval('usuarios_id_seq', 6, true);

INSERT INTO responsaveis_legais (usuario_id, nome_responsavel, telefone_responsavel, email_responsavel) VALUES 
(3, 'Mãe da Carla', '11977773333', 'mae@email.com');

INSERT INTO refresh_tokens (usuario_id, token_hash, expiracao) VALUES 
(1, 'hash123', current_timestamp + interval '1 day');

INSERT INTO perfis_artistas (usuario_id, biografia, localizacao, url_portfolio, nivel_medalha, score_engajamento) VALUES 
(1, 'Artista visual e pintora.', 'São Paulo, SP', 'https://portfolio.com/ana', 2, 85.00),
(2, 'Ilustrador digital.', 'Rio de Janeiro, RJ', 'https://portfolio.com/bruno', 3, 90.00),
(3, 'Música e compositora.', 'Curitiba, PR', 'https://portfolio.com/carla', 1, 70.00);

INSERT INTO perfis_contratantes (usuario_id, nome_empresa, tipo_perfil, biografia, localizacao) VALUES 
(4, 'Alpha Corp', 'Empresa', 'Fomento cultural', 'São Paulo, SP'),
(5, 'Beta Studio', 'Estúdio', 'Criação de mídias', 'Rio de Janeiro, RJ'),
(6, 'Gama Produções', 'Agência', 'Eventos e editais', 'Belo Horizonte, MG');

INSERT INTO visualizacoes_perfil (perfil_visitado_id) VALUES 
(1), (2), (3);

INSERT INTO tags (id, nome) VALUES 
(1, 'Pintura'), (2, 'Ilustração'), (3, 'Música');

SELECT setval('tags_id_seq', 3, true);

INSERT INTO tags_artista (artista_id, tag_id) VALUES 
(1, 1), (2, 2), (3, 3);

INSERT INTO vagas (id, contratante_id, titulo, descricao, requisitos, remunera_valor, forma_pagamento, cidade, estado, modelo_trabalho, tipo_contrato, categoria, status) VALUES 
(1, 4, 'Pintura de Mural', 'Criar mural artístico', 'Experiência prévia', 2500.00, 'Pix', 'São Paulo', 'SP', 'PRESENCIAL', 'PJ', 'Pintura', 'ABERTA');

SELECT setval('vagas_id_seq', 1, true);

INSERT INTO tags_vaga (vaga_id, tag_id) VALUES 
(1, 1);

INSERT INTO fotos_vaga (vaga_id, ordem, url) VALUES 
(1, 1, 'https://img.com/vaga1.png');

INSERT INTO candidaturas (vaga_id, artista_id, mensagem_apresentacao, link_portfolio_candidatura, status) VALUES 
(1, 1, 'Tenho interesse na vaga.', 'https://portfolio.com/ana', 'PENDENTE');

INSERT INTO log_vagas_canceladas (vaga_id, cancelado_por_id, motivo) VALUES 
(1, 4, 'Reestruturação');

INSERT INTO portfolio_arquivos (id, artista_id, url_arquivo, nome_original, tamanho_bytes, tipo_mime, possui_selo) VALUES 
(1, 1, 'https://img.com/obra1.png', 'obra1.png', 1024, 'image/png', true);

SELECT setval('portfolio_arquivos_id_seq', 1, true);

INSERT INTO embeds_externos (artista_id, url_original, codigo_iframe, tipo_midia, legenda) VALUES 
(3, 'https://youtube.com/watch?v=1', '<iframe src=""></iframe>', 'AUDIO', 'Demo musical');

INSERT INTO comunidades (id, criador_id, nome, descricao, categoria_artistica, privacidade) VALUES 
(1, 1, 'Artistas Unidos', 'Comunidade de artes visuais', 'Pintura', 'PUBLICA');

SELECT setval('comunidades_id_seq', 1, true);

INSERT INTO membros_comunidade (comunidade_id, usuario_id, papel) VALUES 
(1, 1, 'ADMIN'), (1, 2, 'MEMBRO');

INSERT INTO editais (id, comunidade_id, publicador_id, titulo, descricao, url_arquivo_oficial, data_inicio_inscricao, data_fim_inscricao, data_resultado) VALUES 
(1, 1, 4, 'Edital 2026', 'Apoio a arte', 'https://edital.pdf', '2026-03-01', '2026-04-01', '2026-04-15');

SELECT setval('editais_id_seq', 1, true);

INSERT INTO retificacoes_edital (edital_id, titulo_retificacao, descricao_alteracoes, url_arquivo_aditivo) VALUES 
(1, 'Retificação 01', 'Mudança de datas', 'https://retif.pdf');

INSERT INTO galerias_virtuais (id, dono_id, comunidade_id, titulo, descricao, categoria, tipo_galeria, status) VALUES 
(1, 1, 1, 'Galeria 1', 'Exposição virtual', 'Pintura', 'COMUNITARIA', 'ATIVA');

SELECT setval('galerias_virtuais_id_seq', 1, true);

INSERT INTO itens_galeria (galeria_id, arquivo_id) VALUES 
(1, 1);

INSERT INTO interacoes_galeria (usuario_id, arquivo_id, curtiu, comentario) VALUES 
(2, 1, true, 'Muito bom!');

INSERT INTO notificacoes (usuario_destino_id, tipo_notificacao, mensagem_alerta, link_contexto) VALUES 
(1, 'CANDIDATURA', 'Nova interação', '/notif');

INSERT INTO salas_chat (id) VALUES (1);
SELECT setval('salas_chat_id_seq', 1, true);

INSERT INTO participantes_chat (sala_id, usuario_id) VALUES 
(1, 1), (1, 4);

INSERT INTO mensagens_chat (sala_id, remetente_id, texto_mensagem) VALUES 
(1, 4, 'Olá!');

INSERT INTO denuncias_plagio (denunciante_id, perfil_denunciado_id, tipo_violacao, descricao_detalhada) VALUES 
(1, 2, 'OUTRO', 'Teste');

INSERT INTO moderacao_conteudo (tipo_conteudo, conteudo_id, autor_id, status_moderacao) VALUES 
('GALERIA', 1, 1, 'APROVADO');

INSERT INTO reportes_usuario (denunciante_id, tipo_conteudo, conteudo_id, motivo_reporte) VALUES 
(2, 'GALERIA', 1, 'Spam');

INSERT INTO itens_salvos (usuario_id, tipo_alvo, alvo_id) VALUES 
(1, 'VAGA', 1);

INSERT INTO agenda_artista (artista_id, titulo_compromisso, tipo_compromisso, data_hora_inicio, data_hora_fim, localizacao_logistica) VALUES 
(1, 'Apresentação', 'Show', '2026-04-10 10:00:00', '2026-04-10 12:00:00', 'São Paulo');

INSERT INTO ranking_top_da_semana (artista_id, score_semanal, data_inicio_ciclo, data_fim_ciclo, posicao_ranking) VALUES 
(1, 95.00, '2026-04-01', '2026-04-07', 1);

INSERT INTO historico_medalhas (artista_id, nivel_antigo, nivel_novo, motivo_progressao) VALUES 
(1, 1, 2, 'Evolução');

INSERT INTO conquistas_desbloqueadas (artista_id, nome_conquista, descricao_conquista) VALUES 
(1, 'Iniciante', 'Primeiro login');

INSERT INTO log_exclusoes_lgpd (usuario_id_antigo, comprovante_hash) VALUES 
(99, 'hashlgpd');

COMMIT;