package com.bugbusters.backend.service;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.dto.basecomiss.ReferenciaFaltanteDTO;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.cobertura.*;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.importbase.ImportStatus;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.service.auth.UsuarioAtualProvider;
import com.bugbusters.backend.service.lock.TravaCompetencia;
import com.bugbusters.backend.service.pendencia.AceitePendenciaProvider;
import com.bugbusters.backend.service.pendencia.StatusAceitePendenciaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoberturaFechamentoServiceTest {

    @Mock
    private CampanhaRepository campanhaRepository;
    @Mock
    private CampanhaAnexoBaseRepository campanhaAnexoBaseRepository;
    @Mock
    private EnvioArquivoRepository envioArquivoRepository;
    @Mock
    private InconsistenciaImportacaoRepository inconsistenciaRepository;
    @Mock
    private RegraRepository regraRepository;
    @Mock
    private SaleRepository saleRepository;
    @Mock
    private RegistrationRepository registrationRepository;
    @Mock
    private CoberturaBaseService coberturaBaseService;
    @Mock
    private ReferenciaPublicoService referenciaPublicoService;
    @Mock
    private TravaCompetencia travaCompetencia;
    @Mock
    private FechamentoService fechamentoService;
    @Mock
    private UsuarioAtualProvider usuarioAtualProvider;
    @Mock
    private AceitePendenciaProvider aceitePendenciaProvider;

    @InjectMocks
    private CoberturaFechamentoService service;

    private Campanha campanha;
    private CampanhaVersao versaoVigente;

    @BeforeEach
    void setUp() {
        campanha = new Campanha();
        campanha.setId(10L);
        campanha.setTitulo("Campanha Teste");
        campanha.setEstado(EstadoCampanha.ATIVA);

        versaoVigente = new CampanhaVersao();
        versaoVigente.setId(101L);
        versaoVigente.setNumero(1);
        versaoVigente.setDataInicio(LocalDate.of(2026, 7, 15));
        versaoVigente.setDataFim(LocalDate.of(2026, 8, 10));
        campanha.setVersaoVigente(versaoVigente);

        lenient().when(envioArquivoRepository.findLotesConcluidosValidos(any(), any())).thenReturn(Collections.emptyList());
        lenient().when(regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(any())).thenReturn(Collections.emptyList());
        lenient().when(campanhaAnexoBaseRepository.findByCampanhaIdAndDesanexadoEmIsNullOrderByAnexadoEmDesc(any())).thenReturn(Collections.emptyList());
        lenient().when(saleRepository.findVendasPorPeriodoComRelacionamentos(any(), any())).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Decomposição de competências: dentro do mês, virada de ano e validação")
    void deveDecomporCompetenciasCorretamente() {
        List<YearMonth> unicoMes = service.listarCompetenciasInterseccao(LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 25));
        assertThat(unicoMes).containsExactly(YearMonth.of(2026, 7));

        List<YearMonth> viradaAno = service.listarCompetenciasInterseccao(LocalDate.of(2026, 11, 20), LocalDate.of(2027, 2, 5));
        assertThat(viradaAno).containsExactly(
                YearMonth.of(2026, 11),
                YearMonth.of(2026, 12),
                YearMonth.of(2027, 1),
                YearMonth.of(2027, 2)
        );

        assertThatThrownBy(() -> service.listarCompetenciasInterseccao(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 1)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Campanha sem versão vigente publicada deve retornar prontaParaFechamento = false")
    void deveRetornarNaoProntaQuandoCampanhaSemVersaoVigente() {
        campanha.setVersaoVigente(null);
        when(campanhaRepository.findById(10L)).thenReturn(Optional.of(campanha));

        CoberturaCampanhaResponseDTO resp = service.diagnosticarCobertura(10L);

        assertThat(resp.prontaParaFechamento()).isFalse();
        assertThat(resp.resumoImpedimentos()).contains("A campanha não possui versão vigente publicada.");
        assertThat(resp.competencias()).isEmpty();
    }

    @Test
    @DisplayName("Campanha com versaoVigente e versaoProposta deve usar estritamente a vigência da versaoVigente")
    void deveUsarEstritamenteVersaoVigenteMesmoComPropostaAberta() {
        CampanhaVersao versaoProposta = new CampanhaVersao();
        versaoProposta.setId(102L);
        versaoProposta.setNumero(2);
        versaoProposta.setDataInicio(LocalDate.of(2026, 7, 1));
        versaoProposta.setDataFim(LocalDate.of(2026, 10, 31));
        campanha.setVersaoProposta(versaoProposta);

        when(campanhaRepository.findById(10L)).thenReturn(Optional.of(campanha));
        when(campanhaAnexoBaseRepository.findByCampanhaIdAndDesanexadoEmIsNullOrderByAnexadoEmDesc(10L))
                .thenReturn(Collections.emptyList());
        when(envioArquivoRepository.findLotesConcluidosValidos("HR", null))
                .thenReturn(Collections.emptyList());
        when(regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(101L))
                .thenReturn(Collections.emptyList());

        CoberturaCampanhaResponseDTO resp = service.diagnosticarCobertura(10L);

        // Deve avaliar apenas 2026-07 e 2026-08 (da versaoVigente), ignorando setembro e outubro da proposta
        assertThat(resp.totalCompetencias()).isEqualTo(2);
        assertThat(resp.competencias()).extracting(CoberturaCompetenciaDTO::competencia)
                .containsExactly("2026-07", "2026-08");
    }

    @Test
    @DisplayName("Regra FALTA_ANEXO: lote concluído existente no banco mas não anexado à campanha")
    void deveIndicarFaltaAnexoQuandoLoteConcluidoNaoEstaAnexado() {
        when(campanhaRepository.findById(10L)).thenReturn(Optional.of(campanha));
        when(campanhaAnexoBaseRepository.findByCampanhaIdAndDesanexadoEmIsNullOrderByAnexadoEmDesc(10L))
                .thenReturn(Collections.emptyList());

        EnvioArquivo loteSales202607 = new EnvioArquivo();
        loteSales202607.setId(50L);
        loteSales202607.setReportId(10050L);
        loteSales202607.setTipoBase("SALES");
        loteSales202607.setCompetencia("2026-07");
        loteSales202607.setStatus(ImportStatus.CONCLUIDO.name());

        when(envioArquivoRepository.findLotesConcluidosValidos("SALES", "2026-07"))
                .thenReturn(List.of(loteSales202607));

        CoberturaCampanhaResponseDTO resp = service.diagnosticarCobertura(10L);

        assertThat(resp.prontaParaFechamento()).isFalse();
        CoberturaCompetenciaDTO comp07 = resp.competencias().get(0);
        assertThat(comp07.vendas().coberto()).isFalse();
        assertThat(comp07.vendas().status()).isEqualTo("FALTA_ANEXO");
        assertThat(comp07.vendas().lotesDisponiveisNaoAnexados()).hasSize(1);
        assertThat(comp07.vendas().lotesDisponiveisNaoAnexados().get(0).reportId()).isEqualTo(10050L);
    }

    @Test
    @DisplayName("Cobertura de RH: colaborador cadastrado em lote antigo não anexado é considerado coberto")
    void deveConsiderarCobertoColaboradorDeLoteHrAntigoNaoAnexado() {
        when(campanhaRepository.findById(10L)).thenReturn(Optional.of(campanha));

        // Lote recente de RH anexado
        EnvioArquivo loteHrRecente = new EnvioArquivo();
        loteHrRecente.setId(10L);
        loteHrRecente.setReportId(10010L);
        loteHrRecente.setTipoBase("HR");
        loteHrRecente.setStatus(ImportStatus.CONCLUIDO.name());

        CampanhaAnexoBase anexoHr = new CampanhaAnexoBase(campanha, loteHrRecente, "HR", null, "SISTEMA");

        when(campanhaAnexoBaseRepository.findByCampanhaIdAndDesanexadoEmIsNullOrderByAnexadoEmDesc(10L))
                .thenReturn(List.of(anexoHr));
        when(envioArquivoRepository.findLotesConcluidosValidos("HR", null))
                .thenReturn(List.of(loteHrRecente));

        // Venda de colaborador cadastrado por lote antigo (idLoteOrigem = "lote-antigo-999")
        Position cargo = new Position();
        cargo.setCode(10);
        cargo.setDescription("VENDEDOR");

        Registration colabAntigo = new Registration();
        colabAntigo.setRegistration("MAT-ANTIGA-01");
        colabAntigo.setPosition(cargo);
        colabAntigo.setIdLoteOrigem("lote-antigo-999");
        colabAntigo.setAdmissDate(LocalDate.of(2025, 1, 1));

        Brand brand = new Brand();
        brand.setCode(1);
        brand.setDescription("MARCA A");

        Sale sale = new Sale();
        sale.setRegistration(colabAntigo);
        sale.setBrand(brand);
        sale.setSaleDate(LocalDate.of(2026, 7, 20));
        sale.setValue(new BigDecimal("1000.00"));

        when(saleRepository.findVendasPorPeriodoComRelacionamentos(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(sale));

        CoberturaCampanhaResponseDTO resp = service.diagnosticarCobertura(10L);

        CoberturaCompetenciaDTO comp07 = resp.competencias().get(0);
        // O RH deve estar coberto pois a matrícula existe em tb_registration com cargo, mesmo vindo de lote antigo
        assertThat(comp07.rh().coberto()).isTrue();
        assertThat(comp07.rh().colaboradoresSemCargo()).isEmpty();
    }

    @Test
    @DisplayName("Cobertura de RH: colaborador sem cargo cadastrado bloqueia RH")
    void deveBloquearRhQuandoColaboradorNaoPossuiCargo() {
        when(campanhaRepository.findById(10L)).thenReturn(Optional.of(campanha));

        EnvioArquivo loteHr = new EnvioArquivo();
        loteHr.setId(10L);
        loteHr.setReportId(10010L);
        loteHr.setTipoBase("HR");
        loteHr.setStatus(ImportStatus.CONCLUIDO.name());

        CampanhaAnexoBase anexoHr = new CampanhaAnexoBase(campanha, loteHr, "HR", null, "SISTEMA");

        when(campanhaAnexoBaseRepository.findByCampanhaIdAndDesanexadoEmIsNullOrderByAnexadoEmDesc(10L))
                .thenReturn(List.of(anexoHr));
        when(envioArquivoRepository.findLotesConcluidosValidos("HR", null))
                .thenReturn(List.of(loteHr));

        Registration colabSemCargo = new Registration();
        colabSemCargo.setRegistration("MAT-SEMCARGO");
        colabSemCargo.setPosition(null);

        Sale sale = new Sale();
        sale.setRegistration(colabSemCargo);
        sale.setSaleDate(LocalDate.of(2026, 7, 20));
        sale.setValue(new BigDecimal("500.00"));

        when(saleRepository.findVendasPorPeriodoComRelacionamentos(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(sale));

        CoberturaCampanhaResponseDTO resp = service.diagnosticarCobertura(10L);

        CoberturaCompetenciaDTO comp07 = resp.competencias().get(0);
        assertThat(comp07.rh().coberto()).isFalse();
        assertThat(comp07.rh().status()).isEqualTo("COLABORADOR_SEM_CARGO");
        assertThat(comp07.rh().colaboradoresSemCargo()).contains("MAT-SEMCARGO");
    }

    @Test
    @DisplayName("Vigência contratual fora da venda gera aviso e NÃO bloqueia fechamento")
    void deveGerarAvisoNaoBloqueanteParaDataForaDaVigenciaContratual() {
        when(campanhaRepository.findById(10L)).thenReturn(Optional.of(campanha));

        EnvioArquivo loteHr = new EnvioArquivo();
        loteHr.setId(10L);
        loteHr.setReportId(10010L);
        loteHr.setTipoBase("HR");
        loteHr.setStatus(ImportStatus.CONCLUIDO.name());

        CampanhaAnexoBase anexoHr = new CampanhaAnexoBase(campanha, loteHr, "HR", null, "SISTEMA");

        when(campanhaAnexoBaseRepository.findByCampanhaIdAndDesanexadoEmIsNullOrderByAnexadoEmDesc(10L))
                .thenReturn(List.of(anexoHr));
        when(envioArquivoRepository.findLotesConcluidosValidos("HR", null))
                .thenReturn(List.of(loteHr));

        Position pos = new Position();
        pos.setCode(1);
        Registration colab = new Registration();
        colab.setRegistration("MAT-AVISO");
        colab.setPosition(pos);
        colab.setAdmissDate(LocalDate.of(2026, 7, 25)); // Venda foi em 2026-07-20

        Sale sale = new Sale();
        sale.setRegistration(colab);
        sale.setSaleDate(LocalDate.of(2026, 7, 20));
        sale.setValue(new BigDecimal("500.00"));

        when(saleRepository.findVendasPorPeriodoComRelacionamentos(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(sale));

        CoberturaCampanhaResponseDTO resp = service.diagnosticarCobertura(10L);

        CoberturaCompetenciaDTO comp07 = resp.competencias().get(0);
        assertThat(comp07.rh().coberto()).isTrue();
        assertThat(comp07.rh().avisosVigenciaContratual()).hasSize(1);
        assertThat(comp07.rh().avisosVigenciaContratual().get(0).matricula()).isEqualTo("MAT-AVISO");
    }

    @Test
    @DisplayName("Pendência de matrícula não cadastrada no lote SALES sem aceite bloqueia RH e Vendas; com aceite, libera ambos")
    void pendenciaMatriculaSemAceiteBloqueiaVendasERhEComAceiteLiberaAmbos() {
        when(campanhaRepository.findById(10L)).thenReturn(Optional.of(campanha));

        // Anexos de HR e SALES
        EnvioArquivo loteHr = new EnvioArquivo();
        loteHr.setId(10L);
        loteHr.setReportId(10010L);
        loteHr.setTipoBase("HR");
        loteHr.setStatus(ImportStatus.CONCLUIDO.name());

        EnvioArquivo loteSales = new EnvioArquivo();
        loteSales.setId(20L);
        loteSales.setReportId(10020L);
        loteSales.setTipoBase("SALES");
        loteSales.setCompetencia("2026-07");
        loteSales.setStatus(ImportStatus.CONCLUIDO.name());

        CampanhaAnexoBase anexoHr = new CampanhaAnexoBase(campanha, loteHr, "HR", null, "SISTEMA");
        CampanhaAnexoBase anexoSales = new CampanhaAnexoBase(campanha, loteSales, "SALES", "2026-07", "SISTEMA");

        when(campanhaAnexoBaseRepository.findByCampanhaIdAndDesanexadoEmIsNullOrderByAnexadoEmDesc(10L))
                .thenReturn(List.of(anexoHr, anexoSales));
        when(envioArquivoRepository.findLotesConcluidosValidos("HR", null)).thenReturn(List.of(loteHr));
        when(envioArquivoRepository.findLotesConcluidosValidos("SALES", "2026-07")).thenReturn(List.of(loteSales));

        InconsistenciaImportacao incMatricula = new InconsistenciaImportacao(
                loteSales, 45, "matricula", "Matrícula não cadastrada: 998877", "ADVERTENCIA"
        );
        incMatricula.setEnvio(loteSales);

        when(inconsistenciaRepository.findByEnvioIdIn(List.of(20L))).thenReturn(List.of(incMatricula));

        // 1. Cenário sem aceite: bloqueia
        when(aceitePendenciaProvider.consultarAceite(eq(10L), eq(20L), eq(incMatricula)))
                .thenReturn(StatusAceitePendenciaDTO.naoAceita());

        CoberturaCampanhaResponseDTO respSemAceite = service.diagnosticarCobertura(10L);
        CoberturaCompetenciaDTO comp07 = respSemAceite.competencias().get(0);

        assertThat(comp07.vendas().coberto()).isFalse();
        assertThat(comp07.vendas().status()).isEqualTo("COM_PENDENCIAS_NAO_ACEITAS");
        assertThat(comp07.rh().coberto()).isFalse();
        assertThat(comp07.rh().status()).isEqualTo("PENDENCIA_MATRICULA_EM_VENDAS");
        assertThat(comp07.rh().pendenciasMatriculaReferenciadasEmVendas()).isEqualTo(1L);
        // Total de pendências não aceitas conta apenas 1 vez (não duplica em RH)
        assertThat(comp07.totalPendenciasNaoAceitas()).isEqualTo(1L);

        // 2. Cenário com aceite concedido (BACK-7): libera ambos simultaneamente
        when(aceitePendenciaProvider.consultarAceite(eq(10L), eq(20L), eq(incMatricula)))
                .thenReturn(StatusAceitePendenciaDTO.aceita("gestor@empresa.com", OffsetDateTime.now(), "Risco aceito"));

        CoberturaCampanhaResponseDTO respComAceite = service.diagnosticarCobertura(10L);
        CoberturaCompetenciaDTO comp07Liberada = respComAceite.competencias().get(0);

        assertThat(comp07Liberada.vendas().coberto()).isTrue();
        assertThat(comp07Liberada.rh().coberto()).isTrue();
        assertThat(comp07Liberada.rh().pendenciasMatriculaReferenciadasEmVendas()).isEqualTo(0L);
        assertThat(comp07Liberada.totalPendenciasNaoAceitas()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Competência sem vendas elegíveis retorna taxasBase com status SEM_VENDAS_ELEGIVEIS e coberto = true")
    void deveRetornarSemVendasElegiveisQuandoNaoHouverVendasDoPublico() {
        when(campanhaRepository.findById(10L)).thenReturn(Optional.of(campanha));

        EnvioArquivo loteComiss = new EnvioArquivo();
        loteComiss.setId(30L);
        loteComiss.setReportId(10030L);
        loteComiss.setTipoBase("COMISSIONS");
        loteComiss.setCompetencia("2026-07");
        loteComiss.setStatus(ImportStatus.CONCLUIDO.name());

        CampanhaAnexoBase anexoComiss = new CampanhaAnexoBase(campanha, loteComiss, "COMISSIONS", "2026-07", "SISTEMA");

        when(campanhaAnexoBaseRepository.findByCampanhaIdAndDesanexadoEmIsNullOrderByAnexadoEmDesc(10L))
                .thenReturn(List.of(anexoComiss));
        when(envioArquivoRepository.findLotesConcluidosValidos("COMISSIONS", "2026-07"))
                .thenReturn(List.of(loteComiss));

        // Nenhuma venda
        when(saleRepository.findVendasPorPeriodoComRelacionamentos(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        CoberturaCampanhaResponseDTO resp = service.diagnosticarCobertura(10L);

        CoberturaCompetenciaDTO comp07 = resp.competencias().get(0);
        assertThat(comp07.taxasBase().status()).isEqualTo("SEM_VENDAS_ELEGIVEIS");
        assertThat(comp07.taxasBase().coberto()).isTrue();
    }

    @Test
    @DisplayName("Anexar lote: rejeita se status não for CONCLUIDO, se estiver excluído ou fora da vigência")
    void deveValidarRestricoesAoAnexarLote() {
        when(campanhaRepository.findById(10L)).thenReturn(Optional.of(campanha));

        EnvioArquivo loteFalhou = new EnvioArquivo();
        loteFalhou.setReportId(1L);
        loteFalhou.setStatus(ImportStatus.FALHOU.name());
        when(envioArquivoRepository.findByReportIdForUpdate(1L)).thenReturn(Optional.of(loteFalhou));

        assertThatThrownBy(() -> service.anexarBase(10L, new AnexarBaseCampanhaRequestDTO(1L, "autor")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("status é 'FALHOU'");

        EnvioArquivo loteExcluido = new EnvioArquivo();
        loteExcluido.setReportId(2L);
        loteExcluido.setStatus(ImportStatus.CONCLUIDO.name());
        loteExcluido.setExcluidoEm(OffsetDateTime.now());
        when(envioArquivoRepository.findByReportIdForUpdate(2L)).thenReturn(Optional.of(loteExcluido));

        assertThatThrownBy(() -> service.anexarBase(10L, new AnexarBaseCampanhaRequestDTO(2L, "autor")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já foi excluído");

        EnvioArquivo loteForaVigencia = new EnvioArquivo();
        loteForaVigencia.setReportId(3L);
        loteForaVigencia.setStatus(ImportStatus.CONCLUIDO.name());
        loteForaVigencia.setTipoBase("SALES");
        loteForaVigencia.setCompetencia("2026-11"); // Fora de 2026-07 a 2026-08
        when(envioArquivoRepository.findByReportIdForUpdate(3L)).thenReturn(Optional.of(loteForaVigencia));

        assertThatThrownBy(() -> service.anexarBase(10L, new AnexarBaseCampanhaRequestDTO(3L, "autor")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não intersecta a vigência");
    }
}
