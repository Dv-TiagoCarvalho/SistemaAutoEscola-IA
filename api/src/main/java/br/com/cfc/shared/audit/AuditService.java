package br.com.cfc.shared.audit;

import br.com.cfc.shared.security.SecurityUtils;
import br.com.cfc.shared.security.UsuarioAutenticado;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;

/**
 * Grava o log de auditoria na mesma transação da alteração: se a alteração for desfeita, o log também é.
 * Passe sempre DTOs/records em "antes" e "depois", nunca entidades JPA (evita lazy loading e dados sensíveis).
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void registrar(Long autoescolaId, String entidade, Long entidadeId, AcaoAuditoria acao,
                          Object antes, Object depois) {
        UsuarioAutenticado usuario = SecurityUtils.usuarioAtual().orElse(null);
        repository.save(new AuditLog(
                autoescolaId,
                usuario != null ? usuario.id() : null,
                usuario != null ? usuario.nome() : "sistema",
                entidade,
                entidadeId,
                acao,
                json(antes),
                json(depois),
                ipAtual(),
                clock.instant()));
    }

    /** Atalho para ações dentro da autoescola do usuário logado. */
    @Transactional
    public void registrar(String entidade, Long entidadeId, AcaoAuditoria acao, Object antes, Object depois) {
        Long autoescolaId = SecurityUtils.usuarioAtual().map(UsuarioAutenticado::autoescolaId).orElse(null);
        registrar(autoescolaId, entidade, entidadeId, acao, antes, depois);
    }

    private String json(Object valor) {
        if (valor == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar dados de auditoria", e);
        }
    }

    private String ipAtual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
