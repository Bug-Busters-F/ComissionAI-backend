package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.calculo.CalculoCompetenciaResponseDTO;
import com.bugbusters.backend.dto.calculo.CalculoImpedimentoDTO;
import com.bugbusters.backend.dto.calculo.CalculoItemResponseDTO;
import com.bugbusters.backend.dto.fechamento.FechamentoRequestDTO;
import com.bugbusters.backend.dto.fechamento.FechamentoResponseDTO;
import com.bugbusters.backend.dto.fechamento.FechamentoStatusDTO;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.FechamentoCompetencia;
import com.bugbusters.backend.model.StatusFechamento;
import com.bugbusters.backend.repository.FechamentoCompetenciaRepository;
import com.bugbusters.backend.service.lock.TravaCompetencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FechamentoServiceTest {

    @Mock
    private FechamentoCompetenciaRepository fechamentoCompetenciaRepository;

    @Mock
    private CalculoService calculoService;

    @Mock
    private TravaCompetencia travaCompetencia;

    private FechamentoService fechamentoService;

    @BeforeEach
    void setUp() {
        var transactions = mock(org.springframework.transaction.PlatformTransactionManager.class);
        lenient().when(transactions.getTransaction(any())).thenAnswer(invocation -> new org.springframework.transaction.support.SimpleTransactionStatus());
        fechamentoService = new FechamentoService(fechamentoCompetenciaRepository, calculoService, travaCompetencia, transactions);
    }

    @Test
    @DisplayName("executarFechamento - Deve processar fechamento com sucesso e marcar como CONCLUIDO")
    void deveExecutarFechamentoComSucesso() {
        String competencia = "2026-10";
        FechamentoRequestDTO request = new FechamentoRequestDTO(competencia, "auditor@empresa.com", "Fechamento mensal oficial");

        when(fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(competencia, StatusFechamento.CONCLUIDO)).thenReturn(false);
        when(fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(competencia, StatusFechamento.PROCESSANDO)).thenReturn(false);

        FechamentoCompetencia fechamentoMock = new FechamentoCompetencia(competencia, UUID.randomUUID(), request.executadoPor(), request.motivo());
        fechamentoMock.setId(UUID.randomUUID());
        when(fechamentoCompetenciaRepository.saveAndFlush(any(FechamentoCompetencia.class))).thenReturn(fechamentoMock);
        when(fechamentoCompetenciaRepository.findById(fechamentoMock.getId())).thenReturn(Optional.of(fechamentoMock));

        CalculoItemResponseDTO itemCalculado = new CalculoItemResponseDTO(
                UUID.randomUUID(), UUID.randomUUID(), "MAT01", 10, 1, 100,
                LocalDate.of(2026, 10, 5), new BigDecimal("1000.00"), new BigDecimal("0.1000"),
                new BigDecimal("100.00"), 5L, "REGRA", OffsetDateTime.now(), 2
        );
        CalculoCompetenciaResponseDTO apuracaoMock = new CalculoCompetenciaResponseDTO(
                competencia, 1, 1, 0, new BigDecimal("1000.00"), new BigDecimal("100.00"),
                List.of(itemCalculado), List.of()
        );
        when(calculoService.processarCompetenciaFechamento(eq(competencia), eq(fechamentoMock.getId()), eq("auditor@empresa.com")))
                .thenReturn(apuracaoMock);

        FechamentoResponseDTO response = fechamentoService.executarFechamento(request);

        assertThat(response).isNotNull();
        assertThat(response.competencia()).isEqualTo("2026-10");
        assertThat(response.status()).isEqualTo(StatusFechamento.CONCLUIDO);
        assertThat(response.totalVendasProcessadas()).isEqualTo(1);
        assertThat(response.totalCalculados()).isEqualTo(1);
        assertThat(response.totalImpedimentos()).isEqualTo(0);
        assertThat(response.valorTotalVendas()).isEqualByComparingTo("1000.00");
        assertThat(response.valorTotalComissao()).isEqualByComparingTo("100.00");
        assertThat(response.executadoPor()).isEqualTo("auditor@empresa.com");
        assertThat(response.resultados()).hasSize(1);
        assertThat(response.resultados().get(0).versaoCampanhaNumero()).isEqualTo(2);

        verify(travaCompetencia, times(2)).adquirirTrava(competencia);
        verify(fechamentoCompetenciaRepository).save(any(FechamentoCompetencia.class));
    }

    @Test
    @DisplayName("executarFechamento - Deve rejeitar fechamento de competência já concluída")
    void deveRejeitarFechamentoSeCompetenciaJaConcluida() {
        String competencia = "2026-10";
        FechamentoRequestDTO request = new FechamentoRequestDTO(competencia, "auditor@empresa.com", "Tentativa repetida");

        when(fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(competencia, StatusFechamento.CONCLUIDO)).thenReturn(true);

        assertThatThrownBy(() -> fechamentoService.executarFechamento(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já possui fechamento final concluído");

        verifyNoInteractions(calculoService);
    }

    @Test
    @DisplayName("executarFechamento - Deve rejeitar fechamento de competência em processamento")
    void deveRejeitarFechamentoSeCompetenciaEmProcessamento() {
        String competencia = "2026-10";
        FechamentoRequestDTO request = new FechamentoRequestDTO(competencia, "auditor@empresa.com", "Tentativa concorrente");

        when(fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(competencia, StatusFechamento.CONCLUIDO)).thenReturn(false);
        when(fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(competencia, StatusFechamento.PROCESSANDO)).thenReturn(true);

        assertThatThrownBy(() -> fechamentoService.executarFechamento(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já possui um processo de fechamento em andamento");

        verifyNoInteractions(calculoService);
    }

    @Test
    @DisplayName("executarFechamento - Deve registrar status FALHOU e liberar competência em caso de erro na apuração")
    void deveRegistrarStatusFalhouELiberarCompetenciaEmCasoDeErro() {
        String competencia = "2026-10";
        FechamentoRequestDTO request = new FechamentoRequestDTO(competencia, "auditor@empresa.com", "Fechamento com erro");

        when(fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(competencia, StatusFechamento.CONCLUIDO)).thenReturn(false);
        when(fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(competencia, StatusFechamento.PROCESSANDO)).thenReturn(false);

        FechamentoCompetencia fechamentoMock = new FechamentoCompetencia(competencia, UUID.randomUUID(), request.executadoPor(), request.motivo());
        fechamentoMock.setId(UUID.randomUUID());
        when(fechamentoCompetenciaRepository.saveAndFlush(any(FechamentoCompetencia.class))).thenReturn(fechamentoMock);
        when(fechamentoCompetenciaRepository.findById(fechamentoMock.getId())).thenReturn(Optional.of(fechamentoMock));

        when(calculoService.processarCompetenciaFechamento(any(), any(), any()))
                .thenThrow(new BusinessException("Falha na resolução de regras da versão"));

        assertThatThrownBy(() -> fechamentoService.executarFechamento(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Falha na resolução de regras da versão");

        ArgumentCaptor<FechamentoCompetencia> captor = ArgumentCaptor.forClass(FechamentoCompetencia.class);
        verify(fechamentoCompetenciaRepository).save(captor.capture());

        FechamentoCompetencia salvo = captor.getValue();
        assertThat(salvo.getStatus()).isEqualTo(StatusFechamento.FALHOU);
        assertThat(salvo.getMotivoFalha()).contains("Falha na resolução de regras da versão");
        assertThat(salvo.getConcluidoEm()).isNotNull();
    }

    @Test void travaTodosOsMesesEmOrdemSemRepetirCompetenciasDePeriodosSobrepostos() {
        fechamentoService.validarPeriodosDisponiveis(LocalDate.of(2040,3,1),LocalDate.of(2040,4,30),
                LocalDate.of(2040,1,1),LocalDate.of(2040,3,31));
        var order = inOrder(travaCompetencia);
        order.verify(travaCompetencia).adquirirTrava("2040-01");
        order.verify(travaCompetencia).adquirirTrava("2040-02");
        order.verify(travaCompetencia).adquirirTrava("2040-03");
        order.verify(travaCompetencia).adquirirTrava("2040-04");
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("consultarStatusCompetencia - Deve retornar status correto para competência concluída, processando, com falha ou aberta")
    void deveConsultarStatusCompetencia() {
        // 1. Concluída
        FechamentoCompetencia concluido = new FechamentoCompetencia("2026-10", UUID.randomUUID(), "auditor", "Ok");
        concluido.setStatus(StatusFechamento.CONCLUIDO);
        concluido.setConcluidoEm(OffsetDateTime.now());
        when(fechamentoCompetenciaRepository.findFirstByCompetenciaAndStatusOrderByConcluidoEmDesc("2026-10", StatusFechamento.CONCLUIDO))
                .thenReturn(Optional.of(concluido));

        FechamentoStatusDTO statusConcluido = fechamentoService.consultarStatusCompetencia("2026-10");
        assertThat(statusConcluido.status()).isEqualTo("CONCLUIDO");
        assertThat(statusConcluido.bloqueada()).isTrue();

        // 2. Aberta
        when(fechamentoCompetenciaRepository.findFirstByCompetenciaAndStatusOrderByConcluidoEmDesc("2026-11", StatusFechamento.CONCLUIDO))
                .thenReturn(Optional.empty());
        when(fechamentoCompetenciaRepository.findFirstByCompetenciaAndStatusOrderByConcluidoEmDesc("2026-11", StatusFechamento.PROCESSANDO))
                .thenReturn(Optional.empty());
        when(fechamentoCompetenciaRepository.findFirstByCompetenciaOrderByIniciadoEmDesc("2026-11"))
                .thenReturn(Optional.empty());

        FechamentoStatusDTO statusAberta = fechamentoService.consultarStatusCompetencia("2026-11");
        assertThat(statusAberta.status()).isEqualTo("ABERTA");
        assertThat(statusAberta.bloqueada()).isFalse();
    }

    @Test
    @DisplayName("validarPeriodoDisponivel - Deve bloquear se alguma competência do período estiver fechada")
    void deveBloquearPeriodoComCompetenciaFechada() {
        LocalDate inicio = LocalDate.of(2026, 9, 15);
        LocalDate fim = LocalDate.of(2026, 11, 15);

        when(fechamentoCompetenciaRepository.existsByCompetenciaAndStatus("2026-09", StatusFechamento.CONCLUIDO)).thenReturn(false);
        when(fechamentoCompetenciaRepository.existsByCompetenciaAndStatus("2026-09", StatusFechamento.PROCESSANDO)).thenReturn(false);
        when(fechamentoCompetenciaRepository.existsByCompetenciaAndStatus("2026-10", StatusFechamento.CONCLUIDO)).thenReturn(true);

        assertThatThrownBy(() -> fechamentoService.validarPeriodoDisponivel(inicio, fim))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2026-10 está com fechamento final concluído");
    }

    @Test
    @DisplayName("listarFechamentos - Deve listar histórico paginado")
    void deveListarFechamentosPaginado() {
        FechamentoCompetencia f = new FechamentoCompetencia("2026-10", UUID.randomUUID(), "auditor", "Mensal");
        f.setId(UUID.randomUUID());
        f.setStatus(StatusFechamento.CONCLUIDO);
        Page<FechamentoCompetencia> page = new PageImpl<>(List.of(f));

        Pageable pageable = PageRequest.of(0, 10);
        when(fechamentoCompetenciaRepository.findAllByOrderByIniciadoEmDesc(pageable)).thenReturn(page);

        Page<FechamentoResponseDTO> result = fechamentoService.listarFechamentos(pageable, null);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).competencia()).isEqualTo("2026-10");
    }
}
