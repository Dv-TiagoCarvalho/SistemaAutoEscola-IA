package br.com.cfc.financeiro;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class ReciboPdfService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record Dados(String autoescolaNome, String autoescolaCnpj, String numero, String alunoNome, String alunoCpf,
                        String descricao, FormaPagamento forma, LocalDate data, BigDecimal valorTitulo,
                        BigDecimal acrescimo, BigDecimal desconto, BigDecimal valorPago, boolean estornado) {
    }

    public byte[] gerar(Dados d) {
        NumberFormat moeda = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A5.rotate(), 40, 40, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font titulo = new Font(Font.HELVETICA, 15, Font.BOLD);
            Font negrito = new Font(Font.HELVETICA, 11, Font.BOLD);
            Font normal = new Font(Font.HELVETICA, 11, Font.NORMAL);

            Paragraph t = new Paragraph("RECIBO Nº " + d.numero() + (d.estornado() ? " (ESTORNADO)" : ""), titulo);
            t.setAlignment(Element.ALIGN_CENTER);
            t.setSpacingAfter(12);
            doc.add(t);
            doc.add(new Paragraph(d.autoescolaNome() + (d.autoescolaCnpj() == null ? "" : " - CNPJ " + d.autoescolaCnpj()), negrito));
            Paragraph corpo = new Paragraph("Recebemos de " + d.alunoNome() + (d.alunoCpf() == null ? "" : " (CPF " + d.alunoCpf() + ")")
                    + " a importância de " + moeda.format(d.valorPago()) + ", referente a: " + d.descricao() + ".", normal);
            corpo.setSpacingBefore(12);
            corpo.setSpacingAfter(8);
            doc.add(corpo);
            doc.add(new Paragraph("Valor original: " + moeda.format(d.valorTitulo()), normal));
            if (d.acrescimo().signum() > 0) {
                doc.add(new Paragraph("Acréscimos (multa/juros): " + moeda.format(d.acrescimo()), normal));
            }
            if (d.desconto().signum() > 0) {
                doc.add(new Paragraph("Desconto: " + moeda.format(d.desconto()), normal));
            }
            doc.add(new Paragraph("Forma de pagamento: " + d.forma().name().replace('_', ' ') + " - Data: " + DATA.format(d.data()), normal));
            Paragraph rodape = new Paragraph("\nDocumento sem valor fiscal. Para nota fiscal, procure a administração.", new Font(Font.HELVETICA, 8));
            doc.add(rodape);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar o recibo.", e);
        }
    }
}
