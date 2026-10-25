package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.simulacao.ConferenciaSimulacaoResponse;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosRequest;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.model.Simulacao;
import com.bugbusters.backend.model.StatusAceiteSimulacao;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.repository.SimulacaoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Component
public class CampanhaSimulacaoValidator {

    private static final Logger log = LoggerFactory.getLogger(CampanhaSimulacaoValidator.class);

    private final SimulacaoRepository simulacaoRepository;
    private final RegraRepository regraRepository;
    private final ObjectMapper objectMapper;

    public CampanhaSimulacaoValidator(SimulacaoRepository simulacaoRepository, RegraRepository regraRepository) {
        this.simulacaoRepository = simulacaoRepository;
        this.regraRepository = regraRepository;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Valida estritamente se a campanha pode ser ativada ou publicada.
     * Lança BusinessException caso a ativação seja contornada ou divirja da simulação aceita.
     */
    public Simulacao validarAptaParaAtivacao(Campanha campanha) {
        if (campanha == null) {
            throw new BusinessException("Campanha não pode ser nula para validação de ativação.");
        }

        Simulacao simulacaoAceita = resolverSimulacaoAceita(campanha);
        if (simulacaoAceita == null) {
            throw new BusinessException(String.format(
                    "Ativação bloqueada: A campanha ID %d ('%s') não possui nenhuma simulação aceita. " +
                    "Execute uma simulação sem impedimentos e registre o aceite formal antes da ativação.",
                    campanha.getId(), campanha.tituloParaRevisao()));
        }

        if (simulacaoAceita.getStatusAceite() != StatusAceiteSimulacao.ACEITA) {
            throw new BusinessException(String.format(
                    "Ativação bloqueada: A simulação vinculada (Protocolo: %s) está com status '%s'. " +
                    "Motivo: %s. Realize uma nova simulação e aprove o aceite antes de ativar.",
                    simulacaoAceita.getProtocolo(),
                    simulacaoAceita.getStatusAceite(),
                    simulacaoAceita.getMotivoInvalidacao() != null ? simulacaoAceita.getMotivoInvalidacao() : "Não aceita"));
        }

        if (simulacaoAceita.getQtdVendasComImpedimento() > 0) {
            throw new BusinessException(String.format(
                    "Ativação bloqueada: A simulação aceita possui %d venda(s) com impedimentos cadastrais ou inconsistências de taxa base.",
                    simulacaoAceita.getQtdVendasComImpedimento()));
        }

        List<String> divergencias = conferirDivergencias(campanha, simulacaoAceita);
        if (!divergencias.isEmpty()) {
            throw new BusinessException(String.format(
                    "Ativação bloqueada: A proposta atual da campanha diverge da simulação aceita (Protocolo: %s). " +
                    "Divergências detectadas:\n- %s\n" +
                    "O aceite anterior foi invalidado por modificação de regras/vigência. Execute nova simulação e aprove o novo aceite.",
                    simulacaoAceita.getProtocolo(),
                    String.join("\n- ", divergencias)));
        }

        return simulacaoAceita;
    }

    /**
     * Confere a correspondência sem lançar exceção, gerando o relatório de auditoria e conformidade.
     */
    public ConferenciaSimulacaoResponse conferirCorrespondencia(Campanha campanha) {
        if (campanha == null) {
            return new ConferenciaSimulacaoResponse(
                    null, null, null, false, null, null, null, null, null, null, null,
                    List.of("Campanha não informada."), "Campanha nula."
            );
        }

        Simulacao simulacaoAceita = resolverSimulacaoAceita(campanha);
        List<Regra> regrasAtuais = carregarRegrasAtuais(campanha);
        String hashCampanhaAtual = calcularHashPropostaCampanha(campanha.inicioParaRevisao(), campanha.fimParaRevisao(), regrasAtuais,
                simulacaoAceita != null ? simulacaoAceita.getCompetencia() : "COMPETENCIA_PADRAO");

        if (simulacaoAceita == null) {
            return new ConferenciaSimulacaoResponse(
                    campanha.getId(),
                    campanha.tituloParaRevisao(),
                    campanha.getEstado(),
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    hashCampanhaAtual,
                    List.of("Nenhuma simulação aceita vinculada a esta campanha."),
                    "Campanha não apta para ativação: simulação prévia sem impedimentos e com aceite formal é obrigatória."
            );
        }

        List<String> divergencias = new ArrayList<>();
        if (simulacaoAceita.getStatusAceite() != StatusAceiteSimulacao.ACEITA) {
            divergencias.add("Status do aceite é " + simulacaoAceita.getStatusAceite() +
                    (simulacaoAceita.getMotivoInvalidacao() != null ? " (" + simulacaoAceita.getMotivoInvalidacao() + ")" : ""));
        }
        if (simulacaoAceita.getQtdVendasComImpedimento() > 0) {
            divergencias.add(String.format("Simulação aceita possui %d vendas com impedimentos.", simulacaoAceita.getQtdVendasComImpedimento()));
        }

        divergencias.addAll(conferirDivergencias(campanha, simulacaoAceita));

        boolean apta = divergencias.isEmpty() && simulacaoAceita.getStatusAceite() == StatusAceiteSimulacao.ACEITA;
        String mensagem = apta
                ? "Campanha em total conformidade com a simulação aceita. Apta para ativação em produção."
                : "Campanha NÃO apta para ativação. Foram detectadas divergências ou ausência de aceite válido.";

        return new ConferenciaSimulacaoResponse(
                campanha.getId(),
                campanha.tituloParaRevisao(),
                campanha.getEstado(),
                apta,
                simulacaoAceita.getStatusAceite(),
                simulacaoAceita.getId(),
                simulacaoAceita.getProtocolo(),
                simulacaoAceita.getAceitoEm(),
                simulacaoAceita.getAceitoPor(),
                simulacaoAceita.getHashProposta(),
                hashCampanhaAtual,
                divergencias,
                mensagem
        );
    }

    /**
     * Detecta divergências de regras, período, competência e bases entre a campanha atual e o snapshot simulado.
     */
    public List<String> conferirDivergencias(Campanha campanha, Simulacao simulacao) {
        List<String> divergencias = new ArrayList<>();

        if (simulacao == null || campanha == null) {
            divergencias.add("Campanha ou simulação nula.");
            return divergencias;
        }

        if (simulacao.getCampanha() != null && !Objects.equals(campanha.getId(), simulacao.getCampanha().getId())) {
            divergencias.add("A simulação pertence a outra campanha.");
        }
        if (!pertenceAVersaoAtual(campanha, simulacao)) {
            divergencias.add("A simulação não pertence à versão atual da proposta.");
        }
        if (simulacao.getHashProposta() == null || simulacao.getHashProposta().isBlank()) {
            divergencias.add("A simulação não possui assinatura da proposta.");
        }

        // 1. Período
        if (!Objects.equals(campanha.inicioParaRevisao(), simulacao.getDataInicioCampanha())) {
            divergencias.add(String.format("Data de início atual (%s) difere da data simulada (%s).",
                    campanha.inicioParaRevisao(), simulacao.getDataInicioCampanha()));
        }
        if (!Objects.equals(campanha.fimParaRevisao(), simulacao.getDataFimCampanha())) {
            divergencias.add(String.format("Data de fim atual (%s) difere da data simulada (%s).",
                    campanha.fimParaRevisao(), simulacao.getDataFimCampanha()));
        }

        // 2. Regras simuladas vs regras salvas
        List<Regra> regrasAtuais = carregarRegrasAtuais(campanha);
        List<RegraItemRequest> regrasSimuladas = extrairRegrasSimuladas(simulacao);

        if (regrasAtuais.size() != regrasSimuladas.size()) {
            divergencias.add(String.format("Quantidade de regras na campanha (%d) difere da simulação aceita (%d).",
                    regrasAtuais.size(), regrasSimuladas.size()));
        }

        // Comparação de hash canônico
        String hashCampanha = calcularHashPropostaCampanha(campanha.inicioParaRevisao(), campanha.fimParaRevisao(), regrasAtuais, simulacao.getCompetencia());
        String hashSimulado = simulacao.getHashProposta();

        if (hashSimulado != null && !hashSimulado.equalsIgnoreCase(hashCampanha)) {
            divergencias.add("Assinatura canônica (fingerprint) das regras e premissas difere do snapshot aprovado.");
        }

        return divergencias;
    }

    /**
     * Calcula o hash SHA-256 canônico a partir de regras e parâmetros de simulação.
     */
    public String calcularHashPropostaSimulacao(LocalDate dataInicio, LocalDate dataFim, List<RegraItemRequest> regras, String competencia) {
        List<String> tokensRegras = new ArrayList<>();
        if (regras != null) {
            for (RegraItemRequest r : regras) {
                tokensRegras.add(normalizarRegraItem(r));
            }
        }
        Collections.sort(tokensRegras);

        StringBuilder sb = new StringBuilder();
        sb.append("COMPETENCIA:").append(competencia != null ? competencia.trim() : "").append(";");
        sb.append("INICIO:").append(dataInicio != null ? dataInicio.toString() : "").append(";");
        sb.append("FIM:").append(dataFim != null ? dataFim.toString() : "").append(";");
        for (String tok : tokensRegras) {
            sb.append("REGRA[").append(tok).append("];");
        }

        return gerarSha256(sb.toString());
    }

    /**
     * Calcula o hash SHA-256 canônico a partir das regras persistidas da campanha.
     */
    public String calcularHashPropostaCampanha(LocalDate dataInicio, LocalDate dataFim, List<Regra> regras, String competencia) {
        List<String> tokensRegras = new ArrayList<>();
        if (regras != null) {
            for (Regra r : regras) {
                tokensRegras.add(normalizarRegraEntidade(r));
            }
        }
        Collections.sort(tokensRegras);

        StringBuilder sb = new StringBuilder();
        sb.append("COMPETENCIA:").append(competencia != null ? competencia.trim() : "").append(";");
        sb.append("INICIO:").append(dataInicio != null ? dataInicio.toString() : "").append(";");
        sb.append("FIM:").append(dataFim != null ? dataFim.toString() : "").append(";");
        for (String tok : tokensRegras) {
            sb.append("REGRA[").append(tok).append("];");
        }

        return gerarSha256(sb.toString());
    }

    private Simulacao resolverSimulacaoAceita(Campanha campanha) {
        if (campanha.getSimulacaoAceita() != null && pertenceAVersaoAtual(campanha, campanha.getSimulacaoAceita())) {
            return campanha.getSimulacaoAceita();
        }
        if (simulacaoRepository == null || campanha.getId() == null) return null;
        // Uma consulta de conferência nunca concede aceite nem associa simulações de outras campanhas.
        return simulacaoRepository.findAllByCampanhaIdAndStatusAceite(campanha.getId(), StatusAceiteSimulacao.ACEITA)
                .stream().filter(s -> pertenceAVersaoAtual(campanha, s))
                .max(Comparator.comparing(Simulacao::getAceitoEm, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private boolean pertenceAVersaoAtual(Campanha campanha, Simulacao simulacao) {
        var versao = campanha.versaoParaRevisao();
        return versao == null ? simulacao.getCampanhaVersao() == null
                : simulacao.getCampanhaVersao() != null && Objects.equals(versao.getId(), simulacao.getCampanhaVersao().getId());
    }

    private List<Regra> carregarRegrasAtuais(Long campanhaId) {
        if (campanhaId == null || regraRepository == null) {
            return List.of();
        }
        return regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(campanhaId);
    }

    private List<Regra> carregarRegrasAtuais(Campanha campanha) {
        if (campanha == null || regraRepository == null) return List.of();
        var versao = campanha.getVersaoProposta() != null ? campanha.getVersaoProposta() : campanha.getVersaoVigente();
        return versao != null
                ? regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(versao.getId())
                : carregarRegrasAtuais(campanha.getId());
    }

    private List<RegraItemRequest> extrairRegrasSimuladas(Simulacao simulacao) {
        if (simulacao.getPremissasSnapshot() == null || simulacao.getPremissasSnapshot().isBlank()) {
            return List.of();
        }
        try {
            SimulacaoCenariosRequest req = objectMapper.readValue(simulacao.getPremissasSnapshot(), SimulacaoCenariosRequest.class);
            return req.regras() != null ? req.regras() : List.of();
        } catch (Exception e) {
            log.warn("Não foi possível desserializar o snapshot de regras da simulação {}: {}", simulacao.getId(), e.getMessage());
            return List.of();
        }
    }

    private String normalizarRegraItem(RegraItemRequest r) {
        return String.format(Locale.US,
                "canal=%s|marca=%s|loja=%s|cargo=%s|matr=%s|min=%s|minInc=%s|max=%s|maxInc=%s|taxa=%s|op=%s|ajuste=%s|tipoBase=%s|taxaBase=%s|bPerc=%s|bFixo=%s",
                val(r.canal()),
                val(r.codMarca()),
                val(r.codLoja()),
                val(r.codCargo()),
                val(r.matricula()),
                num2(r.valorMinimo()),
                val(r.minInclusivo()),
                num2(r.valorMaximo()),
                val(r.maxInclusivo()),
                num4(r.taxa()),
                r.tipoOperacao() != null ? r.tipoOperacao().name() : "DEFINIR_TAXA",
                num4(r.valorAjuste()),
                val(r.tipoBaseReferencia()),
                num4(r.taxaBaseConsultada()),
                num4(r.bonusPercentual()),
                num2(r.bonusValorFixo())
        );
    }

    private String normalizarRegraEntidade(Regra r) {
        return String.format(Locale.US,
                "canal=%s|marca=%s|loja=%s|cargo=%s|matr=%s|min=%s|minInc=%s|max=%s|maxInc=%s|taxa=%s|op=%s|ajuste=%s|tipoBase=%s|taxaBase=%s|bPerc=%s|bFixo=%s",
                val(r.getCanal()),
                val(r.getCodMarca()),
                val(r.getCodLoja()),
                val(r.getCodCargo()),
                val(r.getMatricula()),
                num2(r.getValorMinimo()),
                val(r.getMinInclusivo()),
                num2(r.getValorMaximo()),
                val(r.getMaxInclusivo()),
                num4(r.getTaxa()),
                r.getTipoOperacao() != null ? r.getTipoOperacao().name() : "DEFINIR_TAXA",
                num4(r.getValorAjuste()),
                val(r.getTipoBaseReferencia()),
                num4(r.getTaxaBaseConsultada()),
                num4(r.getBonusPercentual()),
                num2(r.getBonusValorFixo())
        );
    }

    private String val(Object obj) {
        return obj == null ? "NULL" : obj.toString().trim().toUpperCase();
    }

    private String num2(BigDecimal bd) {
        return bd == null ? "NULL" : bd.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String num4(BigDecimal bd) {
        return bd == null ? "NULL" : bd.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private String gerarSha256(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algoritmo SHA-256 não disponível no ambiente", e);
        }
    }
}
