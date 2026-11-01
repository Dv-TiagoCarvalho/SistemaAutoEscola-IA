import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/formatos.dart';
import '../core/sessao.dart';
import 'comum.dart';

/// Lista e busca de alunos (ADMIN e SECRETARIA).
class AlunosPage extends ConsumerStatefulWidget {
  const AlunosPage({super.key});
  @override
  ConsumerState<AlunosPage> createState() => _AlunosPageState();
}

class _AlunosPageState extends ConsumerState<AlunosPage> {
  String _busca = '';
  bool _inativos = false;
  Timer? _debounce;
  late Future<List<dynamic>> _lista = _carregar();

  Future<List<dynamic>> _carregar() async {
    final r = await ref.read(apiProvider).get('/alunos', query: {
      'tamanho': 100,
      if (_busca.isNotEmpty) 'busca': _busca,
      'incluirInativos': _inativos,
    });
    return r['itens'] as List<dynamic>;
  }

  void _recarregar() => setState(() => _lista = _carregar());

  @override
  void dispose() {
    _debounce?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          await context.push('/alunos/novo');
          _recarregar();
        },
        icon: const Icon(Icons.person_add_alt),
        label: const Text('Novo aluno'),
      ),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 0),
          child: Row(children: [
            Expanded(
              child: TextField(
                decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Buscar por nome, CPF ou telefone'),
                onChanged: (v) {
                  _debounce?.cancel();
                  _debounce = Timer(const Duration(milliseconds: 400), () {
                    _busca = v.trim();
                    _recarregar();
                  });
                },
              ),
            ),
            const SizedBox(width: 8),
            FilterChip(
              label: const Text('Inativos'),
              selected: _inativos,
              onSelected: (v) {
                _inativos = v;
                _recarregar();
              },
            ),
          ]),
        ),
        Expanded(
          child: FutureBuilder(
            future: _lista,
            builder: (context, snap) {
              if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
              if (!snap.hasData) return const Carregando();
              final itens = snap.data!;
              if (itens.isEmpty) return const Center(child: Text('Nenhum aluno encontrado.'));
              return RefreshIndicator(
                onRefresh: () async => _recarregar(),
                child: ListView.separated(
                  padding: const EdgeInsets.only(bottom: 88),
                  itemCount: itens.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (_, i) {
                    final a = itens[i] as Map<String, dynamic>;
                    final ativo = a['ativo'] == true;
                    return ListTile(
                      leading: CircleAvatar(child: Text((a['nome'] as String).characters.first.toUpperCase())),
                      title: Text(a['nome'] as String,
                          style: ativo ? null : const TextStyle(decoration: TextDecoration.lineThrough)),
                      subtitle: Text([
                        Formatos.cpf(a['cpf'] as String?),
                        Formatos.telefone(a['telefone'] as String?),
                        if (a['situacaoAtual'] != null) Formatos.situacao(a['situacaoAtual'] as String),
                      ].join(' · ')),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () async {
                        await context.push('/alunos/${a['id']}');
                        _recarregar();
                      },
                    );
                  },
                ),
              );
            },
          ),
        ),
      ]),
    );
  }
}
