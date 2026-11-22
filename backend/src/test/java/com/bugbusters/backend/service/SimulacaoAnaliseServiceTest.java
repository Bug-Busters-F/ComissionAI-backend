package com.bugbusters.backend.service;
import com.bugbusters.backend.dto.simulacao.*;
import com.bugbusters.backend.dto.simulacao.AnaliseSimulacaoDTO.*;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.service.client.AiServiceClient;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulacaoAnaliseServiceTest {
    AiServiceClient ia; CampanhaProgramaPythonService programas; SimulacaoAnaliseService service;
    Simulacao sim; CampanhaVersao versao; CampanhaProgramaPython programa; Regra regra;
    List<SimulacaoCenariosService.VendaCenarioParaCalculo> itens;
    @BeforeEach void setup() {
        ia=mock(AiServiceClient.class); programas=mock(CampanhaProgramaPythonService.class); service=new SimulacaoAnaliseService(ia,programas);
        Campanha camp=new Campanha("Campanha","texto",LocalDate.of(2026,6,1),LocalDate.of(2026,6,30));camp.setId(1L);
        versao=new CampanhaVersao();versao.setNumero(1);versao.setDataInicio(camp.getDataInicio());versao.setDataFim(camp.getDataFim());
        regra=new Regra();regra.setId(10L);regra.setTaxa(new BigDecimal("0.05"));
        when(programas.gerarCodigoConsolidado(eq(versao),anyList())).thenAnswer(x->"taxa "+((List<Regra>)x.getArgument(1)).getFirst().getTaxa());
        programa=new CampanhaProgramaPython(versao,1,"taxa 0.05");
        sim=new Simulacao();sim.setId(UUID.randomUUID());sim.setCampanha(camp);sim.setCompetencia("2026-07");sim.setOrcamentoCampanha(new BigDecimal("450"));sim.setFatorProjecao(BigDecimal.ONE);sim.setVersaoProgramaPython(1);
        Sale sale=new Sale();ReflectionTestUtils.setField(sale,"id",UUID.randomUUID());sale.setSaleDate(LocalDate.of(2026,7,1));
        itens=new ArrayList<>();for(String f:List.of("0.8","1","1.2","1.5")) {BigDecimal fator=new BigDecimal(f);itens.add(new SimulacaoCenariosService.VendaCenarioParaCalculo("v#"+f,sale,f,fator,new BigDecimal("10000").multiply(fator),new BigDecimal("0.03")));}
        when(ia.simularV2(anyMap())).thenReturn(respostaIa());
        when(ia.executarProgramaPython(anyMap())).thenAnswer(x->{Map<String,Object> p=x.getArgument(0);BigDecimal taxa=new BigDecimal(p.get("codigoFonte").toString().substring(5));
            var resultado=((List<Map<String,Object>>)p.get("itens")).stream().map(i->{var v=(Map<String,Object>)i.get("venda");return new AiServiceClient.ItemExecucaoPython(i.get("id").toString(),true,new BigDecimal(v.get("valor").toString()).multiply(taxa).setScale(2,RoundingMode.HALF_UP));}).toList();
            return new AiServiceClient.ExecutarProgramaPythonResponse(true,"PYTHON_SANDBOX",resultado.size(),resultado,null,null,List.of(),CampanhaProgramaPython.calcularChecksum(p.get("codigoFonte").toString()));});
    }
    Map<String,Object> respostaIa() {
        List<Map<String,Object>> resultados=new ArrayList<>(),calibracoes=new ArrayList<>();int idx=0;
        for(String f:List.of("0.8","1","1.2","1.5")) {BigDecimal fator=new BigDecimal(f),total=new BigDecimal("500").multiply(fator);
            resultados.add(Map.of("multiplicadorVendas",f,"totalComissao",total,"custoContratual",new BigDecimal("300").multiply(fator)));
            String taxa=List.of("0.05","0.04","0.035","0.03").get(idx++);
            calibracoes.add(Map.of("multiplicadorVendas",f,"status",f.equals("0.8")?"NAO_NECESSARIA":"ENQUADRADA","custoPropostoRecalculado",new BigDecimal("10000").multiply(fator).multiply(new BigDecimal(taxa)),
                "alteracoes",List.of(Map.of("idLocal","10","campo","taxa","antes","0.05","depois",taxa))));
        }return Map.of("resultados",resultados,"calibracoes",calibracoes);
    }
    CenarioConsolidadoDTO c(String f) {BigDecimal fator=new BigDecimal(f),base=new BigDecimal("300").multiply(fator),proposta=new BigDecimal("500").multiply(fator);return new CenarioConsolidadoDTO(fator,f,new BigDecimal("10000").multiply(fator),base,proposta,proposta.subtract(base),BigDecimal.ZERO,new BigDecimal("0.03"),new BigDecimal("0.05"),proposta,proposta.subtract(base),0,1,0);}
    SimulacaoCenariosResponse resposta() {return new SimulacaoCenariosResponse("2026-07",1,new BigDecimal("10000"),1L,"Campanha",versao.getDataInicio(),versao.getDataFim(),30,BigDecimal.ONE,c("1"),c("0.8"),c("1.2"),c("1.5"),1,List.of(),List.of());}
    AnaliseSimulacaoDTO analisar(boolean calibrar) {return service.analisar(sim,resposta(),versao,programa,List.of(regra),itens,calibrar);}
    @Test void confirmaAlternativasPorReexecucaoEExplicaValoresAtuais() {
        var a=analisar(true);assertThat(a.statusSugestoes()).isEqualTo("OK");assertThat(a.calibracoes()).hasSize(4);
        assertThat(a.calibracoes().get(1).custoProjetadoRecalculado()).isEqualByComparingTo("400");
        assertThat(a.calibracoes().get(3).folga()).isEqualByComparingTo("0");assertThat(a.explicacoes()).hasSize(4);
        assertThat(a.explicacoes().get(1).numeros().get("custoProjetado")).isEqualByComparingTo("500");
        assertThat(regra.getTaxa()).isEqualByComparingTo("0.05");verify(ia,times(3)).executarProgramaPython(anyMap());
        var payload=org.mockito.ArgumentCaptor.forClass(Map.class);verify(ia).simularV2(payload.capture());
        assertThat(((List<Map<String,Object>>)payload.getValue().get("propostas")).getFirst().get("condicoes").toString()).doesNotContain("faixaValor");
    }
    @Test void falhaDoServicoPreservaSimulacaoENaoInventaSugestoes() {when(ia.simularV2(anyMap())).thenThrow(new RuntimeException("offline"));var a=analisar(true);assertThat(a.statusSugestoes()).isEqualTo("FALHA");assertThat(a.calibracoes()).isEmpty();assertThat(a.explicacoes()).hasSize(4);verify(ia,never()).executarProgramaPython(anyMap());}
    @Test void custoNaoConfirmadoNaoViraAlternativaEnquadrada() {doReturn(new AiServiceClient.ExecutarProgramaPythonResponse(true,"PYTHON_SANDBOX",1,List.of(new AiServiceClient.ItemExecucaoPython("v#1",true,new BigDecimal("999"))),null,null,List.of(),CampanhaProgramaPython.calcularChecksum("taxa 0.05"))).when(ia).executarProgramaPython(anyMap());var a=analisar(true);assertThat(a.statusSugestoes()).isEqualTo("FALHA");assertThat(a.calibracoes()).noneMatch(x->x.status().equals("ENQUADRADA"));}
    @Test void codigoManualNaoEsubstituidoPorTemplate() {programa.setCodigoFonte("manual");var a=analisar(true);assertThat(a.statusSugestoes()).isEqualTo("FALHA");assertThat(a.motivo()).contains("personalizado");verifyNoInteractions(ia);}
    @Test void semSolicitacaoNaoChamaIaMasPreservaExplicacoes() {var a=analisar(false);assertThat(a.statusSugestoes()).isEqualTo("NAO_SOLICITADA");assertThat(a.explicacoes()).hasSize(4);verifyNoInteractions(ia);}
    @Test void orcamentoAusenteNaoGeraSucessoFicticio() {sim.setOrcamentoCampanha(null);assertThat(analisar(true).statusSugestoes()).isEqualTo("FALHA");verifyNoInteractions(ia);}
    @Test void respostaPythonIncompletaDuplicadaOuNegativaERejeitada() {
        for(var resultados:List.of(List.<AiServiceClient.ItemExecucaoPython>of(),List.of(new AiServiceClient.ItemExecucaoPython("v#1",true,new BigDecimal("-1"))),List.of(new AiServiceClient.ItemExecucaoPython("outro",true,BigDecimal.ONE)))) {
            doReturn(new AiServiceClient.ExecutarProgramaPythonResponse(true,"PYTHON_SANDBOX",1,resultados,null,null,List.of(),CampanhaProgramaPython.calcularChecksum("taxa 0.05"))).when(ia).executarProgramaPython(anyMap());
            assertThatThrownBy(()->service.executar("taxa 0.05",List.of(itens.get(1)),Map.of())).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void snapshotRestauraExatamenteMesmoResultado() {var a=analisar(true);sim.setAnalisePythonSnapshot(SimulacaoAnaliseService.serializar(a));assertThat(SimulacaoAnaliseService.ler(sim)).isEqualTo(a);}
    @Test void alteracaoNaoAutorizadaOuDesatualizadaNaoAlteraOriginais() {
        for(var a:List.of(new Alteracao(10L,"canal",new BigDecimal("0.05"),new BigDecimal("0.04")),new Alteracao(10L,"taxa",new BigDecimal("0.06"),new BigDecimal("0.04")),new Alteracao(99L,"taxa",new BigDecimal("0.05"),new BigDecimal("0.04")))) {
            assertThatThrownBy(()->SimulacaoAnaliseService.candidatas(List.of(regra),List.of(a))).isInstanceOf(IllegalArgumentException.class);
        }assertThat(regra.getTaxa()).isEqualByComparingTo("0.05");
    }
    @Test void aprovacaoHistoricaNaoEApresentadaComoProgramaAtualExecutavel() {
        programa.setId(UUID.randomUUID());programa.setStatusAprovacao(StatusAprovacaoPrograma.APROVADO);
        versao.setProgramaPythonAprovado(programa);
        assertThat(com.bugbusters.backend.dto.programapython.ProgramaPythonResponse.de(programa).validoParaExecucao()).isTrue();
        versao.setProgramaPythonAprovado(null);
        assertThat(com.bugbusters.backend.dto.programapython.ProgramaPythonResponse.de(programa).validoParaExecucao()).isFalse();
    }

}
