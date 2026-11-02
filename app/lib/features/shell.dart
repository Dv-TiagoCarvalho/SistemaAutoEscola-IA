import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/formatos.dart';
import '../core/router.dart';
import '../core/sessao.dart';
import 'sino.dart';

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
        if (usuario.perfil != 'SUPER_ADMIN') const Sino(),
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
    final muitos = itens.length > 5;
    final visiveis = muitos ? itens.take(4).toList() : itens;
    final extras = muitos ? itens.skip(4).toList() : <Rota>[];
    final indiceMobile = indice < visiveis.length ? indice : visiveis.length; // "Mais" = último
    return Scaffold(
      appBar: appBar,
      body: child,
      bottomNavigationBar: NavigationBar(
        selectedIndex: indiceMobile,
        onDestinationSelected: (i) {
          if (i < visiveis.length) {
            context.go(visiveis[i].caminho);
          } else {
            showModalBottomSheet<void>(
              context: context,
              builder: (_) => SafeArea(
                child: Column(mainAxisSize: MainAxisSize.min, children: [
                  for (final r in extras)
                    ListTile(
                      leading: Icon(r.icone),
                      title: Text(r.titulo),
                      onTap: () {
                        Navigator.pop(context);
                        context.go(r.caminho);
                      },
                    ),
                ]),
              ),
            );
          }
        },
        destinations: [
          for (final r in visiveis) NavigationDestination(icon: Icon(r.icone), label: r.titulo),
          if (muitos) const NavigationDestination(icon: Icon(Icons.menu), label: 'Mais'),
        ],
      ),
    );
  }
}
