package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.simulacao.*;
import com.bugbusters.backend.dto.simulacao.AnaliseSimulacaoDTO.*;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.service.client.AiServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.math.*;
import java.util.*;

/** A IA propõe; apenas a reexecução do programa da campanha confirma o custo da alternativa. */
@Service
public class SimulacaoAnaliseService {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private final AiServiceClient ia;
    private final CampanhaProgramaPythonService programas;
    public SimulacaoAnaliseService(AiServiceClient ia, CampanhaProgramaPythonService programas) {
        this.ia = ia; this.programas = programas;
    }

    public AnaliseSimulacaoDTO analisar(Simulacao s, SimulacaoCenariosResponse resposta,
            CampanhaVersao versao, CampanhaProgramaPython programa, List<Regra> regras,
            List<SimulacaoCenariosService.VendaCenarioParaCalculo> itens, boolean calibrar) {
        List<Calibracao> alternativas = new ArrayList<>();
        String status = "NAO_SOLICITADA", motivo = null;
        BigDecimal teto = s.getOrcamentoCampanha();
        if (calibrar) {
            try {
                if (teto == null || teto.signum() <= 0) throw new IllegalArgumentException("Confirme um orçamento antes de solicitar calibração.");
                if (s.getQtdVendasComImpedimento() > 0 || itens.isEmpty()) throw new IllegalArgumentException("A calibração exige um recorte calculável e não vazio.");
                // O serviço de sugestões recebe regras parametrizadas. Código arbitrário não pode ser substituído por template.
                if (!programa.getCodigoFonte().strip().equals(programas.gerarCodigoConsolidado(versao, regras).strip())
                        || regras.stream().anyMatch(r -> r.getTipoOperacao() != null && r.getTipoOperacao() != TipoOperacaoBase.DEFINIR_TAXA))
                    throw new IllegalArgumentException("Não é possível calibrar automaticamente este programa personalizado ou operação sobre a base. Ajuste os parâmetros e solicite nova revisão.");
                Map<String,Object> requisicao = payload(s, versao, regras, itens);
                requisicao.put("versoesFontes", fontes(s, versao, regras, itens));
                JsonNode raw = JSON.valueToTree(ia.simularV2(requisicao));
                validarCenarios(raw, resposta);
                for (CenarioConsolidadoDTO c : cenarios(resposta)) {
                    String nome = nome(c);
                    JsonNode sugestao = encontrar(raw.path("calibracoes"), c.fatorEscala());
                    if (sugestao == null) throw new IllegalArgumentException("O serviço não devolveu as alternativas dos quatro cenários.");
                    alternativas.add(verificar(s, versao, regras, itens, c, sugestao));
                }
                status = alternativas.stream().anyMatch(c -> "FALHA".equals(c.status())) ? "FALHA" : "OK";
            } catch (RuntimeException ex) {
                status = "FALHA";
                motivo = ex instanceof IllegalArgumentException ? ex.getMessage() : "Não foi possível obter ou verificar as alternativas. A simulação original foi preservada.";
                alternativas.clear();
            }
        }
        return new AnaliseSimulacaoDTO(s.getId(), versao.getNumero(), programa.getVersaoPrograma(),
                programa.getChecksumSha256(), teto, status, motivo, List.copyOf(alternativas),
                explicar(resposta, teto, alternativas, motivo), fontes(s, versao, regras, itens));
    }

    private Calibracao verificar(Simulacao s, CampanhaVersao versao, List<Regra> regras,
            List<SimulacaoCenariosService.VendaCenarioParaCalculo> itens, CenarioConsolidadoDTO c, JsonNode sugestao) {
        String nome = nome(c), status = sugestao.path("status").asText();
        BigDecimal teto = s.getOrcamentoCampanha();
        if (c.comissaoProjetada().compareTo(teto) <= 0)
            return new Calibracao(nome, "NAO_NECESSARIA", c.comissaoProjetada(), null, null, List.of(), null, null);
        if (!"ENQUADRADA".equals(status)) {
            // Ausência de solução relatada pela IA permanece distinguível de indisponibilidade.
            if (!Set.of("SEM_SOLUCAO_VIAVEL", "FALHA").contains(status)) status = "FALHA";
            return new Calibracao(nome, status, c.comissaoProjetada(), null, null, List.of(), null,
                    "SEM_SOLUCAO_VIAVEL".equals(status) ? "A busca não encontrou uma alternativa dentro do orçamento." : "O serviço não conseguiu verificar uma alternativa para este cenário.");
        }
        try {
            List<Alteracao> mudancas = new ArrayList<>();
            if (!sugestao.path("alteracoes").isArray() || sugestao.path("alteracoes").isEmpty()) throw new IllegalArgumentException("Alternativa sem alterações.");
            for (JsonNode a : sugestao.path("alteracoes")) {
                mudancas.add(new Alteracao(Long.valueOf(a.path("idLocal").asText()), a.path("campo").asText(), numero(a.get("antes")), numero(a.get("depois"))));
            }
            List<Regra> candidatas = candidatas(regras, mudancas);
            String codigo = programas.gerarCodigoConsolidado(versao, candidatas).strip();
            List<SimulacaoCenariosService.VendaCenarioParaCalculo> recorte = itens.stream().filter(i -> i.fatorEscala().compareTo(c.fatorEscala()) == 0).toList();
            var resultados = executar(codigo, recorte, Map.of("modo", "CALIBRACAO", "campanhaId", s.getCampanha().getId(), "execucaoId", s.getId().toString()));
            BigDecimal custo = BigDecimal.ZERO;
            for (var item : recorte) {
                var r = resultados.get(item.itemId());
                BigDecimal comissao = r.aplicada() ? r.comissao() : item.valorEscalado().multiply(item.taxaBaseContratual()).setScale(2, RoundingMode.HALF_UP);
                custo = custo.add(comissao);
            }
            if (custo.compareTo(numero(sugestao.get("custoPropostoRecalculado"))) != 0)
                throw new IllegalArgumentException("O custo da alternativa diverge da reexecução Python.");
            custo = custo.multiply(s.getFatorProjecao()).setScale(2, RoundingMode.HALF_UP);
            if (custo.compareTo(teto) > 0)
                throw new IllegalArgumentException("O custo da alternativa não foi confirmado pelo programa da campanha.");
            return new Calibracao(nome, "ENQUADRADA", c.comissaoProjetada(), custo, teto.subtract(custo), List.copyOf(mudancas), CampanhaProgramaPython.calcularChecksum(codigo), null);
        } catch (RuntimeException e) {
            return new Calibracao(nome, "FALHA", c.comissaoProjetada(), null, null, List.of(), null,
                    "A alternativa não passou pela conferência com o programa da campanha. Nenhuma alteração foi aplicada.");
        }
    }

    /** Valida IDs, cobertura integral, valores e resultado antes de aceitar qualquer execução Python. */
    public Map<String, AiServiceClient.ItemExecucaoPython> executar(String codigo,
            List<SimulacaoCenariosService.VendaCenarioParaCalculo> itens, Map<String, Object> contexto) {
        List<Map<String, Object>> entradas = itens.stream().map(i -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", i.itemId()); e.put("venda", CampanhaProgramaPythonService.montarVendaParaPython(i.sale(), i.valorEscalado(), i.taxaBaseContratual(), true));
            e.put("contexto", Map.of("cenario", i.nomeCenario(), "fator", i.fatorEscala())); return e;
        }).toList();
        var r = ia.executarProgramaPython(Map.of("codigoFonte", codigo, "contexto", contexto, "itens", entradas));
        if (r == null || !r.sucesso() || r.resultados() == null || r.totalItens() != itens.size()
                || !Objects.equals(r.assinaturaPrograma(), CampanhaProgramaPython.calcularChecksum(codigo)))
            throw new IllegalArgumentException("A execução não confirmou todos os itens enviados.");
        Set<String> esperados = new HashSet<>(); itens.forEach(i -> esperados.add(i.itemId()));
        if (esperados.size() != itens.size()) throw new IllegalArgumentException("Identificadores de entrada duplicados.");
        Map<String, AiServiceClient.ItemExecucaoPython> saidas = new HashMap<>();
        for (var item : r.resultados()) {
            if (item == null || !esperados.contains(item.id()) || saidas.putIfAbsent(item.id(), item) != null
                    || (item.aplicada() && (item.comissao() == null || item.comissao().signum() < 0 || item.comissao().stripTrailingZeros().scale() > 2))
                    || (!item.aplicada() && item.comissao() != null)) throw new IllegalArgumentException("Resultado Python inválido ou duplicado.");
        }
        if (saidas.size() != esperados.size()) throw new IllegalArgumentException("Resposta Python incompleta.");
        return saidas;
    }

    private Map<String, Object> payload(Simulacao s, CampanhaVersao v, List<Regra> regras,
            List<SimulacaoCenariosService.VendaCenarioParaCalculo> itens) {
        List<Map<String,Object>> propostas = new ArrayList<>(), vendas = new ArrayList<>();
        for (Regra r : regras) {
            Map<String,Object> cond = new LinkedHashMap<>(); cond.put("codMarca", r.getCodMarca()); cond.put("codCargo", r.getCodCargo());
            cond.put("codLoja", r.getCodLoja()); cond.put("canal", r.getCanal()); cond.put("matricula", r.getMatricula());
            Map<String,Object> faixa = new LinkedHashMap<>(); faixa.put("min", r.getValorMinimo()); faixa.put("max", r.getValorMaximo());
            faixa.put("minInclusivo", Boolean.TRUE.equals(r.getMinInclusivo())); faixa.put("maxInclusivo", Boolean.TRUE.equals(r.getMaxInclusivo())); if (r.getValorMinimo()!=null || r.getValorMaximo()!=null) cond.put("faixaValor", faixa);
            Map<String,Object> p = new LinkedHashMap<>(); p.put("idLocal", r.getId().toString()); p.put("taxa", r.getTaxa()); p.put("condicoes", cond);
            Map<String,Object> bonus = new LinkedHashMap<>(); bonus.put("percentual", r.getBonusPercentual()); bonus.put("valorFixo", r.getBonusValorFixo()); if(r.getBonusPercentual()!=null || r.getBonusValorFixo()!=null) p.put("bonus", bonus); propostas.add(p);
        }
        Set<UUID> ids = new HashSet<>();
        for (var i : itens) if (i.fatorEscala().compareTo(BigDecimal.ONE) == 0 && ids.add(i.sale().getId())) {
            Map<String,Object> venda = new LinkedHashMap<>(CampanhaProgramaPythonService.montarVendaParaPython(i.sale(), i.valorEscalado(), i.taxaBaseContratual(), true));
            venda.put("id", i.sale().getId().toString()); venda.put("codMarca", venda.get("cod_marca")); venda.put("codLoja", venda.get("cod_loja")); venda.put("codCargo", venda.get("cod_cargo"));
            venda.put("valorVenda", i.valorEscalado()); venda.put("taxaContratual", i.taxaBaseContratual()); vendas.add(venda);
        }
        Map<String,Object> p = new LinkedHashMap<>(); p.put("competencia", s.getCompetencia()); p.put("propostas", propostas); p.put("vendas", vendas);
        p.put("orcamento", s.getOrcamentoCampanha()); p.put("calibrar", true);
        p.put("periodoCampanha", Map.of("dataInicio", v.getDataInicio(), "dataFim", v.getDataFim()));
        p.put("contexto", Map.of("campanhaId", s.getCampanha().getId().toString(), "execucaoId", s.getId().toString(), "versaoPrograma", s.getVersaoProgramaPython().toString()));
        return p;
    }

    private Map<String,String> fontes(Simulacao s, CampanhaVersao versao, List<Regra> regras,
            List<SimulacaoCenariosService.VendaCenarioParaCalculo> itens) {
        var p=payload(s,versao,regras,itens);
        try {return Map.of("hashVendas",CampanhaProgramaPython.calcularChecksum(JSON.writeValueAsString(p.get("vendas"))),
                "hashRegras",CampanhaProgramaPython.calcularChecksum(JSON.writeValueAsString(p.get("propostas"))),
                "hashRecorte",CampanhaProgramaPython.calcularChecksum(JSON.writeValueAsString(p)),
                "assinaturaPrograma",s.getChecksumPython()==null?"":s.getChecksumPython());}
        catch(Exception e){throw new IllegalStateException("Não foi possível preservar as fontes da simulação.",e);}
    }

    private void validarCenarios(JsonNode raw, SimulacaoCenariosResponse r) {
        if (!raw.path("resultados").isArray() || raw.path("resultados").size() != 4) throw new IllegalArgumentException("Os quatro cenários não foram confirmados pela IA.");
        for (var c : cenarios(r)) {
            JsonNode a = encontrar(raw.path("resultados"), c.fatorEscala());
            if (a == null || numero(a.get("totalComissao")).compareTo(c.totalComissaoProposta()) != 0
                    || numero(a.get("custoContratual")).compareTo(c.totalComissaoBase()) != 0)
                throw new IllegalArgumentException("Os cenários da sugestão divergem da simulação aprovada. As alternativas não serão aplicadas.");
        }
    }
    private static JsonNode encontrar(JsonNode array, BigDecimal fator) {
        JsonNode achado = null;
        if (array.isArray()) for (JsonNode a : array) if (numero(a.get("multiplicadorVendas")).compareTo(fator) == 0) {
            if (achado != null) throw new IllegalArgumentException("Cenário duplicado."); achado = a;
        }
        return achado;
    }
    private static BigDecimal numero(JsonNode n) { if (n == null || n.isNull()) throw new IllegalArgumentException("Valor obrigatório ausente."); return new BigDecimal(n.asText()); }
    private static List<CenarioConsolidadoDTO> cenarios(SimulacaoCenariosResponse r) { return List.of(r.cenario80(), r.cenario100(), r.cenario120(), r.cenario150()); }
    private static String nome(CenarioConsolidadoDTO c) { return c.fatorEscala().multiply(new BigDecimal("100")).intValueExact() + "%"; }

    static List<Regra> candidatas(List<Regra> regras, List<Alteracao> alteracoes) {
        List<Regra> copia = regras.stream().map(SimulacaoAnaliseService::copiar).toList(); Set<String> vistos = new HashSet<>();
        for (Alteracao a : alteracoes) {
            if (!vistos.add(a.regraId()+":"+a.campo())) throw new IllegalArgumentException("Parâmetro duplicado.");
            Regra r = copia.stream().filter(x -> Objects.equals(x.getId(), a.regraId())).findFirst().orElseThrow(() -> new IllegalArgumentException("Regra desconhecida."));
            BigDecimal atual = valor(r,a.campo());
            if (atual == null || a.antes() == null || atual.compareTo(a.antes()) != 0 || a.depois() == null || a.depois().signum() <= 0 || a.depois().compareTo(atual) >= 0)
                throw new IllegalArgumentException("Parâmetro de calibração inválido.");
            int escala = "bonus.valorFixo".equals(a.campo()) ? 2 : 4;
            if (a.depois().stripTrailingZeros().scale() > escala) throw new IllegalArgumentException("Precisão de parâmetro inválida.");
            atribuir(r, a.campo(), a.depois());
        }
        return copia;
    }
    static BigDecimal valor(Regra r, String campo) { return switch(campo) { case "taxa" -> r.getTaxa(); case "bonus.percentual" -> r.getBonusPercentual(); case "bonus.valorFixo" -> r.getBonusValorFixo(); default -> throw new IllegalArgumentException("Parâmetro não permitido."); }; }
    static void atribuir(Regra r,String campo,BigDecimal valor) { switch(campo) { case "taxa" -> {r.setTaxa(valor); r.setValorAjuste(valor);} case "bonus.percentual" -> r.setBonusPercentual(valor); case "bonus.valorFixo" -> r.setBonusValorFixo(valor); default -> throw new IllegalArgumentException("Parâmetro não permitido."); } }
    private static Regra copiar(Regra r) {
        Regra c = new Regra(); c.setId(r.getId()); c.setBlocoId(r.getBlocoId()); c.setNome(r.getNome()); c.setExplicacao(r.getExplicacao());
        c.setCanal(r.getCanal()); c.setCodMarca(r.getCodMarca()); c.setCodCargo(r.getCodCargo()); c.setCodLoja(r.getCodLoja()); c.setMatricula(r.getMatricula());
        c.setValorMinimo(r.getValorMinimo()); c.setValorMaximo(r.getValorMaximo()); c.setMinInclusivo(r.getMinInclusivo()); c.setMaxInclusivo(r.getMaxInclusivo());
        c.setTaxa(r.getTaxa()); c.setTipoOperacao(r.getTipoOperacao()); c.setValorAjuste(r.getValorAjuste()); c.setBonusPercentual(r.getBonusPercentual()); c.setBonusValorFixo(r.getBonusValorFixo()); return c;
    }
    private static List<Explicacao> explicar(SimulacaoCenariosResponse r, BigDecimal teto, List<Calibracao> alternativas, String motivo) {
        List<Explicacao> out = new ArrayList<>();
        for(var c: cenarios(r)) {
            Map<String,BigDecimal> numeros = new LinkedHashMap<>(); numeros.put("custoContratual", c.totalComissaoBase()); numeros.put("custoProposto", c.totalComissaoProposta()); numeros.put("custoProjetado", c.comissaoProjetada()); numeros.put("fatorProjecao", r.fatorProjecao());
            if(teto!=null) {numeros.put("orcamento",teto); numeros.put("saldo",teto.subtract(c.comissaoProjetada()));}
            String resumo = "Custo projetado de R$ " + c.comissaoProjetada().toPlainString() + (teto==null ? "; orçamento não informado." : c.comissaoProjetada().compareTo(teto)<=0 ? ", dentro do orçamento." : ", acima do orçamento.");
            List<String> limites = new ArrayList<>(); limites.add("Projeção pelo mês comercial de 30 dias. Não substitui a apuração definitiva."); limites.add("Ressalvas e exclusões permanecem na memória da simulação original.");
            if(motivo!=null) limites.add(motivo);
            alternativas.stream().filter(a->a.cenario().equals(nome(c)) && "ENQUADRADA".equals(a.status())).findFirst().ifPresent(a->{numeros.put("custoProjetadoRecalculado",a.custoProjetadoRecalculado());numeros.put("folgaRecalculada",a.folga());});
            out.add(new Explicacao(nome(c),resumo,Map.copyOf(numeros),List.copyOf(limites)));
        } return List.copyOf(out);
    }
    public static String serializar(AnaliseSimulacaoDTO a) { try {return JSON.writeValueAsString(a);} catch(Exception e) {throw new IllegalStateException("Falha ao preservar análise da simulação.",e);} }
    public static AnaliseSimulacaoDTO ler(Simulacao s) { if(s.getAnalisePythonSnapshot()==null) return null; try{return JSON.readValue(s.getAnalisePythonSnapshot(),AnaliseSimulacaoDTO.class);}catch(Exception e){throw new IllegalStateException("Análise persistida inválida.",e);} }
}
