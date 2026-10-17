package com.bugbusters.backend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bugbusters.backend.dto.regra.ConsultaConcorrenciaRequest;
import com.bugbusters.backend.dto.regra.ConsultaConcorrenciaResponse;
import com.bugbusters.backend.dto.regra.RegraConcorrenteDTO;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.dto.regra.TipoConcorrenciaRegra;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.repository.RegraRepository;

@Service
public class RegraConcorrenciaService {

    private final RegraRepository regraRepository;

    public RegraConcorrenciaService(RegraRepository regraRepository) {
        this.regraRepository = regraRepository;
    }

    @Transactional(readOnly = true)
    public ConsultaConcorrenciaResponse analisarConcorrencia(ConsultaConcorrenciaRequest request) {
        LocalDate dataInicio = request.dataInicio() != null ? request.dataInicio() : LocalDate.now();
        LocalDate dataFim = request.dataFim();

        List<Regra> candidatas = regraRepository.findRegrasAtivasComCampanha(StatusRegra.ATIVA)
                .stream()
                .filter(r -> request.campanhaId() == null || r.getCampanha() == null || !Objects.equals(r.getCampanha().getId(), request.campanhaId()))
                .filter(r -> haSobreposicaoPeriodo(dataInicio, dataFim, r.getDataInicio(), r.getDataFim()))
                .toList();


        int especificidadeProposta = calcularEspecificidade(
                request.codMarca(),
                request.canal(),
                request.codCargo(),
                request.codLoja(),
                request.matricula()
        );

        List<RegraConcorrenteDTO> concorrentes = new ArrayList<>();

        for (Regra r : candidatas) {
            if (!haColisaoDePublico(request, r)) {
                continue;
            }

            int especificidadeConcorrente = calcularEspecificidade(
                    r.getCodMarca(),
                    r.getCanal(),
                    r.getCodCargo(),
                    r.getCodLoja(),
                    r.getMatricula()
            );

            boolean dimensoesIguais = saoDimensoesIdenticas(request, r);
            boolean taxaIgual = request.taxa() != null && r.getTaxa() != null && request.taxa().compareTo(r.getTaxa()) == 0;
            TipoConcorrenciaRegra tipo;
            String mensagem;
            String nomeCampanha = r.getCampanha() != null ? r.getCampanha().getTitulo() : "Sem campanha vinculada";

            if (dimensoesIguais && (taxaIgual || request.taxa() == null)) {
                tipo = TipoConcorrenciaRegra.IDENTICA;
                mensagem = String.format(
                        "A regra proposta é idêntica à regra '%s' (ID %d) da campanha '%s'. Já existe uma regra com as mesmas dimensões cadastrada para este período.",
                        r.getNome(), r.getId(), nomeCampanha
                );
            } else if (dimensoesIguais || especificidadeProposta == especificidadeConcorrente) {
                tipo = TipoConcorrenciaRegra.EMPATE;
                mensagem = String.format(
                        "A regra proposta (especificidade %d) possui o mesmo grau de prioridade que a regra '%s' (ID %d) da campanha '%s' (especificidade %d). Em caso de vendas no período comum, haverá conflito/ambiguidade determinística no cálculo da comissão.",
                        especificidadeProposta, r.getNome(), r.getId(), nomeCampanha, especificidadeConcorrente
                );
            } else if (especificidadeProposta > especificidadeConcorrente) {
                tipo = TipoConcorrenciaRegra.SOBREPOSTA_MAIOR_PRECEDENCIA;
                mensagem = String.format(
                        "A regra proposta é mais específica (especificidade %d vs %d) que a regra '%s' (ID %d) da campanha '%s'. A regra proposta terá prioridade de aplicação sobre ela.",
                        especificidadeProposta, especificidadeConcorrente, r.getNome(), r.getId(), nomeCampanha
                );
            } else {
                tipo = TipoConcorrenciaRegra.SOBREPOSTA_MENOR_PRECEDENCIA;
                mensagem = String.format(
                        "A regra existente '%s' (ID %d) da campanha '%s' é mais específica (especificidade %d vs %d) que a proposta. A regra existente terá prioridade de aplicação sobre a proposta.",
                        r.getNome(), r.getId(), nomeCampanha, especificidadeConcorrente, especificidadeProposta
                );
            }

            concorrentes.add(new RegraConcorrenteDTO(
                    r.getId(),
                    r.getCampanha() != null ? r.getCampanha().getId() : null,
                    nomeCampanha,
                    r.getNome(),
                    r.getCanal(),
                    r.getCodMarca(),
                    r.getDescrMarca(),
                    r.getCodCargo(),
                    r.getDescriCargo(),
                    r.getCodLoja(),
                    r.getMatricula(),
                    r.getTaxa(),
                    r.getDataInicio(),
                    r.getDataFim(),
                    especificidadeProposta,
                    especificidadeConcorrente,
                    tipo,
                    mensagem
            ));
        }

        boolean temEmpate = concorrentes.stream().anyMatch(c -> c.tipoConcorrencia() == TipoConcorrenciaRegra.EMPATE);
        boolean temIdentica = concorrentes.stream().anyMatch(c -> c.tipoConcorrencia() == TipoConcorrenciaRegra.IDENTICA);

        return new ConsultaConcorrenciaResponse(
                concorrentes.size(),
                temEmpate,
                temIdentica,
                concorrentes
        );
    }

    /**
     * Calcula o peso/nível de especificidade da regra conforme TaxaComissaoResolver:
     * - Matrícula (colaborador individual): 16
     * - Loja: 8
     * - Cargo: 4
     * - Canal específico (diferente de PADRAO ou nulo/vazio): 2
     * - Marca: 1
     */
    public int calcularEspecificidade(Integer codMarca, String canal, Integer codCargo, Integer codLoja, String matricula) {
        int peso = 0;
        if (matricula != null && !matricula.isBlank()) {
            peso += 16;
        }
        if (codLoja != null) {
            peso += 8;
        }
        if (codCargo != null) {
            peso += 4;
        }
        if (canal != null && !canal.isBlank() && !"PADRAO".equalsIgnoreCase(canal.trim())) {
            peso += 2;
        }
        if (codMarca != null) {
            peso += 1;
        }
        return peso;
    }

    private boolean haColisaoDePublico(ConsultaConcorrenciaRequest req, Regra r) {
        // Marca colide se qualquer um for coringa (nulo) ou ambos tiverem o mesmo código
        if (req.codMarca() != null && r.getCodMarca() != null && !Objects.equals(req.codMarca(), r.getCodMarca())) {
            return false;
        }
        // Loja colide se qualquer um for coringa (nulo) ou ambos tiverem a mesma loja
        if (req.codLoja() != null && r.getCodLoja() != null && !Objects.equals(req.codLoja(), r.getCodLoja())) {
            return false;
        }
        // Cargo colide se qualquer um for coringa (nulo) ou ambos tiverem o mesmo cargo
        if (req.codCargo() != null && r.getCodCargo() != null && !Objects.equals(req.codCargo(), r.getCodCargo())) {
            return false;
        }
        // Matrícula colide se qualquer um for coringa (nulo/vazio) ou ambas forem iguais
        String mReq = normalizarMatricula(req.matricula());
        String mRegra = normalizarMatricula(r.getMatricula());
        if (mReq != null && mRegra != null && !mReq.equalsIgnoreCase(mRegra)) {
            return false;
        }
        // Canal colide se qualquer um for coringa (PADRAO/nulo/vazio) ou ambos forem iguais
        String cReq = normalizarCanal(req.canal());
        String cRegra = normalizarCanal(r.getCanal());
        if (cReq != null && cRegra != null && !cReq.equalsIgnoreCase(cRegra)) {
            return false;
        }
        return true;
    }

    private boolean saoDimensoesIdenticas(ConsultaConcorrenciaRequest req, Regra r) {
        return Objects.equals(req.codMarca(), r.getCodMarca())
                && Objects.equals(req.codLoja(), r.getCodLoja())
                && Objects.equals(req.codCargo(), r.getCodCargo())
                && Objects.equals(normalizarMatricula(req.matricula()), normalizarMatricula(r.getMatricula()))
                && Objects.equals(normalizarCanal(req.canal()), normalizarCanal(r.getCanal()));
    }

    private String normalizarCanal(String canal) {
        if (canal == null || canal.isBlank() || "PADRAO".equalsIgnoreCase(canal.trim())) {
            return null; // Coringa
        }
        return canal.trim().toUpperCase();
    }

    private String normalizarMatricula(String matricula) {
        if (matricula == null || matricula.isBlank()) {
            return null; // Coringa
        }
        return matricula.trim();
    }

    private boolean haSobreposicaoPeriodo(LocalDate ini1, LocalDate fim1, LocalDate ini2, LocalDate fim2) {
        if (ini1 == null || ini2 == null) {
            return true;
        }
        boolean fim1DepoisIni2 = (fim1 == null) || !fim1.isBefore(ini2);
        boolean fim2DepoisIni1 = (fim2 == null) || !fim2.isBefore(ini1);
        return fim1DepoisIni2 && fim2DepoisIni1;
    }
}

