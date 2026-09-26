package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.CampanhaResponse;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.RegraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampanhaServiceTest {

    @Mock
    private CampanhaRepository campanhaRepository;

    @Mock
    private RegraRepository regraRepository;

    @InjectMocks
    private CampanhaService campanhaService;

    private Campanha campanha;
    private Regra regra;

    @BeforeEach
    void setUp() {
        campanha = new Campanha("Campanha Natal", "Comissão 5%", LocalDate.now(), LocalDate.now().plusDays(30));
        campanha.setId(10L);
        campanha.setEstado(EstadoCampanha.DRAFT);

        regra = new Regra();
        regra.setId(20L);
        regra.setCampanha(campanha);
        regra.setNome("Regra - Campanha Natal");
        regra.setTaxa(new BigDecimal("0.0500"));
        regra.setStatus(StatusRegra.DRAFT);
    }

    @Test
    @DisplayName("criarCampanha - Deve criar campanha e regra vinculada com estado padrão DRAFT")
    void deveCriarCampanhaComEstadoDraft() {
        CampanhaRequest request = new CampanhaRequest(
                "Campanha Natal", "Comissão 5%", "ECOMMERCE",
                new BigDecimal("0.0500"), LocalDate.now(), LocalDate.now().plusDays(30)
        );

        when(campanhaRepository.save(any(Campanha.class))).thenReturn(campanha);
        when(regraRepository.save(any(Regra.class))).thenReturn(regra);

        CampanhaResponse response = campanhaService.criarCampanha(request);

        assertThat(response).isNotNull();
        assertThat(response.estado()).isEqualTo(EstadoCampanha.DRAFT);
        assertThat(response.regra().status()).isEqualTo(StatusRegra.DRAFT);
        verify(campanhaRepository).save(any(Campanha.class));
        verify(regraRepository).save(any(Regra.class));
    }

    @Test
    @DisplayName("listarAtivas - Deve buscar estritamente campanhas com estado ATIVA")
    void deveListarApenasCampanhasAtivas() {
        Campanha campanhaAtiva = new Campanha("Campanha Ativa", "Texto", LocalDate.now(), LocalDate.now().plusDays(30));
        campanhaAtiva.setId(11L);
        campanhaAtiva.setEstado(EstadoCampanha.ATIVA);

        when(campanhaRepository.findAllByEstadoAndRemovidoEmIsNullOrderByCriadoEmDesc(EstadoCampanha.ATIVA))
                .thenReturn(List.of(campanhaAtiva));
        when(regraRepository.findByCampanhaIdAndRemovidoEmIsNull(11L)).thenReturn(Optional.of(regra));

        List<CampanhaResponse> resultado = campanhaService.listarAtivas();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).estado()).isEqualTo(EstadoCampanha.ATIVA);
        verify(campanhaRepository).findAllByEstadoAndRemovidoEmIsNullOrderByCriadoEmDesc(EstadoCampanha.ATIVA);
    }

    @Test
    @DisplayName("alterarEstado - Deve transicionar para ATIVA e sincronizar Regra para ATIVA")
    void deveAlterarEstadoParaAtivaESincronizarRegra() {
        when(campanhaRepository.findByIdAndRemovidoEmIsNull(10L)).thenReturn(Optional.of(campanha));
        when(campanhaRepository.save(any(Campanha.class))).thenAnswer(inv -> inv.getArgument(0));
        when(regraRepository.findByCampanhaIdAndRemovidoEmIsNull(10L)).thenReturn(Optional.of(regra));
        when(regraRepository.save(any(Regra.class))).thenAnswer(inv -> inv.getArgument(0));

        CampanhaResponse response = campanhaService.alterarEstado(10L, EstadoCampanha.ATIVA);

        assertThat(response.estado()).isEqualTo(EstadoCampanha.ATIVA);
        assertThat(response.regra().status()).isEqualTo(StatusRegra.ATIVA);
        assertThat(campanha.getEstado()).isEqualTo(EstadoCampanha.ATIVA);
        assertThat(regra.getStatus()).isEqualTo(StatusRegra.ATIVA);
    }

    @Test
    @DisplayName("alterarEstado - Deve transicionar para INATIVA/CANCELADA/CONCLUIDA e sincronizar Regra para INATIVA")
    void deveAlterarEstadoParaInativaESincronizarRegra() {
        when(campanhaRepository.findByIdAndRemovidoEmIsNull(10L)).thenReturn(Optional.of(campanha));
        when(campanhaRepository.save(any(Campanha.class))).thenAnswer(inv -> inv.getArgument(0));
        when(regraRepository.findByCampanhaIdAndRemovidoEmIsNull(10L)).thenReturn(Optional.of(regra));
        when(regraRepository.save(any(Regra.class))).thenAnswer(inv -> inv.getArgument(0));

        CampanhaResponse response = campanhaService.alterarEstado(10L, EstadoCampanha.INATIVA);

        assertThat(response.estado()).isEqualTo(EstadoCampanha.INATIVA);
        assertThat(response.regra().status()).isEqualTo(StatusRegra.INATIVA);
    }

    @Test
    @DisplayName("alterarEstado - Deve lançar ResourceNotFoundException para campanha inexistente")
    void deveLancarExcecaoParaCampanhaInexistente() {
        when(campanhaRepository.findByIdAndRemovidoEmIsNull(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> campanhaService.alterarEstado(999L, EstadoCampanha.ATIVA))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Campanha com ID 999 não encontrada");
    }

    @Test
    @DisplayName("removerLogicamente - Deve marcar removidoEm, alterar estado para CANCELADA e desativar regra")
    void deveRemoverLogicamenteCampanha() {
        when(campanhaRepository.findByIdAndRemovidoEmIsNull(10L)).thenReturn(Optional.of(campanha));
        when(regraRepository.findByCampanhaIdAndRemovidoEmIsNull(10L)).thenReturn(Optional.of(regra));

        campanhaService.removerLogicamente(10L);

        assertThat(campanha.getRemovidoEm()).isNotNull();
        assertThat(campanha.getEstado()).isEqualTo(EstadoCampanha.CANCELADA);
        assertThat(regra.getRemovidoEm()).isNotNull();
        assertThat(regra.getStatus()).isEqualTo(StatusRegra.INATIVA);
        verify(campanhaRepository).save(campanha);
        verify(regraRepository).save(regra);
    }
}
