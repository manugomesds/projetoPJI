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
                : cb.greaterThanOrEqualTo(root.get("remuneraValor"), min);
    }

    public static Specification<Vaga> remuneracaoMaxima(BigDecimal max) {
        return (root, query, cb) -> max == null
                ? cb.conjunction()
                : cb.lessThanOrEqualTo(root.get("remuneraValor"), max);
    }

    // distinct(true) evita vaga duplicada no resultado quando ela casa com mais de uma tag do filtro
    public static Specification<Vaga> comAlgumaTag(Set<Long> tagIds) {
        return (root, query, cb) -> {
            if (tagIds == null || tagIds.isEmpty()) {
                return cb.conjunction();
            }
            query.distinct(true);
            Join<Object, Object> tagJoin = root.join("tags");
            return tagJoin.get("id").in(tagIds);
        };
    }

    public static Specification<Vaga> doContratante(Long usuarioId) {
        return (root, query, cb) -> usuarioId == null
                ? cb.conjunction()
                : cb.equal(root.get("contratante").get("usuarioId"), usuarioId);
    }
}
