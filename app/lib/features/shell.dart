import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/formatos.dart';
import '../core/router.dart';
import '../core/sessao.dart';

/// Estrutura com menu por perfil: barra lateral em telas largas, barra inferior no celular.
class Shell extends ConsumerWidget {
  const Shell({super.key, required this.local, required this.child});
  final String local;
  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final usuario = ref.watch(sessaoProvider).valueOrNull;
    if (usuario == null) return const SizedBox.shrink();
    final itens = rotasDoPerfil(usuario.perfil);
    final indice = itens.indexWhere((r) => local.startsWith(r.caminho)).clamp(0, itens.length - 1);
    final largo = MediaQuery.sizeOf(context).width >= 800;

    final appBar = AppBar(
      title: Text(usuario.autoescolaNome ?? 'Plataforma'),
      actions: [
        PopupMenuButton<String>(
          tooltip: 'Conta',
          icon: const Icon(Icons.account_circle_outlined),
          itemBuilder: (_) => [
            PopupMenuItem(enabled: false, child: Text('${usuario.nome}\n${Formatos.perfil(usuario.perfil)}')),
            const PopupMenuItem(value: 'sair', child: Text('Sair')),
          ],
          onSelected: (v) {
            if (v == 'sair') ref.read(sessaoProvider.notifier).logout();
          },
        ),
      ],
    );

    if (largo || itens.length < 2) {
      return Scaffold(
        appBar: appBar,
        body: Row(children: [
          if (itens.length >= 2)
            NavigationRail(
              selectedIndex: indice,
              labelType: NavigationRailLabelType.all,
              onDestinationSelected: (i) => context.go(itens[i].caminho),
              destinations: [
                for (final r in itens) NavigationRailDestination(icon: Icon(r.icone), label: Text(r.titulo)),
              ],
            ),
          Expanded(child: child),
        ]),
      );
    }
    return Scaffold(
      appBar: appBar,
      body: child,
      bottomNavigationBar: NavigationBar(
        selectedIndex: indice,
        onDestinationSelected: (i) => context.go(itens[i].caminho),
        destinations: [for (final r in itens) NavigationDestination(icon: Icon(r.icone), label: r.titulo)],
      ),
    );
  }
}
