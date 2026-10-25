package com.bugbusters.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.bugbusters.backend.dto.interpretador.proposta.FaixaValorDTO;
import com.bugbusters.backend.dto.interpretador.proposta.FiltrosRegraDTO;
import com.bugbusters.backend.dto.interpretador.proposta.OperacaoBaseDTO;
import com.bugbusters.backend.dto.interpretador.proposta.PendenciaPropostaDTO;
import com.bugbusters.backend.dto.interpretador.proposta.PeriodoVigenciaDTO;
import com.bugbusters.backend.dto.interpretador.proposta.PropostaRegraDTO;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.model.CatalogoDominioDomRock;

@Service
public class ArtefatoExplicativoService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Calcula a taxa efetiva final com base na operação sobre a base e nos parâmetros fornecidos.
     */
    public BigDecimal calcularTaxaEfetiva(OperacaoBaseDTO operacao) {
        if (operacao == null || operacao.tipoOperacao() == null) {
            return null;
        }

        TipoOperacaoBase tipo = operacao.tipoOperacao();
        BigDecimal ajuste = operacao.valorAjuste();
        BigDecimal base = (operacao.referenciaBase() != null)
                ? operacao.referenciaBase().taxaBaseConsultada()
                : null;

        return switch (tipo) {
            case DEFINIR_TAXA -> (ajuste != null) ? ajuste.setScale(4, RoundingMode.HALF_UP) : null;
            case ACRESCIMO_PONTOS -> {
                if (base == null || ajuste == null) yield null;
                yield base.add(ajuste).setScale(4, RoundingMode.HALF_UP);
            }
            case DESCONTO_PONTOS -> {
                if (base == null || ajuste == null) yield null;
                yield base.subtract(ajuste).setScale(4, RoundingMode.HALF_UP);
            }
            case MULTIPLICADOR_BASE -> {
                if (base == null || ajuste == null) yield null;
                yield base.multiply(ajuste).setScale(4, RoundingMode.HALF_UP);
            }
            case DIVISOR_BASE -> {
                if (base == null || ajuste == null || ajuste.compareTo(BigDecimal.ZERO) == 0) yield null;
                yield base.divide(ajuste, 4, RoundingMode.HALF_UP);
            }
        };
    }

    /**
     * Valida integralmente uma proposta e retorna a lista de pendências (avisos ou impedimentos).
     */
    public List<PendenciaPropostaDTO> validarProposta(PropostaRegraDTO proposta) {
        List<PendenciaPropostaDTO> pendencias = new ArrayList<>();

        if (proposta == null) {
            pendencias.add(PendenciaPropostaDTO.impedimento("proposta", "PROPOSTA_NULA", "Os dados da proposta não foram informados."));
            return pendencias;
        }

        // 1. Validação da Taxa e Operação Base
        OperacaoBaseDTO operacao = proposta.operacaoBase();
        if (operacao == null) {
            pendencias.add(PendenciaPropostaDTO.impedimento("operacaoBase", "OPERACAO_AUSENTE", "A operação sobre a base de comissão não foi especificada."));
        } else {
            if (operacao.tipoOperacao() == TipoOperacaoBase.DIVISOR_BASE) {
                if (operacao.valorAjuste() == null || operacao.valorAjuste().compareTo(BigDecimal.ZERO) == 0) {
                    pendencias.add(PendenciaPropostaDTO.impedimento("operacaoBase.valorAjuste", "DIVISAO_POR_ZERO", "O fator divisor da base não pode ser nulo nem igual a zero."));
                }
            }

            if (operacao.tipoOperacao() != TipoOperacaoBase.DEFINIR_TAXA) {
                if (operacao.referenciaBase() == null || operacao.referenciaBase().taxaBaseConsultada() == null) {
                    pendencias.add(PendenciaPropostaDTO.impedimento("operacaoBase.referenciaBase", "BASE_REFERENCIA_NAO_ENCONTRADA",
                            "Operação sobre a base requer taxa contratual de referência (tb_basecomiss), que não foi localizada para o público-alvo."));
                }
                if (operacao.valorAjuste() == null) {
                    pendencias.add(PendenciaPropostaDTO.impedimento("operacaoBase.valorAjuste", "AJUSTE_AUSENTE", "O valor do ajuste sobre a base é obrigatório."));
                }
            } else {
                if (operacao.valorAjuste() == null) {
                    pendencias.add(PendenciaPropostaDTO.impedimento("operacaoBase.valorAjuste", "TAXA_AUSENTE", "O percentual fixo da regra é obrigatório."));
                }
            }
        }

        BigDecimal taxaCalculada = calcularTaxaEfetiva(operacao);
        if (taxaCalculada == null && proposta.taxaFinal() == null) {
            pendencias.add(PendenciaPropostaDTO.impedimento("taxaFinal", "TAXA_NAO_CALCULAVEL", "Não foi possível determinar a taxa percentual final da regra."));
        } else {
            BigDecimal taxaEfetiva = taxaCalculada != null ? taxaCalculada : proposta.taxaFinal();
            if (taxaEfetiva.compareTo(BigDecimal.ZERO) <= 0) {
                pendencias.add(PendenciaPropostaDTO.impedimento("taxaFinal", "TAXA_NAO_POSITIVA", "A taxa final resultante deve ser estritamente positiva (> 0.0000)."));
            } else if (taxaEfetiva.compareTo(BigDecimal.ONE) > 0) {
                pendencias.add(PendenciaPropostaDTO.impedimento("taxaFinal", "TAXA_EXCEDE_100_PORCENTO", "A taxa percentual calculada (" + formatarTaxa(taxaEfetiva) + ") excede o limite máximo permitido de 100% (1.0000)."));
            }
        }

        // 2. Validação de Filtros de Público
        FiltrosRegraDTO filtros = proposta.filtros();
        boolean temFiltroPublico = filtros != null && (
                filtros.canal() != null || filtros.codMarca() != null || filtros.descrMarca() != null ||
                filtros.codLoja() != null || filtros.codCargo() != null || filtros.descriCargo() != null ||
                filtros.matricula() != null
        );
        if (!temFiltroPublico) {
            pendencias.add(PendenciaPropostaDTO.aviso("filtros", "PUBLICO_GERAL", "Nenhum filtro de público-alvo restrito identificado. A regra se aplicará de forma genérica a todas as vendas."));
        } else {
            if (filtros.matricula() != null && !filtros.matricula().isBlank()) {
                if (!CatalogoDominioDomRock.isMatriculaValida(filtros.matricula())) {
                    pendencias.add(PendenciaPropostaDTO.aviso("filtros.matricula", "FORMATO_MATRICULA",
                            "A matrícula '" + filtros.matricula() + "' não segue o padrão cadastral da rede (ex: MATRIC-123)."));
                }
            }
            if (filtros.codLoja() != null) {
                if (filtros.codLoja() <= 0) {
                    pendencias.add(PendenciaPropostaDTO.impedimento("filtros.codLoja", "LOJA_INVALIDA",
                            "O código da loja deve ser um número positivo."));
                } else if (!CatalogoDominioDomRock.isLojaValida(filtros.codLoja())) {
                    pendencias.add(PendenciaPropostaDTO.aviso("filtros.codLoja", "LOJA_FORA_CATALOGO",
                            "Código da loja (" + filtros.codLoja() + ") fora do catálogo ativo da rede (Lojas 1 a 80)."));
                }
            }
        }

        // 3. Validação de Faixa de Valor Monetário
        FaixaValorDTO faixa = proposta.condicaoValor();
        if (faixa != null && faixa.possuiFaixa()) {
            if (faixa.valorMinimo() != null && faixa.valorMinimo().compareTo(BigDecimal.ZERO) < 0) {
                pendencias.add(PendenciaPropostaDTO.impedimento("condicaoValor.valorMinimo", "VALOR_MINIMO_NEGATIVO", "O valor mínimo de venda não pode ser negativo."));
            }
            if (faixa.valorMaximo() != null && faixa.valorMaximo().compareTo(BigDecimal.ZERO) <= 0) {
                pendencias.add(PendenciaPropostaDTO.impedimento("condicaoValor.valorMaximo", "VALOR_MAXIMO_INVALIDO", "O valor máximo de venda deve ser estritamente positivo."));
            }
            if (faixa.valorMinimo() != null && faixa.valorMaximo() != null && faixa.valorMinimo().compareTo(faixa.valorMaximo()) > 0) {
                pendencias.add(PendenciaPropostaDTO.impedimento("condicaoValor", "FAIXA_VALOR_INCOERENTE", "O valor mínimo de venda (" + faixa.valorMinimo() + ") não pode ser superior ao valor máximo (" + faixa.valorMaximo() + ")."));
            }
        }

        // 4. Validação de Vigência Temporal
        PeriodoVigenciaDTO vigencia = proposta.vigencia();
        if (vigencia == null || vigencia.dataInicio() == null) {
            pendencias.add(PendenciaPropostaDTO.aviso("vigencia.dataInicio", "DATA_INICIO_OMITIDA", "Data inicial não informada; será assumida a data da aprovação."));
        }
        if (vigencia == null || vigencia.dataFim() == null) {
            pendencias.add(PendenciaPropostaDTO.aviso("vigencia.dataFim", "DATA_FIM_OMITIDA", "Data final não informada; será aplicado o padrão de 30 dias de vigência."));
        } else if (vigencia.dataInicio() != null && vigencia.dataFim().isBefore(vigencia.dataInicio())) {
            pendencias.add(PendenciaPropostaDTO.impedimento("vigencia.dataFim", "DATA_FIM_ANTERIOR_INICIO", "A data final da vigência (" + vigencia.dataFim() + ") não pode ser anterior à data inicial (" + vigencia.dataInicio() + ")."));
        }

        return pendencias;
    }

    /**
     * Gera o texto explicativo em linguagem natural (XAI) descrevendo as condições da proposta.
     */
    public String gerarExplicacao(PropostaRegraDTO proposta) {
        if (proposta == null) {
            return "Proposta de regra não informada.";
        }

        StringBuilder sb = new StringBuilder();
        BigDecimal taxaEfetiva = proposta.taxaFinal() != null ? proposta.taxaFinal() : calcularTaxaEfetiva(proposta.operacaoBase());

        sb.append("Aplica taxa de ");
        if (taxaEfetiva != null) {
            sb.append(formatarTaxa(taxaEfetiva));
        } else {
            sb.append("[não definida]");
        }

        // Detalhe da operação sobre a base
        OperacaoBaseDTO op = proposta.operacaoBase();
        if (op != null && op.tipoOperacao() != null) {
            BigDecimal base = op.referenciaBase() != null ? op.referenciaBase().taxaBaseConsultada() : null;
            BigDecimal ajuste = op.valorAjuste();
            switch (op.tipoOperacao()) {
                case DEFINIR_TAXA -> sb.append(" (taxa fixa definida diretamente)");
                case ACRESCIMO_PONTOS -> {
                    if (base != null && ajuste != null) {
                        sb.append(String.format(" (base contratual de %s + acréscimo de %s)", formatarTaxa(base), formatarTaxa(ajuste)));
                    } else if (ajuste != null) {
                        sb.append(String.format(" (taxa base + acréscimo de %s)", formatarTaxa(ajuste)));
                    }
                }
                case DESCONTO_PONTOS -> {
                    if (base != null && ajuste != null) {
                        sb.append(String.format(" (base contratual de %s - desconto de %s)", formatarTaxa(base), formatarTaxa(ajuste)));
                    } else if (ajuste != null) {
                        sb.append(String.format(" (taxa base - desconto de %s)", formatarTaxa(ajuste)));
                    }
                }
                case MULTIPLICADOR_BASE -> {
                    if (base != null && ajuste != null) {
                        sb.append(String.format(" (base contratual de %s multiplicada por %s)", formatarTaxa(base), ajuste));
                    } else if (ajuste != null) {
                        sb.append(String.format(" (taxa base multiplicada pelo fator %s)", ajuste));
                    }
                }
                case DIVISOR_BASE -> {
                    if (base != null && ajuste != null) {
                        sb.append(String.format(" (base contratual de %s dividida por %s)", formatarTaxa(base), ajuste));
                    } else if (ajuste != null) {
                        sb.append(String.format(" (taxa base dividida pelo fator %s)", ajuste));
                    }
                }
            }
        }

        // Detalhe do público-alvo
        FiltrosRegraDTO filtros = proposta.filtros();
        List<String> criteriosPublico = new ArrayList<>();
        if (filtros != null) {
            if (filtros.canal() != null && !filtros.canal().isBlank()) {
                criteriosPublico.add("no canal " + filtros.canal().toUpperCase());
            }
            if (filtros.descrMarca() != null && !filtros.descrMarca().isBlank()) {
                criteriosPublico.add("para a marca " + filtros.descrMarca().toUpperCase());
            } else if (filtros.codMarca() != null) {
                criteriosPublico.add("para a marca código " + filtros.codMarca());
            }
            if (filtros.descriCargo() != null && !filtros.descriCargo().isBlank()) {
                criteriosPublico.add("para o cargo " + filtros.descriCargo().toUpperCase());
            } else if (filtros.codCargo() != null) {
                criteriosPublico.add("para o cargo código " + filtros.codCargo());
            }
            if (filtros.codLoja() != null) {
                criteriosPublico.add("na loja " + filtros.codLoja());
            }
            if (filtros.matricula() != null && !filtros.matricula().isBlank()) {
                criteriosPublico.add("especificamente para o vendedor de matrícula " + filtros.matricula());
            }
        }

        if (!criteriosPublico.isEmpty()) {
            sb.append(" para vendas ").append(String.join(", ", criteriosPublico));
        } else {
            sb.append(" para todas as vendas elegíveis");
        }

        // Detalhe da faixa monetária
        FaixaValorDTO faixa = proposta.condicaoValor();
        if (faixa != null && faixa.possuiFaixa()) {
            if (faixa.valorMinimo() != null && faixa.valorMaximo() != null) {
                String opMin = Boolean.TRUE.equals(faixa.minInclusivo()) ? "a partir de" : "superior a";
                String opMax = Boolean.TRUE.equals(faixa.maxInclusivo()) ? "até" : "inferior a";
                sb.append(String.format(java.util.Locale.ROOT, " com valor de venda %s R$ %.2f e %s R$ %.2f", opMin, faixa.valorMinimo(), opMax, faixa.valorMaximo()));
            } else if (faixa.valorMinimo() != null) {
                String opMin = Boolean.TRUE.equals(faixa.minInclusivo()) ? "a partir de" : "superior a";
                sb.append(String.format(java.util.Locale.ROOT, " com valor de venda %s R$ %.2f", opMin, faixa.valorMinimo()));
            } else if (faixa.valorMaximo() != null) {
                String opMax = Boolean.TRUE.equals(faixa.maxInclusivo()) ? "até" : "inferior a";
                sb.append(String.format(java.util.Locale.ROOT, " com valor de venda %s R$ %.2f", opMax, faixa.valorMaximo()));
            }
        }

        // Detalhe da vigência
        PeriodoVigenciaDTO vig = proposta.vigencia();
        if (vig != null && vig.dataInicio() != null && vig.dataFim() != null) {
            sb.append(String.format(", vigendo de %s até %s.", vig.dataInicio().format(DATE_FORMATTER), vig.dataFim().format(DATE_FORMATTER)));
        } else if (vig != null && vig.dataInicio() != null) {
            sb.append(String.format(", vigendo a partir de %s.", vig.dataInicio().format(DATE_FORMATTER)));
        } else {
            sb.append(".");
        }

        if (proposta.bonusPercentual() != null) sb.append(" Bônus percentual por venda: ").append(formatarTaxa(proposta.bonusPercentual())).append(".");
        if (proposta.bonusValorFixo() != null) sb.append(" Bônus fixo por venda: R$ ").append(proposta.bonusValorFixo().toPlainString()).append(".");
        return sb.toString();
    }

    /**
     * Gera o código Python equivalente para simulação e execução determinística da regra.
     */
    public String gerarPythonEquivalente(PropostaRegraDTO p) {
        if (p == null || p.taxaFinal() == null) return null;
        StringBuilder py = new StringBuilder("# Python equivalente desta regra; exibido para conferência, não executado pelo sistema.\nfrom decimal import Decimal, ROUND_HALF_UP\n\ndef calcular_comissao(venda):\n");
        PeriodoVigenciaDTO vig = p.vigencia();
        if (vig != null && vig.dataInicio() != null && vig.dataFim() != null) {
            py.append("    data_venda = str(venda.get('data_venda', ''))[:10]\n    if not ('").append(vig.dataInicio()).append("' <= data_venda <= '").append(vig.dataFim()).append("'):\n        return None\n");
        }
        FiltrosRegraDTO f = p.filtros();
        if (f != null) {
            filtroPython(py, "canal", f.canal() == null ? null : f.canal().toUpperCase());
            filtroPython(py, "cod_marca", f.codMarca());
            if (f.codMarca() == null) filtroPython(py, "descr_marca", f.descrMarca());
            filtroPython(py, "cod_loja", f.codLoja());
            filtroPython(py, "cod_cargo", f.codCargo());
            if (f.codCargo() == null) filtroPython(py, "descri_cargo", f.descriCargo());
            filtroPython(py, "matricula", f.matricula());
        }
        py.append("    valor = Decimal(str(venda['valor_venda']))\n    if valor <= 0:\n        return None\n");
        FaixaValorDTO faixa = p.condicaoValor();
        if (faixa != null) {
            if (faixa.valorMinimo() != null) py.append("    if valor ").append(Boolean.TRUE.equals(faixa.minInclusivo()) ? "<" : "<=").append(" Decimal('").append(faixa.valorMinimo().toPlainString()).append("'):\n        return None\n");
            if (faixa.valorMaximo() != null) py.append("    if valor ").append(Boolean.TRUE.equals(faixa.maxInclusivo()) ? ">" : ">=").append(" Decimal('").append(faixa.valorMaximo().toPlainString()).append("'):\n        return None\n");
        }
        py.append("    centavo = Decimal('0.01')\n    taxa_aplicada = Decimal('").append(p.taxaFinal().toPlainString()).append("')\n    comissao = (valor * taxa_aplicada).quantize(centavo, rounding=ROUND_HALF_UP)\n");
        if (p.bonusPercentual() != null) py.append("    comissao += (valor * Decimal('").append(p.bonusPercentual().toPlainString()).append("')).quantize(centavo, rounding=ROUND_HALF_UP)\n");
        if (p.bonusValorFixo() != null) py.append("    comissao += Decimal('").append(p.bonusValorFixo().toPlainString()).append("').quantize(centavo, rounding=ROUND_HALF_UP)\n");
        return py.append("    return comissao\n").toString();
    }

    private void filtroPython(StringBuilder py, String chave, Object valor) {
        if (valor == null || valor.toString().isBlank()) return;
        // JSON strings are valid Python literals for these scalar string values and escape newlines/quotes.
        String literal;
        try { literal = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(valor); }
        catch (Exception e) { throw new IllegalArgumentException("Filtro inválido", e); }
        py.append("    if venda.get('").append(chave).append("') != ").append(literal).append(":\n        return None\n");
    }

    private String formatarTaxa(BigDecimal taxa) {
        if (taxa == null) return "0.00%";
        BigDecimal percent = taxa.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        return percent.toPlainString() + "%";
    }
}
