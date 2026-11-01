import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/formatos.dart';
import '../core/sessao.dart';
import 'comum.dart';

/// Pacotes de aulas. Leitura: ADMIN e SECRETARIA. Edição: só ADMIN.
class PacotesPage extends ConsumerStatefulWidget {
  const PacotesPage({super.key});
  @override
  ConsumerState<PacotesPage> createState() => _PacotesPageState();
}

class _PacotesPageState extends ConsumerState<PacotesPage> {
  late Future<List<dynamic>> _lista = _carregar();

  Future<List<dynamic>> _carregar() async =>
      (await ref.read(apiProvider).get('/pacotes', query: {'incluirInativos': true})) as List<dynamic>;

  void _recarregar() => setState(() => _lista = _carregar());

  Future<void> _form([Map<String, dynamic>? p]) async {
    final api = ref.read(apiProvider);
    final ok = await abrirForm(context,
        titulo: p == null ? 'Novo pacote' : 'Editar pacote',
        campos: [
          Campo('categoria', 'Categoria',
              tipo: TipoCampo.escolha,
              obrigatorio: true,
              inicial: p?['categoria'] as String?,
              opcoes: {for (final c in Formatos.categorias) c: c}),
          Campo('nome', 'Nome do pacote', obrigatorio: true, inicial: p?['nome'] as String?, max: 100),
          Campo('qtd', 'Aulas práticas', tipo: TipoCampo.numero, obrigatorio: true, inicial: '${p?['qtdAulasPraticas'] ?? ''}'),
          Campo('preco', 'Preço (R\$)', tipo: TipoCampo.valor, obrigatorio: true, inicial: p == null ? null : '${p['preco']}'),
          Campo('teorico', 'Teórico incluso?',
              tipo: TipoCampo.escolha,
              obrigatorio: true,
              inicial: (p?['teoricoIncluso'] ?? true) == true ? 'true' : 'false',
              opcoes: const {'true': 'Sim', 'false': 'Não'}),
        ],
        enviar: (v) async {
          final corpo = {
            'categoria': v['categoria'],
            'nome': v['nome'],
            'qtdAulasPraticas': int.tryParse(v['qtd']!),
            'teoricoIncluso': v['teorico'] == 'true',
            'preco': num.tryParse(v['preco']!),
          };
          p == null ? await api.post('/pacotes', corpo) : await api.put('/pacotes/${p['id']}', corpo);
        });
    if (ok) _recarregar();
  }

  Future<void> _alternar(Map<String, dynamic> p) async {
    try {
      await ref.read(apiProvider).patch('/pacotes/${p['id']}/status', {'ativo': p['ativo'] != true});
      _recarregar();
    } catch (e) {
      if (mounted) mostrarErro(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final admin = ref.watch(sessaoProvider).valueOrNull?.perfil == 'ADMIN';
    return Scaffold(
      floatingActionButton: admin
          ? FloatingActionButton.extended(onPressed: _form, icon: const Icon(Icons.add), label: const Text('Novo pacote'))
          : null,
      body: FutureBuilder(
        future: _lista,
        builder: (context, snap) {
          if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
          if (!snap.hasData) return const Carregando();
          final itens = snap.data!;
          if (itens.isEmpty) return const Center(child: Text('Nenhum pacote cadastrado.'));
          return RefreshIndicator(
            onRefresh: () async => _recarregar(),
            child: ListView.separated(
              padding: const EdgeInsets.only(bottom: 88),
              itemCount: itens.length,
              separatorBuilder: (_, __) => const Divider(height: 1),
              itemBuilder: (_, i) {
                final p = itens[i] as Map<String, dynamic>;
                final ativo = p['ativo'] == true;
                return ListTile(
                  leading: CircleAvatar(child: Text(p['categoria'] as String)),
                  title: Text(p['nome'] as String),
                  subtitle: Text(
                      '${p['qtdAulasPraticas']} aulas · ${p['teoricoIncluso'] == true ? 'com teórico' : 'sem teórico'} · ${Formatos.moeda(p['preco'] as num)}${ativo ? '' : ' · inativo'}'),
                  trailing: admin ? Switch(value: ativo, onChanged: (_) => _alternar(p)) : null,
                  onTap: admin ? () => _form(p) : null,
                );
              },
            ),
          );
        },
      ),
    );
  }
}
