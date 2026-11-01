package br.com.cfc.aluno;

import br.com.cfc.TestSupport;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.shared.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentoServiceTest {

    private final DocumentoRepository repo = mock(DocumentoRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private DocumentoService service;

    @BeforeEach
    void setUp() {
        service = new DocumentoService(repo, mock(AlunoService.class), storage, mock(AuditService.class));
        TestSupport.logarComo(1L, 5L, Perfil.SECRETARIA);
        when(repo.save(any())).thenAnswer(i -> {
            DocumentoAluno d = i.getArgument(0);
            d.setId(1L);
            return d;
        });
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    }

    @Test
    void aceitaPngPeloConteudoEGravaComNomeGerado() {
        var r = service.enviar(30L, TipoDocumento.FOTO, "../../etc/foto.jpg", png());

        assertThat(r.contentType()).isEqualTo("image/png");
        assertThat(r.nomeOriginal()).isEqualTo("foto.jpg");
        var caminho = ArgumentCaptor.forClass(String.class);
        verify(storage).salvar(caminho.capture(), any());
        assertThat(caminho.getValue()).startsWith("5/alunos/30/").endsWith(".png").doesNotContain("..");
    }

    @Test
    void rejeitaArquivoQueNaoEImagemNemPdfMesmoComExtensaoFalsa() {
        byte[] script = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.enviar(30L, TipoDocumento.RG, "rg.jpg", script))
                .isInstanceOf(RegraNegocioException.class);
        verify(storage, never()).salvar(anyString(), any());
    }

    @Test
    void rejeitaVazioEExcessoDeDocumentos() {
        assertThatThrownBy(() -> service.enviar(30L, TipoDocumento.RG, "rg.png", new byte[0]))
                .isInstanceOf(RegraNegocioException.class);
        when(repo.countByAlunoId(30L)).thenReturn(30L);
        assertThatThrownBy(() -> service.enviar(30L, TipoDocumento.RG, "rg.png", png()))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void detectaPdf() {
        assertThat(DocumentoService.detectarTipo("%PDF-1.7 ...".getBytes(StandardCharsets.US_ASCII))).isEqualTo("application/pdf");
        assertThat(DocumentoService.detectarTipo(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0})).isEqualTo("image/jpeg");
        assertThat(DocumentoService.detectarTipo(new byte[]{1, 2, 3})).isNull();
    }
}
