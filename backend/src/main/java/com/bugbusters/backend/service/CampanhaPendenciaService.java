package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.auditoria.AuditoriaEventoContexto;
import com.bugbusters.backend.dto.campanha.PendenciaRastreabilidadeDTO;
import com.bugbusters.backend.dto.campanha.RastreabilidadeRegraDTO;
import com.bugbusters.backend.dto.interpretador.proposta.SeveridadePendencia;
import com.bugbusters.backend.dto.pendencia.*;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.CampanhaPendenciaAceiteRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.CampanhaVersaoRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.service.auth.UsuarioAtualProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço responsável pela gestão rastreável e auditável de aceite de pendências
 * e inconsistências cadastrais de campanhas e regras (BUG-102 / BACK-7).
 */
@Service
public class CampanhaPendenciaService {

    private static final Logger log = LoggerFactory.getLogger(CampanhaPendenciaService.class);

    private final CampanhaRepository campanhaRepository;
    private final CampanhaVersaoRepository campanhaVersaoRepository;
    private final RegraRepository regraRepository;
    private final CampanhaPendenciaAceiteRepository aceiteRepository;
    private final AuditoriaGeralService auditoriaService;

    @Autowired(required = false)
    private UsuarioAtualProvider usuarioAtualProvider = (paliativo) -> (paliativo != null && !paliativo.isBlank()) ? paliativo.trim() : "SISTEMA";

    public CampanhaPendenciaService(
            CampanhaRepository campanhaRepository,
            CampanhaVersaoRepository campanhaVersaoRepository,
            RegraRepository regraRepository,
            CampanhaPendenciaAceiteRepository aceiteRepository,
            @Autowired(required = false) AuditoriaGeralService auditoriaService
    ) {
        this.campanhaRepository = campanhaRepository;
        this.campanhaVersaoRepository = campanhaVersaoRepository;
        this.regraRepository = regraRepository;
        this.aceiteRepository = aceiteRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Registra o aceite formal e fundamentado de uma pendência/risco para a campanha ou regra.
     */
    @Transactional
    public PendenciaAceiteResponse aceitarPendencia(Long campanhaId, Integer versaoNumero, AceitarPendenciaRequest request) {
        Campanha campanha = buscarCampanha(campanhaId);
        CampanhaVersao versao = resolverVersao(campanha, versaoNumero);
        validarImutabilidadeVersao(versao);

        String aceitoPor = usuarioAtualProvider.obterUsuarioAtual(request.aceitoPor());
        if (aceitoPor == null || aceitoPor.isBlank() || ("SISTEMA".equals(aceitoPor) && (request.aceitoPor() == null || request.aceitoPor().isBlank()))) {
            throw new BusinessException("A identificação do responsável pelo aceite da pendência é obrigatória.");
        }
        if (request.motivo() == null || request.motivo().trim().isBlank()) {
            throw new BusinessException("A justificativa formal do aceite da pendência é obrigatória.");
        }
        if (request.pendenciaIdentificador() == null || request.pendenciaIdentificador().trim().isBlank()) {
            throw new BusinessException("O identificador da pendência é obrigatório.");
        }
        if (request.codigo() == null || request.codigo().trim().isBlank()) {
            throw new BusinessException("O código da pendência é obrigatório.");
        }

        Regra regra = null;
        if (request.regraId() != null) {
            regra = regraRepository.findById(request.regraId())
                    .orElseThrow(() -> new ResourceNotFoundException("Regra ID " + request.regraId() + " não encontrada."));
            if (regra.getCampanhaVersao() != null && !regra.getCampanhaVersao().getId().equals(versao.getId())) {
                throw new BusinessException("A regra indicada não pertence à versão sob análise.");
            }
        }

        // Verifica se já existe um aceite ativo para o mesmo identificador nesta versão
        String identificador = request.pendenciaIdentificador().trim();
        Optional<CampanhaPendenciaAceite> existente = aceiteRepository.findByCampanhaVersaoIdAndPendenciaIdentificadorAndStatus(
                versao.getId(), identificador, StatusAceitePendencia.ACEITA
        );

        CampanhaPendenciaAceite aceite;
        if (existente.isPresent()) {
            aceite = existente.get();
            aceite.setAceitoPor(aceitoPor);
            aceite.setMotivoAceite(request.motivo().trim());
            if (regra != null) aceite.setRegra(regra);
            if (request.envioArquivoId() != null) aceite.setEnvioArquivoId(request.envioArquivoId());
            if (request.inconsistenciaImportacaoId() != null) aceite.setInconsistenciaImportacaoId(request.inconsistenciaImportacaoId());
            if (request.campo() != null) aceite.setCampo(request.campo().trim());
            if (request.mensagem() != null) aceite.setMensagem(request.mensagem().trim());
            if (request.severidade() != null) aceite.setSeveridade(request.severidade());
        } else {
            aceite = new CampanhaPendenciaAceite(
                    campanha,
                    versao,
                    regra,
                    identificador,
                    request.codigo().trim(),
                    request.campo() != null ? request.campo().trim() : null,
                    request.mensagem() != null ? request.mensagem().trim() : null,
                    request.severidade() != null ? request.severidade() : SeveridadePendencia.IMPEDIMENTO,
                    aceitoPor,
                    request.motivo().trim()
            );
            aceite.setEnvioArquivoId(request.envioArquivoId());
            aceite.setInconsistenciaImportacaoId(request.inconsistenciaImportacaoId());
        }

        aceite = aceiteRepository.save(aceite);

        // Reavalia a completude das regras impactadas
        reavaliarCompletudeRegras(versao);

        if (auditoriaService != null) {
            auditoriaService.registrarEvento(AuditoriaEventoContexto.builder()
                    .usuario(aceitoPor)
                    .tipoAcao(TipoAcaoAuditoria.ACEITE_PENDENCIA)
                    .tipoEntidade(TipoEntidadeAuditoria.PENDENCIA)
                    .entidadeId(String.valueOf(campanhaId))
                    .versaoEntidade(versao.getNumero())
                    .campanhaId(campanha.getId())
                    .campanhaVersaoId(versao.getId())
                    .campanhaTitulo(campanha.getTitulo())
                    .envioArquivoId(aceite.getEnvioArquivoId())
                    .descricao(String.format("Aceite rastreável concedido para pendência '%s' (%s). Motivo: %s",
                            request.codigo(), request.severidade(), request.motivo()))
                    .dadosNovos(String.format("{\"codigoPendencia\": \"%s\", \"severidade\": \"%s\", \"motivo\": \"%s\"}",
                            request.codigo(), request.severidade(), request.motivo()))
                    .sucesso(true));
        }

        log.info("Aceite rastreável registrado para pendência '{}' (Código: {}) da campanha {} por '{}'.",
                identificador, request.codigo(), campanhaId, aceitoPor);

        return PendenciaAceiteResponse.de(aceite);
    }

    /**
     * Revoga formalmente um aceite de pendência previamente concedido.
     */
    @Transactional
    public PendenciaAceiteResponse revogarAceite(Long campanhaId, Long aceiteId, RevogarAceitePendenciaRequest request) {
        Campanha campanha = buscarCampanha(campanhaId);
        CampanhaPendenciaAceite aceite = aceiteRepository.findById(aceiteId)
                .orElseThrow(() -> new ResourceNotFoundException("Aceite de pendência ID " + aceiteId + " não encontrado."));

        if (!aceite.getCampanha().getId().equals(campanha.getId())) {
            throw new BusinessException("O aceite não pertence à campanha informada.");
        }
        if (StatusAceitePendencia.REVOGADA.equals(aceite.getStatus())) {
            throw new BusinessException("O aceite de pendência ID " + aceiteId + " já se encontra revogado.");
        }
        String revogadoPor = usuarioAtualProvider.obterUsuarioAtual(request.revogadoPor());
        if (revogadoPor == null || revogadoPor.isBlank() || ("SISTEMA".equals(revogadoPor) && (request.revogadoPor() == null || request.revogadoPor().isBlank()))) {
            throw new BusinessException("A identificação do responsável pela revogação é obrigatória.");
        }
        if (request.motivo() == null || request.motivo().trim().isBlank()) {
            throw new BusinessException("A justificativa formal da revogação é obrigatória.");
        }

        CampanhaVersao versao = aceite.getCampanhaVersao();
        if (versao != null) {
            validarImutabilidadeVersao(versao);
        }

        aceite.revogar(revogadoPor, request.motivo().trim());
        aceite = aceiteRepository.save(aceite);

        // Reavalia a completude das regras caso algum impedimento volte a ficar em aberto
        if (versao != null) {
            reavaliarCompletudeRegras(versao);
        }

        if (auditoriaService != null) {
            auditoriaService.registrarEvento(AuditoriaEventoContexto.builder()
                    .usuario(revogadoPor)
                    .tipoAcao(TipoAcaoAuditoria.REVOGACAO_ACEITE_PENDENCIA)
                    .tipoEntidade(TipoEntidadeAuditoria.PENDENCIA)
                    .entidadeId(String.valueOf(campanhaId))
                    .versaoEntidade(versao != null ? versao.getNumero() : null)
                    .campanhaId(campanha.getId())
                    .campanhaVersaoId(versao != null ? versao.getId() : null)
                    .campanhaTitulo(campanha.getTitulo())
                    .envioArquivoId(aceite.getEnvioArquivoId())
                    .descricao(String.format("Aceite da pendência '%s' revogado. Motivo: %s", aceite.getCodigo(), request.motivo()))
                    .dadosNovos(String.format("{\"aceiteId\": %d, \"codigoPendencia\": \"%s\", \"motivo\": \"%s\"}",
                            aceite.getId(), aceite.getCodigo(), request.motivo()))
                    .sucesso(true));
        }

        log.warn("Aceite de pendência {} da campanha {} revogado por '{}'. Motivo: {}",
                aceiteId, campanhaId, revogadoPor, request.motivo());

        return PendenciaAceiteResponse.de(aceite);
    }

    /**
     * Lista detalhada e consolidada de todas as pendências da versão com o estado de aceite de cada uma.
     */
    @Transactional(readOnly = true)
    public ResumoPendenciasCampanhaResponse listarPendencias(Long campanhaId, Integer versaoNumero) {
        Campanha campanha = buscarCampanha(campanhaId);
        CampanhaVersao versao = resolverVersao(campanha, versaoNumero);

        List<CampanhaPendenciaAceite> aceitesAtivos = aceiteRepository.findAllByCampanhaVersaoIdAndStatus(
                versao.getId(), StatusAceitePendencia.ACEITA
        );

        Map<String, CampanhaPendenciaAceite> aceitesPorIdentificador = aceitesAtivos.stream()
                .collect(Collectors.toMap(
                        CampanhaPendenciaAceite::getPendenciaIdentificador,
                        a -> a,
                        (existente, substituto) -> existente
                ));

        List<ItemPendenciaResumoDTO> itens = new ArrayList<>();
        List<Regra> regras = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(versao.getId());

        Set<String> idsProcessados = new HashSet<>();

        for (Regra r : regras) {
            RastreabilidadeRegraDTO rastreabilidade = RastreabilidadeRegraService.ler(r);
            if (rastreabilidade != null) {
                for (PendenciaRastreabilidadeDTO p : rastreabilidade.pendencias()) {
                    String pid = (p.id() != null && !p.id().isBlank()) ? p.id() : ("regra:" + r.getId() + ":" + p.codigo() + ":" + (p.campo() != null ? p.campo() : ""));
                    if (idsProcessados.add(pid)) {
                        CampanhaPendenciaAceite ace = aceitesPorIdentificador.get(pid);
                        itens.add(new ItemPendenciaResumoDTO(
                                pid,
                                r.getId(),
                                r.getNome(),
                                p.codigo(),
                                p.campo(),
                                p.mensagem(),
                                p.severidade() != null ? p.severidade() : SeveridadePendencia.AVISO,
                                ace != null,
                                PendenciaAceiteResponse.de(ace)
                        ));
                    }
                }
                for (PendenciaRastreabilidadeDTO p : rastreabilidade.pendenciasCampanha()) {
                    String pid = (p.id() != null && !p.id().isBlank()) ? p.id() : ("campanha:" + p.codigo());
                    if (idsProcessados.add(pid)) {
                        CampanhaPendenciaAceite ace = aceitesPorIdentificador.get(pid);
                        itens.add(new ItemPendenciaResumoDTO(
                                pid,
                                null,
                                "Geral da Campanha",
                                p.codigo(),
                                p.campo(),
                                p.mensagem(),
                                p.severidade() != null ? p.severidade() : SeveridadePendencia.AVISO,
                                ace != null,
                                PendenciaAceiteResponse.de(ace)
                        ));
                    }
                }

                // Verifica pendência de público geral
                boolean semPublico = r.getCanal() == null && r.getCodMarca() == null && r.getDescrMarca() == null
                        && r.getCodCargo() == null && r.getDescriCargo() == null && r.getCodLoja() == null && r.getMatricula() == null;
                if (semPublico && !Boolean.TRUE.equals(rastreabilidade.publicoTodasVendas())) {
                    String pid = "regra:" + r.getId() + ":PUBLICO_GERAL";
                    if (idsProcessados.add(pid)) {
                        CampanhaPendenciaAceite ace = aceitesPorIdentificador.get(pid);
                        itens.add(new ItemPendenciaResumoDTO(
                                pid,
                                r.getId(),
                                r.getNome(),
                                "PUBLICO_GERAL",
                                "filtros",
                                "Nenhum filtro de público-alvo restrito identificado. A regra se aplicará de forma genérica a todas as vendas.",
                                SeveridadePendencia.AVISO,
                                ace != null,
                                PendenciaAceiteResponse.de(ace)
                        ));
                    }
                }
            } else if (r.getPendencias() != null && !r.getPendencias().isBlank()) {
                String pid = "regra:" + r.getId() + ":PENDENCIA_REGRA";
                if (idsProcessados.add(pid)) {
                    CampanhaPendenciaAceite ace = aceitesPorIdentificador.get(pid);
                    itens.add(new ItemPendenciaResumoDTO(
                            pid,
                            r.getId(),
                            r.getNome(),
                            "PENDENCIA_REGRA",
                            "regra",
                            r.getPendencias(),
                            SeveridadePendencia.AVISO,
                            ace != null,
                            PendenciaAceiteResponse.de(ace)
                    ));
                }
            }
        }

        // Adiciona pendências que foram aceitas mas não vieram dos campos acima (aceites prévios)
        for (CampanhaPendenciaAceite ace : aceitesAtivos) {
            if (idsProcessados.add(ace.getPendenciaIdentificador())) {
                itens.add(new ItemPendenciaResumoDTO(
                        ace.getPendenciaIdentificador(),
                        ace.getRegra() != null ? ace.getRegra().getId() : null,
                        ace.getRegra() != null ? ace.getRegra().getNome() : "Campanha",
                        ace.getCodigo(),
                        ace.getCampo(),
                        ace.getMensagem(),
                        ace.getSeveridade(),
                        true,
                        PendenciaAceiteResponse.de(ace)
                ));
            }
        }

        int totalPendencias = itens.size();
        int totalImpedimentos = (int) itens.stream().filter(i -> SeveridadePendencia.IMPEDIMENTO.equals(i.severidade())).count();
        int totalAvisos = (int) itens.stream().filter(i -> SeveridadePendencia.AVISO.equals(i.severidade())).count();
        int totalAceitas = (int) itens.stream().filter(ItemPendenciaResumoDTO::aceita).count();
        int totalPendentesImpeditivas = (int) itens.stream()
                .filter(i -> SeveridadePendencia.IMPEDIMENTO.equals(i.severidade()) && !i.aceita())
                .count();
        boolean aptaParaAvanco = totalPendentesImpeditivas == 0;

        return new ResumoPendenciasCampanhaResponse(
                campanha.getId(),
                versao.getId(),
                versao.getNumero(),
                totalPendencias,
                totalImpedimentos,
                totalAvisos,
                totalAceitas,
                totalPendentesImpeditivas,
                aptaParaAvanco,
                itens
        );
    }

    /**
     * Trilha completa de auditoria de aceites e revogações para uma campanha.
     */
    @Transactional(readOnly = true)
    public List<PendenciaAceiteResponse> listarHistorico(Long campanhaId) {
        buscarCampanha(campanhaId);
        return aceiteRepository.findAllByCampanhaIdOrderByAceitoEmDesc(campanhaId)
                .stream()
                .map(PendenciaAceiteResponse::de)
                .toList();
    }

    /**
     * Reavalia e atualiza o flag `completa` de cada regra na versão, levando em consideração
     * os aceites formais registrados em `tb_campanha_pendencia_aceite`.
     */
    @Transactional
    public void reavaliarCompletudeRegras(CampanhaVersao versao) {
        List<CampanhaPendenciaAceite> aceitesAtivos = aceiteRepository.findAllByCampanhaVersaoIdAndStatus(
                versao.getId(), StatusAceitePendencia.ACEITA
        );

        Map<String, CampanhaPendenciaAceite> aceitesPorIdentificador = aceitesAtivos.stream()
                .collect(Collectors.toMap(
                        CampanhaPendenciaAceite::getPendenciaIdentificador,
                        a -> a,
                        (existente, substituto) -> existente
                ));

        List<Regra> regras = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(versao.getId());
        for (Regra r : regras) {
            RastreabilidadeRegraDTO rastreabilidade = RastreabilidadeRegraService.ler(r);
            if (rastreabilidade != null) {
                Set<String> identAceitosDaRegra = new HashSet<>();
                for (CampanhaPendenciaAceite ace : aceitesAtivos) {
                    if (ace.getRegra() == null || Objects.equals(ace.getRegra().getId(), r.getId())) {
                        identAceitosDaRegra.add(ace.getPendenciaIdentificador());
                    }
                }
                for (PendenciaRastreabilidadeDTO p : rastreabilidade.pendencias()) {
                    if (p.id() == null || p.id().isBlank()) {
                        String pidSintetico = "regra:" + r.getId() + ":" + p.codigo() + ":" + (p.campo() != null ? p.campo() : "");
                        if (aceitesPorIdentificador.containsKey(pidSintetico) && p.codigo() != null) {
                            identAceitosDaRegra.add(p.codigo());
                        }
                    }
                }
                for (PendenciaRastreabilidadeDTO p : rastreabilidade.pendenciasCampanha()) {
                    if (p.id() == null || p.id().isBlank()) {
                        String pidSintetico = "campanha:" + p.codigo();
                        if (aceitesPorIdentificador.containsKey(pidSintetico) && p.codigo() != null) {
                            identAceitosDaRegra.add(p.codigo());
                        }
                    }
                }
                if (aceitesPorIdentificador.containsKey("regra:" + r.getId() + ":PUBLICO_GERAL")) {
                    identAceitosDaRegra.add("PUBLICO_GERAL");
                }
                RastreabilidadeRegraService.aplicar(r, rastreabilidade, identAceitosDaRegra);
            } else {
                // Para regras legadas, se tem taxa válida e não tem impedimentos abertos
                if (r.getTaxa() != null) {
                    r.setCompleta(true);
                }
            }
        }
        regraRepository.saveAll(regras);
    }

    private Campanha buscarCampanha(Long campanhaId) {
        return campanhaRepository.findByIdAndRemovidoEmIsNull(campanhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + campanhaId + " não encontrada ou removida."));
    }

    private CampanhaVersao resolverVersao(Campanha campanha, Integer versaoNumero) {
        if (versaoNumero != null) {
            return campanhaVersaoRepository.findByCampanhaIdAndNumero(campanha.getId(), versaoNumero)
                    .orElseThrow(() -> new ResourceNotFoundException(String.format("Versão %d da campanha %d não encontrada.", versaoNumero, campanha.getId())));
        }
        CampanhaVersao proposta = campanha.getVersaoProposta();
        if (proposta != null) {
            return proposta;
        }
        CampanhaVersao vigente = campanha.getVersaoVigente();
        if (vigente != null) {
            return vigente;
        }
        return campanhaVersaoRepository.findFirstByCampanhaIdOrderByNumeroDesc(campanha.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Nenhuma versão encontrada para a campanha " + campanha.getId()));
    }

    private void validarImutabilidadeVersao(CampanhaVersao versao) {
        if (StatusVersaoCampanha.PUBLICADA.equals(versao.getStatus())) {
            throw new BusinessException("Versões já publicadas são imutáveis. Inicie uma nova proposta para gerenciar aceites de pendências.");
        }
    }
}

