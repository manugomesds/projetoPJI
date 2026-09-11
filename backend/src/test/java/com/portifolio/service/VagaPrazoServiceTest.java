package com.portifolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portifolio.model.enums.StatusVaga;
import com.portifolio.repository.VagaRepository;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VagaPrazoServiceTest {

    @Mock VagaRepository vagaRepository;
    @Mock VagaPrazoPolicy vagaPrazoPolicy;
    @Mock VagaService vagaService;
    @InjectMocks VagaPrazoService vagaPrazoService;

    @Test
    void consultaSomenteElegiveisEmLoteLimitadoSemFindAll() {
        LocalDate hoje = LocalDate.of(2026, 9, 11);
        ReflectionTestUtils.setField(vagaPrazoService, "tamanhoLote", 100);
        when(vagaPrazoPolicy.hoje()).thenReturn(hoje);
        when(vagaRepository.findElegiveisParaEncerramento(
                eq(Set.of(StatusVaga.ABERTA, StatusVaga.PAUSADA)), eq(hoje), any(Pageable.class)))
                .thenReturn(java.util.List.of());

        assertThat(vagaPrazoService.encerrarVencidas()).isZero();

        org.mockito.ArgumentCaptor<Pageable> pageable =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(vagaRepository).findElegiveisParaEncerramento(
                eq(Set.of(StatusVaga.ABERTA, StatusVaga.PAUSADA)), eq(hoje), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        verify(vagaRepository, never()).findAll();
    }
}
