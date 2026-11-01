package br.com.cfc.shared.audit;

import br.com.cfc.shared.security.SecurityUtils;
import br.com.cfc.shared.security.UsuarioAutenticado;
import br.com.cfc.shared.web.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "Auditoria")
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditLogRepository repository;

    public AuditoriaController(AuditLogRepository repository) {
        this.repository = repository;
    }

    public record AuditLogResponse(Long id, Long autoescolaId, Long usuarioId, String usuarioNome, String entidade,
                                   Long entidadeId, AcaoAuditoria acao, String dadosAntes, String dadosDepois,
                                   String ip, Instant dataHora) {
        static AuditLogResponse de(AuditLog a) {
            return new AuditLogResponse(a.getId(), a.getAutoescolaId(), a.getUsuarioId(), a.getUsuarioNome(),
                    a.getEntidade(), a.getEntidadeId(), a.getAcao(), a.getDadosAntes(), a.getDadosDepois(),
                    a.getIp(), a.getDataHora());
        }
    }

    @Operation(summary = "Lista o log de auditoria (ADMIN vê só a própria autoescola; SUPER_ADMIN vê tudo)")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public PaginaResponse<AuditLogResponse> listar(@RequestParam(required = false) String entidade,
                                                   @RequestParam(required = false) Long entidadeId,
                                                   @RequestParam(defaultValue = "0") int pagina,
                                                   @RequestParam(defaultValue = "50") int tamanho) {
        UsuarioAutenticado usuario = SecurityUtils.usuarioObrigatorio();
        Long autoescolaId = usuario.isSuperAdmin() ? null : usuario.autoescolaId();
        var resultado = repository.buscar(autoescolaId, entidade, entidadeId,
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 200)));
        return PaginaResponse.de(resultado.map(AuditLogResponse::de));
    }
}
