import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/formatos.dart';
import '../core/sessao.dart';

/// Tela inicial provisória. O dashboard de verdade entra na Fase 7.
class InicioPage extends ConsumerWidget {
  const InicioPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final u = ref.watch(sessaoProvider).valueOrNull;
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        Text('Olá, ${u?.nome.split(' ').first ?? ''}!', style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 4),
        Text('Você entrou como ${Formatos.perfil(u?.perfil ?? '')}.'),
        const SizedBox(height: 24),
        const Card(
          child: ListTile(
            leading: Icon(Icons.construction_outlined),
            title: Text('Em construção'),
            subtitle: Text('Alunos, agenda, financeiro e o painel chegam nas próximas fases.'),
          ),
        ),
      ],
    );
  }
}
