package br.com.cfc.usuario;

import br.com.cfc.shared.security.Perfil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    public record UsuarioResponse(Long id, String nome, String email, Perfil perfil, boolean ativo,
                                  Instant ultimoLogin, Instant criadoEm) {
        public static UsuarioResponse de(Usuario u) {
            return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(), u.isAtivo(),
                    u.getUltimoLogin(), u.getCriadoEm());
        }
    }

    public record CriarUsuarioRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 120, message = "Nome com no máximo 120 caracteres.") String nome,
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 160) String email,
            @NotBlank(message = "Informe a senha.") @Pattern(regexp = SenhaPolitica.REGEX, message = SenhaPolitica.MENSAGEM) String senha,
            @NotNull(message = "Informe o perfil.") Perfil perfil) {
    }

    public record AtualizarUsuarioRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 120, message = "Nome com no máximo 120 caracteres.") String nome,
            @NotNull(message = "Informe o perfil.") Perfil perfil,
            @NotNull(message = "Informe se o usuário está ativo.") Boolean ativo) {
    }

    public record RedefinirSenhaRequest(
            @NotBlank(message = "Informe a nova senha.") @Pattern(regexp = SenhaPolitica.REGEX, message = SenhaPolitica.MENSAGEM) String novaSenha) {
    }

    /** Snapshot sem a senha, usado no log de auditoria. */
    public record UsuarioAuditoria(Long id, String nome, String email, Perfil perfil, boolean ativo) {
        public static UsuarioAuditoria de(Usuario u) {
            return new UsuarioAuditoria(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(), u.isAtivo());
        }
    }
}
