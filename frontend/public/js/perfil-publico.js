(function () {
  'use strict';

  var API_BASE = (window.PALCO_API_BASE_URL || 'http://localhost:8080/api').replace(/\/$/, '');
  var CHAVE_SESSAO = 'palco.sessao';

  function lerSessao() {
    var bruto = sessionStorage.getItem(CHAVE_SESSAO) || localStorage.getItem(CHAVE_SESSAO);
    if (!bruto) return null;
    try { return JSON.parse(bruto); } catch (erro) { return null; }
  }

  function configurarMensagem(perfil, tipo) {
    var sessao = lerSessao();
    var botao = selecionar('[data-iniciar-mensagem]');
    if (!sessao || !sessao.token || !perfil.usuarioId || sessao.tipoUsuario === tipo) return;
    botao.hidden = false;
    botao.addEventListener('click', async function () {
      botao.disabled = true;
      selecionar('[data-mensagem-erro]').hidden = true;
      try {
        var resposta = await fetch(API_BASE + '/chat/salas', {
          method: 'POST',
          headers: {
            Accept: 'application/json',
            'Content-Type': 'application/json',
            Authorization: 'Bearer ' + sessao.token
          },
          body: JSON.stringify({ usuarioDestinoId: perfil.usuarioId })
        });
        var corpo = await resposta.json();
        if (!resposta.ok) throw new Error(corpo.mensagem || 'Não foi possível iniciar a conversa.');
        window.location.href = '/mensagens?sala=' + encodeURIComponent(corpo.salaId);
      } catch (erro) {
        selecionar('[data-mensagem-erro]').textContent = erro.message;
        selecionar('[data-mensagem-erro]').hidden = false;
        botao.disabled = false;
      }
    });
  }

  function selecionar(seletor) {
    return document.querySelector(seletor);
  }

  function urlHttpSegura(valor) {
    if (!valor || typeof valor !== 'string') return null;
    try {
      var url = new URL(valor);
      return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null;
    } catch (erro) {
      return null;
    }
  }

  function mostrarErro(titulo, mensagem) {
    selecionar('[data-estado-carregando]').hidden = true;
    selecionar('[data-perfil]').hidden = true;
    selecionar('[data-erro-titulo]').textContent = titulo;
    selecionar('[data-erro-mensagem]').textContent = mensagem;
    selecionar('[data-estado-erro]').hidden = false;
  }

  function preencherImagem(elemento, valor, descricao) {
    var segura = urlHttpSegura(valor);
    if (!segura) {
      elemento.hidden = true;
      elemento.removeAttribute('src');
      return;
    }
    elemento.src = segura;
    elemento.alt = descricao;
    elemento.hidden = false;
  }

  function configurarAbas() {
    document.querySelectorAll('[data-aba]').forEach(function (botao) {
      botao.addEventListener('click', function () {
        var alvo = botao.dataset.aba;
        document.querySelectorAll('[data-aba]').forEach(function (aba) {
          aba.setAttribute('aria-selected', String(aba === botao));
        });
        document.querySelectorAll('[data-painel]').forEach(function (painel) {
          painel.hidden = painel.dataset.painel !== alvo;
        });
      });
    });
  }

  function renderizarTags(tags) {
    var container = selecionar('[data-tags]');
    container.replaceChildren();
    if (!Array.isArray(tags) || tags.length === 0) return;
    tags.forEach(function (tag) {
      if (!tag || typeof tag.nome !== 'string' || !tag.nome.trim()) return;
      var item = document.createElement('span');
      item.className = 'perfil-publico__tag';
      item.textContent = tag.nome;
      container.appendChild(item);
    });
    container.hidden = container.childElementCount === 0;
  }

  function renderizarPerfil(perfil, tipo) {
    selecionar('[data-estado-carregando]').hidden = true;
    selecionar('[data-estado-erro]').hidden = true;

    selecionar('[data-tipo]').textContent = tipo === 'ARTISTA' ? 'ARTISTA' : 'CONTRATANTE';
    selecionar('[data-nome]').textContent = perfil.nomeExibicao || 'Perfil Palco';
    document.title = (perfil.nomeExibicao || 'Perfil público') + ' — Palco';

    var localizacao = selecionar('[data-localizacao]');
    localizacao.textContent = perfil.localizacao || '';
    localizacao.hidden = !perfil.localizacao;

    selecionar('[data-biografia]').textContent = perfil.biografia
      || 'Este perfil ainda não adicionou uma apresentação.';

    preencherImagem(selecionar('[data-avatar]'), perfil.avatarUrl, 'Avatar de ' + (perfil.nomeExibicao || 'perfil'));
    preencherImagem(selecionar('[data-banner]'), perfil.bannerUrl, 'Banner de ' + (perfil.nomeExibicao || 'perfil'));

    if (tipo === 'ARTISTA') {
      renderizarTags(perfil.funcoes);
      var portfolio = urlHttpSegura(perfil.urlPortfolio);
      var abaPortfolio = selecionar('[data-aba="portfolio"]');
      if (portfolio) {
        selecionar('[data-portfolio]').href = portfolio;
        abaPortfolio.hidden = false;
      }
    } else if (perfil.tipoPerfil) {
      selecionar('[data-tipo-perfil]').textContent = perfil.tipoPerfil;
      selecionar('[data-detalhes-contratante]').hidden = false;
    }

    configurarMensagem(perfil, tipo);

    selecionar('[data-perfil]').hidden = false;
  }

  async function carregarPerfil() {
    var parametros = new URLSearchParams(window.location.search);
    var tipo = (parametros.get('tipo') || '').toUpperCase();
    var id = parametros.get('id') || '';

    if (!['ARTISTA', 'CONTRATANTE'].includes(tipo) || !/^[1-9][0-9]*$/.test(id)) {
      mostrarErro('Endereço de perfil inválido', 'Informe um tipo e um identificador de perfil válidos.');
      return;
    }

    try {
      var resposta = await fetch(API_BASE + '/perfis/publicos/' + tipo + '/' + id, {
        headers: { Accept: 'application/json' }
      });
      if (!resposta.ok) {
        if (resposta.status === 404) {
          mostrarErro('Perfil não encontrado', 'Este perfil não existe ou não está disponível publicamente.');
          return;
        }
        throw new Error('Resposta HTTP ' + resposta.status);
      }
      renderizarPerfil(await resposta.json(), tipo);
    } catch (erro) {
      mostrarErro('Não foi possível carregar o perfil', 'Tente novamente em alguns instantes.');
    }
  }

  function iniciar() {
    configurarAbas();
    carregarPerfil();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', iniciar, { once: true });
  } else {
    iniciar();
  }
}());
