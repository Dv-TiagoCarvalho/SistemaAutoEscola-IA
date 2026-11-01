package br.com.cfc.shared.security;

public enum Perfil {
    /** Dono da plataforma SaaS: cadastra e gerencia autoescolas. Não pertence a nenhuma autoescola. */
    SUPER_ADMIN,
    /** Dono/administrador da autoescola: acesso total, incluindo financeiro e configurações. */
    ADMIN,
    /** Alunos, matrículas, agenda e financeiro, sem configurações. */
    SECRETARIA,
    /** Própria agenda, registro de aulas e avaliação dos alunos. */
    INSTRUTOR,
    /** Ver e marcar aulas, progresso, simulados e parcelas. */
    ALUNO;

    public String authority() {
        return "ROLE_" + name();
    }
}
