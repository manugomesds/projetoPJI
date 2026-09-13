/* Fluxos de conta: só uma resposta da API pode confirmar autenticação. */
(function () {
  'use strict';
  if (!document.querySelector('[data-google-erro]')) return;
  var ocupado = false;
  function mostrar(seletor, mensagem) {
    document.querySelectorAll('.fluxo__estado').forEach(function (el) { el.hidden = true; });
    var alvo = document.querySelector(seletor);
    if (alvo) alvo.hidden = false;
    if (mensagem) document.querySelector('[data-google-erro-texto]').textContent = mensagem;
  }
  // Credential arrives from the configured provider callback, never the URL.
  window.receberCredencialPalcoGoogle = async function (dados) {
    if (ocupado) return;
    ocupado = true;
    mostrar('[data-google-carregando]');
    try {
      var resposta = await window.PalcoGoogle.entrar(dados && dados.credential);
      if (resposta.status === 'AGUARDANDO_DADOS' || (resposta.statusConta && resposta.statusConta !== 'ATIVA')) {
        mostrar('[data-google-tipo]');
      } else {
        window.location.assign('/dashboard');
      }
    } catch (erro) {
      mostrar('[data-google-erro]', erro.message || 'Não foi possível concluir o acesso.');
    } finally {
      ocupado = false;
    }
  };
  var params = new URLSearchParams(window.location.search);
  if (params.has('error')) {
    mostrar('[data-google-cancelado]');
  } else {
    // The export contains no deployment-specific Google client configuration.
    mostrar('[data-google-erro]', 'O acesso pelo provedor Google ainda não está configurado nesta versão. Use o login por e-mail.');
  }
})();
