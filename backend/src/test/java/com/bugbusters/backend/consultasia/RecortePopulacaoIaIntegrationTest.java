package com.bugbusters.backend.consultasia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.consultasia.RecortePopulacaoIaDTOs.ConsistenciaRecorteIaResponse;
import com.bugbusters.backend.consultasia.RecortePopulacaoIaDTOs.RecortePopulacaoIaRequest;
import com.bugbusters.backend.consultasia.RecortePopulacaoIaDTOs.RecortePopulacaoIaResponse;
import com.bugbusters.backend.consultasia.RecortePopulacaoIaDTOs.VerificarConsistenciaRecorteIaRequest;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.exception.RecortePopulacaoIncompletoException;
import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.CampanhaAnexoBase;
import com.bugbusters.backend.repository.CampanhaAnexoBaseRepository;
import com.bugbusters.backend.model.EnvioArquivo;
import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.InconsistenciaImportacao;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.EnvioArquivoRepository;
import com.bugbusters.backend.repository.InconsistenciaImportacaoRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.service.pendencia.AceitePendenciaProvider;
import com.bugbusters.backend.service.pendencia.StatusAceitePendenciaDTO;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecortePopulacaoIaIntegrationTest {

    @Autowired
    private RecortePopulacaoIaService recorteService;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private BaseComissRepository baseComissRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private RegraRepository regraRepository;

    @Autowired
    private EnvioArquivoRepository envioArquivoRepository;

    @Autowired
    private CampanhaAnexoBaseRepository campanhaAnexoBaseRepository;

    @Autowired
    private InconsistenciaImportacaoRepository inconsistenciaRepository;

    @MockitoBean
    private AceitePendenciaProvider aceitePendenciaProvider;

    private Brand marca;
    private Store loja;
    private Position cargo;
    private Registration colaborador;
    private Campanha campanha;

    @BeforeEach
    void setUp() {
        marca = new Brand();
        marca.setCode(8101);
        marca.setDescription("MARCA PREMIUM");
        marca = brandRepository.save(marca);

        loja = new Store();
        loja.setCode(8201);
        loja.setDescription("LOJA SHOPPING SUL");
        loja = storeRepository.save(loja);

        cargo = new Position();
        cargo.setCode(8301);
        cargo.setDescription("VENDEDOR SENIOR");
        cargo = positionRepository.save(cargo);

        colaborador = new Registration();
        colaborador.setRegistration("COLAB-888");
        colaborador.setStore(loja);
        colaborador.setPosition(cargo);
        colaborador.setAdmissDate(LocalDate.of(2025, 1, 1));
        colaborador = registrationRepository.save(colaborador);

        campanha = new Campanha();
        campanha.setTitulo("Campanha Fim de Ano 2026");
        campanha.setTextoOriginal("Campanha de simulação de vendas e comissionamento");
        campanha.setDataInicio(LocalDate.of(2026, 11, 1));
        campanha.setDataFim(LocalDate.of(2026, 11, 30));
        campanha.setEstado(EstadoCampanha.ATIVA);
        campanha = campanhaRepository.save(campanha);

        Regra regra = new Regra();
        regra.setCampanha(campanha);
        regra.setNome("Bonus Venda Meta");
        regra.setStatus(StatusRegra.ATIVA);
        regra.setTaxa(new BigDecimal("0.0300"));
        regra.setDataInicio(LocalDate.of(2026, 11, 1));
        regra.setDataFim(LocalDate.of(2026, 11, 30));
        regra.setPythonEquivalente("def calcular(venda):\n    return venda * 0.03");
        regra.setExplicacao("Comissão bônus padrão");
        regraRepository.save(regra);
    }

    private Sale criarVenda(String valor, LocalDate data) {
        Sale s = new Sale();
        s.setId(UUID.randomUUID());
        s.setRegistration(colaborador);
        s.setStore(loja);
        s.setBrand(marca);
        s.setValue(new BigDecimal(valor));
        s.setSaleDate(data);
        s.setSaleChannel("LOJA_FISICA");
        return saleRepository.save(s);
    }

    @Test
    @DisplayName("Gera recorte completo e reproduzível com vendas, regras, RH, taxas contratuais e hashes")
    void deveGerarRecorteCompletoComSucesso() {
        criarVenda("1500.00", LocalDate.of(2026, 11, 10));
        criarVenda("2500.00", LocalDate.of(2026, 11, 15));
        saleRepository.flush();

        BaseComiss base = new BaseComiss(marca, cargo, new BigDecimal("0.0350"), LocalDate.of(2026, 11, 1));
        baseComissRepository.save(base);

        RecortePopulacaoIaRequest request = new RecortePopulacaoIaRequest(campanha.getId());

        RecortePopulacaoIaResponse response = recorteService.obterRecorte(request);

        assertThat(response).isNotNull();
        assertThat(response.campanhaId()).isEqualTo(campanha.getId());
        assertThat(response.nomeCampanha()).isEqualTo("Campanha Fim de Ano 2026");
        assertThat(response.periodo().dataInicio()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(response.periodo().dataFim()).isEqualTo(LocalDate.of(2026, 11, 30));

        // Totais e coleções
        assertThat(response.totais().totalVendas()).isEqualTo(2);
        assertThat(response.totais().totalColaboradoresRh()).isEqualTo(1);
        assertThat(response.totais().totalTaxasContratuais()).isEqualTo(1);
        assertThat(response.totais().totalRegras()).isEqualTo(1);

        assertThat(response.vendas()).hasSize(2);
        assertThat(response.colaboradoresRh()).hasSize(1);
        assertThat(response.colaboradoresRh().get(0).matricula()).isEqualTo("COLAB-888");
        assertThat(response.colaboradoresRh().get(0).codCargo()).isEqualTo(8301);
        assertThat(response.colaboradoresRh().get(0).codLoja()).isEqualTo(8201);

        assertThat(response.taxasContratuais()).hasSize(1);
        assertThat(response.taxasContratuais().get(0).percentual()).isEqualByComparingTo("0.0350");

        assertThat(response.regras()).hasSize(1);
        assertThat(response.regras().get(0).nome()).isEqualTo("Bonus Venda Meta");
        assertThat(response.regras().get(0).codigoPython()).isNotBlank();
        assertThat(response.regras().get(0).explicacao()).isNotBlank();

        // Hashes canônicos SHA-256 (64 hex characters)
        assertThat(response.hashRecorte()).matches("^[0-9a-f]{64}$");
        assertThat(response.hashVendas()).matches("^[0-9a-f]{64}$");
        assertThat(response.hashRh()).matches("^[0-9a-f]{64}$");
        assertThat(response.hashTaxasContratuais()).matches("^[0-9a-f]{64}$");
        assertThat(response.hashRegras()).matches("^[0-9a-f]{64}$");
        assertThat(response.hashPendenciasAceitas()).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("Impede recorte silenciosamente incompleto quando taxa base contratual estiver ausente (HTTP 422)")
    void deveLancar422QuandoBaseContratualEstiverFaltando() {
        criarVenda("1200.00", LocalDate.of(2026, 11, 5));
        saleRepository.flush();

        // Nenhuma BaseComiss salva para marca 8101 e cargo 8301
        RecortePopulacaoIaRequest request = new RecortePopulacaoIaRequest(campanha.getId());

        assertThatThrownBy(() -> recorteService.obterRecorte(request))
                .isInstanceOf(RecortePopulacaoIncompletoException.class)
                .hasMessageContaining("inconsistências ou ausência de dados necessários para cálculo")
                .satisfies(ex -> {
                    RecortePopulacaoIncompletoException pie = (RecortePopulacaoIncompletoException) ex;
                    assertThat(pie.getValidacoes()).anyMatch(msg -> msg.motivo().contains("Taxa base contratual ausente em tb_basecomiss"));
                });
    }

    @Test
    @DisplayName("Impede recorte silenciosamente incompleto quando venda estiver fora da vigência contratual do colaborador (HTTP 422)")
    void deveLancar422QuandoMatriculaNaoTiverCargo() {
        Registration demitido = new Registration();
        demitido.setRegistration("COLAB-DEMITIDO");
        demitido.setStore(loja);
        demitido.setPosition(cargo);
        demitido.setAdmissDate(LocalDate.of(2024, 1, 1));
        demitido.setDemissDate(LocalDate.of(2024, 12, 31));
        demitido = registrationRepository.save(demitido);

        Sale s = new Sale();
        s.setId(UUID.randomUUID());
        s.setRegistration(demitido);
        s.setStore(loja);
        s.setBrand(marca);
        s.setValue(new BigDecimal("500.00"));
        s.setSaleDate(LocalDate.of(2026, 11, 12));
        s.setSaleChannel("LOJA_FISICA");
        saleRepository.save(s);
        saleRepository.flush();

        RecortePopulacaoIaRequest request = new RecortePopulacaoIaRequest(campanha.getId());

        assertThatThrownBy(() -> recorteService.obterRecorte(request))
                .isInstanceOf(RecortePopulacaoIncompletoException.class)
                .satisfies(ex -> {
                    RecortePopulacaoIncompletoException pie = (RecortePopulacaoIncompletoException) ex;
                    assertThat(pie.getValidacoes()).anyMatch(msg -> msg.motivo().contains("fora da vigência do colaborador"));
                });
    }

    @Test
    @DisplayName("Lança 409 Conflict se hashRecorteEsperado divergir do estado atual da base")
    void deveLancar409QuandoHashRecorteEsperadoDivergir() {
        criarVenda("1000.00", LocalDate.of(2026, 11, 10));
        saleRepository.flush();

        BaseComiss base = new BaseComiss(marca, cargo, new BigDecimal("0.0350"), LocalDate.of(2026, 11, 1));
        baseComissRepository.save(base);

        RecortePopulacaoIaRequest requestInicial = new RecortePopulacaoIaRequest(campanha.getId());
        RecortePopulacaoIaResponse inicial = recorteService.obterRecorte(requestInicial);
        String hashInicial = inicial.hashRecorte();

        // Mutação concorrente na base de vendas
        criarVenda("2000.00", LocalDate.of(2026, 11, 12));
        saleRepository.flush();

        RecortePopulacaoIaRequest requestComHashAntigo = new RecortePopulacaoIaRequest(
                campanha.getId(), null, null, null, null, null, null, hashInicial, null
        );

        assertThatThrownBy(() -> recorteService.obterRecorte(requestComHashAntigo))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("divergiu do hash esperado pela IA");
    }

    @Test
    @DisplayName("Lança 409 Conflict se versaoCampanhaEsperada divergir da versão atual da campanha")
    void deveLancar409QuandoVersaoCampanhaDivergir() {
        BaseComiss base = new BaseComiss(marca, cargo, new BigDecimal("0.0350"), LocalDate.of(2026, 11, 1));
        baseComissRepository.save(base);

        RecortePopulacaoIaRequest requestVersaoErrada = new RecortePopulacaoIaRequest(
                campanha.getId(), null, null, null, null, null, null, null, 9999
        );

        assertThatThrownBy(() -> recorteService.obterRecorte(requestVersaoErrada))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("Recarregue os dados antes de prosseguir");
    }

    @Test
    @DisplayName("Inclui pendências aceitas do lote e reflete no hash determinístico")
    void deveIncluirPendenciasAceitasERefletirNoHash() {
        criarVenda("1000.00", LocalDate.of(2026, 11, 10));
        saleRepository.flush();

        BaseComiss base = new BaseComiss(marca, cargo, new BigDecimal("0.0350"), LocalDate.of(2026, 11, 1));
        baseComissRepository.save(base);

        // Cria envio e inconsistência
        EnvioArquivo envio = new EnvioArquivo();
        envio.setNomeArquivo("vendas_2026_11.xlsx");
        envio.setTipoBase("vendas");
        envio.setCompetencia("2026-11");
        envio.setStatus("CONCLUIDO_COM_PENDENCIAS");
        envio.setTotalLinhas(10);
        envio.setLinhasValidas(9);
        envio.setRejeicaoIntegral(false);
        envio = envioArquivoRepository.save(envio);

        InconsistenciaImportacao inc = new InconsistenciaImportacao(envio, 5, "valor", "Valor divergente", "MEDIA");
        inconsistenciaRepository.save(inc);

        CampanhaAnexoBase anexo = new CampanhaAnexoBase(campanha, envio, "SALES", "2026-11", "analista@empresa.com");
        campanhaAnexoBaseRepository.save(anexo);

        // Mock do provider simulando aceite da pendência para a campanha
        when(aceitePendenciaProvider.consultarAceite(eq(campanha.getId()), any(), any()))
                .thenReturn(StatusAceitePendenciaDTO.aceita("gestor@empresa.com", OffsetDateTime.now(), "Risco assumido"));

        RecortePopulacaoIaRequest request = new RecortePopulacaoIaRequest(campanha.getId());

        RecortePopulacaoIaResponse response = recorteService.obterRecorte(request);

        assertThat(response.totais().totalPendenciasAceitas()).isEqualTo(1);
        assertThat(response.pendenciasAceitas()).hasSize(1);
        assertThat(response.pendenciasAceitas().get(0).aceitaPor()).isEqualTo("gestor@empresa.com");
        assertThat(response.pendenciasAceitas().get(0).motivoAceite()).isEqualTo("Risco assumido");
        assertThat(response.hashPendenciasAceitas()).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("Verifica consistência de forma rápida sem retornar todo o payload")
    void deveVerificarConsistenciaRapida() {
        criarVenda("1000.00", LocalDate.of(2026, 11, 10));
        saleRepository.flush();

        BaseComiss base = new BaseComiss(marca, cargo, new BigDecimal("0.0350"), LocalDate.of(2026, 11, 1));
        baseComissRepository.save(base);

        RecortePopulacaoIaRequest request = new RecortePopulacaoIaRequest(campanha.getId());
        RecortePopulacaoIaResponse recorte = recorteService.obterRecorte(request);

        // Quando o hash bate
        VerificarConsistenciaRecorteIaRequest reqValida = new VerificarConsistenciaRecorteIaRequest(
                campanha.getId(), null, recorte.hashRecorte()
        );
        ConsistenciaRecorteIaResponse respValida = recorteService.verificarConsistencia(reqValida);
        assertThat(respValida.consistente()).isTrue();
        assertThat(respValida.hashRecorteAtual()).isEqualTo(recorte.hashRecorte());

        // Quando o hash não bate
        VerificarConsistenciaRecorteIaRequest reqInvalida = new VerificarConsistenciaRecorteIaRequest(
                campanha.getId(), null, "hash_invalido_1234567890abcdef"
        );
        ConsistenciaRecorteIaResponse respInvalida = recorteService.verificarConsistencia(reqInvalida);
        assertThat(respInvalida.consistente()).isFalse();
        assertThat(respInvalida.hashRecorteAtual()).isEqualTo(recorte.hashRecorte());
    }
}
