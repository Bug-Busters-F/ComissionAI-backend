package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.simulacao.*;
import com.bugbusters.backend.dto.programapython.*;
import com.bugbusters.backend.exception.*;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.dto.auditoria.AuditoriaEventoContexto;
import com.bugbusters.backend.service.auth.UsuarioAtualProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class AplicacaoCalibracaoService {
    private final SimulacaoRepository simulacoes;
    private final CampanhaRepository campanhas;
    private final RegraRepository regras;
    private final CampanhaProgramaPythonService programas;
    private final CampanhaSimulacaoValidator validator;
    private final FechamentoService fechamento;
    private final UsuarioAtualProvider usuario;
    private final AuditoriaGeralService auditoria;
    public AplicacaoCalibracaoService(SimulacaoRepository s, CampanhaRepository c, RegraRepository r,
            CampanhaProgramaPythonService p, CampanhaSimulacaoValidator v,
            FechamentoService f, UsuarioAtualProvider u, AuditoriaGeralService a) {
        simulacoes=s;campanhas=c;regras=r;programas=p;validator=v;fechamento=f;usuario=u;auditoria=a;
    }

    @Transactional(readOnly=true)
    public AnaliseSimulacaoDTO consultar(UUID id) {
        Simulacao s = buscar(id);
        AnaliseSimulacaoDTO a = SimulacaoAnaliseService.ler(s);
        if(a==null) throw new ResourceNotFoundException("Esta simulação não possui análise Python. Execute uma nova simulação.");
        return a;
    }

    @Transactional
    public ProgramaPythonResponse aplicar(UUID id, AplicarCalibracaoRequest request) {
        Simulacao s = buscar(id);
        if(s.getCampanha()==null) throw new BusinessException("Simulação sem campanha vinculada.");
        Campanha c = campanhas.buscarParaCalibracao(s.getCampanha().getId()).orElseThrow(()->new ResourceNotFoundException("Campanha não encontrada."));
        if(!Objects.equals(c.getVersaoControle(),request.revisaoEsperada())) throw new ResourceConflictException("A campanha foi alterada. Atualize antes de aplicar a alternativa.");
        CampanhaVersao v=c.getVersaoProposta();
        if(v==null || s.getCampanhaVersao()==null || !Objects.equals(v.getId(),s.getCampanhaVersao().getId())
                || v.getStatus()==StatusVersaoCampanha.PUBLICADA || v.getStatus()==StatusVersaoCampanha.CANCELADA
                || c.getEstado()==EstadoCampanha.CANCELADA)
            throw new BusinessException("A alternativa somente pode ser aplicada à proposta editável que originou a simulação.");
        if(s.getInvalidadoEm()!=null || s.getStatusAceite()==StatusAceiteSimulacao.INVALIDADA || !Objects.equals(s.getHashProposta(), validator.calcularHashPropostaCampanha(v.getDataInicio(),v.getDataFim(),
                        regras.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(v.getId()),s.getCompetencia())))
            throw new ResourceConflictException("A simulação está desatualizada. Execute novamente antes de aplicar a alternativa.");
        fechamento.validarPeriodoDisponivel(v.getDataInicio(),v.getDataFim());
        AnaliseSimulacaoDTO a=consultar(id);
        if(v.getOrcamento()==null || a.orcamento()==null || v.getOrcamento().compareTo(a.orcamento())!=0)
            throw new ResourceConflictException("O orçamento da proposta mudou. Execute uma nova simulação.");
        CampanhaProgramaPython atual=v.getProgramaPythonAprovado();
        if(atual==null || !atual.isAprovado() || !Objects.equals(a.assinaturaPrograma(),request.assinaturaProgramaEsperada())
                || !Objects.equals(atual.getChecksumSha256(),a.assinaturaPrograma()))
            throw new ResourceConflictException("O programa aprovado mudou. Execute novamente após a revisão técnica.");
        CampanhaProgramaPythonService.validarIntegridadePrograma(atual);
        var alternativa=a.calibracoes().stream().filter(x->x.cenario().equals(request.cenario()) && "ENQUADRADA".equals(x.status())).findFirst()
                .orElseThrow(()->new BusinessException("Não há alternativa verificada para este cenário."));
        List<Regra> originais=regras.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(v.getId());
        List<Regra> candidatas=SimulacaoAnaliseService.candidatas(originais,alternativa.alteracoes());
        String codigo=programas.gerarCodigoConsolidado(v,candidatas).strip();
        if(!Objects.equals(CampanhaProgramaPython.calcularChecksum(codigo),alternativa.assinaturaCandidata()))
            throw new ResourceConflictException("As regras da alternativa não correspondem mais à proposta atual.");
        OffsetDateTime agora=OffsetDateTime.now();
        for(var mudanca:alternativa.alteracoes()) {
            Regra r=originais.stream().filter(x->x.getId().equals(mudanca.regraId())).findFirst().orElseThrow();
            SimulacaoAnaliseService.atribuir(r,mudanca.campo(),mudanca.depois());
            r.setExplicacao(null);r.setPythonEquivalente(null);invalidarOrigem(r,mudanca.campo());
            r.setStatusAprovacaoCodigo(StatusAprovacaoCodigo.INVALIDADO);r.setCodigoInvalidadoEm(agora);
            r.setMotivoInvalidacaoCodigo("Parâmetro alterado por calibração confirmada; exige nova revisão.");r.setHashArtefatosCodigo(null);
        }
        regras.saveAll(originais);
        for(Simulacao anterior:simulacoes.findAllByCampanhaVersaoId(v.getId())) {
            anterior.setStatusAceite(StatusAceiteSimulacao.INVALIDADA); anterior.setInvalidadoEm(agora);
            anterior.setMotivoInvalidacao("Alternativa de calibração aplicada. É necessária nova revisão e simulação.");
        }
        v.setSimulacaoAceita(null);v.setStatusAprovacaoCodigo(StatusAprovacaoCodigo.INVALIDADO);v.setCodigoInvalidadoEm(agora);
        v.setMotivoInvalidacaoCodigo("Calibração aplicada à proposta.");v.setEncaminhadaGerente(false);
        v.setUltimaEtapa(2);v.setEtapaMaisAvancada(2);v.setEstadoEsteira(EstadoEsteiraCampanha.EM_REVISAO_TECNICA);
        if(c.getVersaoVigente()==null) c.setSimulacaoAceita(null);
        c.setAtualizadoEm(agora);
        programas.invalidarAprovacaoPorMudancaRegra(v,"Calibração aplicada; nova aprovação obrigatória.");
        ProgramaPythonResponse novo=programas.salvarOuAtualizarCodigo(c.getId(),v.getNumero(),
                new AtualizarProgramaPythonRequest(codigo,usuario.obterUsuarioAtual(null),"Alternativa confirmada do cenário "+request.cenario()+", simulação "+id));
        campanhas.saveAndFlush(c);
        auditoria.registrarEvento(AuditoriaEventoContexto.builder().usuario(usuario.obterUsuarioAtual(null))
                .tipoAcao(TipoAcaoAuditoria.ACAO_OPERACIONAL).tipoEntidade(TipoEntidadeAuditoria.SIMULACAO)
                .entidadeId(id.toString()).campanhaId(c.getId()).campanhaVersaoId(v.getId()).simulacaoId(id)
                .descricao("Alternativa de calibração aplicada ao rascunho no cenário "+request.cenario()+". Nova revisão e simulação obrigatórias.")
                .dadosAnteriores(s.getAnalisePythonSnapshot()).dadosNovos("{\"assinaturaCandidata\":\""+alternativa.assinaturaCandidata()+"\"}").sucesso(true));
        return novo;
    }
    private void invalidarOrigem(Regra regra, String campo) {
        if(regra.getRastreabilidadeJson()==null) return;
        try {
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
            var dados=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(regra.getRastreabilidadeJson());
            dados.put("artefatosAtualizados",false);
            if(dados.get("origensPorCampo") instanceof com.fasterxml.jackson.databind.node.ObjectNode origens) {
                origens.remove(campo);
                if("taxa".equals(campo)) {origens.remove("taxaFinal");origens.remove("taxaPercentual");}
            }
            regra.setRastreabilidadeJson(mapper.writeValueAsString(dados));
        } catch(Exception e) { throw new BusinessException("Não foi possível atualizar a rastreabilidade da regra. Nenhuma alteração foi aplicada."); }
    }
    private Simulacao buscar(UUID id) {return simulacoes.findById(id).orElseThrow(()->new ResourceNotFoundException("Simulação não encontrada."));}
}
