package com.bugbusters.backend.review;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.campanha.*;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.preparacao.*;
import com.bugbusters.backend.dto.preparacao.impacto.PreviaImpactoCorrecaoDTO;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.importbase.reader.PreparacaoExcelReader;
import com.bugbusters.backend.importbase.reader.PreparacaoExcelReader.LinhaLidaPreparacao;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.service.CampanhaService;
import com.bugbusters.backend.service.preparacao.PreparacaoBaseService;
import com.bugbusters.backend.service.preparacao.PropagacaoImpactoCorrecaoService;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;
import com.bugbusters.backend.support.CampaignDataCleanup;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class Sprint3ReviewScenariosTest {

    @Autowired
    private PreparacaoBaseService preparacaoBaseService;

    @Autowired
    private CampanhaService campanhaService;

    @Autowired
    private PropagacaoImpactoCorrecaoService propagacaoImpactoCorrecaoService;

    @Autowired
    private PreparacaoExcelReader preparacaoExcelReader;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private CampanhaVersaoRepository campanhaVersaoRepository;

    @Autowired
    private RegraRepository regraRepository;

    @Autowired
    private LotePreparacaoRepository lotePreparacaoRepository;

    @Autowired
    private LinhaPreparacaoRepository linhaPreparacaoRepository;

    @Autowired
    private EnvioArquivoRepository envioArquivoRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private SimulacaoRepository simulacaoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Campanha campanha;
    private Brand brand;
    private Store store;
    private Position position;
    private Registration colaborador;

    @BeforeEach
    void setUp() {
        CampaignDataCleanup.clear(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM tb_resultado_calculo");
        jdbcTemplate.update("DELETE FROM tb_simulacao");
        jdbcTemplate.update("DELETE FROM tb_sales");
        jdbcTemplate.update("DELETE FROM tb_registration");
        jdbcTemplate.update("DELETE FROM tb_inconsistencia_importacao");
        jdbcTemplate.update("DELETE FROM tb_envio_arquivo");
        try {
            jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS seq_envio_arquivo_report_id START WITH 1000000 INCREMENT BY 1");
        } catch (Exception ignored) {}

        brand = brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("Marca Teste");
            return brandRepository.save(b);
        });

        store = storeRepository.findByCode(20).orElseGet(() -> {
            Store s = new Store();
            s.setCode(20);
            s.setDescription("Loja Teste");
            return storeRepository.save(s);
        });

        position = positionRepository.findByCode(30).orElseGet(() -> {
            Position p = new Position();
            p.setCode(30);
            p.setDescription("Vendedor");
            return positionRepository.save(p);
        });

        colaborador = registrationRepository.findByRegistration("VEND01").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("VEND01");
            r.setStore(store);
            r.setPosition(position);
            r.setAdmissDate(LocalDate.of(2025, 1, 1));
            r.setVersao(1);
            r.setOrigemUltimaAlteracao("TESTE");
            return registrationRepository.save(r);
        });

        campanha = campanhaRepository.save(new Campanha(
                "Campanha Sprint 3 Test",
                "Descrição de teste",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        ));
    }

    // =========================================================================
    // CENÁRIO 1: Correção de valorVenda via preparação de lote existente
    // =========================================================================
    @Test
    @DisplayName("Cenário 1: Correção de valorVenda via preparação de lote existente não duplica venda e vincula substituidoPorId")
    void cenario1_correcaoValorVendaViaPreparacaoDeLoteExistente_naoDuplicaVendaEAtualizaEnvio() throws Exception {
        // 1. Cria e efetiva um lote inicial de vendas com 1 venda de 100.00
        MockMultipartFile fileOriginal = criarExcelSales(new Object[][]{
                {"2026-05-10", 10, "Marca Teste", 20, "Loja Teste", "VEND01", 100.00}
        });

        LotePreparacaoResponseDTO lote1DTO = preparacaoBaseService.iniciarPreparacaoUpload(
                campanha.getId(), fileOriginal, "SALES", "2026-05", "MANUAL", null, "admin");

        EfetivacaoPreparacaoResponseDTO efetivacao1 = preparacaoBaseService.efetivarLote(
                campanha.getId(), lote1DTO.id(),
                new EfetivarLotePreparacaoRequestDTO("admin", "Primeira efetivação", true));

        List<Sale> vendasIniciais = saleRepository.findAll();
        assertThat(vendasIniciais).hasSize(1);
        Sale vendaOriginal = vendasIniciais.get(0);
        assertThat(vendaOriginal.getValue()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(vendaOriginal.getVersao()).isEqualTo(1);

        Long reportId1 = efetivacao1.reportIdGerado();
        EnvioArquivo envio1 = envioArquivoRepository.findByReportId(reportId1).orElseThrow();
        assertThat(envio1.getSubstituidoPorId()).isNull();

        // 2. Inicia preparação do lote existente (substituição)
        LotePreparacaoResponseDTO lote2DTO = preparacaoBaseService.iniciarPreparacaoDeLoteExistente(campanha.getId(), reportId1, "admin");
        List<LinhaPreparacao> linhasLote2 = linhaPreparacaoRepository.findByLotePreparacaoIdOrderByNumeroLinhaAsc(lote2DTO.id());
        assertThat(linhasLote2).hasSize(1);
        LinhaPreparacao linhaParaEditar = linhasLote2.get(0);
        // O registroAlvoId deve estar devidamente preenchido com a venda original!
        assertThat(linhaParaEditar.getRegistroAlvoId()).isEqualTo(vendaOriginal.getId());

        // 3. Edita o valor da venda de 100 para 150
        EditarLinhaPreparacaoRequestDTO requestEdicao = new EditarLinhaPreparacaoRequestDTO(
                linhaParaEditar.getVersao(), "MANUAL", "analista", "Correção de valor",
                false, linhaParaEditar.getRegistroAlvoId(), linhaParaEditar.getDataReferencia(),
                linhaParaEditar.getCodMarca(), linhaParaEditar.getDescrMarca(),
                linhaParaEditar.getCodLoja(), linhaParaEditar.getDescrLoja(),
                linhaParaEditar.getMatricula(), null, null, null, null,
                BigDecimal.valueOf(150.00)
        );
        preparacaoBaseService.editarLinha(campanha.getId(), lote2DTO.id(), linhaParaEditar.getNumeroLinha(), requestEdicao);

        // 4. Efetiva o segundo lote
        EfetivacaoPreparacaoResponseDTO efetivacao2 = preparacaoBaseService.efetivarLote(
                campanha.getId(), lote2DTO.id(),
                new EfetivarLotePreparacaoRequestDTO("admin", "Segunda efetivação corrigida", true));

        // 5. Verificações:
        // Continua existindo EXATAMENTE 1 venda em tb_sales!
        List<Sale> vendasFinais = saleRepository.findAll();
        assertThat(vendasFinais).hasSize(1);
        Sale vendaFinal = vendasFinais.get(0);
        assertThat(vendaFinal.getId()).isEqualTo(vendaOriginal.getId());
        assertThat(vendaFinal.getValue()).isEqualByComparingTo(BigDecimal.valueOf(150.00));
        assertThat(vendaFinal.getVersao()).isEqualTo(2);

        // O EnvioArquivo antigo agora tem substituidoPorId apontando para o novo!
        EnvioArquivo envio1Atualizado = envioArquivoRepository.findByReportId(reportId1).orElseThrow();
        EnvioArquivo envio2 = envioArquivoRepository.findByReportId(efetivacao2.reportIdGerado()).orElseThrow();
        assertThat(envio1Atualizado.getSubstituidoPorId()).isEqualTo(envio2.getId());
    }

    // =========================================================================
    // CENÁRIO 2: PATCH /orcamento em campanha ATIVA cria proposta preservando regras
    // =========================================================================
    @Test
    @DisplayName("Cenário 2: PATCH /orcamento em campanha ATIVA cria proposta preservando todas as regras vigentes")
    void cenario2_patchOrcamentoEmCampanhaAtiva_criaPropostaComTodasRegrasPreservadas() {
        // 1. Cria uma versão vigente com regras para a campanha
        CampanhaVersao vigente = criarVersao(campanha, 1, StatusVersaoCampanha.PUBLICADA);
        vigente.setOrcamento(BigDecimal.valueOf(10000.00));
        vigente.setOrcamentoConfirmado(true);
        vigente = campanhaVersaoRepository.save(vigente);
        campanha.setVersaoVigente(vigente);
        campanha.setEstado(EstadoCampanha.ATIVA);
        campanha.setOrcamento(BigDecimal.valueOf(10000.00));
        campanha.setOrcamentoConfirmado(true);
        campanha = campanhaRepository.save(campanha);

        Regra regra1 = new Regra();
        regra1.setCampanha(campanha);
        regra1.setCampanhaVersao(vigente);
        regra1.setNome("Regra Vigente 1");
        regra1.setBlocoId("bloco-1");
        regra1.setStatus(StatusRegra.ATIVA);
        regra1.setTipoOperacao(TipoOperacaoBase.DEFINIR_TAXA);
        regra1.setTaxa(BigDecimal.valueOf(5.0));
        regra1.setDataInicio(campanha.getDataInicio());
        regra1.setDataFim(campanha.getDataFim());
        regraRepository.save(regra1);

        assertThat(regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(vigente.getId())).hasSize(1);

        // 2. Executa confirmação/alteração de orçamento
        ConfirmarOrcamentoCampanhaRequest request = new ConfirmarOrcamentoCampanhaRequest(
                BigDecimal.valueOf(15000.00), null, "Ajuste de orçamento aprovado", "gerente", 1);
        OrcamentoCampanhaResponse response = campanhaService.confirmarOuAlterarOrcamento(campanha.getId(), request);

        assertThat(response.orcamento()).isEqualByComparingTo(BigDecimal.valueOf(15000.00));
        assertThat(response.orcamentoConfirmado()).isTrue();

        Campanha campanhaAtualizada = campanhaRepository.findById(campanha.getId()).orElseThrow();
        Long propostaId = campanhaAtualizada.getVersaoProposta() != null ? campanhaAtualizada.getVersaoProposta().getId() : null;
        assertThat(propostaId).isNotNull();
        CampanhaVersao proposta = campanhaVersaoRepository.findById(propostaId).orElseThrow();
        assertThat(proposta.getStatus()).isEqualTo(StatusVersaoCampanha.PROPOSTA);

        // Verifica que as regras foram clonadas para a nova proposta!
        List<Regra> regrasProposta = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(proposta.getId());
        assertThat(regrasProposta).hasSize(1);
        assertThat(regrasProposta.get(0).getNome()).isEqualTo("Regra Vigente 1");
    }

    // =========================================================================
    // CENÁRIO 3: Excel de RH com dataDemissao em fórmula (=DATE(...))
    // =========================================================================
    @Test
    @DisplayName("Cenário 3: Excel de RH com dataDemissao em fórmula é avaliada corretamente sem apagar o dado")
    void cenario3_excelDeRhComFormulaEmDataDemissao_avaliaCorretamenteENaoApagaData() throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("dataReferencia");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("2026-05-01");
            row.createCell(1).setCellValue(10);
            row.createCell(2).setCellValue("Marca Alpha");
            row.createCell(3).setCellValue(20);
            row.createCell(4).setCellValue("Loja Centro");
            row.createCell(5).setCellValue("MATR-FORMULA");
            row.createCell(6).setCellValue("2025-01-01");

            // Coluna 7: dataDemissao com fórmula DATE(2026, 5, 31)
            Cell cellDemissao = row.createCell(7);
            cellDemissao.setCellFormula("DATE(2026,5,31)");

            row.createCell(8).setCellValue(30);
            row.createCell(9).setCellValue("Vendedor");

            workbook.write(output);
            byte[] bytes = output.toByteArray();

            List<LinhaLidaPreparacao> linhasLidas = preparacaoExcelReader.lerLinhas(new ByteArrayInputStream(bytes), "HR");
            assertThat(linhasLidas).hasSize(1);

            LinhaLidaPreparacao l = linhasLidas.get(0);
            assertThat(l.dataDemissao()).isEqualTo(LocalDate.of(2026, 5, 31));
            assertThat(l.errosDeFormato()).isEmpty();
        }
    }

    // =========================================================================
    // CENÁRIO 4: Dois POST /efetivar concorrentes / repetidos
    // =========================================================================
    @Test
    @DisplayName("Cenário 4: Dois POST /efetivar no mesmo lote: o primeiro tem sucesso e o segundo retorna 409 Conflict")
    void cenario4_doisEfetivarConcorrentes_primeiroTemSucessoESegundoRetorna409Conflict() throws Exception {
        MockMultipartFile file = criarExcelHr(new Object[][]{
                {"2026-05-01", 10, "Marca Alpha", 20, "Loja Centro", "MATR-LOCK", "2025-01-01", null, 30, "Consultor"}
        });

        LotePreparacaoResponseDTO loteDTO = preparacaoBaseService.iniciarPreparacaoUpload(
                campanha.getId(), file, "HR", "2026-05", "MANUAL", null, "admin");

        // 1ª efetivação: deve passar com sucesso
        EfetivacaoPreparacaoResponseDTO efet1 = preparacaoBaseService.efetivarLote(
                campanha.getId(), loteDTO.id(),
                new EfetivarLotePreparacaoRequestDTO("admin", "Primeira efetivação", true));
        assertThat(efet1.status()).isEqualTo("EFETIVADO");

        // 2ª efetivação no mesmo lote: deve lançar 409 Conflict
        assertThatThrownBy(() -> preparacaoBaseService.efetivarLote(
                campanha.getId(), loteDTO.id(),
                new EfetivarLotePreparacaoRequestDTO("admin", "Segunda efetivação", true)))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("Apenas lotes com status EM_PREPARACAO podem ser efetivados");

        // Verifica que tb_envio_arquivo não foi duplicado
        List<EnvioArquivo> envios = envioArquivoRepository.findAll();
        assertThat(envios).hasSize(1);
    }

    // =========================================================================
    // CENÁRIO 5: Lote de RH apenas com admissões não invalida aprovação de código
    // =========================================================================
    @Test
    @DisplayName("Cenário 5: Lote de RH apenas com novas admissões não invalida aprovações de código de campanhas existentes")
    void cenario5_loteRhApenasComAdmissoes_naoInvalidaAprovacoesDeCodigoDeCampanhas() throws Exception {
        // Campanha existente com regra e código aprovado
        CampanhaVersao versao = criarVersao(campanha, 1, StatusVersaoCampanha.PUBLICADA);
        versao = campanhaVersaoRepository.save(versao);
        campanha.setVersaoVigente(versao);
        campanha.setEstado(EstadoCampanha.ATIVA);
        campanha = campanhaRepository.save(campanha);

        Regra regra = new Regra();
        regra.setCampanha(campanha);
        regra.setCampanhaVersao(versao);
        regra.setNome("Regra Comercial");
        regra.setCodMarca(10);
        regra.setCodLoja(20);
        regra.setCodCargo(30);
        regra.setStatus(StatusRegra.ATIVA);
        regra.setStatusAprovacaoCodigo(StatusAprovacaoCodigo.APROVADO);
        regra.setDataInicio(campanha.getDataInicio());
        regra.setDataFim(campanha.getDataFim());
        regraRepository.save(regra);

        // Lote de RH contendo admissão de nova matrícula sem nenhuma venda
        MockMultipartFile file = criarExcelHr(new Object[][]{
                {"2026-05-01", 10, "Marca Alpha", 20, "Loja Centro", "NOVO-COLAB", "2026-05-01", null, 30, "Consultor"}
        });

        LotePreparacaoResponseDTO loteDTO = preparacaoBaseService.iniciarPreparacaoUpload(
                campanha.getId(), file, "HR", null, "MANUAL", null, "admin");

        LotePreparacao lote = lotePreparacaoRepository.findById(loteDTO.id()).orElseThrow();
        List<LinhaPreparacao> linhas = linhaPreparacaoRepository.findByLotePreparacaoIdOrderByNumeroLinhaAsc(lote.getId());

        PreviaImpactoCorrecaoDTO previa = propagacaoImpactoCorrecaoService.calcularPreviaImpacto(lote, linhas);

        // Nenhuma campanha, aprovação de código ou cálculo deve ser afetado por simples admissões sem vendas
        assertThat(previa.campanhasAfetadas()).isEmpty();
        assertThat(previa.aprovacoesCodigoAfetadas()).isEmpty();
        assertThat(previa.calculosAfetados()).isEmpty();
        assertThat(previa.possuiImpactos()).isFalse();
    }

    // =========================================================================
    // CENÁRIO 6: POST /encaminhar-gerente com simulação PENDENTE ou com impedimentos
    // =========================================================================
    @Test
    @DisplayName("Cenário 6: Encaminhar ao gerente com simulação PENDENTE ou com impedimentos lança 400 Bad Request")
    void cenario6_encaminharAoGerente_bloqueiaComSimulacaoPendenteOuComImpedimentos() {
        CampanhaVersao versao = criarVersao(campanha, 1, StatusVersaoCampanha.PROPOSTA);
        versao.setOrcamento(BigDecimal.valueOf(50000.00));
        versao.setOrcamentoConfirmado(true);
        versao = campanhaVersaoRepository.save(versao);
        campanha.setVersaoProposta(versao);
        campanha.setOrcamento(BigDecimal.valueOf(50000.00));
        campanha.setOrcamentoConfirmado(true);
        campanha = campanhaRepository.save(campanha);

        // 1. Simulação PENDENTE
        Simulacao simPendente = criarSimulacao(campanha, versao, StatusAceiteSimulacao.PENDENTE, 0);

        assertThatThrownBy(() -> campanhaService.encaminharAoGerente(campanha.getId(), new EncaminharCampanhaGerenteRequest(simPendente.getId(), "analista", "ok", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Apenas simulações com status ACEITA podem ser encaminhadas");

        // 2. Simulação ACEITA mas com vendas com impedimento
        Simulacao simComImpedimento = criarSimulacao(campanha, versao, StatusAceiteSimulacao.ACEITA, 3);

        assertThatThrownBy(() -> campanhaService.encaminharAoGerente(campanha.getId(), new EncaminharCampanhaGerenteRequest(simComImpedimento.getId(), "analista", "ok", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("contém 3 venda(s) com impedimento");
    }

    // =========================================================================
    // CENÁRIO 7: PATCH /linhas/{n} com versão desatualizada ou ausente
    // =========================================================================
    @Test
    @DisplayName("Cenário 7: PATCH /linhas/{n} com versão null ou desatualizada retorna 409 Conflict")
    void cenario7_editarLinhaComVersaoInvalidaOuAusente_retorna409Conflict() throws Exception {
        MockMultipartFile file = criarExcelHr(new Object[][]{
                {"2026-05-01", 10, "Marca Alpha", 20, "Loja Centro", "MATR-OPT-LOCK", "2025-01-01", null, 30, "Consultor"}
        });

        LotePreparacaoResponseDTO loteDTO = preparacaoBaseService.iniciarPreparacaoUpload(
                campanha.getId(), file, "HR", "2026-05", "MANUAL", null, "admin");

        LinhaPreparacao linha = linhaPreparacaoRepository.findByLotePreparacaoIdOrderByNumeroLinhaAsc(loteDTO.id()).get(0);
        assertThat(linha.getVersao()).isEqualTo(0L);

        // 1. Envio com versão null
        EditarLinhaPreparacaoRequestDTO reqNull = new EditarLinhaPreparacaoRequestDTO(
                null, "MANUAL", "admin", "teste", false, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> preparacaoBaseService.editarLinha(campanha.getId(), loteDTO.id(), linha.getNumeroLinha(), reqNull))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("a versão enviada (null) é inválida ou difere da versão atual (0)");

        // 2. Envio com versão desatualizada (99L)
        EditarLinhaPreparacaoRequestDTO reqDesatualizada = new EditarLinhaPreparacaoRequestDTO(
                99L, "MANUAL", "admin", "teste", false, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> preparacaoBaseService.editarLinha(campanha.getId(), loteDTO.id(), linha.getNumeroLinha(), reqDesatualizada))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("a versão enviada (99) é inválida ou difere da versão atual (0)");

        // 3. Edição válida incrementa a versão e grava versaoNova > versaoAnterior no histórico de auditoria
        EditarLinhaPreparacaoRequestDTO reqValida = new EditarLinhaPreparacaoRequestDTO(
                0L, "MANUAL", "admin", "Ajuste de cargo", false, null, null, null, null, null, null, null, null, null, 30, "Consultor Senior", null);
        preparacaoBaseService.editarLinha(campanha.getId(), loteDTO.id(), linha.getNumeroLinha(), reqValida);

        List<HistoricoEdicaoLinhaResponseDTO> historico = preparacaoBaseService.consultarHistoricoEdicaoLinha(campanha.getId(), loteDTO.id(), linha.getNumeroLinha());
        assertThat(historico).hasSize(1);
        assertThat(historico.get(0).versaoAnterior()).isEqualTo(0L);
        assertThat(historico.get(0).versaoNova()).isEqualTo(1L);
    }

    // =========================================================================
    // CENÁRIO 8: POST /aprovacao-codigo rejeita aprovado null e hash divergente
    // =========================================================================
    @Test
    @DisplayName("Cenário 8: POST /aprovacao-codigo com aprovado null lança 400 e hash divergente lança 409 Conflict")
    void cenario8_aprovacaoCodigo_rejeitaPayloadSemAprovadoOuComHashDivergente() {
        CampanhaVersao versao = criarVersao(campanha, 1, StatusVersaoCampanha.PROPOSTA);
        versao = campanhaVersaoRepository.save(versao);
        campanha.setVersaoProposta(versao);
        campanha = campanhaRepository.save(campanha);

        Regra regra = new Regra();
        regra.setCampanha(campanha);
        regra.setCampanhaVersao(versao);
        regra.setNome("Regra Python");
        regra.setStatus(StatusRegra.DRAFT);
        regra.setPythonEquivalente("valor * 0.05");
        regra.setDataInicio(campanha.getDataInicio());
        regra.setDataFim(campanha.getDataFim());
        regraRepository.save(regra);

        // 1. aprovado = null -> BusinessException (400 Bad Request)
        assertThatThrownBy(() -> campanhaService.aprovarCodigoCampanha(campanha.getId(), new AprovacaoCodigoCampanhaRequest(null, "motivo", "autor")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("O campo 'aprovado' é obrigatório");

        // 2. hashArtefatosCodigo divergente -> ResourceConflictException (409 Conflict)
        assertThatThrownBy(() -> campanhaService.aprovarCodigoCampanha(campanha.getId(), new AprovacaoCodigoCampanhaRequest(true, "motivo", "autor", "hash-divergente-incorreto")))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("diverge do hash atual calculado");
    }

    // =========================================================================
    // CENÁRIO 9: Download do arquivo original preservado com open-in-view=false
    // =========================================================================
    @Test
    @DisplayName("Cenário 9: Download de arquivo original preservado retorna DTO com bytes sem LazyInitializationException")
    void cenario9_downloadArquivoOriginal_retornaBytesSemLazyInitializationException() throws Exception {
        MockMultipartFile file = criarExcelHr(new Object[][]{
                {"2026-05-01", 10, "Marca Alpha", 20, "Loja Centro", "MATR-DOWN", "2025-01-01", null, 30, "Consultor"}
        });

        LotePreparacaoResponseDTO loteDTO = preparacaoBaseService.iniciarPreparacaoUpload(
                campanha.getId(), file, "HR", "2026-05", "MANUAL", null, "admin");

        // Executa o download: o service deve retornar o DTO com o byte[] já carregado
        ArquivoOriginalDownloadDTO download = preparacaoBaseService.baixarArquivoOriginal(campanha.getId(), loteDTO.id());

        assertThat(download).isNotNull();
        assertThat(download.nomeArquivo()).isEqualTo("base_rh.xlsx");
        assertThat(download.conteudo()).isNotEmpty();
        assertThat(download.tamanhoBytes()).isGreaterThan(0);
    }

    // =========================================================================
    // MÉTODOS AUXILIARES
    // =========================================================================
    private Simulacao criarSimulacao(Campanha c, CampanhaVersao v, StatusAceiteSimulacao status, int vendasComImpedimento) {
        Simulacao s = new Simulacao();
        s.setProtocolo(UUID.randomUUID());
        s.setExecutadoEm(java.time.OffsetDateTime.now());
        s.setCompetencia("2026-05");
        s.setCampanha(c);
        s.setCampanhaVersao(v);
        s.setTituloCampanha(c.getTitulo());
        s.setDataInicioCampanha(c.getDataInicio());
        s.setDataFimCampanha(c.getDataFim());
        s.setDiasInclusivos(31);
        s.setFatorProjecao(BigDecimal.ONE);
        s.setOrigemDados("TESTE");
        s.setStatusAceite(status);
        s.setPremissasSnapshot("{}");
        s.setTotalVendasRecortadas(10);
        s.setValorTotalRecortado(BigDecimal.valueOf(1000.00));
        s.setTotalComissaoBase(BigDecimal.valueOf(100.00));
        s.setTotalComissaoProposta(BigDecimal.valueOf(120.00));
        s.setDeltaComissao(BigDecimal.valueOf(20.00));
        s.setDeltaPercentual(BigDecimal.valueOf(20.00));
        s.setTaxaMediaEfetivaBase(BigDecimal.valueOf(0.1000));
        s.setTaxaMediaEfetivaProposta(BigDecimal.valueOf(0.1200));
        s.setComissaoProjetada(BigDecimal.valueOf(120.00));
        s.setDeltaProjetado(BigDecimal.valueOf(20.00));
        s.setQtdVendasComMudancaFaixa(0);
        s.setQtdVendasComRegraAplicada(10);
        s.setQtdVendasComFallbackBase(0);
        s.setQtdVendasComImpedimento(vendasComImpedimento);

        s.setVendas80(BigDecimal.valueOf(800.00));
        s.setComissaoBase80(BigDecimal.valueOf(80.00));
        s.setComissaoProposta80(BigDecimal.valueOf(96.00));
        s.setDelta80(BigDecimal.valueOf(16.00));
        s.setDeltaPerc80(BigDecimal.valueOf(20.00));
        s.setTaxaMedia80(BigDecimal.valueOf(0.1200));
        s.setProjetada80(BigDecimal.valueOf(96.00));
        s.setDeltaProj80(BigDecimal.valueOf(16.00));
        s.setMudancaFaixa80(0);

        s.setVendas120(BigDecimal.valueOf(1200.00));
        s.setComissaoBase120(BigDecimal.valueOf(120.00));
        s.setComissaoProposta120(BigDecimal.valueOf(144.00));
        s.setDelta120(BigDecimal.valueOf(24.00));
        s.setDeltaPerc120(BigDecimal.valueOf(20.00));
        s.setTaxaMedia120(BigDecimal.valueOf(0.1200));
        s.setProjetada120(BigDecimal.valueOf(144.00));
        s.setDeltaProj120(BigDecimal.valueOf(24.00));
        s.setMudancaFaixa120(0);

        s.setVendas150(BigDecimal.valueOf(1500.00));
        s.setComissaoBase150(BigDecimal.valueOf(150.00));
        s.setComissaoProposta150(BigDecimal.valueOf(180.00));
        s.setDelta150(BigDecimal.valueOf(30.00));
        s.setDeltaPerc150(BigDecimal.valueOf(20.00));
        s.setTaxaMedia150(BigDecimal.valueOf(0.1200));
        s.setProjetada150(BigDecimal.valueOf(180.00));
        s.setDeltaProj150(BigDecimal.valueOf(30.00));
        s.setMudancaFaixa150(0);

        s.setOrcamentoCampanha(BigDecimal.valueOf(50000.00));
        return simulacaoRepository.save(s);
    }

    private CampanhaVersao criarVersao(Campanha c, int numero, StatusVersaoCampanha status) {
        CampanhaVersao v = new CampanhaVersao();
        v.setCampanha(c);
        v.setNumero(numero);
        v.setStatus(status);
        v.setTitulo(c.getTitulo());
        v.setTextoOriginal("Texto original da campanha");
        v.setDataInicio(c.getDataInicio());
        v.setDataFim(c.getDataFim());
        return v;
    }

    private MockMultipartFile criarExcelHr(Object[][] rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("Cabeçalho");
            for (int i = 0; i < rows.length; i++) {
                var r = sheet.createRow(i + 1);
                for (int j = 0; j < rows[i].length; j++) {
                    Object val = rows[i][j];
                    if (val instanceof Number n) {
                        r.createCell(j).setCellValue(n.doubleValue());
                    } else if (val != null) {
                        r.createCell(j).setCellValue(val.toString());
                    }
                }
            }
            workbook.write(output);
            return new MockMultipartFile("arquivo", "base_rh.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private MockMultipartFile criarExcelSales(Object[][] rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("Cabeçalho");
            for (int i = 0; i < rows.length; i++) {
                var r = sheet.createRow(i + 1);
                for (int j = 0; j < rows[i].length; j++) {
                    Object val = rows[i][j];
                    if (val instanceof Number n) {
                        r.createCell(j).setCellValue(n.doubleValue());
                    } else if (val != null) {
                        r.createCell(j).setCellValue(val.toString());
                    }
                }
            }
            workbook.write(output);
            return new MockMultipartFile("arquivo", "base_vendas.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
