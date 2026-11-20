package com.bugbusters.backend.service.preparacao;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.alerta.AlertaEstatisticoResponse;
import com.bugbusters.backend.dto.preparacao.*;
import com.bugbusters.backend.dto.preparacao.impacto.PreviaImpactoCorrecaoDTO;
import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.service.AlertaEstatisticoService;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;
import com.bugbusters.backend.support.CampaignDataCleanup;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class Front6PendenciasBackendIntegrationTest {

    @Autowired private PreparacaoBaseService preparacaoBaseService;
    @Autowired private AlertaEstatisticoService alertaEstatisticoService;
    @Autowired private CampanhaRepository campanhaRepository;
    @Autowired private LotePreparacaoRepository lotePreparacaoRepository;
    @Autowired private LinhaPreparacaoRepository linhaPreparacaoRepository;
    @Autowired private EnvioArquivoRepository envioArquivoRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private PositionRepository positionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Campanha campanhaJulho;
    private Brand brand10;
    private Store store14;
    private Position position100;

    @BeforeEach
    void setUp() {
        alertaEstatisticoService.aguardarAnalisesPendentes(15_000);
        CampaignDataCleanup.clear(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM tb_alerta_estatistico");
        jdbcTemplate.update("DELETE FROM tb_resultado_calculo");
        jdbcTemplate.update("DELETE FROM tb_sales");
        jdbcTemplate.update("DELETE FROM tb_registration");
        jdbcTemplate.update("DELETE FROM tb_inconsistencia_importacao");
        jdbcTemplate.update("DELETE FROM tb_envio_arquivo");

        brand10 = brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("PRETO");
            return brandRepository.save(b);
        });
        store14 = storeRepository.findByCode(14).orElseGet(() -> {
            Store s = new Store();
            s.setCode(14);
            s.setDescription("LOJA 14");
            return storeRepository.save(s);
        });
        position100 = positionRepository.findByCode(100).orElseGet(() -> {
            Position p = new Position();
            p.setCode(100);
            p.setDescription("VENDEDOR LOJA");
            return positionRepository.save(p);
        });

        campanhaJulho = campanhaRepository.save(new Campanha(
                "Campanha Julho 2025", "Teste pendencias FRONT-6",
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 31)
        ));
    }

    @AfterEach
    void tearDown() {
        alertaEstatisticoService.aguardarAnalisesPendentes(15_000);
    }

    private Registration criarColaborador(String matricula) {
        Registration r = new Registration();
        r.setRegistration(matricula);
        r.setStore(store14);
        r.setPosition(position100);
        r.setAdmissDate(LocalDate.of(2024, 1, 10));
        return registrationRepository.save(r);
    }

    @Test
    @DisplayName("Item 1: Listar lotes de preparação aceita filtros nulos e vazios sem erro")
    void item1_listarLotesSemTipoBaseOuStatusNaoFalha() throws Exception {
        criarColaborador("MAT-LISTA");
        MockMultipartFile sales = criarExcelSales(new Object[][]{
                {"2025-07-10", 10, "PRETO", 14, "LOJA 14", "MAT-LISTA", 1500.00}
        });
        preparacaoBaseService.iniciarPreparacaoUpload(
                campanhaJulho.getId(), sales, "SALES", "2025-07", "MANUAL", null, "admin");

        assertThat(preparacaoBaseService.listarLotes(campanhaJulho.getId(), null, null, null)).hasSize(1);
        assertThat(preparacaoBaseService.listarLotes(campanhaJulho.getId(), "", "2025-07", "")).hasSize(1);
        assertThat(preparacaoBaseService.listarLotes(campanhaJulho.getId(), "SALES", "2025-07", "EM_PREPARACAO")).hasSize(1);
    }

    @Test
    @DisplayName("Item 2: Múltiplas rodadas de edição de valor na preparação atualizam vendas sem duplicar nem gerar falsas inconsistências")
    void item2_multiplasRodadasDeEdicaoNaoDuplicamVendasENaoInflamInconsistencias() throws Exception {
        criarColaborador("MATRIC-379");
        criarColaborador("MATRIC-551");

        MockMultipartFile arquivoInicial = criarExcelSales(new Object[][]{
                {"2025-07-10", 10, "PRETO", 14, "LOJA 14", "MATRIC-379", 5870.00},
                {"2025-07-11", 10, "PRETO", 14, "LOJA 14", "MATRIC-551", 1200.00}
        });

        LotePreparacaoResponseDTO loteInicial = preparacaoBaseService.iniciarPreparacaoUpload(
                campanhaJulho.getId(), arquivoInicial, "SALES", "2025-07", "MANUAL", null, "admin");
        EfetivacaoPreparacaoResponseDTO efInicial = preparacaoBaseService.efetivarLote(
                campanhaJulho.getId(), loteInicial.id(),
                new EfetivarLotePreparacaoRequestDTO("admin", "Carga inicial", true));

        assertThat(saleRepository.findAll()).hasSize(2);

        // 1ª correção: editar MATRIC-379 de 5.870 para 500.000
        LotePreparacaoResponseDTO loteCorr1 = preparacaoBaseService.iniciarPreparacaoDeLoteExistente(
                campanhaJulho.getId(), efInicial.reportIdGerado(), "admin");
        List<LinhaPreparacao> linhasCorr1 = linhaPreparacaoRepository.findByLotePreparacaoIdOrderByNumeroLinhaAsc(loteCorr1.id());
        LinhaPreparacao linha379 = linhasCorr1.stream().filter(l -> "MATRIC-379".equals(l.getMatricula())).findFirst().orElseThrow();
        assertThat(linha379.getRegistroAlvoId()).isNotNull();

        preparacaoBaseService.editarLinha(campanhaJulho.getId(), loteCorr1.id(), linha379.getNumeroLinha(),
                new EditarLinhaPreparacaoRequestDTO(
                        linha379.getVersao(), "MANUAL", "admin", "Ajuste 379", false,
                        linha379.getRegistroAlvoId(), linha379.getDataReferencia(),
                        linha379.getCodMarca(), linha379.getDescrMarca(),
                        linha379.getCodLoja(), linha379.getDescrLoja(),
                        linha379.getMatricula(), null, null, null, null,
                        BigDecimal.valueOf(500000.00)
                ));

        PreviaImpactoCorrecaoDTO previa1 = preparacaoBaseService.consultarPreviaImpacto(campanhaJulho.getId(), loteCorr1.id());
        assertThat(previa1.resumoAlteracoesBase().registrosNovos()).isZero();
        assertThat(previa1.resumoAlteracoesBase().registrosAtualizados()).isEqualTo(1);
        assertThat(previa1.resumoAlteracoesBase().registrosInalterados()).isEqualTo(1);

        EfetivacaoPreparacaoResponseDTO efCorr1 = preparacaoBaseService.efetivarLote(
                campanhaJulho.getId(), loteCorr1.id(),
                new EfetivarLotePreparacaoRequestDTO("admin", "1a correcao", true));

        assertThat(efCorr1.registrosInseridos()).isZero();
        assertThat(efCorr1.registrosAtualizados()).isEqualTo(1);
        assertThat(saleRepository.findAll()).hasSize(2);

        EnvioArquivo envioCorr1 = envioArquivoRepository.findByReportId(efCorr1.reportIdGerado()).orElseThrow();
        assertThat(envioCorr1.getTotalLinhas()).isEqualTo(2);
        assertThat(envioCorr1.getLinhasValidas()).isEqualTo(2);

        // 2ª correção aberta a partir do envio da 1ª correção: editar MATRIC-551 para 300.000
        LotePreparacaoResponseDTO loteCorr2 = preparacaoBaseService.iniciarPreparacaoDeLoteExistente(
                campanhaJulho.getId(), efCorr1.reportIdGerado(), "admin");
        List<LinhaPreparacao> linhasCorr2 = linhaPreparacaoRepository.findByLotePreparacaoIdOrderByNumeroLinhaAsc(loteCorr2.id());
        LinhaPreparacao linha379Rodada2 = linhasCorr2.stream().filter(l -> "MATRIC-379".equals(l.getMatricula())).findFirst().orElseThrow();
        LinhaPreparacao linha551Rodada2 = linhasCorr2.stream().filter(l -> "MATRIC-551".equals(l.getMatricula())).findFirst().orElseThrow();

        // MATRIC-379 deve refletir o valor já corrigido (500.000) e preservar registroAlvoId
        assertThat(linha379Rodada2.getValorVenda()).isEqualByComparingTo(BigDecimal.valueOf(500000.00));
        assertThat(linha379Rodada2.getRegistroAlvoId()).isNotNull();
        assertThat(linha551Rodada2.getRegistroAlvoId()).isNotNull();

        preparacaoBaseService.editarLinha(campanhaJulho.getId(), loteCorr2.id(), linha551Rodada2.getNumeroLinha(),
                new EditarLinhaPreparacaoRequestDTO(
                        linha551Rodada2.getVersao(), "MANUAL", "admin", "Ajuste 551", false,
                        linha551Rodada2.getRegistroAlvoId(), linha551Rodada2.getDataReferencia(),
                        linha551Rodada2.getCodMarca(), linha551Rodada2.getDescrMarca(),
                        linha551Rodada2.getCodLoja(), linha551Rodada2.getDescrLoja(),
                        linha551Rodada2.getMatricula(), null, null, null, null,
                        BigDecimal.valueOf(300000.00)
                ));

        PreviaImpactoCorrecaoDTO previa2 = preparacaoBaseService.consultarPreviaImpacto(campanhaJulho.getId(), loteCorr2.id());
        assertThat(previa2.resumoAlteracoesBase().registrosNovos()).isZero();
        assertThat(previa2.resumoAlteracoesBase().registrosAtualizados()).isEqualTo(1);
        assertThat(previa2.resumoAlteracoesBase().registrosInalterados()).isEqualTo(1);

        EfetivacaoPreparacaoResponseDTO efCorr2 = preparacaoBaseService.efetivarLote(
                campanhaJulho.getId(), loteCorr2.id(),
                new EfetivarLotePreparacaoRequestDTO("admin", "2a correcao", true));

        // Contagem inalterada (2 vendas), sem duplicar MATRIC-551 e sem reinserir 5.870 de MATRIC-379
        List<Sale> vendasFinais = saleRepository.findAll();
        assertThat(vendasFinais).hasSize(2);
        Sale venda379 = vendasFinais.stream().filter(s -> "MATRIC-379".equals(s.getRegistration().getRegistration())).findFirst().orElseThrow();
        Sale venda551 = vendasFinais.stream().filter(s -> "MATRIC-551".equals(s.getRegistration().getRegistration())).findFirst().orElseThrow();
        assertThat(venda379.getValue()).isEqualByComparingTo(BigDecimal.valueOf(500000.00));
        assertThat(venda379.getVersao()).isEqualTo(2);
        assertThat(venda551.getValue()).isEqualByComparingTo(BigDecimal.valueOf(300000.00));
        assertThat(venda551.getVersao()).isEqualTo(2);

        EnvioArquivo envioCorr2 = envioArquivoRepository.findByReportId(efCorr2.reportIdGerado()).orElseThrow();
        assertThat(envioCorr2.getTotalLinhas()).isEqualTo(2);
        assertThat(envioCorr2.getLinhasValidas()).isEqualTo(2);
    }

    @Test
    @DisplayName("Item 2 (fallback sem registroAlvoId): reupload com valor editado atualiza venda por chave de negócio e bloqueia chave ambígua")
    void item2_reuploadSemRegistroAlvoAtualizaPorChaveSemValorEBloqueiaAmbiguidade() throws Exception {
        Registration reg = criarColaborador("MAT-KEY");

        MockMultipartFile inicial = criarExcelSales(new Object[][]{
                {"2025-07-15", 10, "PRETO", 14, "LOJA 14", "MAT-KEY", 1000.00}
        });
        LotePreparacaoResponseDTO lote1 = preparacaoBaseService.iniciarPreparacaoUpload(
                campanhaJulho.getId(), inicial, "SALES", "2025-07", "MANUAL", null, "admin");
        preparacaoBaseService.efetivarLote(campanhaJulho.getId(), lote1.id(),
                new EfetivarLotePreparacaoRequestDTO("admin", "Inicial", true));

        // Novo upload (sem registroAlvoId) alterando apenas o valor -> atualiza em vez de duplicar
        MockMultipartFile reuploadEditado = criarExcelSales(new Object[][]{
                {"2025-07-15", 10, "PRETO", 14, "LOJA 14", "MAT-KEY", 2500.00}
        });
        LotePreparacaoResponseDTO lote2 = preparacaoBaseService.iniciarPreparacaoUpload(
                campanhaJulho.getId(), reuploadEditado, "SALES", "2025-07", "MANUAL", null, "admin");
        EfetivacaoPreparacaoResponseDTO ef2 = preparacaoBaseService.efetivarLote(campanhaJulho.getId(), lote2.id(),
                new EfetivarLotePreparacaoRequestDTO("admin", "Reupload editado", true));

        assertThat(ef2.registrosInseridos()).isZero();
        assertThat(ef2.registrosAtualizados()).isEqualTo(1);
        List<Sale> vendas = saleRepository.findAll();
        assertThat(vendas).hasSize(1);
        assertThat(vendas.get(0).getValue()).isEqualByComparingTo(BigDecimal.valueOf(2500.00));
        assertThat(vendas.get(0).getVersao()).isEqualTo(2);

        // Chave ambígua: 2 vendas no banco no mesmo dia/loja/marca com valores diferentes + reupload sem registroAlvoId
        Sale segundaVenda = new Sale();
        segundaVenda.setRegistration(reg);
        segundaVenda.setStore(store14);
        segundaVenda.setBrand(brand10);
        segundaVenda.setSaleDate(LocalDate.of(2025, 7, 15));
        segundaVenda.setValue(BigDecimal.valueOf(3500.00));
        segundaVenda.setTipoVenda("IMPORTADA");
        saleRepository.save(segundaVenda);

        MockMultipartFile reuploadAmbiguo = criarExcelSales(new Object[][]{
                {"2025-07-15", 10, "PRETO", 14, "LOJA 14", "MAT-KEY", 9999.00}
        });
        LotePreparacaoResponseDTO loteAmbiguo = preparacaoBaseService.iniciarPreparacaoUpload(
                campanhaJulho.getId(), reuploadAmbiguo, "SALES", "2025-07", "MANUAL", null, "admin");

        assertThatThrownBy(() -> preparacaoBaseService.efetivarLote(campanhaJulho.getId(), loteAmbiguo.id(),
                new EfetivarLotePreparacaoRequestDTO("admin", "Ambiguo", true)))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("chave de venda ambígua");
    }

    @Test
    @DisplayName("Item 3 e Item 5: grupoComparavel usa códigos de negócio (loja=14|cargo=100|marca=10) e análise roda assíncrona pós-commit")
    void item3e5_grupoComparavelUsaCodigosDeNegocioEAnaliseRodaPosCommit() throws Exception {
        criarColaborador("MAT-O1");
        criarColaborador("MAT-O2");
        criarColaborador("MAT-O3");
        criarColaborador("MAT-O4");
        criarColaborador("MAT-O5");

        MockMultipartFile sales = criarExcelSales(new Object[][]{
                {"2025-07-05", 10, "PRETO", 14, "LOJA 14", "MAT-O1", 1000.00},
                {"2025-07-06", 10, "PRETO", 14, "LOJA 14", "MAT-O2", 1010.00},
                {"2025-07-07", 10, "PRETO", 14, "LOJA 14", "MAT-O3", 990.00},
                {"2025-07-08", 10, "PRETO", 14, "LOJA 14", "MAT-O4", 1005.00},
                {"2025-07-09", 10, "PRETO", 14, "LOJA 14", "MAT-O5", 500000.00}
        });

        LotePreparacaoResponseDTO lote = preparacaoBaseService.iniciarPreparacaoUpload(
                campanhaJulho.getId(), sales, "SALES", "2025-07", "MANUAL", null, "admin");
        preparacaoBaseService.efetivarLote(campanhaJulho.getId(), lote.id(),
                new EfetivarLotePreparacaoRequestDTO("admin", "Efetivacao outlier", true));

        Page<AlertaEstatisticoResponse> alertas = aguardarAlertas(lote.id(), 1);
        assertThat(alertas.getContent()).isNotEmpty();
        AlertaEstatisticoResponse outlier = alertas.getContent().stream()
                .filter(a -> "OUTLIER_VENDA".equals(a.tipo()))
                .findFirst()
                .orElseThrow();

        assertThat(outlier.matricula()).isEqualTo("MAT-O5");
        assertThat(outlier.grupoComparavel()).isEqualTo("2025-07|loja=14|cargo=100|marca=10");
    }

    @Test
    @DisplayName("Item 4: SEM_VENDAS_PARA_RH considera apenas vendas da competência alvo e preenche dataReferencia")
    void item4_semVendasParaRhIgnoraVendasDeOutroMesEPreencheDataReferencia() throws Exception {
        // Colaborador 1 vendeu só em 08/2025; Colaborador 2 vendeu em 07/2025
        MockMultipartFile hrFile = criarExcelHr(new Object[][]{
                {"2025-07-01", 10, "PRETO", 14, "LOJA 14", "MAT-SO-AGOSTO", "2024-01-01", null, 100, "VENDEDOR LOJA"},
                {"2025-07-01", 10, "PRETO", 14, "LOJA 14", "MAT-COM-JULHO", "2024-01-01", null, 100, "VENDEDOR LOJA"}
        });

        LotePreparacaoResponseDTO loteHr1 = preparacaoBaseService.iniciarPreparacaoUpload(
                campanhaJulho.getId(), hrFile, "HR", null, "MANUAL", null, "admin");
        preparacaoBaseService.efetivarLote(campanhaJulho.getId(), loteHr1.id(),
                new EfetivarLotePreparacaoRequestDTO("admin", "Carga RH inicial", true));

        Registration regSoAgosto = registrationRepository.findByRegistration("MAT-SO-AGOSTO").orElseThrow();
        Registration regComJulho = registrationRepository.findByRegistration("MAT-COM-JULHO").orElseThrow();

        // Venda apenas em 08/2025 para MAT-SO-AGOSTO
        Sale vendaAgosto = new Sale();
        vendaAgosto.setRegistration(regSoAgosto);
        vendaAgosto.setStore(store14);
        vendaAgosto.setBrand(brand10);
        vendaAgosto.setSaleDate(LocalDate.of(2025, 8, 15));
        vendaAgosto.setValue(BigDecimal.valueOf(4000.00));
        vendaAgosto.setTipoVenda("IMPORTADA");
        saleRepository.save(vendaAgosto);

        // Venda em 07/2025 para MAT-COM-JULHO
        Sale vendaJulho = new Sale();
        vendaJulho.setRegistration(regComJulho);
        vendaJulho.setStore(store14);
        vendaJulho.setBrand(brand10);
        vendaJulho.setSaleDate(LocalDate.of(2025, 7, 20));
        vendaJulho.setValue(BigDecimal.valueOf(3000.00));
        vendaJulho.setTipoVenda("IMPORTADA");
        saleRepository.save(vendaJulho);

        // Reprocessar alertas do lote HR na campanha de 07/2025
        alertaEstatisticoService.reprocessar(loteHr1.id());

        Page<AlertaEstatisticoResponse> alertasJulho = alertaEstatisticoService.consultar(
                null, "2025-07", TipoAlertaEstatistico.SEM_VENDAS_PARA_RH, loteHr1.id(), false, PageRequest.of(0, 20));

        assertThat(alertasJulho.getContent()).hasSize(1);
        AlertaEstatisticoResponse alerta = alertasJulho.getContent().get(0);
        assertThat(alerta.matricula()).isEqualTo("MAT-SO-AGOSTO");
        assertThat(alerta.dataReferencia()).isEqualTo(LocalDate.of(2025, 7, 1));
        assertThat(alerta.grupoComparavel()).isEqualTo("SEM_VENDAS_ASSOCIADAS");
        assertThat(alerta.tamanhoAmostra()).isZero();
    }

    @Test
    @DisplayName("Item 5: Efetivação de 5.000 linhas com edição pontual conclui bem abaixo de 60s sem duplicação")
    void item5_efetivacao5000LinhasComEdicaoConcluiBemAbaixoDe60Segundos() throws Exception {
        int totalVendas = 5000;
        Registration reg = criarColaborador("MAT-PERF");
        Object[][] rows = new Object[totalVendas][];
        for (int i = 0; i < totalVendas; i++) {
            int dia = (i % 28) + 1;
            String data = String.format("2025-07-%02d", dia);
            rows[i] = new Object[]{data, 10, "PRETO", 14, "LOJA 14", reg.getRegistration(), 100.00 + (i * 0.13)};
        }

        MockMultipartFile arquivo = criarExcelSales(rows);
        LotePreparacaoResponseDTO lote1 = preparacaoBaseService.iniciarPreparacaoUpload(
                campanhaJulho.getId(), arquivo, "SALES", "2025-07", "MANUAL", null, "admin");

        long inicioEf1 = System.currentTimeMillis();
        EfetivacaoPreparacaoResponseDTO ef1 = preparacaoBaseService.efetivarLote(
                campanhaJulho.getId(), lote1.id(),
                new EfetivarLotePreparacaoRequestDTO("admin", "Carga 5k", true));
        long duracaoEf1Ms = System.currentTimeMillis() - inicioEf1;

        assertThat(ef1.registrosInseridos()).isEqualTo(totalVendas);
        assertThat(duracaoEf1Ms).isLessThan(60_000L);

        // 2ª rodada: abre preparação do lote de 5.000 linhas, edita 1 venda e efetiva
        long inicioAbrir = System.currentTimeMillis();
        LotePreparacaoResponseDTO lote2 = preparacaoBaseService.iniciarPreparacaoDeLoteExistente(
                campanhaJulho.getId(), ef1.reportIdGerado(), "admin");
        long duracaoAbrirMs = System.currentTimeMillis() - inicioAbrir;

        LinhaPreparacao primeiraLinha = linhaPreparacaoRepository
                .findByLotePreparacaoIdAndNumeroLinha(lote2.id(), 2)
                .orElseThrow();

        preparacaoBaseService.editarLinha(campanhaJulho.getId(), lote2.id(), primeiraLinha.getNumeroLinha(),
                new EditarLinhaPreparacaoRequestDTO(
                        primeiraLinha.getVersao(), "MANUAL", "admin", "Ajuste pontual 5k", false,
                        primeiraLinha.getRegistroAlvoId(), primeiraLinha.getDataReferencia(),
                        primeiraLinha.getCodMarca(), primeiraLinha.getDescrMarca(),
                        primeiraLinha.getCodLoja(), primeiraLinha.getDescrLoja(),
                        primeiraLinha.getMatricula(), null, null, null, null,
                        BigDecimal.valueOf(99999.99)
                ));

        long inicioEf2 = System.currentTimeMillis();
        EfetivacaoPreparacaoResponseDTO ef2 = preparacaoBaseService.efetivarLote(
                campanhaJulho.getId(), lote2.id(),
                new EfetivarLotePreparacaoRequestDTO("admin", "Correcao 1 linha em 5k", true));
        long duracaoEf2Ms = System.currentTimeMillis() - inicioEf2;

        assertThat(ef2.registrosInseridos()).isZero();
        assertThat(ef2.registrosAtualizados()).isEqualTo(1);
        assertThat(saleRepository.count()).isEqualTo(totalVendas);
        assertThat(duracaoAbrirMs).isLessThan(60_000L);
        assertThat(duracaoEf2Ms).isLessThan(60_000L);
    }

    private Page<AlertaEstatisticoResponse> aguardarAlertas(UUID loteId, int minimo) throws InterruptedException {
        Page<AlertaEstatisticoResponse> page = Page.empty();
        for (int i = 0; i < 50; i++) {
            page = alertaEstatisticoService.consultar(null, null, null, loteId, false, PageRequest.of(0, 50));
            if (page.getTotalElements() >= minimo) {
                return page;
            }
            Thread.sleep(100);
        }
        return page;
    }

    private MockMultipartFile criarExcelSales(Object[][] rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("Data");
            for (int i = 0; i < rows.length; i++) {
                var r = sheet.createRow(i + 1);
                for (int j = 0; j < rows[i].length; j++) {
                    Object val = rows[i][j];
                    if (val instanceof Number n) r.createCell(j).setCellValue(n.doubleValue());
                    else if (val != null) r.createCell(j).setCellValue(val.toString());
                }
            }
            workbook.write(output);
            return new MockMultipartFile("arquivo", "vendas_2025_07.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private MockMultipartFile criarExcelHr(Object[][] rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("Cabeçalho");
            for (int i = 0; i < rows.length; i++) {
                var r = sheet.createRow(i + 1);
                for (int j = 0; j < rows[i].length; j++) {
                    Object val = rows[i][j];
                    if (val instanceof Number n) r.createCell(j).setCellValue(n.doubleValue());
                    else if (val != null) r.createCell(j).setCellValue(val.toString());
                }
            }
            workbook.write(output);
            return new MockMultipartFile("arquivo", "rh_2025_07.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}

