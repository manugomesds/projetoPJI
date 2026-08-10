/* Remocao de tags nas telas de edicao. Carregado junto com main.js. */
document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('[data-remover-tag]').forEach(function (botao) {
    botao.addEventListener('click', function () {
      botao.closest('.requisitos__tag').remove();
    });
  });
});
