/* Palco — RF03 frontend público: filtros, cursor e scroll infinito. */
(function (global) {
  'use strict';
  function remuneracaoDaVaga(vaga) {
    var partes = [];
    function moedaValor(v) { return Number(v).toLocaleString('pt-BR', {style:'currency', currency:'BRL'}); }
    if (vaga.valorMinimo != null) partes.push(moedaValor(vaga.valorMinimo));
    if (vaga.valorMaximo != null && vaga.valorMaximo !== vaga.valorMinimo) partes.push(moedaValor(vaga.valorMaximo));
    if (!partes.length && vaga.remuneraValor != null) partes.push(moedaValor(vaga.remuneraValor));
    if (vaga.formaRemuneracao) partes.push(vaga.formaRemuneracao.replace(/_/g, ' ').toLowerCase());
    return partes.join(' · ') || 'Remuneração não informada';
  }


  var TAMANHO_PAGINA = 20;
  var CAMPOS = [
    'titulo', 'empresa', 'cidade', 'estado', 'modeloTrabalho',
    'tipoContrato', 'faixaSalarialMin', 'faixaSalarialMax', 'areaAtuacao'
  ];
  var FALLBACKS = [
    'assets/vaga-foto-1.png',
    'assets/home/hero-vaga-jardim.png',
    'assets/home/hero-vaga-bar.png'
  ];

  function texto(valor, fallback) {
    if (valor === null || valor === undefined || String(valor).trim() === '') {
      return fallback || 'Não informado';
    }
    return String(valor).trim();
  }

  function rotuloEnum(valor) {
    return texto(valor, '').toLowerCase().replace(/(^|_)(\p{L})/gu, function (_, espaco, letra) {
      return (espaco ? ' ' : '') + letra.toUpperCase();
    });
  }

  function moeda(valor) {
    if (valor === null || valor === undefined || valor === '') return 'Remuneração não informada';
    var numero = Number(valor);
    if (!Number.isFinite(numero)) return 'Remuneração não informada';
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL'
    }).format(numero);
  }

  function dataBrasileira(valor) {
    if (!valor) return 'Prazo não informado';
    var partes = String(valor).substring(0, 10).split('-');
    return partes.length === 3
      ? 'Prazo até ' + partes[2] + '/' + partes[1] + '/' + partes[0]
      : 'Prazo ' + String(valor);
  }

  function elemento(tag, classe, conteudo) {
    var no = document.createElement(tag);
    if (classe) no.className = classe;
    if (conteudo !== undefined) no.textContent = conteudo;
    return no;
  }

  function fallbackDaVaga(vaga, indice) {
    var base = Number(vaga && vaga.id);
    var posicao = Number.isFinite(base) ? Math.abs(base) : indice;
    return FALLBACKS[posicao % FALLBACKS.length];
  }

  function criarBadge(rotulo, modificador) {
    return elemento('span', 'vaga-busca-card__badge' + (modificador ? ' ' + modificador : ''), rotulo);
  }

  function criarCard(vaga, cancelada, indice) {
    var card = elemento('article', 'vaga-busca-card' + (cancelada ? ' vaga-busca-card--cancelada' : ''));
    card.dataset.vagaId = String(vaga.id);

    var fallback = fallbackDaVaga(vaga, indice);
    var imagem = elemento('img', 'vaga-busca-card__imagem');
    imagem.loading = 'lazy';
    imagem.alt = '';
    imagem.src = global.PalcoVagas.primeiraFotoValida(vaga.fotos) || fallback;
    imagem.addEventListener('error', function () {
      if (!imagem.src.endsWith(fallback)) imagem.src = fallback;
    }, { once: true });
    card.appendChild(imagem);

    var conteudo = elemento('div', 'vaga-busca-card__conteudo');
    var badges = elemento('div', 'vaga-busca-card__badges');
    if (cancelada) {
      badges.appendChild(criarBadge('Vaga Cancelada', 'vaga-busca-card__badge--cancelada'));
    } else {
      badges.appendChild(criarBadge(texto(vaga.categoria || vaga.tipoContrato, 'Oportunidade')));
    }
    if (vaga.propriaDoContratante === true) {
      badges.appendChild(criarBadge('Sua vaga', 'vaga-busca-card__badge--propria'));
    }
    conteudo.appendChild(badges);

    conteudo.appendChild(elemento('h3', 'vaga-busca-card__titulo', texto(vaga.titulo, 'Vaga sem título')));
    conteudo.appendChild(elemento('p', 'vaga-busca-card__empresa', texto(vaga.nomeContratante, 'Contratante não informado')));

    var metas = elemento('div', 'vaga-busca-card__metas');
    var local = [texto(vaga.cidade, ''), texto(vaga.estado, '')].filter(Boolean).join('/');
    metas.appendChild(elemento('span', '', local || 'Local não informado'));
    metas.appendChild(elemento('span', '', rotuloEnum(vaga.modeloTrabalho) || 'Modelo não informado'));
    metas.appendChild(elemento('span', '', texto(vaga.tipoContrato, 'Contrato não informado')));
    metas.appendChild(elemento('span', '', remuneracaoDaVaga(vaga)));
    conteudo.appendChild(metas);

    conteudo.appendChild(elemento('p', 'vaga-busca-card__descricao', texto(vaga.descricao, 'Descrição não informada.')));

    var rodape = elemento('div', 'vaga-busca-card__rodape');
    rodape.appendChild(elemento('p', 'vaga-busca-card__prazo', dataBrasileira(vaga.dataLimiteCandidatura)));
    var link = elemento('a', 'vaga-busca-card__link', 'Ver detalhes');
    link.href = global.PalcoVagas.urlDetalhe(vaga.id);
    rodape.appendChild(link);
    conteudo.appendChild(rodape);
    card.appendChild(conteudo);
    return card;
  }

  function iniciarVoltarTopo() {
    var botao = document.querySelector('[data-voltar-topo]');
    if (!botao) return;
    var preferencia = global.matchMedia && global.matchMedia('(prefers-reduced-motion: reduce)');
    var quadro = 0;

    function atualizar() {
      quadro = 0;
      var visivel = global.scrollY > 300;
      botao.classList.toggle('busca-voltar-topo--visivel', visivel);
      botao.setAttribute('aria-hidden', String(!visivel));
      botao.tabIndex = visivel ? 0 : -1;
    }

    botao.hidden = false;
    atualizar();
    global.addEventListener('scroll', function () {
      if (!quadro) quadro = global.requestAnimationFrame(atualizar);
    }, { passive: true });
    botao.addEventListener('click', function () {
      global.scrollTo({
        top: 0,
        left: 0,
        behavior: preferencia && preferencia.matches ? 'auto' : 'smooth'
      });
    });
  }

  function iniciar() {
    if (!global.PalcoVagas) return;
    var form = document.querySelector('[data-form-filtros]');
    var lista = document.querySelector('[data-lista-vagas]');
    var listaCanceladas = document.querySelector('[data-lista-canceladas]');
    if (!form || !lista || !listaCanceladas || document.body.dataset.buscaVagasIniciada) return;
    document.body.dataset.buscaVagasIniciada = 'true';

    var dom = {
      carregando: document.querySelector('[data-estado-carregando]'),
      carregandoMais: document.querySelector('[data-estado-carregando-mais]'),
      erro: document.querySelector('[data-estado-erro]'),
      erroTexto: document.querySelector('[data-erro-texto]'),
      vazio: document.querySelector('[data-estado-vazio]'),
      fim: document.querySelector('[data-fim-lista]'),
      status: document.querySelector('[data-status-resultados]'),
      secaoCanceladas: document.querySelector('[data-secao-canceladas]'),
      sentinela: document.querySelector('[data-sentinela]'),
      tentarNovamente: document.querySelector('[data-tentar-novamente]'),
      limpar: document.querySelector('[data-limpar-filtros]')
    };

    var estado = {
      filtros: {},
      cursor: null,
      cursorCanceladas: null,
      hasMore: true,
      hasMoreCanceladas: true,
      carregando: false,
      erro: null,
      total: 0,
      totalCanceladas: 0,
      geracao: 0,
      abortController: null
    };
    var ids = new Set();
    var idsCanceladas = new Set();
    var observador = null;

    function filtrosDoFormulario() {
      var filtros = {};
      CAMPOS.forEach(function (campo) {
        var controle = form.elements[campo];
        var valor = controle ? String(controle.value || '').trim() : '';
        if (valor) filtros[campo] = campo === 'estado' ? valor.toUpperCase() : valor;
      });
      return filtros;
    }

    function preencherFormularioDaUrl() {
      var parametros = new URLSearchParams(global.location.search);
      CAMPOS.forEach(function (campo) {
        if (form.elements[campo]) form.elements[campo].value = parametros.get(campo) || '';
      });
    }

    function refletirFiltrosNaUrl() {
      var parametros = global.PalcoVagas.parametrosListagem(estado.filtros, { size: TAMANHO_PAGINA });
      parametros.delete('size');
      var consulta = parametros.toString();
      global.history.replaceState(null, '', global.location.pathname + (consulta ? '?' + consulta : ''));
    }

    function atualizarInterface() {
      var temResultados = estado.total + estado.totalCanceladas > 0;
      dom.carregando.hidden = !(estado.carregando && !temResultados);
      dom.carregandoMais.hidden = !(estado.carregando && temResultados);
      dom.erro.hidden = !estado.erro;
      dom.vazio.hidden = estado.carregando || Boolean(estado.erro) || temResultados;
      dom.secaoCanceladas.hidden = estado.totalCanceladas === 0;
      dom.fim.hidden = estado.carregando || Boolean(estado.erro) || !temResultados
        || estado.hasMore || estado.hasMoreCanceladas;
      if (estado.erro) dom.erroTexto.textContent = estado.erro;
      dom.status.textContent = estado.total + (estado.total === 1 ? ' vaga aberta exibida' : ' vagas abertas exibidas');
    }

    function adicionarVagas(vagas, canceladas) {
      var destino = canceladas ? listaCanceladas : lista;
      var conjunto = canceladas ? idsCanceladas : ids;
      var adicionadas = 0;
      (Array.isArray(vagas) ? vagas : []).forEach(function (vaga, indice) {
        if (!vaga || vaga.id === null || vaga.id === undefined) return;
        var chave = String(vaga.id);
        if (conjunto.has(chave)) return;
        conjunto.add(chave);
        destino.appendChild(criarCard(vaga, canceladas, conjunto.size + indice));
        adicionadas += 1;
      });
      if (canceladas) estado.totalCanceladas += adicionadas;
      else estado.total += adicionadas;
    }

    function ultimoId(vagas, atual) {
      if (!Array.isArray(vagas) || !vagas.length) return atual;
      var ultimo = vagas[vagas.length - 1];
      return ultimo && ultimo.id !== undefined && ultimo.id !== null ? ultimo.id : atual;
    }

    function atualizarObservacao() {
      if (!observador || !dom.sentinela) return;
      observador.unobserve(dom.sentinela);
      if (!estado.erro && (estado.hasMore || estado.hasMoreCanceladas)) {
        observador.observe(dom.sentinela);
      }
    }

    async function carregarMais() {
      if (estado.carregando || (!estado.hasMore && !estado.hasMoreCanceladas)) return;
      estado.carregando = true;
      estado.erro = null;
      atualizarInterface();
      var geracao = estado.geracao;
      estado.abortController = 'AbortController' in global ? new AbortController() : null;
      var parametros = global.PalcoVagas.parametrosListagem(estado.filtros, {
        cursor: estado.cursor,
        cursorCanceladas: estado.cursorCanceladas,
        size: TAMANHO_PAGINA
      });

      try {
        var resposta = await global.PalcoVagas.requisitar('/vagas?' + parametros.toString(), {
          signal: estado.abortController ? estado.abortController.signal : undefined
        });
        if (geracao !== estado.geracao) return;

        var abertas = Array.isArray(resposta.content) ? resposta.content : [];
        var canceladas = Array.isArray(resposta.vagasCanceladasComCandidatura)
          ? resposta.vagasCanceladasComCandidatura
          : [];
        adicionarVagas(abertas, false);
        adicionarVagas(canceladas, true);

        estado.hasMore = resposta.hasMore === true && resposta.nextCursor !== null
          && resposta.nextCursor !== undefined;
        estado.hasMoreCanceladas = resposta.hasMoreCanceladas === true
          && resposta.nextCursorCanceladas !== null
          && resposta.nextCursorCanceladas !== undefined;
        estado.cursor = estado.hasMore ? resposta.nextCursor : ultimoId(abertas, estado.cursor);
        estado.cursorCanceladas = estado.hasMoreCanceladas
          ? resposta.nextCursorCanceladas
          : ultimoId(canceladas, estado.cursorCanceladas);
      } catch (erro) {
        if (erro && erro.name === 'AbortError') return;
        if (geracao === estado.geracao) {
          estado.erro = erro && erro.message
            ? erro.message
            : 'Não foi possível carregar as vagas agora. Tente novamente.';
        }
      } finally {
        if (geracao === estado.geracao) {
          estado.carregando = false;
          estado.abortController = null;
          atualizarInterface();
          atualizarObservacao();
        }
      }
    }

    function resetarBusca(refletirUrl) {
      estado.geracao += 1;
      if (estado.abortController) estado.abortController.abort();
      estado.filtros = filtrosDoFormulario();
      estado.cursor = null;
      estado.cursorCanceladas = null;
      estado.hasMore = true;
      estado.hasMoreCanceladas = true;
      estado.carregando = false;
      estado.erro = null;
      estado.total = 0;
      estado.totalCanceladas = 0;
      ids.clear();
      idsCanceladas.clear();
      lista.replaceChildren();
      listaCanceladas.replaceChildren();
      if (refletirUrl) refletirFiltrosNaUrl();
      carregarMais();
    }

    form.addEventListener('submit', function (evento) {
      evento.preventDefault();
      var filtros = filtrosDoFormulario();
      if (filtros.faixaSalarialMin && filtros.faixaSalarialMax
          && Number(filtros.faixaSalarialMin) > Number(filtros.faixaSalarialMax)) {
        estado.erro = 'A remuneração mínima não pode ser maior que a máxima.';
        atualizarInterface();
        return;
      }
      resetarBusca(true);
    });

    dom.limpar.addEventListener('click', function () {
      form.reset();
      resetarBusca(true);
    });
    dom.tentarNovamente.addEventListener('click', carregarMais);

    if ('IntersectionObserver' in global) {
      observador = new IntersectionObserver(function (entradas) {
        if (entradas.some(function (entrada) { return entrada.isIntersecting; })) carregarMais();
      }, { rootMargin: '500px 0px' });
    } else {
      global.addEventListener('scroll', function () {
        if (!dom.sentinela || estado.carregando) return;
        if (dom.sentinela.getBoundingClientRect().top < global.innerHeight + 400) carregarMais();
      }, { passive: true });
    }

    global.addEventListener('popstate', function () {
      preencherFormularioDaUrl();
      resetarBusca(false);
    });

    preencherFormularioDaUrl();
    iniciarVoltarTopo();
    resetarBusca(false);
  }

  global.PalcoBuscaVagas = Object.freeze({
    iniciar: iniciar,
    tamanhoPagina: TAMANHO_PAGINA
  });
  document.addEventListener('DOMContentLoaded', iniciar);
})(window);
