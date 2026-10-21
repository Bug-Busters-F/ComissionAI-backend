package com.bugbusters.backend.importbase.dependencia;

import com.bugbusters.backend.model.EnvioArquivo;
import com.bugbusters.backend.model.LogCalculoImutavel;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.model.ResultadoCalculo;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.sales.SaleRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class VerificadorDependenciaRH implements VerificadorDependenciaLote {

    private final RegistrationRepository registrationRepository;
    private final SaleRepository saleRepository;
    private final ResultadoCalculoRepository resultadoCalculoRepository;
    private final LogCalculoRepository logCalculoRepository;
    private final RegraRepository regraRepository;

    public VerificadorDependenciaRH(RegistrationRepository registrationRepository,
                                   SaleRepository saleRepository,
                                   ResultadoCalculoRepository resultadoCalculoRepository,
                                   LogCalculoRepository logCalculoRepository,
                                   RegraRepository regraRepository) {
        this.registrationRepository = registrationRepository;
        this.saleRepository = saleRepository;
        this.resultadoCalculoRepository = resultadoCalculoRepository;
        this.logCalculoRepository = logCalculoRepository;
        this.regraRepository = regraRepository;
    }

    @Override
    public List<DependenciaMotivoDTO> verificar(EnvioArquivo lote) {
        if (lote == null || !"HR".equalsIgnoreCase(lote.getTipoBase())) {
            return List.of();
        }

        List<DependenciaMotivoDTO> motivos = new ArrayList<>();
        String idLote = lote.getIdLote();
        if (idLote == null || idLote.isBlank()) {
            return motivos;
        }

        List<Registration> matriculas = registrationRepository.findByIdLoteOrigem(idLote);
        if (matriculas.isEmpty()) {
            return motivos;
        }

        List<UUID> registrationIds = matriculas.stream().map(Registration::getId).toList();
        List<String> codigosMatriculas = matriculas.stream().map(Registration::getRegistration).toList();

        // 1. Vendas que referenciam as matrículas do lote (registration_id)
        if (saleRepository.existsByRegistrationIdIn(registrationIds)) {
            List<String> exemplos = codigosMatriculas.stream().limit(5).toList();
            motivos.add(new DependenciaMotivoDTO(
                    "vendas",
                    String.format("Existem venda(s) vinculadas às matrículas deste lote de RH. Total de matrículas no lote: %d. Exemplos: %s", matriculas.size(), exemplos)
            ));
        }

        // 2. Resultados de cálculo com as matrículas do lote
        if (resultadoCalculoRepository.existsByMatriculaIn(codigosMatriculas)) {
            List<ResultadoCalculo> top5 = resultadoCalculoRepository.findTop5ByMatriculaIn(codigosMatriculas);
            List<String> exemplos = top5.stream().map(ResultadoCalculo::getMatricula).distinct().limit(5).toList();
            motivos.add(new DependenciaMotivoDTO(
                    "calculos",
                    String.format("Existem resultado(s) de cálculo vinculados às matrículas deste lote de RH. Exemplos de matrícula: %s", exemplos)
            ));
        }

        // 3. Logs financeiros imutáveis com as matrículas do lote
        if (logCalculoRepository.existsByMatriculaIn(codigosMatriculas)) {
            List<LogCalculoImutavel> top5 = logCalculoRepository.findTop5ByMatriculaIn(codigosMatriculas);
            List<String> exemplos = top5.stream().map(LogCalculoImutavel::getMatricula).distinct().limit(5).toList();
            motivos.add(new DependenciaMotivoDTO(
                    "logs_financeiros",
                    String.format("Existem log(s) de auditoria financeira vinculados às matrículas deste lote de RH. Exemplos de matrícula: %s", exemplos)
            ));
        }

        // 4. Regras ativas de campanha cujo filtro de matrícula coincide com matrículas do lote
        if (regraRepository.existsByMatriculaInAndRemovidoEmIsNull(codigosMatriculas)) {
            List<Regra> top5 = regraRepository.findTop5ByMatriculaInAndRemovidoEmIsNull(codigosMatriculas);
            List<String> exemplos = top5.stream().map(Regra::getMatricula).distinct().limit(5).toList();
            motivos.add(new DependenciaMotivoDTO(
                    "regras_campanha",
                    String.format("Existem regra(s) de campanha vinculadas às matrículas deste lote de RH. Exemplos de matrícula: %s", exemplos)
            ));
        }

        return motivos;
    }
}
