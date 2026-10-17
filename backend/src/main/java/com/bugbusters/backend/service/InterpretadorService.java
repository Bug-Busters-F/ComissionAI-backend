package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.interpretador.InterpretacaoRegraRequest;
import com.bugbusters.backend.dto.interpretador.InterpretacaoRegraResponse;
import com.bugbusters.backend.dto.interpretador.proposta.AtualizarArtefatosRequest;
import com.bugbusters.backend.dto.interpretador.proposta.AtualizarArtefatosResponse;
import com.bugbusters.backend.dto.interpretador.proposta.FaixaValorDTO;
import com.bugbusters.backend.dto.interpretador.proposta.FiltrosRegraDTO;
import com.bugbusters.backend.dto.interpretador.proposta.InterpretacaoMultiplaRequest;
import com.bugbusters.backend.dto.interpretador.proposta.InterpretacaoMultiplaResponse;
import com.bugbusters.backend.dto.interpretador.proposta.OperacaoBaseDTO;
import com.bugbusters.backend.dto.interpretador.proposta.OrigemCampo;
import com.bugbusters.backend.dto.interpretador.proposta.PendenciaPropostaDTO;
import com.bugbusters.backend.dto.interpretador.proposta.PeriodoVigenciaDTO;
import com.bugbusters.backend.dto.interpretador.proposta.PropostaRegraDTO;
import com.bugbusters.backend.dto.interpretador.proposta.ReferenciaBaseDTO;
import com.bugbusters.backend.dto.interpretador.proposta.ReferenciaConsultadaDTO;
import com.bugbusters.backend.dto.interpretador.proposta.ReinterpretarBlocoRequest;
import com.bugbusters.backend.dto.interpretador.proposta.ReinterpretarBlocoResponse;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.interpretador.proposta.ValidarPropostaRequest;
import com.bugbusters.backend.dto.interpretador.proposta.ValidarPropostaResponse;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.Cargo;
import com.bugbusters.backend.model.CatalogoDominioDomRock;
import com.bugbusters.backend.model.Marca;

import com.bugbusters.backend.service.client.AiServiceClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InterpretadorService {

    public static final Map<Integer, String> MARCAS_CONHECIDAS = CatalogoDominioDomRock.MARCAS;
    public static final Map<Integer, String> CARGOS_CONHECIDOS = CatalogoDominioDomRock.CARGOS_POR_CODIGO;
    public static final List<String> CANAIS_CONHECIDOS = CatalogoDominioDomRock.CANAIS;
    public static final Map<Integer, String> LOJAS_CONHECIDAS = CatalogoDominioDomRock.LOJAS;

    public static final Map<String, Object> DICIONARIO_CAMPOS_RECONHECIDOS = CatalogoDominioDomRock.obterDicionarioDimensoes();

    private final AiServiceClient aiClient;
    private final ArtefatoExplicativoService artefatoService;
    private final DominioCatalogoService dominioService;
    private final RegraConcorrenciaService regraConcorrenciaService;

    @org.springframework.beans.factory.annotation.Autowired
    public InterpretadorService(
            AiServiceClient aiClient,
            ArtefatoExplicativoService artefatoService,
            DominioCatalogoService dominioService,
            RegraConcorrenciaService regraConcorrenciaService) {
        this.aiClient = aiClient;
        this.artefatoService = artefatoService != null ? artefatoService : new ArtefatoExplicativoService();
        this.dominioService = dominioService != null ? dominioService : new DominioCatalogoService(null, null, null, null, null);
        this.regraConcorrenciaService = regraConcorrenciaService;
    }

    public InterpretadorService(AiServiceClient aiClient, ArtefatoExplicativoService artefatoService) {
        this(aiClient, artefatoService, null, null);
    }

    public InterpretadorService(AiServiceClient aiClient) {
        this(aiClient, new ArtefatoExplicativoService(), null, null);
    }

    public ArtefatoExplicativoService getArtefatoService() {
        return this.artefatoService;
    }

    public DominioCatalogoService getDominioService() {
        return this.dominioService;
    }


    public Map<String, Object> getDicionarioCamposReconhecidos() {
        return dominioService != null ? dominioService.obterDicionarioDimensoes() : DICIONARIO_CAMPOS_RECONHECIDOS;
    }

    public InterpretacaoRegraResponse processarInterpretacao(InterpretacaoRegraRequest request) {
        // 1. Enriquecer o contexto com o dicionário de dimensões reais antes de chamar o serviço de IA
        Map<String, Object> contextoEnriquecido = new HashMap<>();
        if (request.contexto() != null) {
            contextoEnriquecido.putAll(request.contexto());
        }
        contextoEnriquecido.putIfAbsent("ano_referencia", LocalDate.now().getYear());
        contextoEnriquecido.putIfAbsent("dicionario_dimensoes", getDicionarioCamposReconhecidos());
        InterpretacaoRegraRequest requestPreparado = new InterpretacaoRegraRequest(request.texto(), contextoEnriquecido);


        // Chamar o serviço de IA em Python
        InterpretacaoRegraResponse respostaBruta = aiClient.chamarServicoPython(requestPreparado);

        // 2. Validação defensiva no Spring Boot (zero-trust)
        List<String> pendencias = new ArrayList<>();
        if (respostaBruta.pendencias() != null) {
            pendencias.addAll(respostaBruta.pendencias());
        }

        // Validação e normalização de Canal
        String canalSanitizado = respostaBruta.canal() != null ? respostaBruta.canal().trim().toUpperCase() : null;

        // Validação e normalização de Marca (codMarca / descrMarca)
        Integer codMarca = respostaBruta.codMarca();
        String descrMarca = Marca.padronizar(respostaBruta.descrMarca());
        if (descrMarca != null && codMarca == null) {
            var marcaOpt = Marca.buscarPorNome(descrMarca);
            if (marcaOpt.isPresent()) {
                codMarca = marcaOpt.get().getCodigo();
                descrMarca = marcaOpt.get().getDescricao();
            }
        } else if (codMarca != null && descrMarca == null) {
            descrMarca = Marca.buscarPorCodigo(codMarca).map(Marca::getDescricao).orElse(null);
        }

        // Validação e normalização de Cargo (codCargo / descriCargo)
        Integer codCargo = respostaBruta.codCargo();
        String descriCargo = respostaBruta.descriCargo() != null ? respostaBruta.descriCargo().trim().toUpperCase() : null;
        if (descriCargo != null && codCargo == null) {
            var cargoOpt = Cargo.buscarPorDescricao(descriCargo);
            if (cargoOpt.isPresent()) {
                codCargo = cargoOpt.get().getCodigo();
                descriCargo = cargoOpt.get().getDescricao();
            } else {
                for (var entry : CARGOS_CONHECIDOS.entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(descriCargo)) {
                        codCargo = entry.getKey();
                        descriCargo = entry.getValue();
                        break;
                    }
                }
            }
            if (codCargo == null && descriCargo.contains("QUIOSQUE")) {
                codCargo = 150;
            }
        } else if (codCargo != null && descriCargo == null) {
            if (codCargo != 150) {
                descriCargo = CARGOS_CONHECIDOS.get(codCargo);
            } else {
                boolean jaTemPendenciaCargo = pendencias.stream().anyMatch(p -> p.contains("150") || p.toLowerCase().contains("cargo"));
                if (!jaTemPendenciaCargo) {
                    pendencias.add("Cargo 150 possui múltiplas funções (GERENTE DE LOJA, GERENTE QUIOSQUE). Favor especificar o cargo exato.");
                }
            }
        }

        // Validação e extração de Loja (codLoja)
        Integer codLoja = respostaBruta.codLoja();
        if (codLoja == null) {
            codLoja = CatalogoDominioDomRock.extrairCodigoLoja(request.texto()).orElse(null);
        }
        if (codLoja != null) {
            if (codLoja <= 0) {
                pendencias.add("Código da loja inválido (" + codLoja + "). Deve ser um número positivo.");
                codLoja = null;
            } else if (!dominioService.isLojaValida(codLoja)) {
                pendencias.add("Código da loja (" + codLoja + ") está fora da faixa de lojas cadastradas na rede (Lojas 1 a 80).");
            }

        }

        // Verificação de dimensões: aceitar qualquer dimensão válida sem descartar parâmetros
        boolean temAlgumaDimensao = canalSanitizado != null
                || codMarca != null || descrMarca != null
                || codCargo != null || descriCargo != null
                || codLoja != null;

        if (!temAlgumaDimensao) {
            pendencias.add("Nenhuma dimensão de público-alvo (marca, loja, cargo ou canal) identificada no texto. Favor selecionar manualmente.");
        }

        // Validação de taxa decimal
        BigDecimal taxa = respostaBruta.taxa();
        if (taxa == null) {
            pendencias.add("Percentual de comissão não identificado.");
        } else if (taxa.compareTo(BigDecimal.ZERO) <= 0 || taxa.compareTo(new BigDecimal("1.0000")) > 0) {
            pendencias.add("A taxa inferida (" + taxa + ") é inconsistente. Deve estar entre 0.0001 (0.01%) e 1.0000 (100%).");
            taxa = null;
        }

        // Validação de datas
        LocalDate inicio = respostaBruta.dataInicio();
        LocalDate fim = respostaBruta.dataFim();

        if (inicio != null && fim != null && fim.isBefore(inicio)) {
            pendencias.add("A data final inferida (" + fim + ") é anterior à data inicial (" + inicio + ").");
            fim = null;
        }

        if (inicio == null) {
            pendencias.add("Data de início não identificada; será atribuída a data atual se não informada.");
        }

        if (fim == null) {
            pendencias.add("Data final omitida; serão aplicados 30 dias de vigência padrão na confirmação.");
        }

        // 3. Devolver proposta mapeada sem salvar nem ativar no banco de dados
        return new InterpretacaoRegraResponse(
                canalSanitizado,
                codMarca,
                descrMarca,
                codCargo,
                descriCargo,
                codLoja,
                taxa,
                inicio,
                fim,
                respostaBruta.confianca() != null ? respostaBruta.confianca() : BigDecimal.ZERO,
                pendencias
        );
    }

    /**
     * Processa a extração de múltiplas propostas de regras a partir de texto livre com rastreabilidade e explicabilidade.
     */
    public InterpretacaoMultiplaResponse processarInterpretacaoMultipla(InterpretacaoMultiplaRequest request) {
        String texto = request.texto();
        Map<String, Object> contexto = request.contexto() != null ? request.contexto() : Map.of();

        List<String> trechos = dividirEmBlocos(texto);
        List<PropostaRegraDTO> propostas = new ArrayList<>();

        for (int i = 0; i < trechos.size(); i++) {
            String trecho = trechos.get(i);
            String blocoId = "bloco-" + (i + 1);

            InterpretacaoRegraRequest subRequest = new InterpretacaoRegraRequest(trecho, contexto);
            InterpretacaoRegraResponse respostaParcial = processarInterpretacao(subRequest);

            PropostaRegraDTO proposta = converterParaProposta(respostaParcial, blocoId, trecho, contexto);
            propostas.add(proposta);
        }

        String tituloSugerido = gerarTituloSugerido(propostas, texto);
        BigDecimal confiancaGeral = propostas.stream()
                .map(p -> Boolean.TRUE.equals(p.completa()) ? new BigDecimal("0.95") : new BigDecimal("0.70"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!propostas.isEmpty()) {
            confiancaGeral = confiancaGeral.divide(new BigDecimal(propostas.size()), 2, java.math.RoundingMode.HALF_UP);
        }

        return new InterpretacaoMultiplaResponse(
                tituloSugerido,
                propostas,
                propostas.size(),
                propostas.stream().anyMatch(p -> p.pendencias() != null && !p.pendencias().isEmpty()),
                propostas.stream().allMatch(p -> Boolean.TRUE.equals(p.completa())),
                confiancaGeral
        );
    }

    /**
     * Reinterpreta um bloco específico dentro da proposta, preservando o contexto e os demais blocos.
     */
    public ReinterpretarBlocoResponse reinterpretarBloco(ReinterpretarBlocoRequest request) {
        String blocoAlvoId = request.blocoId();
        List<PropostaRegraDTO> propostasAtuais = request.propostasAtuais();

        int indexAlvo = -1;
        for (int i = 0; i < propostasAtuais.size(); i++) {
            if (blocoAlvoId.equalsIgnoreCase(propostasAtuais.get(i).blocoId())) {
                indexAlvo = i;
                break;
            }
        }

        if (indexAlvo == -1) {
            throw new ResourceNotFoundException("Bloco com ID '" + blocoAlvoId + "' não encontrado na lista de propostas informada.");
        }

        InterpretacaoRegraRequest subRequest = new InterpretacaoRegraRequest(request.instrucaoAjuste(), request.contexto());
        InterpretacaoRegraResponse respostaAjuste = processarInterpretacao(subRequest);

        PropostaRegraDTO blocoAnterior = propostasAtuais.get(indexAlvo);
        PropostaRegraDTO blocoAtualizado = mesclarProposta(blocoAnterior, respostaAjuste, request.instrucaoAjuste(), request.contexto());

        List<PropostaRegraDTO> listaAtualizada = new ArrayList<>(propostasAtuais);
        listaAtualizada.set(indexAlvo, blocoAtualizado);

        String resumo = "Bloco '" + blocoAlvoId + "' reprocessado com sucesso com base na instrução de ajuste.";

        return new ReinterpretarBlocoResponse(blocoAlvoId, resumo, listaAtualizada);
    }

    /**
     * Atualiza os artefatos explicativos (XAI e Python equivalente) e recalcula taxas após edição manual de campos no front.
     */
    public AtualizarArtefatosResponse atualizarArtefatos(AtualizarArtefatosRequest request) {
        PropostaRegraDTO p = request.proposta();

        BigDecimal taxaCalculada = artefatoService.calcularTaxaEfetiva(p.operacaoBase());
        BigDecimal taxaFinal = taxaCalculada != null ? taxaCalculada : p.taxaFinal();

        PropostaRegraDTO candidata = new PropostaRegraDTO(
                p.blocoId(),
                p.trechoOrigem(),
                p.filtros(),
                p.condicaoValor(),
                p.operacaoBase(),
                taxaFinal,
                p.referenciasConsultadas(),
                p.origensPorCampo(),
                null,
                null,
                null,
                null,
                p.vigencia()
        );

        List<PendenciaPropostaDTO> pendencias = artefatoService.validarProposta(candidata);
        boolean completa = pendencias.stream().noneMatch(PendenciaPropostaDTO::isImpeditiva);

        String novaExplicacao = artefatoService.gerarExplicacao(candidata);
        String novoPython = artefatoService.gerarPythonEquivalente(candidata);

        return new AtualizarArtefatosResponse(
                p.blocoId(),
                taxaFinal,
                novaExplicacao,
                novoPython,
                pendencias,
                completa
        );
    }

    // --- Métodos Auxiliares de Extração e Conversão ---

    private List<String> dividirEmBlocos(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }

        String[] linhas = texto.split("\r?\n");
        List<String> blocos = new ArrayList<>();
        for (String linha : linhas) {
            String limpa = linha.trim();
            if (limpa.startsWith("-") || limpa.startsWith("*") || limpa.matches("^\\d+[.)].*")) {
                limpa = limpa.replaceFirst("^[-*\\d.)\\s]+", "").trim();
            }
            if (!limpa.isBlank()) {
                blocos.add(limpa);
            }
        }

        if (blocos.isEmpty()) {
            if (texto.contains(";")) {
                for (String parte : texto.split(";")) {
                    String pLimpa = parte.trim();
                    if (!pLimpa.isBlank()) {
                        blocos.add(pLimpa);
                    }
                }
            } else {
                blocos.add(texto.trim());
            }
        }
        return blocos;
    }

    private PropostaRegraDTO converterParaProposta(InterpretacaoRegraResponse resp, String blocoId, String trechoOrigem, Map<String, Object> contexto) {
        String matricula = CatalogoDominioDomRock.extrairMatricula(trechoOrigem).orElse(null);
        Integer codLoja = resp.codLoja() != null ? resp.codLoja() : CatalogoDominioDomRock.extrairCodigoLoja(trechoOrigem).orElse(null);

        FiltrosRegraDTO filtros = new FiltrosRegraDTO(
                resp.canal(),
                resp.codMarca(),
                resp.descrMarca(),
                codLoja,
                resp.codCargo(),
                resp.descriCargo(),
                matricula
        );

        FaixaValorDTO faixa = extrairFaixaValor(trechoOrigem);
        OperacaoBaseDTO operacao = extrairOperacaoBase(
                trechoOrigem,
                resp.taxa(),
                resp.codMarca(),
                resp.descrMarca(),
                resp.codCargo(),
                resp.descriCargo()
        );
        BigDecimal taxaFinal = artefatoService.calcularTaxaEfetiva(operacao);
        if (taxaFinal == null) {
            taxaFinal = resp.taxa();
        }

        Map<String, OrigemCampo> origens = new HashMap<>();
        if (resp.canal() != null) origens.put("canal", OrigemCampo.TEXTO);
        if (resp.codMarca() != null) origens.put("marca", OrigemCampo.TEXTO);
        if (codLoja != null) origens.put("loja", OrigemCampo.TEXTO);
        if (resp.codCargo() != null) origens.put("cargo", OrigemCampo.TEXTO);
        if (matricula != null) origens.put("matricula", OrigemCampo.TEXTO);
        if (resp.taxa() != null) origens.put("taxa", OrigemCampo.TEXTO);
        if (faixa.possuiFaixa()) origens.put("condicaoValor", OrigemCampo.TEXTO);

        origens.put("dataInicio", resp.dataInicio() != null ? OrigemCampo.TEXTO : OrigemCampo.PADRAO_SISTEMA);
        origens.put("dataFim", resp.dataFim() != null ? OrigemCampo.TEXTO : OrigemCampo.PADRAO_SISTEMA);

        List<ReferenciaConsultadaDTO> referencias = new ArrayList<>();
        if (resp.descrMarca() != null && resp.codMarca() != null) {
            referencias.add(new ReferenciaConsultadaDTO("MARCA", "cod_marca=" + resp.codMarca(), "Marca " + resp.descrMarca()));
        }
        if (resp.descriCargo() != null && resp.codCargo() != null) {
            referencias.add(new ReferenciaConsultadaDTO("CARGO", "cod_cargo=" + resp.codCargo(), "Cargo " + resp.descriCargo()));
        }
        if (matricula != null) {
            referencias.add(new ReferenciaConsultadaDTO("MATRICULA", "matricula=" + matricula, "Colaborador " + matricula));
        }
        if (codLoja != null) {
            referencias.add(new ReferenciaConsultadaDTO("LOJA", "cod_loja=" + codLoja, CatalogoDominioDomRock.formatarLoja(codLoja)));
        }
        if (operacao.referenciaBase() != null) {
            referencias.add(new ReferenciaConsultadaDTO("BASE_COMISS", operacao.referenciaBase().tipoBase(), operacao.referenciaBase().descricao()));
        }

        PeriodoVigenciaDTO vigencia = PeriodoVigenciaDTO.de(resp.dataInicio(), resp.dataFim());

        PropostaRegraDTO propostaBase = new PropostaRegraDTO(
                blocoId,
                trechoOrigem,
                filtros,
                faixa,
                operacao,
                taxaFinal,
                referencias,
                origens,
                null,
                null,
                null,
                null,
                vigencia
        );

        List<PendenciaPropostaDTO> pendencias = new ArrayList<>(artefatoService.validarProposta(propostaBase));
        if (resp.pendencias() != null) {
            for (String p : resp.pendencias()) {
                boolean jaExiste = pendencias.stream().anyMatch(pen -> pen.mensagem().equalsIgnoreCase(p));
                if (!jaExiste) {
                    pendencias.add(PendenciaPropostaDTO.aviso("geral", "AVISO_NLP", p));
                }
            }
        }

        boolean completa = pendencias.stream().noneMatch(PendenciaPropostaDTO::isImpeditiva);
        String explicacao = artefatoService.gerarExplicacao(propostaBase);
        String python = artefatoService.gerarPythonEquivalente(propostaBase);

        return new PropostaRegraDTO(
                blocoId,
                trechoOrigem,
                filtros,
                faixa,
                operacao,
                taxaFinal,
                referencias,
                origens,
                pendencias,
                completa,
                explicacao,
                python,
                vigencia
        );
    }

    private PropostaRegraDTO mesclarProposta(PropostaRegraDTO anterior, InterpretacaoRegraResponse novo, String instrucao, Map<String, Object> contexto) {
        String matricula = anterior.filtros().matricula();
        var matOpt = CatalogoDominioDomRock.extrairMatricula(instrucao);
        if (matOpt.isPresent()) {
            matricula = matOpt.get();
        }

        Integer codLoja = novo.codLoja() != null ? novo.codLoja() : anterior.filtros().codLoja();
        if (codLoja == null) {
            codLoja = CatalogoDominioDomRock.extrairCodigoLoja(instrucao).orElse(null);
        }

        FiltrosRegraDTO filtros = new FiltrosRegraDTO(
                novo.canal() != null ? novo.canal() : anterior.filtros().canal(),
                novo.codMarca() != null ? novo.codMarca() : anterior.filtros().codMarca(),
                novo.descrMarca() != null ? novo.descrMarca() : anterior.filtros().descrMarca(),
                codLoja,
                novo.codCargo() != null ? novo.codCargo() : anterior.filtros().codCargo(),
                novo.descriCargo() != null ? novo.descriCargo() : anterior.filtros().descriCargo(),
                matricula
        );

        FaixaValorDTO faixaNova = extrairFaixaValor(instrucao);
        FaixaValorDTO faixa = faixaNova.possuiFaixa() ? faixaNova : anterior.condicaoValor();

        OperacaoBaseDTO opNova = extrairOperacaoBase(
                instrucao,
                novo.taxa(),
                filtros.codMarca(),
                filtros.descrMarca(),
                filtros.codCargo(),
                filtros.descriCargo()
        );
        OperacaoBaseDTO operacao = (opNova.tipoOperacao() != TipoOperacaoBase.DEFINIR_TAXA || novo.taxa() != null)
                ? opNova
                : anterior.operacaoBase();

        BigDecimal taxaCalculada = artefatoService.calcularTaxaEfetiva(operacao);
        BigDecimal taxaFinal = taxaCalculada != null ? taxaCalculada : (novo.taxa() != null ? novo.taxa() : anterior.taxaFinal());

        PeriodoVigenciaDTO vigencia = new PeriodoVigenciaDTO(
                novo.dataInicio() != null ? novo.dataInicio() : anterior.vigencia().dataInicio(),
                novo.dataFim() != null ? novo.dataFim() : anterior.vigencia().dataFim()
        );

        PropostaRegraDTO propostaBase = new PropostaRegraDTO(
                anterior.blocoId(),
                anterior.trechoOrigem() + " [Ajuste: " + instrucao + "]",
                filtros,
                faixa,
                operacao,
                taxaFinal,
                anterior.referenciasConsultadas(),
                anterior.origensPorCampo(),
                null,
                null,
                null,
                null,
                vigencia
        );

        List<PendenciaPropostaDTO> pendencias = artefatoService.validarProposta(propostaBase);
        boolean completa = pendencias.stream().noneMatch(PendenciaPropostaDTO::isImpeditiva);
        String explicacao = artefatoService.gerarExplicacao(propostaBase);
        String python = artefatoService.gerarPythonEquivalente(propostaBase);

        return new PropostaRegraDTO(
                propostaBase.blocoId(),
                propostaBase.trechoOrigem(),
                filtros,
                faixa,
                operacao,
                taxaFinal,
                propostaBase.referenciasConsultadas(),
                propostaBase.origensPorCampo(),
                pendencias,
                completa,
                explicacao,
                python,
                vigencia
        );
    }

    private FaixaValorDTO extrairFaixaValor(String texto) {
        if (texto == null) return FaixaValorDTO.semLimites();
        String t = texto.toLowerCase();

        BigDecimal min = null;
        Boolean minInc = null;
        BigDecimal max = null;
        Boolean maxInc = null;

        // Regex para valores como R$ 5.000, 5000, 5.000,00
        var matcherMin = java.util.regex.Pattern.compile("(acima de|a partir de|superior a|maior que|m[ií]nimo de)\\s*(r\\$\\s*)?([0-9]+([.,][0-9]{3})*([.,][0-9]{1,2})?)").matcher(t);
        if (matcherMin.find()) {
            String termo = matcherMin.group(1);
            String valorStr = matcherMin.group(3).replace(".", "").replace(",", ".");
            try {
                min = new BigDecimal(valorStr);
                minInc = termo.contains("partir") || termo.contains("mínimo") || termo.contains("minimo");
            } catch (Exception ignored) {}
        }

        var matcherMax = java.util.regex.Pattern.compile("(at[eé]|inferior a|menor que|m[aá]ximo de)\\s*(r\\$\\s*)?([0-9]+([.,][0-9]{3})*([.,][0-9]{1,2})?)").matcher(t);
        if (matcherMax.find()) {
            String termo = matcherMax.group(1);
            String valorStr = matcherMax.group(3).replace(".", "").replace(",", ".");
            try {
                max = new BigDecimal(valorStr);
                maxInc = termo.contains("até") || termo.contains("ate") || termo.contains("máximo") || termo.contains("maximo");
            } catch (Exception ignored) {}
        }

        return new FaixaValorDTO(min, minInc, max, maxInc);
    }

    public OperacaoBaseDTO extrairOperacaoBase(String texto, BigDecimal taxaExtraida) {
        return extrairOperacaoBase(texto, taxaExtraida, null, null, null, null);
    }

    public OperacaoBaseDTO extrairOperacaoBase(
            String texto,
            BigDecimal taxaExtraida,
            Integer codMarca,
            String descrMarca,
            Integer codCargo,
            String descriCargo
    ) {
        if (texto == null) return OperacaoBaseDTO.definirTaxa(taxaExtraida);
        String t = texto.toLowerCase();

        BigDecimal taxaBaseReferencia = dominioService.obterTaxaBaseComFallback(codMarca, codCargo, descriCargo);
        String descrReferencia = dominioService.gerarDescricaoReferencia(codMarca, descrMarca, codCargo, descriCargo);


        // 1. Acréscimo sobre a base
        if (t.contains("acréscimo") || t.contains("acrescimo") || t.contains("taxa base +") || t.contains("base +") || t.contains("+") && t.contains("base")) {
            BigDecimal ajuste = extrairPercentualOuNumero(t);
            ReferenciaBaseDTO ref = new ReferenciaBaseDTO("BASE_COMISS", taxaBaseReferencia, descrReferencia);
            return new OperacaoBaseDTO(TipoOperacaoBase.ACRESCIMO_PONTOS, ajuste != null ? ajuste : new BigDecimal("0.0150"), ref);
        }

        // 2. Desconto sobre a base
        if (t.contains("desconto") || t.contains("taxa base -") || t.contains("base -") || t.contains("-") && t.contains("base")) {
            BigDecimal ajuste = extrairPercentualOuNumero(t);
            ReferenciaBaseDTO ref = new ReferenciaBaseDTO("BASE_COMISS", taxaBaseReferencia, descrReferencia);
            return new OperacaoBaseDTO(TipoOperacaoBase.DESCONTO_PONTOS, ajuste != null ? ajuste : new BigDecimal("0.0050"), ref);
        }

        // 3. Multiplicador da base
        if (t.contains("multiplicad") || t.contains("fator") || t.contains("x a base") || t.contains("vezes a base")) {
            BigDecimal fator = extrairNumero(t);
            ReferenciaBaseDTO ref = new ReferenciaBaseDTO("BASE_COMISS", taxaBaseReferencia, descrReferencia);
            return new OperacaoBaseDTO(TipoOperacaoBase.MULTIPLICADOR_BASE, fator != null ? fator : new BigDecimal("1.5000"), ref);
        }

        // 4. Divisor da base
        if (t.contains("dividid") || t.contains("divisor") || t.contains("/ a base") || t.contains("metade da base") || t.contains("/ 2")) {
            BigDecimal fator = t.contains("metade") ? new BigDecimal("2.0000") : extrairNumero(t);
            ReferenciaBaseDTO ref = new ReferenciaBaseDTO("BASE_COMISS", taxaBaseReferencia, descrReferencia);
            return new OperacaoBaseDTO(TipoOperacaoBase.DIVISOR_BASE, fator != null ? fator : new BigDecimal("2.0000"), ref);
        }

        return OperacaoBaseDTO.definirTaxa(taxaExtraida);
    }

    private BigDecimal extrairPercentualOuNumero(String texto) {
        var m = java.util.regex.Pattern.compile("([0-9]+([.,][0-9]+)?)\\s*%").matcher(texto);
        if (m.find()) {
            try {
                BigDecimal valor = new BigDecimal(m.group(1).replace(",", "."));
                return valor.divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private BigDecimal extrairNumero(String texto) {
        var m = java.util.regex.Pattern.compile("([0-9]+([.,][0-9]+)?)").matcher(texto);
        if (m.find()) {
            try {
                return new BigDecimal(m.group(1).replace(",", ".")).setScale(4, java.math.RoundingMode.HALF_UP);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String gerarTituloSugerido(List<PropostaRegraDTO> propostas, String texto) {
        if (propostas == null || propostas.isEmpty()) {
            return "Nova Campanha";
        }
        if (propostas.size() == 1) {
            PropostaRegraDTO p = propostas.get(0);
            if (p.filtros().canal() != null) {
                return "Campanha Canal " + p.filtros().canal();
            }
            if (p.filtros().descrMarca() != null) {
                return "Campanha Marca " + p.filtros().descrMarca();
            }
        }
        return "Campanha Múltiplas Regras (" + propostas.size() + " blocos)";
    }

    public ValidarPropostaResponse validarProposta(ValidarPropostaRequest request) {
        if (request == null) {
            return new ValidarPropostaResponse(false, List.of("Dados da proposta não fornecidos"), List.of(), List.of(), null);
        }

        List<String> erros = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        List<ReferenciaConsultadaDTO> referencias = new ArrayList<>();

        // 1. Marca
        if (request.codMarca() != null) {
            if (!dominioService.isMarcaValida(request.codMarca())) {
                erros.add("Marca com código " + request.codMarca() + " não foi encontrada no cadastro de marcas.");
            } else {
                String descr = dominioService.buscarDescricaoMarca(request.codMarca())
                        .orElse(request.descrMarca() != null ? request.descrMarca() : "Marca " + request.codMarca());
                referencias.add(new ReferenciaConsultadaDTO("MARCA", "cod_marca=" + request.codMarca(), descr));
            }
        }

        // 2. Cargo
        if (request.codCargo() != null) {
            if (!dominioService.isCargoValido(request.codCargo())) {
                erros.add("Cargo com código " + request.codCargo() + " não foi encontrado no cadastro de cargos.");
            } else {
                String descr = dominioService.buscarDescricaoCargo(request.codCargo())
                        .orElse(request.descriCargo() != null ? request.descriCargo() : "Cargo " + request.codCargo());
                referencias.add(new ReferenciaConsultadaDTO("CARGO", "cod_cargo=" + request.codCargo(), descr));
            }
        }

        // 3. Loja
        if (request.codLoja() != null) {
            if (request.codLoja() <= 0 || !dominioService.isLojaValida(request.codLoja())) {
                erros.add("Loja com código " + request.codLoja() + " não foi encontrada no cadastro de lojas ativas.");
            } else {
                String descr = dominioService.buscarDescricaoLoja(request.codLoja())
                        .orElse("LOJA-" + request.codLoja());
                referencias.add(new ReferenciaConsultadaDTO("LOJA", "cod_loja=" + request.codLoja(), descr));
            }
        }

        // 4. Canal
        if (request.canal() != null && !request.canal().isBlank() && !"PADRAO".equalsIgnoreCase(request.canal().trim())) {
            if (!dominioService.isCanalValido(request.canal())) {
                erros.add("Canal de venda '" + request.canal() + "' não é reconhecido. Canais válidos: " + dominioService.obterCanais());
            } else {
                referencias.add(new ReferenciaConsultadaDTO("CANAL", "canal=" + request.canal().trim().toUpperCase(), request.canal().trim().toUpperCase()));
            }
        }

        // 5. Matrícula
        if (request.matricula() != null && !request.matricula().isBlank()) {
            if (!dominioService.isMatriculaValida(request.matricula())) {
                erros.add("Matrícula '" + request.matricula() + "' não foi encontrada no cadastro de colaboradores.");
            } else {
                var regOpt = dominioService.buscarMatricula(request.matricula());
                if (regOpt.isPresent()) {
                    var reg = regOpt.get();
                    referencias.add(new ReferenciaConsultadaDTO("COLABORADOR", "matricula=" + request.matricula(), "Colaborador " + request.matricula() + " (Ativo)"));

                    if (request.codLoja() != null && reg.getStore() != null && reg.getStore().getCode() != null) {
                        if (!reg.getStore().getCode().equals(request.codLoja())) {
                            avisos.add("Divergência cadastral: a matrícula " + request.matricula() + " está alocada na Loja " + reg.getStore().getCode() + ", mas a proposta especifica a Loja " + request.codLoja() + ".");
                        }
                    }

                    if (request.codCargo() != null && reg.getPosition() != null && reg.getPosition().getCode() != null) {
                        if (!reg.getPosition().getCode().equals(request.codCargo())) {
                            avisos.add("Divergência cadastral: a matrícula " + request.matricula() + " possui cargo com código " + reg.getPosition().getCode() + ", mas a proposta especifica o código de cargo " + request.codCargo() + ".");
                        }
                    }
                } else {
                    referencias.add(new ReferenciaConsultadaDTO("COLABORADOR", "matricula=" + request.matricula(), "Colaborador " + request.matricula()));
                }
            }
        }

        // 6. Taxa e Base Contratual
        if (request.taxa() != null) {
            if (request.taxa().compareTo(BigDecimal.ZERO) <= 0) {
                erros.add("A taxa de comissão deve ser um valor estritamente positivo (> 0).");
            } else if (request.taxa().compareTo(new BigDecimal("1.0")) > 0) {
                avisos.add("A taxa informada (" + request.taxa() + ") é maior que 100%. Verifique se o valor não deveria estar no formato decimal (ex: 0.05 para 5%).");
            }

            var taxaBaseOpt = dominioService.obterTaxaBaseContratual(request.codMarca(), request.codCargo(), request.descriCargo());
            if (taxaBaseOpt.isPresent()) {
                referencias.add(new ReferenciaConsultadaDTO(
                        "TAXA_BASE_CONTRATUAL",
                        "cod_marca=" + request.codMarca() + ", cod_cargo=" + request.codCargo(),
                        dominioService.gerarDescricaoReferencia(request.codMarca(), request.descrMarca(), request.codCargo(), request.descriCargo())
                ));
            }
        }

        // 7. Período
        if (request.dataInicio() != null && request.dataFim() != null) {
            if (request.dataInicio().isAfter(request.dataFim())) {
                erros.add("A data de início (" + request.dataInicio() + ") não pode ser posterior à data de término (" + request.dataFim() + ").");
            }
        }

        // 8. Análise de Concorrência
        com.bugbusters.backend.dto.regra.ConsultaConcorrenciaResponse analiseConcorrencia = null;
        if (regraConcorrenciaService != null) {
            var reqConcorrencia = new com.bugbusters.backend.dto.regra.ConsultaConcorrenciaRequest(
                    request.campanhaId(),
                    request.codMarca(),
                    request.codCargo(),
                    request.codLoja(),
                    request.canal(),
                    request.matricula(),
                    request.dataInicio(),
                    request.dataFim()
            );
            analiseConcorrencia = regraConcorrenciaService.analisarConcorrencia(reqConcorrencia);
            if (analiseConcorrencia.temEmpate()) {
                avisos.add("Atenção: existem regras ativas concorrentes com o mesmo nível de prioridade (empate), o que gerará conflito em vendas simultâneas.");
            }
            if (analiseConcorrencia.temIdentica()) {
                avisos.add("Atenção: já existe uma regra exatamente idêntica cadastrada no mesmo período.");
            }
        }

        boolean valida = erros.isEmpty();
        return new ValidarPropostaResponse(valida, erros, avisos, referencias, analiseConcorrencia);
    }
}