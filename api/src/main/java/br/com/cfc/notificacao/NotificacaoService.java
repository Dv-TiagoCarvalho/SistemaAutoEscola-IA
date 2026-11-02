package br.com.cfc.notificacao;

import br.com.cfc.shared.exception.NaoEncontradoException;
import br.com.cfc.shared.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificacaoService {

    public record NotificacaoResponse(Long id, String titulo, String mensagem, boolean lida, Instant criadoEm) {
        static NotificacaoResponse de(Notificacao n) {
            return new NotificacaoResponse(n.getId(), n.getTitulo(), n.getMensagem(), n.isLida(), n.getCriadoEm());
        }
    }

    public record ContagemResponse(long naoLidas) {
    }

    private final NotificacaoRepository repository;

    public NotificacaoService(NotificacaoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<NotificacaoResponse> minhas() {
        return repository.findTop50ByUsuarioIdOrderByCriadoEmDescIdDesc(SecurityUtils.usuarioObrigatorio().id())
                .stream().map(NotificacaoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public ContagemResponse contar() {
        return new ContagemResponse(repository.countByUsuarioIdAndLidaFalse(SecurityUtils.usuarioObrigatorio().id()));
    }

    @Transactional
    public void marcarLida(Long id) {
        Long usuarioId = SecurityUtils.usuarioObrigatorio().id();
        Notificacao n = repository.findById(id).filter(x -> x.getUsuarioId().equals(usuarioId))
                .orElseThrow(() -> new NaoEncontradoException("Notificação não encontrada."));
        n.setLida(true);
    }

    @Transactional
    public void marcarTodasLidas() {
        repository.marcarTodasComoLidas(SecurityUtils.usuarioObrigatorio().id());
    }

    /** Cria a notificação, ignorando se já existe uma com a mesma chave para o usuário. Retorna true se criou. */
    @Transactional
    public boolean criarSeNova(Long usuarioId, String chave, String titulo, String mensagem) {
        if (chave != null && repository.existsByUsuarioIdAndChave(usuarioId, chave)) {
            return false;
        }
        Notificacao n = new Notificacao();
        n.setUsuarioId(usuarioId);
        n.setChave(chave);
        n.setTitulo(titulo);
        n.setMensagem(mensagem);
        repository.save(n);
        return true;
    }
}
