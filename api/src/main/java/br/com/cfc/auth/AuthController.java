package br.com.cfc.auth;

import br.com.cfc.auth.AuthDtos.AlterarSenhaRequest;
import br.com.cfc.auth.AuthDtos.LoginRequest;
import br.com.cfc.auth.AuthDtos.RefreshRequest;
import br.com.cfc.auth.AuthDtos.TokenResponse;
import br.com.cfc.auth.AuthDtos.UsuarioLogadoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticação")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @Operation(summary = "Login com e-mail e senha. Retorna access token (curto) e refresh token (longo).")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return service.login(req.email(), req.senha(), http.getRemoteAddr());
    }

    @Operation(summary = "Troca o refresh token por um novo par de tokens (o antigo deixa de valer).")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return service.refresh(req.refreshToken());
    }

    @Operation(summary = "Encerra a sessão revogando o refresh token.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest req) {
        service.logout(req.refreshToken());
    }

    @Operation(summary = "Dados do usuário logado.")
    @GetMapping("/me")
    public UsuarioLogadoResponse me() {
        return service.usuarioLogado();
    }

    @Operation(summary = "Altera a própria senha e encerra as sessões em outros aparelhos.")
    @PutMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void alterarSenha(@Valid @RequestBody AlterarSenhaRequest req) {
        service.alterarPropriaSenha(req.senhaAtual(), req.novaSenha());
    }
}
