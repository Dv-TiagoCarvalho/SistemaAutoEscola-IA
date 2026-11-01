import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/formatos.dart';
import '../core/sessao.dart';
import 'comum.dart';

const _tipos = {'TEORICO': 'Teórico', 'PRATICO': 'Prático'};
const _resultados = {'AGENDADO': 'Agendado', 'APROVADO': 'Aprovado', 'REPROVADO': 'Reprovado', 'FALTOU': 'Faltou', 'CANCELADO': 'Cancelado'};

/// Exames teórico e prático (resultado lançado manualmente; sem integração com o órgão de trânsito).
class ExamesPage extends ConsumerStatefulWidget {
  const ExamesPage({super.key});
  @override
  ConsumerState<ExamesPage> createState() => _ExamesPageState();
}

class _ExamesPageState extends ConsumerState<ExamesPage> {
  String? _resultado = 'AGENDADO';
  late Future<List<dynamic>> _f = _carregar();

  Future<List<dynamic>> _carregar() async =>
      (await ref.read(apiProvider).get('/exames', query: {if (_resultado != null) 'resultado': _resultado})) as List<dynamic>;
  void _recarregar() => setState(() => _f = _carregar());

  Future<void> _agendar() async {
    final api = ref.read(apiProvider);
    try {
      final matriculas = ((await api.get('/matriculas', query: {'status': 'ATIVA', 'tamanho': 100}))['itens'] as List).cast<Map<String, dynamic>>();
      if (!mounted) return;
      if (matriculas.isEmpty) return mostrarErro(context, 'Não há alunos com matrícula ativa.');
      final ok = await abrirForm(context,
          titulo: 'Agendar exame',
          campos: [
            Campo('processo', 'Aluno',
                tipo: TipoCampo.escolha,
                obrigatorio: true,
                opcoes: {for (final m in matriculas) '${m['processoId']}': '${m['alunoNome']} · ${m['categoria']}'}),
            const Campo('tipo', 'Tipo', tipo: TipoCampo.escolha, obrigatorio: true, inicial: 'TEORICO', opcoes: _tipos),
            Campo('data', 'Data', tipo: TipoCampo.data, obrigatorio: true, inicial: DateTime.now().add(const Duration(days: 7)).toIso8601String().substring(0, 10)),
            const Campo('hora', 'Horário', tipo: TipoCampo.hora, obrigatorio: true),
            const Campo('local', 'Local', max: 120),
          ],
          enviar: (v) async => api.post('/exames', {
                'processoId': int.parse(v['processo']!),
                'tipo': v['tipo'],
                'dataHora': '${v['data']}T${v['hora']}',
                'local': v['local'],
              }));
      if (ok) _recarregar();
    } catch (e) {
      if (mounted) mostrarErro(context, e);
    }
  }

  Future<void> _lancar(Map<String, dynamic> e) async {
    final api = ref.read(apiProvider);
    final ok = await abrirForm(context,
        titulo: 'Resultado do exame',
        campos: [
          const Campo('resultado', 'Resultado',
              tipo: TipoCampo.escolha,
              obrigatorio: true,
              opcoes: {'APROVADO': 'Aprovado', 'REPROVADO': 'Reprovado', 'FALTOU': 'Aluno faltou', 'CANCELADO': 'Exame cancelado'}),
          const Campo('obs', 'Observações', tipo: TipoCampo.multilinha, max: 300),
        ],
        enviar: (v) async => api.post('/exames/${e['id']}/resultado', {'resultado': v['resultado'], 'observacoes': v['obs']}));
    if (ok) _recarregar();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(onPressed: _agendar, icon: const Icon(Icons.add), label: const Text('Agendar exame')),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.all(8),
          child: Wrap(spacing: 8, children: [
            for (final f in <String?>['AGENDADO', 'APROVADO', 'REPROVADO', null])
              ChoiceChip(
                label: Text(f == null ? 'Todos' : _resultados[f]!),
                selected: _resultado == f,
                onSelected: (_) {
                  _resultado = f;
                  _recarregar();
                },
              ),
          ]),
        ),
        Expanded(
          child: FutureBuilder(
            future: _f,
            builder: (context, snap) {
              if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
              if (!snap.hasData) return const Carregando();
              final itens = snap.data!.cast<Map<String, dynamic>>();
              if (itens.isEmpty) return const Center(child: Text('Nenhum exame.'));
              return ListView.separated(
                padding: const EdgeInsets.only(bottom: 88),
                itemCount: itens.length,
                separatorBuilder: (_, __) => const Divider(height: 1),
                itemBuilder: (_, i) {
                  final e = itens[i];
                  final agendado = e['resultado'] == 'AGENDADO';
                  return ListTile(
                    leading: Icon(e['tipo'] == 'TEORICO' ? Icons.menu_book_outlined : Icons.directions_car_outlined),
                    title: Text('${e['alunoNome']} · ${_tipos[e['tipo']]} (${e['tentativa']}ª tentativa)'),
                    subtitle: Text('${Formatos.dataHora(DateTime.parse(e['dataHora'] as String))}${e['local'] != null ? ' · ${e['local']}' : ''} · ${_resultados[e['resultado']]}'),
                    trailing: agendado ? TextButton(onPressed: () => _lancar(e), child: const Text('Resultado')) : null,
                  );
                },
              );
            },
          ),
        ),
      ]),
    );
  }
}
