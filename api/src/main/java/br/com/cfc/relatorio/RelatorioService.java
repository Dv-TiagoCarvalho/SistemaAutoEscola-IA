package br.com.cfc.relatorio;

import br.com.cfc.agenda.Aula;
import br.com.cfc.agenda.AulaRepository;
import br.com.cfc.agenda.Instrutor;
import br.com.cfc.agenda.InstrutorRepository;
import br.com.cfc.agenda.StatusAula;
import br.com.cfc.agenda.Veiculo;
import br.com.cfc.agenda.VeiculoRepository;
import br.com.cfc.aluno.Aluno;
import br.com.cfc.aluno.AlunoRepository;
import br.com.cfc.aluno.AlunoService;
import br.com.cfc.autoescola.ConfiguracaoAutoescola;
import br.com.cfc.autoescola.ConfiguracaoService;
import br.com.cfc.financeiro.FinanceiroDtos.MesFluxo;
import br.com.cfc.financeiro.FinanceiroDtos.ResumoFinanceiro;
import br.com.cfc.financeiro.FinanceiroService;
import br.com.cfc.financeiro.StatusTitulo;
import br.com.cfc.financeiro.TituloReceber;
import br.com.cfc.financeiro.TituloReceberRepository;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.SecurityUtils;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Relatórios exportáveis. CSV em UTF-8 com BOM e separador ";" (abre direto no Excel em pt-BR); datas dd/mm/aaaa e
 * valores com vírgula decimal. Tudo respeita o tenant da requisição.
 */
@Service
public class RelatorioService {

    static final int LIMITE_LINHAS = 20_000;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final AlunoRepository alunos;
    private final AlunoService alunoService;
    private final TituloReceberRepository titulos;
    private final AulaRepository aulas;
    private final InstrutorRepository instrutores;
    private final VeiculoRepository veiculos;
    private final FinanceiroService financeiro;
    private final ConfiguracaoService configuracaoService;

    public RelatorioService(AlunoRepository alunos, AlunoService alunoService, TituloReceberRepository titulos,
                            AulaRepository aulas, InstrutorRepository instrutores, VeiculoRepository veiculos,
                            FinanceiroService financeiro, ConfiguracaoService configuracaoService) {
        this.alunos = alunos;
        this.alunoService = alunoService;
        this.titulos = titulos;
        this.aulas = aulas;
        this.instrutores = instrutores;
        this.veiculos = veiculos;
        this.financeiro = financeiro;
        this.configuracaoService = configuracaoService;
    }

    @Transactional(readOnly = true)
    public byte[] alunosCsv() {
        SecurityUtils.autoescolaIdObrigatorio();
        StringBuilder sb = cabecalho("Nome", "CPF", "Telefone", "E-mail", "Cidade", "UF", "Ativo");
        for (Aluno a : alunos.findAll(Sort.by("nome")).stream().limit(LIMITE_LINHAS).toList()) {
            linha(sb, a.getNome(), a.getCpf(), a.getTelefone(), a.getEmail(), a.getCidade(), a.getUf(), a.isAtivo() ? "Sim" : "Não");
        }
        return bytes(sb);
    }

    @Transactional(readOnly = true)
    public byte[] titulosCsv(StatusTitulo status, LocalDate de, LocalDate ate) {
        SecurityUtils.autoescolaIdObrigatorio();
        List<TituloReceber> lista = titulos.buscar(null, status, de, ate, PageRequest.of(0, LIMITE_LINHAS));
        Map<Long, String> nomes = alunoService.nomes(lista.stream().map(TituloReceber::getAlunoId).collect(Collectors.toSet()));
        StatusTituloRotulo r = new StatusTituloRotulo();
        StringBuilder sb = cabecalho("Aluno", "Descrição", "Parcela", "Vencimento", "Valor (R$)", "Situação");
        for (TituloReceber t : lista) {
            linha(sb, nomes.get(t.getAlunoId()), t.getDescricao(), t.getNumeroParcela() + "/" + t.getTotalParcelas(),
                    DATA.format(t.getVencimento()), decimal(t.getValor()), r.rotulo(t.getStatus()));
        }
        return bytes(sb);
    }

    @Transactional(readOnly = true)
    public byte[] aulasCsv(LocalDate de, LocalDate ate) {
        SecurityUtils.autoescolaIdObrigatorio();
        LocalDate d = de == null ? configuracaoService.hoje().withDayOfMonth(1) : de;
        LocalDate f = ate == null ? d.plusMonths(1).minusDays(1) : ate;
        if (f.isBefore(d)) {
            throw new RegraNegocioException("A data final não pode ser anterior à inicial.");
        }
        if (d.plusDays(366).isBefore(f)) {
            throw new RegraNegocioException("Exporte no máximo 1 ano por vez.");
        }
        ZoneId zona = ZoneId.of(configuracaoService.configuracaoAtual().getFusoHorario());
        List<Aula> lista = aulas.buscar(d.atStartOfDay(zona).toInstant(), f.plusDays(1).atStartOfDay(zona).toInstant(),
                null, null, null, null);
        Map<Long, String> nomesAlunos = alunoService.nomes(ids(lista, Aula::getAlunoId));
        Map<Long, String> nomesInstrutores = instrutores.findAllById(ids(lista, Aula::getInstrutorId)).stream()
                .collect(Collectors.toMap(Instrutor::getId, Instrutor::getNome));
        Map<Long, String> placas = veiculos.findByIdIn(ids(lista, Aula::getVeiculoId)).stream()
                .collect(Collectors.toMap(Veiculo::getId, Veiculo::getPlaca));
        StringBuilder sb = cabecalho("Data", "Início", "Fim", "Aluno", "Instrutor", "Veículo", "Situação", "Nota", "Conteúdo");
        for (Aula a : lista.stream().limit(LIMITE_LINHAS).toList()) {
            linha(sb, DATA.format(a.getInicio().atZone(zona)), HORA.format(a.getInicio().atZone(zona)),
                    HORA.format(a.getFim().atZone(zona)), nomesAlunos.get(a.getAlunoId()),
                    nomesInstrutores.get(a.getInstrutorId()), placas.get(a.getVeiculoId()), rotulo(a.getStatus()),
                    a.getNota() == null ? "" : a.getNota().toString(), a.getConteudo());
        }
        return bytes(sb);
    }

    /** PDF de uma página com o fechamento do mês: indicadores e fluxo dos últimos meses. */
    @Transactional(readOnly = true)
    public byte[] financeiroPdf(YearMonth mes) {
        ResumoFinanceiro r = financeiro.resumo(mes);
        ConfiguracaoAutoescola cfg = configuracaoService.configuracaoAtual();
        NumberFormat moeda = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font titulo = new Font(Font.HELVETICA, 16, Font.BOLD);
            Font negrito = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font normal = new Font(Font.HELVETICA, 10, Font.NORMAL);
            YearMonth m = YearMonth.parse(r.mes());
            Paragraph p = new Paragraph("Fechamento financeiro — " + String.format("%02d/%d", m.getMonthValue(), m.getYear()), titulo);
            p.setSpacingAfter(12);
            doc.add(p);
            PdfPTable ind = new PdfPTable(2);
            ind.setWidthPercentage(100);
            celula(ind, "Recebido no mês", negrito);
            celula(ind, moeda.format(r.recebidoNoMes()), normal);
            celula(ind, "Despesas pagas no mês", negrito);
            celula(ind, moeda.format(r.despesasPagasNoMes()), normal);
            celula(ind, "Saldo do mês", negrito);
            celula(ind, moeda.format(r.saldoDoMes()), normal);
            celula(ind, "Total a receber (em aberto)", negrito);
            celula(ind, moeda.format(r.aReceber()), normal);
            celula(ind, "Vencido (" + r.titulosVencidos() + " títulos)", negrito);
            celula(ind, moeda.format(r.vencido()), normal);
            celula(ind, "Despesas em aberto", negrito);
            celula(ind, moeda.format(r.despesasEmAberto()), normal);
            doc.add(ind);
            Paragraph h = new Paragraph("Últimos 6 meses", negrito);
            h.setSpacingBefore(16);
            h.setSpacingAfter(6);
            doc.add(h);
            PdfPTable serie = new PdfPTable(4);
            serie.setWidthPercentage(100);
            for (String c : List.of("Mês", "Recebido", "Despesas", "Saldo")) {
                celula(serie, c, negrito);
            }
            for (MesFluxo x : r.ultimosMeses()) {
                YearMonth ym = YearMonth.parse(x.mes());
                celula(serie, String.format("%02d/%d", ym.getMonthValue(), ym.getYear()), normal);
                celula(serie, moeda.format(x.recebido()), normal);
                celula(serie, moeda.format(x.despesas()), normal);
                celula(serie, moeda.format(x.recebido().subtract(x.despesas())), normal);
            }
            doc.add(serie);
            Paragraph rodape = new Paragraph("\nGerado em " + DATA.format(configuracaoService.hoje())
                    + ". Relatório gerencial, sem valor fiscal.", new Font(Font.HELVETICA, 8));
            rodape.setAlignment(Element.ALIGN_LEFT);
            doc.add(rodape);
            doc.close();
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Falha ao gerar o PDF", e);
        }
    }

    // ---------- util ----------

    private static <T> Collection<Long> ids(List<T> lista, Function<T, Long> f) {
        return lista.stream().map(f).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
    }

    private static void celula(PdfPTable t, String texto, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(texto, f));
        c.setPadding(5);
        t.addCell(c);
    }

    private static String rotulo(StatusAula s) {
        return switch (s) {
            case AGENDADA -> "Agendada";
            case REALIZADA -> "Realizada";
            case FALTA -> "Falta";
            case CANCELADA -> "Cancelada";
        };
    }

    private static final class StatusTituloRotulo {
        String rotulo(StatusTitulo s) {
            return switch (s) {
                case ABERTO -> "Em aberto";
                case PAGO -> "Pago";
                case CANCELADO -> "Cancelado";
            };
        }
    }

    static String decimal(BigDecimal v) {
        return v == null ? "" : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    static StringBuilder cabecalho(String... colunas) {
        StringBuilder sb = new StringBuilder("﻿");
        linha(sb, (Object[]) colunas);
        return sb;
    }

    static void linha(StringBuilder sb, Object... campos) {
        sb.append(Stream.of(campos).map(RelatorioService::escapar).collect(Collectors.joining(";"))).append("\r\n");
    }

    /** Escapa para CSV e neutraliza fórmulas (=, +, -, @) para não executarem ao abrir no Excel. */
    static String escapar(Object valor) {
        String s = valor == null ? "" : valor.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static byte[] bytes(StringBuilder sb) {
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
