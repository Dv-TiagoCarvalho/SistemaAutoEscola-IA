import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/autoescolas_page.dart';
import '../features/configuracoes_page.dart';
import '../features/inicio_page.dart';
import '../features/login_page.dart';
import '../features/shell.dart';
import '../features/usuarios_page.dart';
import 'sessao.dart';

/// Rotas e quais perfis podem abrir cada uma. O back-end valida de novo; isto só organiza a navegação.
class Rota {
  const Rota(this.caminho, this.titulo, this.icone, this.perfis);
  final String caminho;
  final String titulo;
  final IconData icone;
  final Set<String> perfis;
}

const rotas = [
  Rota('/inicio', 'Início', Icons.home_outlined, {'ADMIN', 'SECRETARIA', 'INSTRUTOR', 'ALUNO'}),
  Rota('/autoescolas', 'Autoescolas', Icons.business_outlined, {'SUPER_ADMIN'}),
  Rota('/usuarios', 'Equipe', Icons.group_outlined, {'ADMIN'}),
  Rota('/configuracoes', 'Configurações', Icons.settings_outlined, {'ADMIN'}),
];

List<Rota> rotasDoPerfil(String perfil) => rotas.where((r) => r.perfis.contains(perfil)).toList();

final routerProvider = Provider<GoRouter>((ref) {
  final notificador = ValueNotifier<int>(0);
  ref.listen(sessaoProvider, (_, __) => notificador.value++);
  ref.onDispose(notificador.dispose);

  return GoRouter(
    initialLocation: '/inicio',
    refreshListenable: notificador,
    redirect: (context, state) {
      final sessao = ref.read(sessaoProvider);
      if (sessao.isLoading) return state.matchedLocation == '/carregando' ? null : '/carregando';
      final usuario = sessao.valueOrNull;
      final local = state.matchedLocation;
      if (usuario == null) return local == '/login' ? null : '/login';
      final permitidas = rotasDoPerfil(usuario.perfil);
      final rotaAtual = rotas.where((r) => local.startsWith(r.caminho)).firstOrNull;
      if (local == '/login' || local == '/carregando' || (rotaAtual != null && !rotaAtual.perfis.contains(usuario.perfil))) {
        return permitidas.first.caminho;
      }
      return null;
    },
    routes: [
      GoRoute(path: '/carregando', builder: (_, __) => const Scaffold(body: Center(child: CircularProgressIndicator()))),
      GoRoute(path: '/login', builder: (_, __) => const LoginPage()),
      ShellRoute(
        builder: (context, state, child) => Shell(local: state.matchedLocation, child: child),
        routes: [
          GoRoute(path: '/inicio', builder: (_, __) => const InicioPage()),
          GoRoute(path: '/autoescolas', builder: (_, __) => const AutoescolasPage()),
          GoRoute(path: '/usuarios', builder: (_, __) => const UsuariosPage()),
          GoRoute(path: '/configuracoes', builder: (_, __) => const ConfiguracoesPage()),
        ],
      ),
    ],
  );
});
