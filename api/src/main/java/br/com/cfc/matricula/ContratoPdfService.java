package br.com.cfc.matricula;

import br.com.cfc.autoescola.Categoria;
import br.com.cfc.matricula.Parcelamento.Parcela;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Gera o contrato de prestação de serviços em PDF.
 * As cláusulas são um modelo genérico: a autoescola deve revisá-las com seu assessor jurídico.
 * Os números (duração da aula, antecedência de cancelamento, cobrança de falta) vêm das configurações da autoescola.
 */
@Service
public class ContratoPdfService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record Dados(
            String autoescolaNome, String autoescolaCnpj, String autoescolaCidade, String autoescolaUf,
            String alunoNome, String alunoCpf, String alunoRg, LocalDate alunoNascimento,
            String alunoEndereco, String alunoTelefone,
            Categoria categoria, String pacoteNome, int aulasPraticas, int duracaoAulaMin, boolean teoricoIncluso,
            BigDecimal valorBruto, BigDecimal desconto, BigDecimal valorTotal, LocalDate dataMatricula,
            List<Parcela> parcelas,
            int antecedenciaCancelamentoHoras, boolean cobrarFalta, BigDecimal valorFalta) {
    }

    public byte[] gerar(Dados d) {
        NumberFormat moeda = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 54, 54, 54, 54);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titulo = new Font(Font.HELVETICA, 13, Font.BOLD);
            Font negrito = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font normal = new Font(Font.HELVETICA, 10, Font.NORMAL);

            Paragraph t = new Paragraph("CONTRATO DE PRESTAÇÃO DE SERVIÇOS DE FORMAÇÃO DE CONDUTORES", titulo);
            t.setAlignment(Element.ALIGN_CENTER);
            t.setSpacingAfter(14);
            doc.add(t);

            doc.add(secao("CONTRATADA", negrito));
            doc.add(texto(d.autoescolaNome() + ", CNPJ " + d.autoescolaCnpj() + ", com sede em "
                    + d.autoescolaCidade() + "/" + d.autoescolaUf() + ".", normal));

            doc.add(secao("CONTRATANTE (ALUNO)", negrito));
            StringBuilder aluno = new StringBuilder(d.alunoNome()).append(", CPF ").append(d.alunoCpf());
            if (d.alunoRg() != null) {
                aluno.append(", RG ").append(d.alunoRg());
            }
            aluno.append(", nascido(a) em ").append(d.alunoNascimento().format(DATA));
            if (d.alunoEndereco() != null && !d.alunoEndereco().isBlank()) {
                aluno.append(", residente em ").append(d.alunoEndereco());
            }
            aluno.append(", telefone ").append(d.alunoTelefone()).append(".");
            doc.add(texto(aluno.toString(), normal));

            doc.add(secao("CLÁUSULA 1ª — DO OBJETO", negrito));
            doc.add(texto("O presente contrato tem por objeto a prestação de serviços de formação para a obtenção da "
                    + "habilitação na categoria " + d.categoria() + ", conforme o pacote \"" + d.pacoteNome() + "\", "
                    + "que inclui " + d.aulasPraticas() + " aula(s) prática(s) de direção veicular de "
                    + d.duracaoAulaMin() + " minutos cada" + (d.teoricoIncluso()
                    ? " e o curso teórico." : ", sem o curso teórico."), normal));

            doc.add(secao("CLÁUSULA 2ª — DAS AULAS PRÁTICAS", negrito));
            StringBuilder aulas = new StringBuilder("As aulas práticas serão agendadas conforme a disponibilidade de "
                    + "instrutores e veículos. O cancelamento ou a remarcação deve ser feito com antecedência mínima de "
                    + d.antecedenciaCancelamentoHoras() + " hora(s).");
            if (d.cobrarFalta()) {
                aulas.append(" A falta sem aviso prévio dentro desse prazo será cobrada no valor de ")
                        .append(moeda.format(d.valorFalta())).append(" por aula.");
            } else {
                aulas.append(" A falta sem aviso prévio dentro desse prazo consome uma aula do pacote.");
            }
            doc.add(texto(aulas.toString(), normal));

            doc.add(secao("CLÁUSULA 3ª — DO VALOR E DA FORMA DE PAGAMENTO", negrito));
            StringBuilder valores = new StringBuilder("Valor do pacote: " + moeda.format(d.valorBruto()));
            if (d.desconto().signum() > 0) {
                valores.append("; desconto: ").append(moeda.format(d.desconto()));
            }
            valores.append("; valor total contratado: ").append(moeda.format(d.valorTotal()))
                    .append(". O pagamento será feito conforme o quadro abaixo. O atraso sujeita o CONTRATANTE aos "
                            + "encargos previstos em lei.");
            doc.add(texto(valores.toString(), normal));

            if (!d.parcelas().isEmpty()) {
                PdfPTable tabela = new PdfPTable(new float[]{3, 2, 2});
                tabela.setWidthPercentage(100);
                tabela.setSpacingBefore(4);
                tabela.setSpacingAfter(8);
                for (String cab : new String[]{"Parcela", "Vencimento", "Valor"}) {
                    tabela.addCell(celula(cab, negrito));
                }
                for (Parcela p : d.parcelas()) {
                    tabela.addCell(celula(p.descricao(), normal));
                    tabela.addCell(celula(p.vencimento().format(DATA), normal));
                    tabela.addCell(celula(moeda.format(p.valor()), normal));
                }
                doc.add(tabela);
            }

            doc.add(secao("CLÁUSULA 4ª — DAS TAXAS", negrito));
            doc.add(texto("As taxas do órgão de trânsito (exames médico e psicológico, provas, emissão de documentos e "
                    + "da CNH) não estão incluídas neste contrato, salvo disposição expressa em contrário.", normal));

            doc.add(secao("CLÁUSULA 5ª — DA APROVAÇÃO", negrito));
            doc.add(texto("A CONTRATADA se compromete a prestar o serviço com qualidade, mas a aprovação nos exames "
                    + "depende do desempenho do CONTRATANTE e da decisão do órgão de trânsito, não havendo garantia de "
                    + "aprovação.", normal));

            doc.add(secao("CLÁUSULA 6ª — DA DESISTÊNCIA E DO CANCELAMENTO", negrito));
            doc.add(texto("Em caso de desistência, serão devidos os serviços já prestados, e o saldo será tratado "
                    + "conforme a legislação de defesa do consumidor.", normal));

            doc.add(secao("CLÁUSULA 7ª — DA PROTEÇÃO DE DADOS", negrito));
            doc.add(texto("Os dados pessoais do CONTRATANTE são tratados pela CONTRATADA para a execução deste contrato e "
                    + "o cumprimento de obrigações legais, nos termos da Lei nº 13.709/2018 (LGPD). O CONTRATANTE pode "
                    + "solicitar acesso, correção ou eliminação dos seus dados nos limites da lei.", normal));

            doc.add(secao("CLÁUSULA 8ª — DO FORO", negrito));
            doc.add(texto("Fica eleito o foro da comarca de " + d.autoescolaCidade() + "/" + d.autoescolaUf()
                    + " para dirimir quaisquer dúvidas decorrentes deste contrato.", normal));

            Paragraph local = new Paragraph(d.autoescolaCidade() + "/" + d.autoescolaUf() + ", "
                    + d.dataMatricula().format(DATA) + ".", normal);
            local.setSpacingBefore(18);
            local.setSpacingAfter(34);
            doc.add(local);

            PdfPTable assinaturas = new PdfPTable(2);
            assinaturas.setWidthPercentage(100);
            assinaturas.addCell(assinatura("CONTRATADA\n" + d.autoescolaNome(), normal));
            assinaturas.addCell(assinatura("CONTRATANTE\n" + d.alunoNome(), normal));
            doc.add(assinaturas);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar o contrato em PDF.", e);
        }
    }

    private static Paragraph secao(String texto, Font fonte) {
        Paragraph p = new Paragraph(texto, fonte);
        p.setSpacingBefore(8);
        p.setSpacingAfter(3);
        return p;
    }

    private static Paragraph texto(String texto, Font fonte) {
        Paragraph p = new Paragraph(texto, fonte);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setLeading(13);
        return p;
    }

    private static PdfPCell celula(String texto, Font fonte) {
        PdfPCell c = new PdfPCell(new Phrase(texto, fonte));
        c.setPadding(5);
        return c;
    }

    private static PdfPCell assinatura(String texto, Font fonte) {
        PdfPCell c = new PdfPCell(new Phrase("______________________________\n" + texto, fonte));
        c.setBorder(0);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setPadding(6);
        return c;
    }
}
