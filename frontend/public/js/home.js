/* ==========================================================================
   Palco — home.js
   Comportamentos exclusivos da HomeOficial: menu suspenso "Explorar" e
   os carrosseis (hero, Top da Semana e equipe).

   O acordeao do SAQ nao aparece aqui de proposito: e <details>/<summary>
   nativo, que ja abre, fecha e e navegavel por teclado sem uma linha de JS.

   Segue a convencao do main.js: liga por data-*, nunca por classe de estilo.
   ========================================================================== */

(function () {
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


  /* ------------------------------------------------------------------------
     Menu suspenso "Explorar" da navbar
     ------------------------------------------------------------------------ */
  function iniciarExplorar() {
    var gatilho = document.querySelector('[data-explorar-gatilho]');
    var menu = document.querySelector('[data-explorar-menu]');
    if (!gatilho || !menu) return;

    function alternar(aberto) {
      menu.hidden = !aberto;
      gatilho.setAttribute('aria-expanded', String(aberto));
    }

    gatilho.addEventListener('click', function (evento) {
      evento.stopPropagation();
      alternar(menu.hidden);
    });

    // Fecha ao clicar fora ou apertar Esc
    document.addEventListener('click', function (evento) {
      if (!menu.contains(evento.target) && evento.target !== gatilho) {
        alternar(false);
      }
    });

    document.addEventListener('keydown', function (evento) {
      if (evento.key === 'Escape' && !menu.hidden) {
        alternar(false);
        gatilho.focus();
      }
    });
  }

  /* ------------------------------------------------------------------------
     Carrosseis
     A trilha ja rola sozinha por toque (overflow-x + scroll-snap). O JS so
     acrescenta as setas e os pontinhos, entao um erro aqui nao deixa o
     conteudo inacessivel.
     ------------------------------------------------------------------------ */
  function iniciarCarrosseis() {
    var carrosseis = document.querySelectorAll('[data-carrossel]');
    var preferenciaMovimento = window.matchMedia &&
      window.matchMedia('(prefers-reduced-motion: reduce)');
    var toleranciaOverflow = 1.5;

    carrosseis.forEach(function (carrossel) {
      /* Impede listeners e indicadores duplicados se esta inicializacao for
         reaproveitada por outra entrada no futuro. */
      if (carrossel.hasAttribute('data-carrossel-iniciado')) return;
      carrossel.setAttribute('data-carrossel-iniciado', '');

      var trilha = carrossel.querySelector('[data-carrossel-trilha]');
      if (!trilha) return;

      var anterior = carrossel.querySelector('[data-carrossel-anterior]');
      var proximo = carrossel.querySelector('[data-carrossel-proximo]');
      var caixaPontos = carrossel.querySelector('[data-carrossel-pontos]');
      var itens = Array.prototype.slice.call(trilha.children);
      if (!itens.length) return;

      var indice = 0;
      var classeDestaque = trilha.classList.contains('vitrine__trilha')
        ? 'vaga-mini--destaque'
        : trilha.classList.contains('palco-artistas__trilha')
          ? 'artista-card--destaque'
          : '';
      var quadroAnimacao = 0;
      var quadroLeitura = 0;
      var quadroRealinhamento = 0;
      var rolagemProgramatica = false;

      function movimentoReduzido() {
        return preferenciaMovimento && preferenciaMovimento.matches;
      }

      /* Posicao de rolagem que deixa o item centrado — que e exatamente o
         ponto de encaixe do scroll-snap-align:center.

         Nao usar scrollBy aqui: com scroll-snap-type mandatory o navegador
         devolve qualquer posicao intermediaria para o encaixe mais proximo,
         e o carrossel simplesmente nao sai do lugar. Mirar o ponto exato
         resolve. Tambem nao usar scrollIntoView: ele rola os ancestrais e
         leva a pagina inteira junto. */
      function alvo(item) {
        var centro = item.offsetLeft + item.offsetWidth / 2 - trilha.clientWidth / 2;
        var maximo = trilha.scrollWidth - trilha.clientWidth;
        return Math.max(0, Math.min(centro, maximo));
      }

      function existeOverflow() {
        return trilha.scrollWidth - trilha.clientWidth > toleranciaOverflow;
      }

      var pontos = [];
      function reconstruirPontos() {
        if (!caixaPontos) return;
        caixaPontos.replaceChildren();
        pontos = [];
        itens.forEach(function () {
          var ponto = document.createElement('span');
          ponto.className = 'ponto';
          caixaPontos.appendChild(ponto);
          pontos.push(ponto);
        });
      }
      reconstruirPontos();

      function pintarEstado() {
        pontos.forEach(function (ponto, i) {
          ponto.classList.toggle('ponto--ativo', i === indice);
        });

        itens.forEach(function (item, i) {
          var atual = i === indice;
          if (classeDestaque) item.classList.toggle(classeDestaque, atual);
          item.setAttribute('aria-current', atual ? 'true' : 'false');
        });

        var temOverflow = existeOverflow();
        carrossel.setAttribute('data-carrossel-overflow', String(temOverflow));
        carrossel.setAttribute('data-carrossel-indice', String(indice));

        if (caixaPontos) caixaPontos.hidden = !temOverflow || itens.length < 2;

        if (anterior) {
          anterior.hidden = !temOverflow;
          anterior.disabled = !temOverflow || indice <= 0;
          anterior.setAttribute('aria-disabled', String(anterior.disabled));
        }

        if (proximo) {
          proximo.hidden = !temOverflow;
          proximo.disabled = !temOverflow || indice >= itens.length - 1;
          proximo.setAttribute('aria-disabled', String(proximo.disabled));
        }
      }

      /* Com o alvo limitado a 0/max, os cards das pontas nem sempre conseguem
         ficar geometricamente no centro da viewport. Comparar scrollLeft com
         o alvo real de cada item preserva o indice logico tambem nas bordas. */
      function indiceMaisProximo() {
        var posicao = trilha.scrollLeft;
        var menor = Infinity;
        var encontrado = indice;

        itens.forEach(function (item, i) {
          var distancia = Math.abs(alvo(item) - posicao);
          if (distancia < menor) {
            menor = distancia;
            encontrado = i;
          }
        });

        return encontrado;
      }

      function lerIndice() {
        quadroLeitura = 0;
        if (rolagemProgramatica) return;
        indice = indiceMaisProximo();
        pintarEstado();
      }

      trilha.addEventListener('scroll', function () {
        if (rolagemProgramatica || quadroLeitura) return;
        quadroLeitura = requestAnimationFrame(lerIndice);
      });

      function cancelarAnimacao(manterSnapDesativado) {
        if (quadroAnimacao) cancelAnimationFrame(quadroAnimacao);
        quadroAnimacao = 0;
        rolagemProgramatica = false;

        if (!manterSnapDesativado) {
          trilha.classList.remove('carrossel-trilha--animando');
        }
      }

      /* Animacao horizontal controlada em um unico lugar. Um novo clique
         cancela e redireciona o quadro atual, sem duas rolagens concorrentes. */
      function animarAte(destino) {
        /* O scroll-snap nativo ignora os frames intermediarios e salta para
           o proximo encaixe. Desliga-lo somente durante a animacao permite
           interpolar de verdade, sem afetar o snap de toque/trackpad. */
        cancelarAnimacao(true);

        var inicio = trilha.scrollLeft;
        var distancia = destino - inicio;

        if (movimentoReduzido() || Math.abs(distancia) < 1) {
          trilha.classList.remove('carrossel-trilha--animando');
          trilha.scrollLeft = destino;
          pintarEstado();
          return;
        }

        var inicioTempo = null;
        var duracao = 360;
        rolagemProgramatica = true;
        trilha.classList.add('carrossel-trilha--animando');

        function passo(agora) {
          if (inicioTempo === null) inicioTempo = agora;

          var progresso = Math.min((agora - inicioTempo) / duracao, 1);
          var suavizado = 1 - Math.pow(1 - progresso, 3);
          trilha.scrollLeft = inicio + distancia * suavizado;

          if (progresso < 1) {
            quadroAnimacao = requestAnimationFrame(passo);
            return;
          }

          trilha.scrollLeft = destino;
          trilha.classList.remove('carrossel-trilha--animando');
          quadroAnimacao = 0;
          rolagemProgramatica = false;
          pintarEstado();
        }

        quadroAnimacao = requestAnimationFrame(passo);
      }

      function ir(delta) {
        if (!existeOverflow()) return;

        /* Depois de toque/trackpad, parte do item que realmente representa a
           posicao atual. Durante animacao, preserva o destino logico pedido. */
        if (!rolagemProgramatica) indice = indiceMaisProximo();
        indice = Math.max(0, Math.min(indice + delta, itens.length - 1));
        pintarEstado();
        animarAte(alvo(itens[indice]));
      }

      if (anterior) {
        anterior.addEventListener('click', function () { ir(-1); });
      }
      if (proximo) {
        proximo.addEventListener('click', function () { ir(1); });
      }

      /* Um gesto manual assume o controle imediatamente e interrompe apenas
         a animacao horizontal em curso; o scroll nativo continua intacto. */
      function iniciarRolagemManual() {
        cancelarAnimacao();
      }

      trilha.addEventListener('pointerdown', iniciarRolagemManual, { passive: true });
      trilha.addEventListener('touchstart', iniciarRolagemManual, { passive: true });
      trilha.addEventListener('wheel', iniciarRolagemManual, { passive: true });

      /* Abre centralizado no card em destaque, que e o protagonista do
         layout. Sem isso a trilha comeca no primeiro item e o card grande
         nasce cortado. */
      var destaque = trilha.querySelector('.vaga-mini--destaque, .artista-card--destaque');

      function realinharSemAnimacao() {
        cancelarAnimacao();
        trilha.scrollLeft = existeOverflow() ? alvo(itens[indice]) : 0;
        pintarEstado();
      }

      if (destaque) indice = itens.indexOf(destaque);
      else indice = indiceMaisProximo();

      /* O CSS usa scroll-behavior:auto: esta atribuicao acontece antes do
         primeiro paint e nao produz o passeio visivel que havia no load. */
      realinharSemAnimacao();

      function agendarRealinhamento() {
        if (quadroRealinhamento) return;

        quadroRealinhamento = requestAnimationFrame(function () {
          quadroRealinhamento = 0;
          var destino = alvo(itens[indice]);

          if (Math.abs(trilha.scrollLeft - destino) > 1) {
            realinharSemAnimacao();
          } else {
            pintarEstado();
          }
        });
      }

      /* Recalcula o mesmo item logico quando as medidas mudam; nunca volta
         automaticamente ao destaque depois que a pessoa navegou. */
      window.addEventListener('load', agendarRealinhamento, { once: true });
      window.addEventListener('resize', agendarRealinhamento, { passive: true });

      if ('ResizeObserver' in window) {
        var observadorTamanho = new ResizeObserver(agendarRealinhamento);
        observadorTamanho.observe(trilha);
      }

      function observarImagens() {
        Array.prototype.forEach.call(trilha.querySelectorAll('img'), function (imagem) {
          if (!imagem.complete) {
            imagem.addEventListener('load', agendarRealinhamento, { once: true });
          }
        });
      }
      observarImagens();

      if (document.fonts && document.fonts.ready) {
        document.fonts.ready.then(agendarRealinhamento);
      }

      function aoMudarPreferencia(evento) {
        if (evento.matches) realinharSemAnimacao();
      }

      if (preferenciaMovimento && preferenciaMovimento.addEventListener) {
        preferenciaMovimento.addEventListener('change', aoMudarPreferencia);
      } else if (preferenciaMovimento && preferenciaMovimento.addListener) {
        preferenciaMovimento.addListener(aoMudarPreferencia);
      }

      /* A landing pode substituir os cards demonstrativos por vagas reais.
         Atualiza a coleção fechada pelo carrossel sem reinstalar listeners. */
      carrossel.addEventListener('palco:carrossel-atualizar', function () {
        cancelarAnimacao();
        itens = Array.prototype.slice.call(trilha.children);
        if (!itens.length) return;
        reconstruirPontos();
        var novoDestaque = trilha.querySelector('.vaga-mini--destaque, .artista-card--destaque');
        indice = novoDestaque ? itens.indexOf(novoDestaque) : Math.min(indice, itens.length - 1);
        observarImagens();
        agendarRealinhamento();
      });
    });
  }

  /* ------------------------------------------------------------------------
     Vagas reais no hero com fallback visual progressivo
     ------------------------------------------------------------------------ */
  var imagensFallbackVagas = [
    'assets/home/hero-vaga-jardim.png',
    'assets/home/hero-vaga-bar.png',
    'assets/vaga-foto-1.png',
    'assets/home/vaga-detalhe-3.png'
  ];

  function valorOu(valor, fallback) {
    return valor === null || valor === undefined || String(valor).trim() === ''
      ? fallback
      : String(valor).trim();
  }

  function moedaVaga(valor) {
    var numero = Number(valor);
    if (valor === null || valor === undefined || !Number.isFinite(numero)) return '';
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
      maximumFractionDigits: 0
    }).format(numero);
  }

  function rotuloModelo(valor) {
    return valorOu(valor, '').toLowerCase().replace(/_/g, ' ').replace(/^./, function (letra) {
      return letra.toUpperCase();
    });
  }

  function criarCardVagaReal(vaga, indice) {
    var card = document.createElement('li');
    card.className = 'vaga-mini' + (indice === 1 ? ' vaga-mini--destaque' : '');
    card.dataset.vagaId = String(vaga.id);

    var fallback = imagensFallbackVagas[indice % imagensFallbackVagas.length];
    var imagem = document.createElement('img');
    imagem.className = 'vaga-mini__foto';
    imagem.alt = '';
    imagem.src = window.PalcoVagas.primeiraFotoValida(vaga.fotos) || fallback;
    imagem.addEventListener('error', function () {
      if (!imagem.src.endsWith(fallback)) imagem.src = fallback;
    }, { once: true });
    card.appendChild(imagem);

    var corpo = document.createElement('div');
    corpo.className = 'vaga-mini__corpo';
    var titulo = document.createElement('h2');
    titulo.className = 'vaga-mini__titulo';
    titulo.textContent = valorOu(vaga.titulo, 'Vaga sem título');
    corpo.appendChild(titulo);

    var autor = document.createElement('p');
    autor.className = 'vaga-mini__autor';
    var prefixo = vaga.propriaDoContratante === true ? 'Sua vaga · ' : '';
    autor.textContent = prefixo + valorOu(vaga.nomeContratante, 'Contratante')
      + ' · ' + valorOu(vaga.categoria || vaga.tipoContrato, 'Oportunidade');
    corpo.appendChild(autor);

    var local = document.createElement('p');
    local.className = 'vaga-mini__local';
    var localizacao = [valorOu(vaga.cidade, ''), valorOu(vaga.estado, '')].filter(Boolean).join(', ');
    var detalhes = [localizacao, rotuloModelo(vaga.modeloTrabalho), remuneracaoDaVaga(vaga)]
      .filter(Boolean);
    local.textContent = detalhes.length ? detalhes.join(' · ') : 'Detalhes na página da vaga';
    corpo.appendChild(local);

    var descricao = document.createElement('p');
    descricao.className = 'vaga-mini__descricao';
    var rotulo = document.createElement('strong');
    rotulo.textContent = 'Descrição';
    descricao.appendChild(rotulo);
    descricao.appendChild(document.createTextNode(' ' + valorOu(vaga.descricao, 'Consulte os detalhes da oportunidade.')));
    corpo.appendChild(descricao);

    var link = document.createElement('a');
    link.className = 'btn-palco btn-palco--amarelo vaga-mini__cta';
    link.href = window.PalcoVagas.urlDetalhe(vaga.id);
    link.textContent = 'Ver vaga';
    corpo.appendChild(link);
    card.appendChild(corpo);
    return card;
  }

  async function carregarVagasReaisLanding() {
    var trilha = document.querySelector('[data-vagas-landing]');
    if (!trilha || !window.PalcoVagas) return;
    trilha.replaceChildren();
    try {
      var resposta = await window.PalcoVagas.requisitar('/vagas?size=8');
      var vagas = (Array.isArray(resposta.content) ? resposta.content : []).filter(function (vaga) {
        return vaga && vaga.id !== null && vaga.id !== undefined;
      }).slice(0, 8);
      if (!vagas.length) { trilha.textContent = 'Nenhuma vaga disponível no momento.'; trilha.dataset.fonteVagas = 'api'; return; }

      var fragmento = document.createDocumentFragment();
      vagas.forEach(function (vaga, indice) {
        fragmento.appendChild(criarCardVagaReal(vaga, indice));
      });
      trilha.replaceChildren(fragmento);
      trilha.dataset.fonteVagas = 'api';
      trilha.setAttribute('aria-label', 'Vagas reais em destaque');
      var carrossel = trilha.closest('[data-carrossel]');
      if (carrossel) carrossel.dispatchEvent(new Event('palco:carrossel-atualizar'));
    } catch (erro) {
      trilha.textContent = 'Não foi possível carregar as vagas. Tente novamente mais tarde.';
      trilha.dataset.fonteVagas = 'erro';
    }
  }

  /* ------------------------------------------------------------------------
     Filtros demonstrativos de portfólio
     Atuam somente nos cards estáticos da landing: sem API, rota ou RF20.
     ------------------------------------------------------------------------ */
  function iniciarFiltrosPortfolio() {
    var filtros = Array.prototype.slice.call(
      document.querySelectorAll('[data-portfolio-filtro]')
    );
    var cards = Array.prototype.slice.call(
      document.querySelectorAll('.portfolio-card[data-categoria]')
    );
    var status = document.querySelector('[data-portfolio-status]');
    if (!filtros.length || !cards.length) return;

    function aplicarFiltro(categoria) {
      var visiveis = 0;

      cards.forEach(function (card) {
        var mostrar = categoria === 'todos' || card.getAttribute('data-categoria') === categoria;
        card.hidden = !mostrar;
        if (mostrar) visiveis += 1;
      });

      filtros.forEach(function (filtro) {
        var ativo = filtro.getAttribute('data-portfolio-filtro') === categoria;
        filtro.classList.toggle('chip-filtro--ativo', ativo);
        filtro.setAttribute('aria-pressed', String(ativo));
      });

      if (status) {
        status.textContent = visiveis + (visiveis === 1
          ? ' portfólio demonstrativo exibido.'
          : ' portfólios demonstrativos exibidos.');
      }
    }

    filtros.forEach(function (filtro) {
      filtro.addEventListener('click', function () {
        aplicarFiltro(filtro.getAttribute('data-portfolio-filtro'));
      });
    });

    aplicarFiltro('todos');
  }

  /* ------------------------------------------------------------------------
     Botão fixo de retorno ao topo
     ------------------------------------------------------------------------ */
  function iniciarVoltarTopo() {
    var botao = document.querySelector('[data-voltar-topo]');
    if (!botao) return;

    var preferenciaMovimento = window.matchMedia &&
      window.matchMedia('(prefers-reduced-motion: reduce)');
    var quadro = 0;

    function atualizarVisibilidade() {
      quadro = 0;
      var visivel = window.scrollY > 300;
      botao.classList.toggle('voltar-topo--visivel', visivel);
      botao.setAttribute('aria-hidden', String(!visivel));
      botao.tabIndex = visivel ? 0 : -1;
    }

    function agendarAtualizacao() {
      if (quadro) return;
      quadro = requestAnimationFrame(atualizarVisibilidade);
    }

    botao.hidden = false;
    atualizarVisibilidade();
    window.addEventListener('scroll', agendarAtualizacao, { passive: true });

    botao.addEventListener('click', function () {
      var reduzido = preferenciaMovimento && preferenciaMovimento.matches;
      window.scrollTo({ top: 0, left: 0, behavior: reduzido ? 'auto' : 'smooth' });
    });
  }

  window.PalcoHome = Object.freeze({
    iniciarCarrosseis: iniciarCarrosseis,
    carregarVagasReaisLanding: carregarVagasReaisLanding
  });

  document.addEventListener('DOMContentLoaded', function () {
    iniciarExplorar();
    iniciarCarrosseis();
    carregarVagasReaisLanding();
    iniciarFiltrosPortfolio();
    iniciarVoltarTopo();
  });
})();
