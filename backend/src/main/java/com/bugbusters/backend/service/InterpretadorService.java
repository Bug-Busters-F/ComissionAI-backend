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
import com.bugbusters.backend.exception.AiInvalidResponseException;
import com.bugbusters.backend.exception.AiServiceUnavailableException;
import com.bugbusters.backend.exception.AiTimeoutException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.Cargo;
import com.bugbusters.backend.model.CatalogoDominioDomRock;
import com.bugbusters.backend.model.Marca;

import com.bugbusters.backend.service.client.AiServiceClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InterpretadorService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InterpretadorService.class);

    public static final Map<Integer, String> MARCAS_CONHECIDAS = CatalogoDominioDomRock.MARCAS;
    public static final Map<Integer, String> CARGOS_CONHECIDOS = CatalogoDominioDomRock.CARGOS_POR_CODIGO;
    public static final List<String> CANAIS_CONHECIDOS = CatalogoDominioDomRock.CANAIS;
    public static final Map<Integer, String> LOJAS_CONHECIDAS = CatalogoDominioDomRock.LOJAS;

    public static final Map<String, Object> DICIONARIO_CAMPOS_RECONHECIDOS = CatalogoDominioDomRock.obterDicionarioDimensoes();

    /** Chaves aceitas no contexto da requisição que o agente de IA espera no topo do payload. */
    private static final List<String> CAMPOS_TOPO_AGENTE = List.of("vigenciaCampanha", "dataReferencia", "competencia", "catalogo");

    /** Pendências do agente que não impedem ativar a regra. */
    private static final java.util.Set<String> PENDENCIAS_AGENTE_AVISO = java.util.Set.of("VIGENCIA_INCOMPATIVEL", "TRECHO_INCOMPLETO");

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
     * Extrai as propostas de regras do texto livre usando o agente de interpretação do serviço de IA
     * (/api/v2/interpretar), que separa as regras, guarda o trecho de origem de cada uma e confere marcas,
     * cargos, lojas e matrículas no cadastro pelas consultas-ia. A resposta do agente é convertida para
     * PropostaRegraDTO; ajustes sobre a taxa base e os artefatos explicativos continuam sendo calculados aqui.
     * Nada é aprovado nem persistido.
     */
    public InterpretacaoMultiplaResponse processarInterpretacaoMultipla(InterpretacaoMultiplaRequest request) {
        Map<String, Object> contexto = request.contexto() != null ? request.contexto() : Map.of();
        PeriodoVigenciaDTO vigenciaCampanha = extrairVigenciaCampanha(contexto);

        Map<String, Object> resposta = aiClient.chamarServicoPythonV2(montarPayloadAgente(request.texto(), contexto));
        if (resposta == null) {
            throw new AiInvalidResponseException("O serviço de IA retornou uma resposta vazia.");
        }
        if (resposta.containsKey("propostas") && !(resposta.get("propostas") instanceof List)) {
            throw new AiInvalidResponseException("O campo 'propostas' retornado pelo serviço de IA deve ser uma lista.");
        }

        List<PendenciaPropostaDTO> pendenciasGerais = new ArrayList<>();
        for (Map<String, Object> p : listaDeMapas(resposta.get("pendenciasGerais"))) {
            pendenciasGerais.add(pendenciaDoAgente(
                    texto(p.get("campo"), "geral"),
                    texto(p.get("codigo"), "AVISO_AGENTE"),
                    texto(p.get("mensagem"), "")
            ));
        }
        for (Map<String, Object> trecho : listaDeMapas(resposta.get("trechosNaoMapeados"))) {
            pendenciasGerais.add(PendenciaPropostaDTO.aviso("texto", "TRECHO_NAO_MAPEADO",
                    "Trecho do pedido que não virou regra: '" + texto(trecho.get("trecho"), "") + "'."));
        }

        List<?> propostasBrutas = resposta.get("propostas") instanceof List<?> l ? l : List.of();
        List<Map<String, Object>> blocosValidos = new ArrayList<>();
        for (int i = 0; i < propostasBrutas.size(); i++) {
            Object item = propostasBrutas.get(i);
            if (item instanceof Map<?, ?> itemMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> blocoMap = (Map<String, Object>) itemMap;
                blocosValidos.add(blocoMap);
            } else {
                pendenciasGerais.add(PendenciaPropostaDTO.impedimento(
                        "propostas[" + i + "]",
                        "BLOCO_INVALIDO",
                        "Falha ao converter o bloco de índice " + i + " retornado pelo serviço de IA: formato de bloco inválido."
                ));
            }
        }

        Map<String, Long> contagemIds = blocosValidos.stream()
                .map(InterpretadorService::extrairIdAgente)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        Set<String> idsUsados = new HashSet<>();
        List<PropostaRegraDTO> propostas = new ArrayList<>();
        BigDecimal somaConfianca = BigDecimal.ZERO;

        for (int i = 0; i < blocosValidos.size(); i++) {
            Map<String, Object> bloco = blocosValidos.get(i);
            String idAgente = extrairIdAgente(bloco);
            String blocoId;
            if (idAgente != null && contagemIds.getOrDefault(idAgente, 0L) == 1L && !idsUsados.contains(idAgente)) {
                blocoId = idAgente;
            } else {
                blocoId = "bloco-" + (i + 1);
            }
            int contador = i + 1;
            while (idsUsados.contains(blocoId)) {
                contador++;
                blocoId = "bloco-" + contador;
            }
            idsUsados.add(blocoId);

            try {
                PropostaRegraDTO proposta = converterBlocoDoAgente(bloco, blocoId, vigenciaCampanha, contexto);
                propostas.add(proposta);
                BigDecimal confianca = decimal(bloco.get("confianca"));
                somaConfianca = somaConfianca.add(confianca != null ? confianca
                        : Boolean.TRUE.equals(proposta.completa()) ? new BigDecimal("0.95") : new BigDecimal("0.70"));
            } catch (Exception e) {
                pendenciasGerais.add(PendenciaPropostaDTO.impedimento(
                        "propostas[" + i + "]",
                        "BLOCO_INVALIDO",
                        "Falha ao converter o bloco de índice " + i + " retornado pelo serviço de IA: " + e.getMessage()
                ));
            }
        }

        if (propostas.isEmpty()) {
            pendenciasGerais.add(PendenciaPropostaDTO.impedimento(
                    "texto",
                    "NENHUMA_PROPOSTA",
                    "Nenhuma regra foi identificada no texto informado."
            ));
        }

        BigDecimal confiancaGeral = propostas.isEmpty() ? BigDecimal.ZERO
                : somaConfianca.divide(new BigDecimal(propostas.size()), 2, java.math.RoundingMode.HALF_UP);

        boolean temPendenciaGeralImpeditiva = pendenciasGerais.stream().anyMatch(PendenciaPropostaDTO::isImpeditiva);
        boolean possuiPendencias = !pendenciasGerais.isEmpty() || propostas.stream().anyMatch(p -> !p.pendencias().isEmpty());
        boolean todasCompletas = !propostas.isEmpty()
                && !temPendenciaGeralImpeditiva
                && propostas.stream().allMatch(p -> Boolean.TRUE.equals(p.completa()));

        return new InterpretacaoMultiplaResponse(
                gerarTituloSugerido(propostas, request.texto()),
                propostas,
                propostas.size(),
                possuiPendencias,
                todasCompletas,
                confiancaGeral,
                pendenciasGerais
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

        Map<String, Object> contexto = request.contexto();
        PeriodoVigenciaDTO vigenciaCampanha = extrairVigenciaCampanha(contexto);

        List<Map<String, Object>> demaisBlocos = new ArrayList<>();
        for (int i = 0; i < propostasAtuais.size(); i++) {
            if (i != indexAlvo) {
                demaisBlocos.add(blocoParaAgente(propostasAtuais.get(i)));
            }
        }
        Map<String, Object> payload = montarPayloadContexto(contexto);
        payload.put("idLocal", blocoAlvoId);
        payload.put("textoAlterado", request.instrucaoAjuste());
        payload.put("blocoAtual", blocoParaAgente(propostasAtuais.get(indexAlvo)));
        payload.put("demaisBlocos", demaisBlocos);

        Map<String, Object> resposta = aiClient.reinterpretarBlocoV2(payload);
        if (resposta == null) {
            throw new AiInvalidResponseException("O serviço de IA retornou uma resposta vazia.");
        }
        if (resposta.get("propostasSubstituicao") != null && !(resposta.get("propostasSubstituicao") instanceof List)) {
            throw new AiInvalidResponseException("O campo 'propostasSubstituicao' retornado pelo serviço de IA deve ser uma lista.");
        }
        List<Map<String, Object>> substitutos = listaDeMapas(resposta.get("propostasSubstituicao"));
        if (substitutos.isEmpty()) {
            throw new AiInvalidResponseException("O serviço de IA não devolveu nenhuma proposta para o bloco '" + blocoAlvoId + "'.");
        }

        // O primeiro substituto mantém o id do bloco alvo; blocos extras (divisão em várias regras) ganham ids próprios.
        Set<String> idsUsados = new HashSet<>();
        for (int i = 0; i < propostasAtuais.size(); i++) {
            if (i != indexAlvo) {
                idsUsados.add(propostasAtuais.get(i).blocoId().toLowerCase());
            }
        }
        List<PropostaRegraDTO> substituidas = new ArrayList<>();
        for (int i = 0; i < substitutos.size(); i++) {
            String id = i == 0 ? blocoAlvoId : extrairIdAgente(substitutos.get(i));
            int contador = i + 1;
            while (id == null || idsUsados.contains(id.toLowerCase()) || (i > 0 && id.equalsIgnoreCase(blocoAlvoId))) {
                id = blocoAlvoId + "_" + contador++;
            }
            idsUsados.add(id.toLowerCase());
            substituidas.add(converterBlocoDoAgente(substitutos.get(i), id, vigenciaCampanha, contexto));
        }

        List<PendenciaPropostaDTO> pendenciasGerais = new ArrayList<>();
        for (Map<String, Object> p : listaDeMapas(resposta.get("pendenciasGerais"))) {
            pendenciasGerais.add(pendenciaDoAgente(texto(p.get("campo"), "geral"), texto(p.get("codigo"), "AVISO_AGENTE"), texto(p.get("mensagem"), "")));
        }
        if (!pendenciasGerais.isEmpty()) {
            substituidas.set(0, comPendenciasAdicionais(substituidas.get(0), pendenciasGerais));
        }

        List<PropostaRegraDTO> listaAtualizada = new ArrayList<>(propostasAtuais);
        listaAtualizada.remove(indexAlvo);
        listaAtualizada.addAll(indexAlvo, substituidas);

        StringBuilder resumo = new StringBuilder("Bloco '" + blocoAlvoId + "' reinterpretado pelo agente de IA");
        if (substituidas.size() > 1) {
            resumo.append(" e dividido em ").append(substituidas.size()).append(" regras");
        }
        List<String> camposAlterados = listaDeTextos(resposta.get("camposAlterados"));
        if (!camposAlterados.isEmpty()) {
            resumo.append("; campos alterados: ").append(String.join(", ", camposAlterados));
        }
        resumo.append('.');

        return new ReinterpretarBlocoResponse(blocoAlvoId, resumo.toString(), listaAtualizada);
    }

    /**
     * Atualiza os artefatos explicativos (XAI e Python equivalente) e recalcula taxas após edição manual de campos no front.
     */
    public AtualizarArtefatosResponse atualizarArtefatos(AtualizarArtefatosRequest request) {
        PropostaRegraDTO p = request.proposta();

        OperacaoBaseDTO operacao = p.operacaoBase();
        if (request.camposEditados().contains("taxa")) {
            // taxa digitada pelo usuário é a origem da regra: não é recalculada sobre a base
            operacao = OperacaoBaseDTO.definirTaxa(p.taxaFinal());
        }
        YearMonth ymRef = (p.vigencia() != null && p.vigencia().dataInicio() != null)
                ? YearMonth.from(p.vigencia().dataInicio())
                : null;

        if (operacao != null && operacao.tipoOperacao() != TipoOperacaoBase.DEFINIR_TAXA) {
            Integer codMarca = p.filtros() != null ? p.filtros().codMarca() : null;
            String descrMarca = p.filtros() != null ? p.filtros().descrMarca() : null;
            Integer codCargo = p.filtros() != null ? p.filtros().codCargo() : null;
            String descriCargo = p.filtros() != null ? p.filtros().descriCargo() : null;
            BigDecimal base = ymRef != null
                    ? dominioService.obterTaxaBaseContratual(codMarca, codCargo, descriCargo, ymRef).orElse(null)
                    : null;
            String descrRef = ymRef != null
                    ? dominioService.gerarDescricaoReferencia(codMarca, descrMarca, codCargo, descriCargo, ymRef)
                    : "Taxa Contratual Padrão";
            ReferenciaBaseDTO ref = new ReferenciaBaseDTO("BASE_COMISS", base, descrRef);
            operacao = new OperacaoBaseDTO(operacao.tipoOperacao(), operacao.valorAjuste(), ref);
        }

        BigDecimal taxaCalculada = artefatoService.calcularTaxaEfetiva(operacao);
        BigDecimal taxaFinal;
        if (operacao != null && operacao.tipoOperacao() != TipoOperacaoBase.DEFINIR_TAXA) {
            taxaFinal = taxaCalculada;
        } else {
            taxaFinal = taxaCalculada != null ? taxaCalculada : p.taxaFinal();
        }

        PropostaRegraDTO candidata = new PropostaRegraDTO(
                p.blocoId(),
                p.trechoOrigem(),
                p.filtros(),
                p.condicaoValor(),
                operacao,
                taxaFinal,
                p.referenciasConsultadas(),
                p.origensPorCampo(),
                null,
                null,
                null,
                null,
                p.vigencia(),
                p.bonusPercentual(),
                p.bonusValorFixo()
        );

        List<PendenciaPropostaDTO> pendencias = artefatoService.validarProposta(candidata);
        boolean completa = pendencias.stream().noneMatch(PendenciaPropostaDTO::isImpeditiva);

        String[] artefatos = gerarArtefatos(candidata, request.camposEditados());
        String novaExplicacao = artefatos[0];
        String novoPython = artefatos[1];

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


    /**
     * Payload do agente. O front manda vigência, competência e catálogo dentro de "contexto"; o agente os espera
     * no topo e, em "contexto", guarda só os ids da campanha (o resto é ignorado e a vigência se perderia).
     */
    private Map<String, Object> montarPayloadAgente(String texto, Map<String, Object> contextoRecebido) {
        Map<String, Object> payload = montarPayloadContexto(contextoRecebido);
        payload.put("texto", texto);
        return payload;
    }

    private Map<String, Object> montarPayloadContexto(Map<String, Object> contextoRecebido) {
        Map<String, Object> contexto = new HashMap<>(contextoRecebido);
        Map<String, Object> payload = new HashMap<>();
        for (String campo : CAMPOS_TOPO_AGENTE) {
            Object valor = contexto.remove(campo);
            if (valor != null) {
                payload.put(campo, valor);
            }
        }
        payload.put("contexto", contexto);
        return payload;
    }

    /**
     * Gera explicação e Python do bloco no serviço de IA (/api/v2/atualizar-artefatos), a partir dos campos já
     * recalculados aqui. Se o serviço estiver indisponível, usa a geração local para a edição não ficar bloqueada.
     */
    private String[] gerarArtefatos(PropostaRegraDTO proposta, List<String> camposEditados) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("bloco", blocoParaAgente(proposta));
            payload.put("camposEditados", camposEditados);
            Map<String, Object> resposta = aiClient.atualizarArtefatosV2(payload);
            Map<String, Object> bloco = mapa(resposta == null ? null : resposta.get("bloco"));
            String explicacao = texto(bloco.get("explicacao"), null);
            String codigo = texto(bloco.get("codigoPython"), null);
            if (explicacao != null && !explicacao.isBlank() && codigo != null && !codigo.isBlank()) {
                return new String[] {explicacao, codigo};
            }
            log.warn("Serviço de IA devolveu artefatos incompletos para o bloco {}; usando geração local.", proposta.blocoId());
        } catch (AiServiceUnavailableException | AiTimeoutException | AiInvalidResponseException ex) {
            log.warn("Serviço de IA indisponível ao atualizar artefatos do bloco {} ({}); usando geração local.",
                    proposta.blocoId(), ex.getMessage());
        }
        return new String[] {artefatoService.gerarExplicacao(proposta), artefatoService.gerarPythonEquivalente(proposta)};
    }

    /** Operação devolvida pelo agente (contrato v2) no modelo de operações do backend. */
    private OperacaoBaseDTO operacaoDoAgente(Map<String, Object> operacao, Map<String, Object> bloco,
                                             FiltrosRegraDTO filtros, YearMonth ymRef) {
        String tipo = texto(operacao.get("tipo"), "TAXA_ABSOLUTA");
        BigDecimal valor = decimal(operacao.get("valor"));
        if ("TAXA_ABSOLUTA".equals(tipo) || valor == null) {
            return OperacaoBaseDTO.definirTaxa(valor);
        }
        TipoOperacaoBase tipoBackend;
        BigDecimal valorBackend = valor;
        switch (tipo) {
            case "ADICIONAR_PP" -> tipoBackend = TipoOperacaoBase.ACRESCIMO_PONTOS;
            case "REDUZIR_PP" -> tipoBackend = TipoOperacaoBase.DESCONTO_PONTOS;
            case "AUMENTAR_PERCENTUAL" -> {
                tipoBackend = TipoOperacaoBase.MULTIPLICADOR_BASE;
                valorBackend = BigDecimal.ONE.add(valor);
            }
            case "REDUZIR_PERCENTUAL" -> {
                tipoBackend = TipoOperacaoBase.MULTIPLICADOR_BASE;
                valorBackend = BigDecimal.ONE.subtract(valor);
            }
            default -> {
                return OperacaoBaseDTO.definirTaxa(decimal(bloco.get("taxa")));
            }
        }
        BigDecimal taxaBase = listaDeMapas(bloco.get("referencias")).stream()
                .filter(r -> "BASE_COMISS".equals(r.get("fonte")))
                .map(r -> decimal(r.get("taxaBase")))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        ReferenciaBaseDTO referencia = taxaBase == null ? null : new ReferenciaBaseDTO("BASE_COMISS", taxaBase,
                ymRef != null
                        ? dominioService.gerarDescricaoReferencia(filtros.codMarca(), filtros.descrMarca(), filtros.codCargo(), filtros.descriCargo(), ymRef)
                        : "Taxa Contratual Padrão");
        return new OperacaoBaseDTO(tipoBackend, valorBackend, referencia);
    }

    /** Proposta do backend no formato de bloco do contrato v2 do agente. */
    private Map<String, Object> blocoParaAgente(PropostaRegraDTO p) {
        Map<String, Object> condicoes = new LinkedHashMap<>();
        FiltrosRegraDTO f = p.filtros();
        colocarSeNaoNulo(condicoes, "canal", f.canal());
        colocarSeNaoNulo(condicoes, "codMarca", f.codMarca());
        colocarSeNaoNulo(condicoes, "descrMarca", f.descrMarca());
        colocarSeNaoNulo(condicoes, "codCargo", f.codCargo());
        colocarSeNaoNulo(condicoes, "descriCargo", f.descriCargo());
        colocarSeNaoNulo(condicoes, "codLoja", f.codLoja());
        colocarSeNaoNulo(condicoes, "matricula", f.matricula());
        FaixaValorDTO faixa = p.condicaoValor();
        if (faixa != null && faixa.possuiFaixa()) {
            Map<String, Object> faixaAgente = new LinkedHashMap<>();
            colocarSeNaoNulo(faixaAgente, "min", faixa.valorMinimo());
            colocarSeNaoNulo(faixaAgente, "max", faixa.valorMaximo());
            colocarSeNaoNulo(faixaAgente, "minInclusivo", faixa.minInclusivo());
            colocarSeNaoNulo(faixaAgente, "maxInclusivo", faixa.maxInclusivo());
            condicoes.put("faixaValor", faixaAgente);
        }

        Map<String, Object> bloco = new LinkedHashMap<>();
        bloco.put("idLocal", p.blocoId());
        bloco.put("condicoes", condicoes);

        OperacaoBaseDTO op = p.operacaoBase();
        List<Map<String, Object>> referencias = new ArrayList<>();
        if (op != null && op.tipoOperacao() != null && op.valorAjuste() != null) {
            String tipo = null;
            BigDecimal valor = op.valorAjuste();
            switch (op.tipoOperacao()) {
                case DEFINIR_TAXA -> tipo = "TAXA_ABSOLUTA";
                case ACRESCIMO_PONTOS -> tipo = "ADICIONAR_PP";
                case DESCONTO_PONTOS -> tipo = "REDUZIR_PP";
                case MULTIPLICADOR_BASE -> {
                    boolean aumento = valor.compareTo(BigDecimal.ONE) >= 0;
                    tipo = aumento ? "AUMENTAR_PERCENTUAL" : "REDUZIR_PERCENTUAL";
                    valor = aumento ? valor.subtract(BigDecimal.ONE) : BigDecimal.ONE.subtract(valor);
                }
                default -> tipo = null; // DIVISOR_BASE não tem equivalente no agente: vale a taxa final
            }
            if (tipo != null) {
                Map<String, Object> operacao = new LinkedHashMap<>();
                operacao.put("tipo", tipo);
                operacao.put("valor", valor);
                bloco.put("operacao", operacao);
            }
        }
        if (op != null && op.referenciaBase() != null && op.referenciaBase().taxaBaseConsultada() != null) {
            Map<String, Object> chave = new LinkedHashMap<>();
            colocarSeNaoNulo(chave, "codMarca", f.codMarca());
            colocarSeNaoNulo(chave, "codCargo", f.codCargo());
            if (p.vigencia() != null && p.vigencia().dataInicio() != null) {
                chave.put("competencia", YearMonth.from(p.vigencia().dataInicio()).toString());
            }
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("fonte", "BASE_COMISS");
            ref.put("chave", chave);
            ref.put("taxaBase", op.referenciaBase().taxaBaseConsultada());
            referencias.add(ref);
        }
        bloco.put("referencias", referencias);
        colocarSeNaoNulo(bloco, "taxa", p.taxaFinal());
        if (p.bonusPercentual() != null || p.bonusValorFixo() != null) {
            Map<String, Object> bonus = new LinkedHashMap<>();
            bonus.put("percentual", p.bonusPercentual());
            bonus.put("valorFixo", p.bonusValorFixo());
            bloco.put("bonus", bonus);
        }
        if (p.vigencia() != null) {
            colocarSeNaoNulo(bloco, "dataInicio", p.vigencia().dataInicio() == null ? null : p.vigencia().dataInicio().toString());
            colocarSeNaoNulo(bloco, "dataFim", p.vigencia().dataFim() == null ? null : p.vigencia().dataFim().toString());
        }
        if (p.trechoOrigem() != null && !p.trechoOrigem().isBlank()) {
            bloco.put("origem", Map.of("trecho", p.trechoOrigem()));
        }
        return bloco;
    }

    private static void colocarSeNaoNulo(Map<String, Object> destino, String chave, Object valor) {
        if (valor != null) {
            destino.put(chave, valor);
        }
    }

    private static String textoOu(Object valor, java.util.function.Supplier<String> alternativa) {
        String t = texto(valor, null);
        return t == null || t.isBlank() ? alternativa.get() : t;
    }

    private static List<String> listaDeTextos(Object valor) {
        return valor instanceof List<?> l ? l.stream().filter(Objects::nonNull).map(Object::toString).toList() : List.of();
    }

    private static PropostaRegraDTO comPendenciasAdicionais(PropostaRegraDTO p, List<PendenciaPropostaDTO> extras) {
        List<PendenciaPropostaDTO> pendencias = new ArrayList<>(p.pendencias());
        pendencias.addAll(extras);
        return new PropostaRegraDTO(p.blocoId(), p.trechoOrigem(), p.filtros(), p.condicaoValor(), p.operacaoBase(),
                p.taxaFinal(), p.referenciasConsultadas(), p.origensPorCampo(), pendencias,
                pendencias.stream().noneMatch(PendenciaPropostaDTO::isImpeditiva), p.explicacao(), p.pythonEquivalente(),
                p.vigencia(), p.bonusPercentual(), p.bonusValorFixo());
    }

    /** Converte um bloco devolvido pelo agente para o contrato de proposta do backend. */
    private PropostaRegraDTO converterBlocoDoAgente(Map<String, Object> bloco, String blocoId, PeriodoVigenciaDTO vigenciaCampanha, Map<String, Object> contexto) {
        Map<String, Object> condicoes = mapa(bloco.get("condicoes"));
        String trechoOrigem = texto(mapa(bloco.get("origem")).get("trecho"), null);

        FiltrosRegraDTO filtros = new FiltrosRegraDTO(
                texto(condicoes.get("canal"), null),
                inteiro(condicoes.get("codMarca")),
                texto(condicoes.get("descrMarca"), null),
                inteiro(condicoes.get("codLoja")),
                inteiro(condicoes.get("codCargo")),
                texto(condicoes.get("descriCargo"), null),
                texto(condicoes.get("matricula"), null)
        );

        Map<String, Object> faixaAgente = mapa(condicoes.get("faixaValor"));
        FaixaValorDTO faixa = faixaAgente.isEmpty() ? FaixaValorDTO.semLimites() : new FaixaValorDTO(
                decimal(faixaAgente.get("min")), booleano(faixaAgente.get("minInclusivo")),
                decimal(faixaAgente.get("max")), booleano(faixaAgente.get("maxInclusivo")));

        Map<String, Object> bonus = mapa(bloco.get("bonus"));
        LocalDate dataInicioAgente = data(bloco.get("dataInicio"));
        LocalDate dataFimAgente = data(bloco.get("dataFim"));

        LocalDate dataInicio;
        LocalDate dataFim;
        boolean temVigenciaCampanha = vigenciaCampanha != null;

        if (temVigenciaCampanha) {
            dataInicio = vigenciaCampanha.dataInicio();
            dataFim = vigenciaCampanha.dataFim();
        } else {
            dataInicio = dataInicioAgente;
            dataFim = dataFimAgente;
        }
        PeriodoVigenciaDTO vigencia = PeriodoVigenciaDTO.de(dataInicio, dataFim);
        YearMonth ymRef = extrairCompetenciaReferencia(contexto, vigencia);

        // O agente resolve os ajustes sobre a base (pontos percentuais ou variação relativa) e devolve operação e taxa
        // final. Respostas sem operação (agentes anteriores) continuam usando a extração determinística do trecho.
        BigDecimal taxaAgente = decimal(bloco.get("taxa"));
        Map<String, Object> operacaoAgente = mapa(bloco.get("operacao"));
        boolean agenteEnviouOperacao = operacaoAgente.get("tipo") != null;
        boolean agenteDeixouTaxaPendente = !agenteEnviouOperacao && taxaAgente == null
                && listaDeMapas(bloco.get("pendencias")).stream().anyMatch(p -> "taxa".equals(texto(p.get("campo"), ""))
                        && !"CAMPO_AUSENTE".equals(texto(p.get("codigo"), "")));
        OperacaoBaseDTO operacao;
        BigDecimal taxaFinal;
        if (agenteEnviouOperacao) {
            operacao = operacaoDoAgente(operacaoAgente, bloco, filtros, ymRef);
            taxaFinal = taxaAgente;
        } else if (agenteDeixouTaxaPendente) {
            operacao = OperacaoBaseDTO.definirTaxa(null);
            taxaFinal = null;
        } else {
            operacao = extrairOperacaoBase(trechoOrigem, taxaAgente, filtros.codMarca(), filtros.descrMarca(),
                    filtros.codCargo(), filtros.descriCargo(), ymRef);
            taxaFinal = artefatoService.calcularTaxaEfetiva(operacao);
            if (taxaFinal == null && operacao.tipoOperacao() == TipoOperacaoBase.DEFINIR_TAXA) {
                taxaFinal = taxaAgente;
            }
        }
        boolean ajusteResolvido = operacao.tipoOperacao() != TipoOperacaoBase.DEFINIR_TAXA && taxaFinal != null;

        List<ReferenciaConsultadaDTO> referencias = new ArrayList<>();
        java.util.Set<String> dimensoesConsultadas = new java.util.HashSet<>();
        for (Map<String, Object> ref : listaDeMapas(bloco.get("referencias"))) {
            Map<String, Object> chave = mapa(ref.get("chave"));
            if ("CADASTRO".equals(ref.get("fonte"))) {
                chave.forEach((dimensao, valor) -> {
                    dimensoesConsultadas.add(dimensao);
                    referencias.add(referenciaDeCadastro(dimensao, valor, filtros));
                });
            } else if (!(agenteEnviouOperacao && "BASE_COMISS".equals(ref.get("fonte")))) {
                referencias.add(new ReferenciaConsultadaDTO(texto(ref.get("fonte"), "REFERENCIA"), chave.toString(),
                        "Taxa base " + texto(ref.get("taxaBase"), "não informada")));
            }
        }
        if (operacao.referenciaBase() != null) {
            referencias.add(new ReferenciaConsultadaDTO("BASE_COMISS", operacao.referenciaBase().tipoBase(), operacao.referenciaBase().descricao()));
        }

        Map<String, OrigemCampo> origens = new HashMap<>();
        if (filtros.canal() != null) origens.put("canal", OrigemCampo.TEXTO);
        if (filtros.codMarca() != null) origens.put("marca", dimensoesConsultadas.contains("marca") ? OrigemCampo.BASE_DADOS : OrigemCampo.TEXTO);
        if (filtros.codLoja() != null) origens.put("loja", dimensoesConsultadas.contains("loja") ? OrigemCampo.BASE_DADOS : OrigemCampo.TEXTO);
        if (filtros.codCargo() != null) origens.put("cargo", dimensoesConsultadas.contains("cargo") ? OrigemCampo.BASE_DADOS : OrigemCampo.TEXTO);
        if (filtros.matricula() != null) origens.put("matricula", dimensoesConsultadas.contains("matricula") ? OrigemCampo.BASE_DADOS : OrigemCampo.TEXTO);
        if (taxaAgente != null) origens.put("taxa", OrigemCampo.TEXTO);
        if (faixa.possuiFaixa()) origens.put("condicaoValor", OrigemCampo.TEXTO);
        if (temVigenciaCampanha) {
            origens.put("dataInicio", dataInicio != null ? OrigemCampo.CONTEXTO : OrigemCampo.PADRAO_SISTEMA);
            origens.put("dataFim", dataFim != null ? OrigemCampo.CONTEXTO : OrigemCampo.PADRAO_SISTEMA);
        } else {
            origens.put("dataInicio", dataInicio != null ? OrigemCampo.TEXTO : OrigemCampo.PADRAO_SISTEMA);
            origens.put("dataFim", dataFim != null ? OrigemCampo.TEXTO : OrigemCampo.PADRAO_SISTEMA);
        }

        PropostaRegraDTO propostaBase = new PropostaRegraDTO(blocoId, trechoOrigem, filtros, faixa, operacao, taxaFinal,
                referencias, origens, null, null, null, null, vigencia,
                decimal(bonus.get("percentual")), decimal(bonus.get("valorFixo")));

        List<PendenciaPropostaDTO> pendencias = new ArrayList<>(artefatoService.validarProposta(propostaBase));
        // Quando o agente já explicou por que a taxa está pendente (ex.: BASE_AUSENTE), as pendências genéricas de
        // taxa não calculável seriam o mesmo problema repetido.
        boolean agenteExplicouTaxa = agenteEnviouOperacao && listaDeMapas(bloco.get("pendencias")).stream()
                .anyMatch(p -> "taxa".equals(texto(p.get("campo"), "")));
        if (agenteExplicouTaxa) {
            pendencias.removeIf(pen -> "BASE_REFERENCIA_NAO_ENCONTRADA".equals(pen.codigo()) || "TAXA_NAO_CALCULAVEL".equals(pen.codigo()));
        }

        boolean datasDiferentes = temVigenciaCampanha && (
                (dataInicioAgente != null && !Objects.equals(dataInicioAgente, vigenciaCampanha.dataInicio()))
                || (dataFimAgente != null && !Objects.equals(dataFimAgente, vigenciaCampanha.dataFim()))
        );

        boolean agenteEnviouVigenciaIncompativel = listaDeMapas(bloco.get("pendencias")).stream()
                .anyMatch(p -> "VIGENCIA_INCOMPATIVEL".equalsIgnoreCase(texto(p.get("codigo"), "")));

        if (datasDiferentes && !agenteEnviouVigenciaIncompativel) {
            String periodoTexto;
            if (dataInicioAgente != null && dataFimAgente != null) {
                periodoTexto = dataInicioAgente + " a " + dataFimAgente;
            } else if (dataInicioAgente != null) {
                periodoTexto = "início em " + dataInicioAgente;
            } else {
                periodoTexto = "fim em " + dataFimAgente;
            }
            pendencias.add(PendenciaPropostaDTO.aviso(
                    "vigencia",
                    "VIGENCIA_INCOMPATIVEL",
                    "Período informado no texto (" + periodoTexto + ") difere da vigência da campanha; mantida a vigência da campanha."
            ));
        }

        for (Map<String, Object> p : listaDeMapas(bloco.get("pendencias"))) {
            String campo = texto(p.get("campo"), "geral");
            if (ajusteResolvido && "taxa".equals(campo)) {
                continue; // a taxa do ajuste sobre a base foi calculada aqui
            }
            String codigo = texto(p.get("codigo"), "AVISO_AGENTE");
            String mensagem = texto(p.get("mensagem"), "");
            if (pendencias.stream().noneMatch(pen -> saoPendenciasIguais(pen, campo, codigo, mensagem))) {
                pendencias.add(pendenciaDoAgente(campo, codigo, mensagem));
            }
        }

        return new PropostaRegraDTO(blocoId, trechoOrigem, filtros, faixa, operacao, taxaFinal, referencias, origens,
                pendencias, pendencias.stream().noneMatch(PendenciaPropostaDTO::isImpeditiva),
                textoOu(bloco.get("explicacao"), () -> artefatoService.gerarExplicacao(propostaBase)),
                textoOu(bloco.get("codigoPython"), () -> artefatoService.gerarPythonEquivalente(propostaBase)),
                vigencia, propostaBase.bonusPercentual(), propostaBase.bonusValorFixo());
    }

    private static boolean saoPendenciasIguais(PendenciaPropostaDTO a, String campo, String codigo, String mensagem) {
        if (a == null) return false;
        if (!Objects.equals(a.campo(), campo)) return false;
        if (!Objects.equals(a.codigo(), codigo)) return false;
        if (a.mensagem() == null || mensagem == null) {
            return Objects.equals(a.mensagem(), mensagem);
        }
        return a.mensagem().equalsIgnoreCase(mensagem);
    }

    private static String extrairIdAgente(Map<String, Object> bloco) {
        if (bloco == null) return null;
        for (String chave : List.of("idLocal", "blocoId", "id")) {
            Object val = bloco.get(chave);
            if (val != null) {
                String s = val.toString().trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }

    private static PeriodoVigenciaDTO extrairVigenciaCampanha(Map<String, Object> contexto) {
        if (contexto == null) {
            return null;
        }
        Object vigenciaObj = contexto.get("vigenciaCampanha");
        if (!(vigenciaObj instanceof Map<?, ?> vigenciaMap)) {
            return null;
        }
        LocalDate inicio = data(vigenciaMap.get("inicio"));
        LocalDate fim = data(vigenciaMap.get("fim"));
        if (inicio == null && fim == null) {
            return null;
        }
        return PeriodoVigenciaDTO.de(inicio, fim);
    }

    private static Boolean booleano(Object valor) {
        if (valor == null) return null;
        if (valor instanceof Boolean b) return b;
        if (valor instanceof String s) {
            String trimmed = s.trim();
            if ("true".equalsIgnoreCase(trimmed)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(trimmed)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    /**
     * Vigência diferente da campanha (a da campanha é mantida) e trecho sem origem confirmada são avisos;
     * o resto (ambiguidade, cadastro inexistente, taxa ausente, conflito...) impede ativar até o usuário resolver.
     */
    private static PendenciaPropostaDTO pendenciaDoAgente(String campo, String codigo, String mensagem) {
        return PENDENCIAS_AGENTE_AVISO.contains(codigo)
                ? PendenciaPropostaDTO.aviso(campo, codigo, mensagem)
                : PendenciaPropostaDTO.impedimento(campo, codigo, mensagem);
    }

    private static ReferenciaConsultadaDTO referenciaDeCadastro(String dimensao, Object valor, FiltrosRegraDTO filtros) {
        return switch (dimensao) {
            case "marca" -> new ReferenciaConsultadaDTO("MARCA", "cod_marca=" + valor, "Marca " + (filtros.descrMarca() != null ? filtros.descrMarca() : valor));
            case "cargo" -> new ReferenciaConsultadaDTO("CARGO", "cod_cargo=" + valor, "Cargo " + (filtros.descriCargo() != null ? filtros.descriCargo() : valor));
            case "loja" -> new ReferenciaConsultadaDTO("LOJA", "cod_loja=" + valor, valor instanceof Number n ? CatalogoDominioDomRock.formatarLoja(n.intValue()) : "Loja " + valor);
            case "matricula" -> new ReferenciaConsultadaDTO("MATRICULA", "matricula=" + valor, "Colaborador " + valor);
            default -> new ReferenciaConsultadaDTO(dimensao.toUpperCase(), dimensao + "=" + valor, String.valueOf(valor));
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object valor) {
        return valor instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listaDeMapas(Object valor) {
        if (!(valor instanceof List<?> lista)) {
            return List.of();
        }
        return lista.stream().filter(Map.class::isInstance).map(i -> (Map<String, Object>) i).toList();
    }

    /** O serviço de IA serializa Decimal como texto ("0.0300"); números também são aceitos. */
    private static BigDecimal decimal(Object valor) {
        if (valor == null) return null;
        if (valor instanceof BigDecimal b) return b;
        if (valor instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        String texto = valor.toString().trim();
        if (texto.isEmpty()) return null;
        try {
            return new BigDecimal(texto);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer inteiro(Object valor) {
        if (valor == null) return null;
        if (valor instanceof Number n) return Integer.valueOf(n.intValue());
        if (valor instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) return null;
            try {
                return Integer.valueOf(trimmed);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static LocalDate data(Object valor) {
        if (valor == null) return null;
        if (valor instanceof LocalDate ld) return ld;
        String texto = valor.toString().trim();
        if (texto.isEmpty()) return null;
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String texto(Object valor, String padrao) {
        return valor == null ? padrao : valor.toString();
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

    private static YearMonth extrairCompetenciaReferencia(Map<String, Object> contexto, PeriodoVigenciaDTO vigencia) {
        if (contexto != null && contexto.containsKey("competencia")) {
            Object compObj = contexto.get("competencia");
            if (compObj != null) {
                String compStr = compObj.toString().trim();
                try {
                    return YearMonth.parse(compStr);
                } catch (Exception ignored) {
                    try {
                        return YearMonth.from(LocalDate.parse(compStr));
                    } catch (Exception ignored2) {}
                }
            }
        }
        if (vigencia != null && vigencia.dataInicio() != null) {
            return YearMonth.from(vigencia.dataInicio());
        }
        return null;
    }

    public OperacaoBaseDTO extrairOperacaoBase(String texto, BigDecimal taxaExtraida) {
        return extrairOperacaoBase(texto, taxaExtraida, null, null, null, null, null);
    }

    public OperacaoBaseDTO extrairOperacaoBase(
            String texto,
            BigDecimal taxaExtraida,
            Integer codMarca,
            String descrMarca,
            Integer codCargo,
            String descriCargo
    ) {
        return extrairOperacaoBase(texto, taxaExtraida, codMarca, descrMarca, codCargo, descriCargo, null);
    }

    public OperacaoBaseDTO extrairOperacaoBase(
            String texto,
            BigDecimal taxaExtraida,
            Integer codMarca,
            String descrMarca,
            Integer codCargo,
            String descriCargo,
            YearMonth competencia
    ) {
        if (texto == null) return OperacaoBaseDTO.definirTaxa(taxaExtraida);
        String t = texto.toLowerCase();

        BigDecimal taxaBaseReferencia = null;
        String descrReferencia = "Taxa Contratual Padrão";
        if (competencia != null) {
            taxaBaseReferencia = dominioService.obterTaxaBaseContratual(codMarca, codCargo, descriCargo, competencia).orElse(null);
            descrReferencia = dominioService.gerarDescricaoReferencia(codMarca, descrMarca, codCargo, descriCargo, competencia);
        }

        // 1. Acréscimo sobre a base
        if (t.contains("acréscimo") || t.contains("acrescimo") || t.contains("taxa base +") || t.contains("base +") || (t.contains("+") && t.contains("base"))) {
            BigDecimal ajuste = extrairPercentualOuNumero(t);
            ReferenciaBaseDTO ref = new ReferenciaBaseDTO("BASE_COMISS", taxaBaseReferencia, descrReferencia);
            return new OperacaoBaseDTO(TipoOperacaoBase.ACRESCIMO_PONTOS, ajuste != null ? ajuste : new BigDecimal("0.0150"), ref);
        }

        // 2. Desconto sobre a base
        if (t.contains("desconto") || t.contains("taxa base -") || t.contains("base -") || (t.contains("-") && t.contains("base"))) {
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
        YearMonth compProposta = request.dataInicio() != null ? YearMonth.from(request.dataInicio()) : null;
        if (request.taxa() != null) {
            if (request.taxa().compareTo(BigDecimal.ZERO) <= 0) {
                erros.add("A taxa de comissão deve ser um valor estritamente positivo (> 0).");
            } else if (request.taxa().compareTo(new BigDecimal("1.0")) > 0) {
                avisos.add("A taxa informada (" + request.taxa() + ") é maior que 100%. Verifique se o valor não deveria estar no formato decimal (ex: 0.05 para 5%).");
            }

            var taxaBaseOpt = dominioService.obterTaxaBaseContratual(request.codMarca(), request.codCargo(), request.descriCargo(), compProposta);
            if (taxaBaseOpt.isPresent()) {
                referencias.add(new ReferenciaConsultadaDTO(
                        "TAXA_BASE_CONTRATUAL",
                        "cod_marca=" + request.codMarca() + ", cod_cargo=" + request.codCargo(),
                        dominioService.gerarDescricaoReferencia(request.codMarca(), request.descrMarca(), request.codCargo(), request.descriCargo(), compProposta)
                ));
            }
        } else {
            var taxaBaseOpt = dominioService.obterTaxaBaseContratual(request.codMarca(), request.codCargo(), request.descriCargo(), compProposta);
            if (taxaBaseOpt.isEmpty()) {
                erros.add("Operação sobre a base requer taxa contratual de referência (tb_basecomiss), que não foi localizada para o público-alvo na competência.");
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
                    request.taxa(),
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