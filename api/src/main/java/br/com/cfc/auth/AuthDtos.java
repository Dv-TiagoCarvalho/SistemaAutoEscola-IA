package br.com.cfc.auth;

import br.com.cfc.shared.security.Perfil;
import br.com.cfc.usuario.SenhaPolitica;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Informe o e-mail.") @Size(max = 160) String email,
            @NotBlank(message = "Informe a senha.") @Size(max = 72) String senha) {
    }

    public record RefreshRequest(@NotBlank(message = "Informe o refresh token.") @Size(max = 200) String refreshToken) {
    }

    public record AlterarSenhaRequest(
            @NotBlank(message = "Informe a senha atual.") String senhaAtual,
            @NotBlank(message = "Informe a nova senha.") @Pattern(regexp = SenhaPolitica.REGEX, message = SenhaPolitica.MENSAGEM) String novaSenha) {
    }

    public record UsuarioLogadoResponse(Long id, String nome, String email, Perfil perfil,
                                        Long autoescolaId, String autoescolaNome) {
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType,
                                long expiraEmSegundos, UsuarioLogadoResponse usuario) {
    }
}
