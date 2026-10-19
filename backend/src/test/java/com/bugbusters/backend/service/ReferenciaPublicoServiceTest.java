package com.bugbusters.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.referencia.CompetenciaReferenciaDTO;
import com.bugbusters.backend.dto.referencia.RecortePublicoRequest;
import com.bugbusters.backend.dto.referencia.RecortePublicoResponse;
import com.bugbusters.backend.dto.referencia.VendaRecortadaDTO;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.Store;

@ExtendWith(MockitoExtension.class)
class ReferenciaPublicoServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private CoberturaBaseService coberturaBaseService;

    @Mock
    private CampanhaRepository campanhaRepository;

    @Mock
    private com.bugbusters.backend.repository.RegraRepository regraRepository;

    private ReferenciaPublicoService service;

    private Brand brandPreto;
    private Store store75;
    private Store store80;
    private Position positionVendedor;
    private Registration regColaborador1;
    private Registration regColaborador2;

    @BeforeEach
    void setUp() {
        service = new ReferenciaPublicoService(saleRepository, coberturaBaseService, campanhaRepository, regraRepository);

        brandPreto = new Brand();
        brandPreto.setCode(10);
        brandPreto.setDescription("PRETO");

        store75 = new Store();
        store75.setCode(75);
        store75.setDescription("LOJA 75");

        store80 = new Store();
        store80.setCode(80);
        store80.setDescription("LOJA 80");

        positionVendedor = new Position();
        positionVendedor.setCode(200);
        positionVendedor.setDescription("VENDEDOR BALCAO");

        regColaborador1 = new Registration();
        regColaborador1.setRegistration("MATRIC-1");
        regColaborador1.setPosition(positionVendedor);
        regColaborador1.setStore(store75);
        regColaborador1.setAdmissDate(LocalDate.of(2025, 1, 1));

        regColaborador2 = new Registration();
        regColaborador2.setRegistration("MATRIC-2");
        regColaborador2.setPosition(positionVendedor);
        regColaborador2.setStore(store80);
        regColaborador2.setAdmissDate(LocalDate.of(2025, 1, 1));
    }

    private Sale criarVenda(UUID id, Registration reg, Store store, Brand brand, BigDecimal valor, LocalDate data, String canal) {
        Sale s = new Sale();
        s.setId(id);
        s.setRegistration(reg);
        s.setStore(store);
        s.setBrand(brand);
        s.setValue(valor);
        s.setSaleDate(data);
        s.setSaleChannel(canal);
        return s;
    }

    private RegraItemRequest criarRegra(String blocoId, String nome, String canal, Integer codMarca, Integer codLoja, Integer codCargo, String matricula, BigDecimal min, BigDecimal max, BigDecimal taxa) {
        return new RegraItemRequest(
                blocoId,
                nome,
                nome,
                canal,
                codMarca,
                null,
                codLoja,
                codCargo,
                null,
                matricula,
                min,
                true,
                max,
                true,
                TipoOperacaoBase.DEFINIR_TAXA,
                taxa,
                null,
                null,
                null,
                taxa,
                null,
                null,
                null,
                true,
                StatusRegra.ATIVA
        );
    }

    @Test
    @DisplayName("1. Deve listar competências disponíveis com métricas consolidadas")
    void deveListarCompetenciasDisponiveis() {
        List<Object[]> listaVendas = new ArrayList<>();
        listaVendas.add(new Object[]{2026, 9});
        when(saleRepository.findDistinctAnosEMesesVendas()).thenReturn(listaVendas);

        List<Object[]> listaBase = new ArrayList<>();
        listaBase.add(new Object[]{2026, 9});
        when(coberturaBaseService.findDistinctAnosEMesesBaseComiss()).thenReturn(listaBase);

        Sale s1 = criarVenda(UUID.randomUUID(), regColaborador1, store75, brandPreto, new BigDecimal("1000.00"), LocalDate.of(2026, 9, 10), "ECOMMERCE");
        when(saleRepository.findVendasPorPeriodoComRelacionamentos(any(), any())).thenReturn(List.of(s1));
        when(coberturaBaseService.contarBasesNaCompetencia(any())).thenReturn(1L);

        List<CompetenciaReferenciaDTO> resultado = service.listarCompetenciasReferencia();

        assertThat(resultado).hasSize(1);
        CompetenciaReferenciaDTO dto = resultado.get(0);
        assertThat(dto.competencia()).isEqualTo("2026-09");
        assertThat(dto.totalVendas()).isEqualTo(1);
        assertThat(dto.valorTotalVendas()).isEqualByComparingTo("1000.00");
        assertThat(dto.statusReferencia()).isEqualTo("DISPONIVEL");
    }

    @Test
    @DisplayName("2. Recorte: Deve combinar regras por OU (união) e filtros por E")
    void deveRecortarUniaoDosPublicosPorOU() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        // Venda 1: Loja 75, Canal ECOMMERCE
        Sale s1 = criarVenda(id1, regColaborador1, store75, brandPreto, new BigDecimal("1000.00"), LocalDate.of(2026, 9, 10), "ECOMMERCE");
        // Venda 2: Loja 80, Canal FISICO (mas da Regra B por matrícula MATRIC-2)
        Sale s2 = criarVenda(id2, regColaborador2, store80, brandPreto, new BigDecimal("2000.00"), LocalDate.of(2026, 9, 12), "LOJA_FISICA");
        // Venda 3: Loja 80, Canal FISICO, sem colaborador MATRIC-2 (fora de ambas as regras)
        Registration outroColab = new Registration();
        outroColab.setRegistration("MATRIC-99");
        outroColab.setPosition(positionVendedor);
        Sale s3 = criarVenda(id3, outroColab, store80, brandPreto, new BigDecimal("3000.00"), LocalDate.of(2026, 9, 14), "LOJA_FISICA");

        when(saleRepository.findVendasPorPeriodoComRelacionamentos(any(), any())).thenReturn(List.of(s1, s2, s3));

        // Regra A: Loja 75 E Canal ECOMMERCE (filtros combinados por E)
        RegraItemRequest regraA = criarRegra("A", "Regra Loja 75 E-commerce", "ECOMMERCE", null, 75, null, null, null, null, new BigDecimal("0.05"));
        // Regra B: Matrícula MATRIC-2
        RegraItemRequest regraB = criarRegra("B", "Regra Individual MATRIC-2", null, null, null, null, "MATRIC-2", null, null, new BigDecimal("0.07"));

        RecortePublicoRequest request = new RecortePublicoRequest("2026-09", null, List.of(regraA, regraB), 500, 0, false, false);
        RecortePublicoResponse response = service.recortarUniaoPublicos(request);

        // Deve conter s1 (por Regra A) e s2 (por Regra B) -> União OU = 2 vendas
        // s3 deve ficar de fora
        assertThat(response.totalVendasCompetencia()).isEqualTo(3);
        assertThat(response.totalVendasRecortadas()).isEqualTo(2);
        assertThat(response.valorTotalRecortado()).isEqualByComparingTo("3000.00");

        List<UUID> idsRecortados = response.vendas().stream().map(VendaRecortadaDTO::id).toList();
        assertThat(idsRecortados).containsExactlyInAnyOrder(id1, id2);
    }

    @Test
    @DisplayName("3. Deduplicação: Venda que atende a múltiplas regras entra apenas UMA vez no recorte")
    void deveDeduplicarVendaPeloIdQuandoAtendeMultiplasRegras() {
        UUID idVenda = UUID.randomUUID();
        // Venda na Loja 75 por MATRIC-1
        Sale s1 = criarVenda(idVenda, regColaborador1, store75, brandPreto, new BigDecimal("1500.00"), LocalDate.of(2026, 9, 15), "PADRAO");

        when(saleRepository.findVendasPorPeriodoComRelacionamentos(any(), any())).thenReturn(List.of(s1));

        // Regra 1: Loja 75
        RegraItemRequest regra1 = criarRegra("1", "Regra Loja 75", null, null, 75, null, null, null, null, new BigDecimal("0.04"));
        // Regra 2: MATRIC-1
        RegraItemRequest regra2 = criarRegra("2", "Regra Individual", null, null, null, null, "MATRIC-1", null, null, new BigDecimal("0.07"));

        RecortePublicoRequest request = new RecortePublicoRequest("2026-09", null, List.of(regra1, regra2), 500, 0, false, false);
        RecortePublicoResponse response = service.recortarUniaoPublicos(request);

        // Venda atende a ambas as regras, mas o recorte deve ter tamanho 1 (sem duplicação)
        assertThat(response.totalVendasRecortadas()).isEqualTo(1);
        assertThat(response.vendas()).hasSize(1);

        VendaRecortadaDTO vendaRecortada = response.vendas().get(0);
        assertThat(vendaRecortada.id()).isEqualTo(idVenda);
        assertThat(vendaRecortada.temSobreposicao()).isTrue();
        assertThat(vendaRecortada.regrasCorrespondentes()).hasSize(2);
        // Regra de matrícula (peso 16) supera loja (peso 8)
        assertThat(vendaRecortada.regraVencedoraNome()).isEqualTo("Regra Individual");
        assertThat(vendaRecortada.temEmpateEspecificidade()).isFalse();
    }

    @Test
    @DisplayName("4. Recorte antes das faixas: Venda fora do valor monetário permanece no público e classifica atendeFaixaValor")
    void deveRealizarRecortePorPublicoAntesDasFaixasDeValor() {
        UUID idVenda = UUID.randomUUID();
        // Venda de R$ 500 na Loja 75
        Sale s1 = criarVenda(idVenda, regColaborador1, store75, brandPreto, new BigDecimal("500.00"), LocalDate.of(2026, 9, 15), "PADRAO");

        when(saleRepository.findVendasPorPeriodoComRelacionamentos(any(), any())).thenReturn(List.of(s1));

        // Regra exige valor mínimo de R$ 1.000,00
        RegraItemRequest regraMin1000 = criarRegra("1", "Regra Meta 1K", null, null, 75, null, null, new BigDecimal("1000.00"), null, new BigDecimal("0.05"));

        RecortePublicoRequest request = new RecortePublicoRequest("2026-09", null, List.of(regraMin1000), 500, 0, false, false);
        RecortePublicoResponse response = service.recortarUniaoPublicos(request);

        // A venda pertence ao público (Loja 75), portanto permanece no recorte!
        assertThat(response.totalVendasRecortadas()).isEqualTo(1);
        VendaRecortadaDTO dto = response.vendas().get(0);
        assertThat(dto.atendeFaixaValor()).isFalse(); // Mas não atingiu a meta de valor
        assertThat(response.totalVendasAtendemFaixa()).isEqualTo(0);
    }

    @Test
    @DisplayName("5. Deve apontar impedimentos cadastrais (demissão, valor <= 0, basecomiss ausente)")
    void deveApontarImpedimentosCadastraisEEspecificos() {
        UUID idVenda = UUID.randomUUID();
        // Colaborador demitido antes da data da venda
        Registration demitido = new Registration();
        demitido.setRegistration("MATRIC-DEM");
        demitido.setPosition(positionVendedor);
        demitido.setStore(store75);
        demitido.setAdmissDate(LocalDate.of(2025, 1, 1));
        demitido.setDemissDate(LocalDate.of(2026, 8, 31)); // Demitido em Agosto

        Sale s1 = criarVenda(idVenda, demitido, store75, brandPreto, new BigDecimal("0.00"), LocalDate.of(2026, 9, 15), "PADRAO");

        when(saleRepository.findVendasPorPeriodoComRelacionamentos(any(), any())).thenReturn(List.of(s1));
        when(coberturaBaseService.obterTaxaBase(anyInt(), anyInt(), any()))
                .thenReturn(Optional.empty()); // Sem basecomiss cadastrada

        RegraItemRequest regraGeral = criarRegra("G", "Regra Geral", null, null, null, null, null, null, null, new BigDecimal("0.05"));

        RecortePublicoRequest request = new RecortePublicoRequest("2026-09", null, List.of(regraGeral), 500, 0, false, false);
        RecortePublicoResponse response = service.recortarUniaoPublicos(request);

        VendaRecortadaDTO dto = response.vendas().get(0);
        assertThat(dto.valida()).isFalse();
        assertThat(dto.impedimentos()).anyMatch(msg -> msg.contains("posterior à demissão"));
        assertThat(dto.impedimentos()).anyMatch(msg -> msg.contains("deve ser estritamente positivo"));
        assertThat(dto.impedimentos()).anyMatch(msg -> msg.contains("Taxa base contratual ausente"));
    }

    @Test
    @DisplayName("6. Deve detectar sobreposição com empate na maior especificidade e alertar ambiguidade")
    void deveDetectarSobreposicaoEEmpateDeEspecificidade() {
        UUID idVenda = UUID.randomUUID();
        Sale s1 = criarVenda(idVenda, regColaborador1, store75, brandPreto, new BigDecimal("2000.00"), LocalDate.of(2026, 9, 15), "PADRAO");

        when(saleRepository.findVendasPorPeriodoComRelacionamentos(any(), any())).thenReturn(List.of(s1));

        // Duas regras para Loja 75 (mesmo peso 8) com taxas diferentes
        RegraItemRequest rA = criarRegra("A", "Regra Loja 75 Taxa 4%", null, null, 75, null, null, null, null, new BigDecimal("0.04"));
        RegraItemRequest rB = criarRegra("B", "Regra Loja 75 Taxa 6%", null, null, 75, null, null, null, null, new BigDecimal("0.06"));

        RecortePublicoRequest request = new RecortePublicoRequest("2026-09", null, List.of(rA, rB), 500, 0, false, false);
        RecortePublicoResponse response = service.recortarUniaoPublicos(request);

        assertThat(response.totalVendasSobrepostas()).isEqualTo(1);
        assertThat(response.possuiAmbiguidadeCritica()).isTrue();

        VendaRecortadaDTO dto = response.vendas().get(0);
        assertThat(dto.temSobreposicao()).isTrue();
        assertThat(dto.temEmpateEspecificidade()).isTrue();
        assertThat(dto.valida()).isFalse();
        assertThat(dto.impedimentos()).anyMatch(msg -> msg.contains("Conflito de regras: múltiplas regras empatadas na maior especificidade"));
    }

    @Test
    @DisplayName("7. Deve retornar impedimento estruturado quando a competência não possui vendas registradas")
    void deveRetornarImpedimentoQuandoCompetenciaSemVendas() {
        when(saleRepository.findVendasPorPeriodoComRelacionamentos(any(), any())).thenReturn(List.of());

        RegraItemRequest regra = criarRegra("1", "Regra Teste", null, null, 75, null, null, null, null, new BigDecimal("0.05"));
        RecortePublicoRequest request = new RecortePublicoRequest("2026-11", null, List.of(regra), 500, 0, false, false);

        RecortePublicoResponse response = service.recortarUniaoPublicos(request);

        assertThat(response.totalVendasCompetencia()).isEqualTo(0);
        assertThat(response.totalVendasRecortadas()).isEqualTo(0);
        assertThat(response.impedimentosGerais()).anyMatch(msg -> msg.contains("Referência insuficiente"));
    }
}
