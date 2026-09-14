package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.interpretador.InterpretacaoRegraRequest;
import com.bugbusters.backend.dto.interpretador.InterpretacaoRegraResponse;
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

    public static final Map<Integer, String> MARCAS_CONHECIDAS = Map.of(
            10, "PRETO",
            20, "BRANCO",
            30, "AZUL",
            40, "VERMELHO",
            50, "AMARELO",
            60, "CINZA"
    );

    public static final Map<Integer, String> CARGOS_CONHECIDOS = Map.of(
            100, "VENDEDOR LOJA",
            150, "GERENTE DE LOJA",
            200, "VENDEDOR BALCAO",
            300, "ASSISTENTE DE VENDAS"
    );

    public static final List<String> CANAIS_CONHECIDOS = List.of(
            "LOJA_FISICA", "ECOMMERCE", "BALCAO", "QUIOSQUE", "APP", "PADRAO"
    );

    public static final Map<String, Object> DICIONARIO_CAMPOS_RECONHECIDOS = Map.of(
            "marcas", MARCAS_CONHECIDAS,
            "cargos", CARGOS_CONHECIDOS,
            "canais", CANAIS_CONHECIDOS
    );

    private final AiServiceClient aiClient;

    public InterpretadorService(AiServiceClient aiClient) {
        this.aiClient = aiClient;
    }

    public Map<String, Object> getDicionarioCamposReconhecidos() {
        return DICIONARIO_CAMPOS_RECONHECIDOS;
    }

    public InterpretacaoRegraResponse processarInterpretacao(InterpretacaoRegraRequest request) {
        // 1. Enriquecer o contexto com o dicionário de dimensões reais antes de chamar o serviço de IA
        Map<String, Object> contextoEnriquecido = new HashMap<>();
        if (request.contexto() != null) {
            contextoEnriquecido.putAll(request.contexto());
        }
        contextoEnriquecido.putIfAbsent("ano_referencia", LocalDate.now().getYear());
        contextoEnriquecido.putIfAbsent("dicionario_dimensoes", DICIONARIO_CAMPOS_RECONHECIDOS);
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
            for (var entry : CARGOS_CONHECIDOS.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(descriCargo)) {
                    codCargo = entry.getKey();
                    descriCargo = entry.getValue();
                    break;
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

        // Validação de Loja (codLoja)
        Integer codLoja = respostaBruta.codLoja();
        if (codLoja != null && codLoja <= 0) {
            pendencias.add("Código da loja inválido (" + codLoja + "). Deve ser um número positivo.");
            codLoja = null;
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
}