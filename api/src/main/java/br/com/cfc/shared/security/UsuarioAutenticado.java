package br.com.cfc.shared.security;

/**
 * Dados do usuário logado, extraídos do access token (sem consulta ao banco).
 *
 * @param autoescolaId nulo apenas para SUPER_ADMIN
 */
public record UsuarioAutenticado(Long id, Long autoescolaId, Perfil perfil, String nome, String email) {

    public boolean isSuperAdmin() {
        return perfil == Perfil.SUPER_ADMIN;
    }
}
