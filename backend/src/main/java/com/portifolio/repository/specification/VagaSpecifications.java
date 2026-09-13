package com.portifolio.repository.specification;

import com.portifolio.model.Vaga;
import com.portifolio.model.enums.ModeloTrabalho;
import com.portifolio.model.enums.StatusVaga;
import jakarta.persistence.criteria.Join;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

public final class VagaSpecifications {

    private VagaSpecifications() {
    }

    public static Specification<Vaga> comStatus(StatusVaga status) {
        return (root, query, cb) -> status == null
                ? cb.conjunction()
                : cb.equal(root.get("status"), status);
    }

    public static Specification<Vaga> idMaiorQue(Long cursor) {
        return (root, query, cb) -> cursor == null
                ? cb.conjunction()
                : cb.greaterThan(root.get("id"), cursor);
    }

    public static Specification<Vaga> tituloContem(String titulo) {
        return (root, query, cb) -> (titulo == null || titulo.isBlank())
                ? cb.conjunction()
                : cb.like(cb.lower(root.get("titulo")), "%" + titulo.trim().toLowerCase() + "%");
    }

    public static Specification<Vaga> empresaContem(String empresa) {
        return (root, query, cb) -> {
            if (empresa == null || empresa.isBlank()) {
                return cb.conjunction();
            }
            String termo = "%" + empresa.trim().toLowerCase() + "%";
            var contratante = root.join("contratante");
            var usuario = contratante.join("usuario");
            return cb.or(
                    cb.like(cb.lower(contratante.get("nomeEmpresa")), termo),
                    cb.like(cb.lower(usuario.get("nome")), termo));
        };
    }

    public static Specification<Vaga> cidadeIgual(String cidade) {
        return (root, query, cb) -> (cidade == null || cidade.isBlank())
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("cidade")), cidade.trim().toLowerCase());
    }

    public static Specification<Vaga> estadoIgual(String estado) {
        return (root, query, cb) -> (estado == null || estado.isBlank())
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("estado")), estado.trim().toLowerCase());
    }

    public static Specification<Vaga> modeloTrabalhoIgual(ModeloTrabalho modelo) {
        return (root, query, cb) -> modelo == null
                ? cb.conjunction()
                : cb.equal(root.get("modeloTrabalho"), modelo);
    }

    public static Specification<Vaga> tipoContratoIgual(String tipoContrato) {
        return (root, query, cb) -> (tipoContrato == null || tipoContrato.isBlank())
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("tipoContrato")), tipoContrato.trim().toLowerCase());
    }

    public static Specification<Vaga> remuneracaoMinima(BigDecimal min) {
        return (root, query, cb) -> min == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("valorMinimo"), min);
    }

    public static Specification<Vaga> remuneracaoMaxima(BigDecimal max) {
        return (root, query, cb) -> max == null
                ? cb.conjunction()
                : cb.lessThanOrEqualTo(root.get("valorMinimo"), max);
    }

    public static Specification<Vaga> areaAtuacaoContem(String areaAtuacao) {
        return (root, query, cb) -> (areaAtuacao == null || areaAtuacao.isBlank())
                ? cb.conjunction()
                : cb.like(cb.lower(root.join("area").get("nome")),
                        "%" + areaAtuacao.trim().toLowerCase() + "%");
    }

    public static Specification<Vaga> idDiferente(Long id) {
        return (root, query, cb) -> id == null
                ? cb.conjunction()
                : cb.notEqual(root.get("id"), id);
    }

    // distinct(true) evita vaga duplicada no resultado quando ela casa com mais de uma funcao do filtro
    public static Specification<Vaga> comAlgumaFuncao(Set<Long> funcaoIds) {
        return (root, query, cb) -> {
            if (funcaoIds == null || funcaoIds.isEmpty()) {
                return cb.conjunction();
            }
            query.distinct(true);
            Join<Object, Object> funcaoJoin = root.join("funcoes");
            return funcaoJoin.get("id").in(funcaoIds);
        };
    }

    public static Specification<Vaga> doContratante(Long usuarioId) {
        return (root, query, cb) -> usuarioId == null
                ? cb.conjunction()
                : cb.equal(root.get("contratante").get("usuarioId"), usuarioId);
    }
}
