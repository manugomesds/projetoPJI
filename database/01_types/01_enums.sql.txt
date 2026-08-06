
CREATE TYPE tipo_usuario_enum AS ENUM ('contratante', 'artista');
CREATE TYPE modelo_trabalho_enum AS ENUM ('presencial', 'remoto', 'hibrido');
CREATE TYPE status_vaga_enum AS ENUM ('aberta', 'pausada', 'encerrada', 'cancelada');
CREATE TYPE status_candidatura_enum AS ENUM ('pendente', 'em analise', 'aprovado', 'rejeitado');
CREATE TYPE status_denuncia_enum AS ENUM ('recebida', 'em analise', 'procedente', 'improcedente');
CREATE TYPE tipo_violacao_enum AS ENUM ('plagio de imagem', 'plagio de audio', 'copia de biografia', 'outro');
CREATE TYPE tipo_midia_enum AS ENUM ('video', 'audio', 'post social');
CREATE TYPE privacidade_comunidade_enum AS ENUM ('publica', 'privada');
CREATE TYPE papel_comunidade_enum AS ENUM ('membro', 'moderador', 'admin');
CREATE TYPE tipo_galeria_enum AS ENUM ('individual', 'comunitaria');
CREATE TYPE status_galeria_enum AS ENUM ('ativa', 'agendada', 'encerrada');
CREATE TYPE tipo_notificacao_enum AS ENUM ('candidatura', 'mensagem', 'convite', 'edital', 'salvo');
CREATE TYPE tipo_conteudo_enum AS ENUM ('vaga', 'comunidade', 'galeria', 'mensagem');
CREATE TYPE status_moderacao_enum AS ENUM ('aprovado', 'bloqueado', 'sob analise');
CREATE TYPE tipo_alvo_salvo_enum AS ENUM ('artista', 'obra', 'vaga');