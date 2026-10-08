package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.CampanhaResponse;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.exception.BusinessException;
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
import static org.mockito.ArgumentMatchers.anyList;
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
        regra.setCompleta(true);
    }

    @Test
    @DisplayName("criarCampanha - Deve criar campanha e coleção de regras com estado padrão DRAFT")
    void deveCriarCampanhaComEstadoDraft() {
        RegraItemRequest regraItem = new RegraItemRequest(
                "bloco-1", "Comissão 5%", "Regra - Campanha Natal", "ECOMMERCE",
                null, null, null, null, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, new BigDecimal("0.0500"),
                null, null, null, new BigDecimal("0.0500"),
                null, null, List.of(), true, StatusRegra.DRAFT
        );

        CampanhaRequest request = new CampanhaRequest(
                "Campanha Natal", "Comissão 5%", LocalDate.now(), LocalDate.now().plusDays(30),
                EstadoCampanha.DRAFT, List.of(regraItem)
        );

        when(campanhaRepository.save(any(Campanha.class))).thenReturn(campanha);
        when(regraRepository.saveAll(anyList())).thenReturn(List.of(regra));

        CampanhaResponse response = campanhaService.criarCampanha(request);

        assertThat(response).isNotNull();
        assertThat(response.estado()).isEqualTo(EstadoCampanha.DRAFT);
        assertThat(response.regras()).hasSize(1);
        assertThat(response.regras().get(0).status()).isEqualTo(StatusRegra.DRAFT);
        verify(campanhaRepository).save(any(Campanha.class));
        verify(regraRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("listarAtivas - Deve buscar estritamente campanhas com estado ATIVA e carregar coleção")
    void deveListarApenasCampanhasAtivas() {
        Campanha campanhaAtiva = new Campanha("Campanha Ativa", "Texto", LocalDate.now(), LocalDate.now().plusDays(30));
        campanhaAtiva.setId(11L);
        campanhaAtiva.setEstado(EstadoCampanha.ATIVA);
        regra.setCampanha(campanhaAtiva);

        when(campanhaRepository.findAllByEstadoAndRemovidoEmIsNullOrderByCriadoEmDesc(EstadoCampanha.ATIVA))
                .thenReturn(List.of(campanhaAtiva));
        when(regraRepository.findAllByCampanhaIdInAndRemovidoEmIsNull(List.of(11L)))
                .thenReturn(List.of(regra));

        List<CampanhaResponse> resultado = campanhaService.listarAtivas();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).estado()).isEqualTo(EstadoCampanha.ATIVA);
        assertThat(resultado.get(0).regras()).hasSize(1);
        verify(campanhaRepository).findAllByEstadoAndRemovidoEmIsNullOrderByCriadoEmDesc(EstadoCampanha.ATIVA);
    }

    @Test
    @DisplayName("alterarEstado - Deve transicionar para ATIVA e sincronizar todas as regras para ATIVA")
    void deveAlterarEstadoParaAtivaESincronizarRegra() {
        when(campanhaRepository.findByIdAndRemovidoEmIsNull(10L)).thenReturn(Optional.of(campanha));
        when(campanhaRepository.save(any(Campanha.class))).thenAnswer(inv -> inv.getArgument(0));
        when(regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(10L)).thenReturn(List.of(regra));
        when(regraRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        CampanhaResponse response = campanhaService.alterarEstado(10L, EstadoCampanha.ATIVA);

        assertThat(response.estado()).isEqualTo(EstadoCampanha.ATIVA);
        assertThat(response.regras().get(0).status()).isEqualTo(StatusRegra.ATIVA);
        assertThat(campanha.getEstado()).isEqualTo(EstadoCampanha.ATIVA);
        assertThat(regra.getStatus()).isEqualTo(StatusRegra.ATIVA);
    }

    @Test
    @DisplayName("alterarEstado - Deve rejeitar ativação de campanha contendo regras incompletas")
    void deveRejeitarAtivacaoDeCampanhaComRegrasIncompletas() {
        Regra regraIncompleta = new Regra();
        regraIncompleta.setId(21L);
        regraIncompleta.setCompleta(false);
        regraIncompleta.setTaxa(null);

        when(campanhaRepository.findByIdAndRemovidoEmIsNull(10L)).thenReturn(Optional.of(campanha));
        when(regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(10L)).thenReturn(List.of(regraIncompleta));

        assertThatThrownBy(() -> campanhaService.alterarEstado(10L, EstadoCampanha.ATIVA))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Não é permitido ativar campanha contendo regras ou propostas incompletas");
    }

    @Test
    @DisplayName("alterarEstado - Deve transicionar para INATIVA/CANCELADA/CONCLUIDA e sincronizar regras para INATIVA")
    void deveAlterarEstadoParaInativaESincronizarRegra() {
        when(campanhaRepository.findByIdAndRemovidoEmIsNull(10L)).thenReturn(Optional.of(campanha));
        when(campanhaRepository.save(any(Campanha.class))).thenAnswer(inv -> inv.getArgument(0));
        when(regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(10L)).thenReturn(List.of(regra));
        when(regraRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        CampanhaResponse response = campanhaService.alterarEstado(10L, EstadoCampanha.INATIVA);

        assertThat(response.estado()).isEqualTo(EstadoCampanha.INATIVA);
        assertThat(response.regras().get(0).status()).isEqualTo(StatusRegra.INATIVA);
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
    @DisplayName("removerLogicamente - Deve marcar removidoEm, alterar estado para CANCELADA e inativar regras")
    void deveRemoverLogicamenteCampanha() {
        when(campanhaRepository.findByIdAndRemovidoEmIsNull(10L)).thenReturn(Optional.of(campanha));
        when(regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(10L)).thenReturn(List.of(regra));

        campanhaService.removerLogicamente(10L);

        assertThat(campanha.getRemovidoEm()).isNotNull();
        assertThat(campanha.getEstado()).isEqualTo(EstadoCampanha.CANCELADA);
        assertThat(regra.getRemovidoEm()).isNotNull();
        assertThat(regra.getStatus()).isEqualTo(StatusRegra.INATIVA);
        verify(campanhaRepository).save(campanha);
        verify(regraRepository).saveAll(anyList());
    }
}
