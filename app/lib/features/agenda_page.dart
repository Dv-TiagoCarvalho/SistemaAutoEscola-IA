import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/formatos.dart';
import '../core/sessao.dart';
import 'comum.dart';

String _iso(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

String _hora(String instantIso) {
  final d = DateTime.parse(instantIso).toLocal();
  return '${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';
}

const _statusAula = {
  'AGENDADA': 'Agendada',
  'REALIZADA': 'Realizada',
  'FALTA': 'Falta',
  'CANCELADA': 'Cancelada',
};

/// Agenda de aulas práticas.
/// Equipe: visão do dia, com agendamento. Instrutor e aluno: suas próximas aulas (30 dias).
class AgendaPage extends ConsumerStatefulWidget {
  const AgendaPage({super.key});
  @override
  ConsumerState<AgendaPage> createState() => _AgendaPageState();
}

class _AgendaPageState extends ConsumerState<AgendaPage> {
  DateTime _dia = DateUtils.dateOnly(DateTime.now());
  late Future<List<dynamic>> _f = _carregar();

  String get _perfil => ref.read(sessaoProvider).valueOrNull?.perfil ?? '';
  bool get _equipe => _perfil == 'ADMIN' || _perfil == 'SECRETARIA';

  Future<List<dynamic>> _carregar() async {
    final hoje = DateUtils.dateOnly(DateTime.now());
    final r = await ref.read(apiProvider).get('/aulas', query: {
      'de': _iso(_equipe ? _dia : hoje),
      'ate': _iso(_equipe ? _dia : hoje.add(const Duration(days: 30))),
    });
    return r as List<dynamic>;
  }

  void _recarregar() => setState(() => _f = _carregar());

  void _mudarDia(DateTime d) {
    _dia = DateUtils.dateOnly(d);
    _recarregar();
  }

  Future<void> _agendar() async {
    final api = ref.read(apiProvider);
    try {
      final matriculas = ((await api.get('/matriculas', query: {'status': 'ATIVA', 'tamanho': 100}))['itens'] as List).cast<Map<String, dynamic>>();
      final instrutores = ((await api.get('/instrutores')) as List).cast<Map<String, dynamic>>();
      final veiculos = ((await api.get('/veiculos')) as List).cast<Map<String, dynamic>>();
      if (!mounted) return;
      if (matriculas.isEmpty) return mostrarErro(context, 'Não há matrículas ativas. Matricule um aluno primeiro.');
      if (instrutores.isEmpty) return mostrarErro(context, 'Cadastre um instrutor em Cadastros.');
      if (veiculos.isEmpty) return mostrarErro(context, 'Cadastre um veículo em Cadastros.');
      final ok = await abrirForm(context,
          titulo: 'Agendar aula',
          rotuloBotao: 'Agendar',
          campos: [
            Campo('matricula', 'Aluno (matrícula)',
                tipo: TipoCampo.escolha,
                obrigatorio: true,
                opcoes: {for (final m in matriculas) '${m['id']}': '${m['alunoNome']} · ${m['categoria']}'}),
            Campo('instrutor', 'Instrutor',
                tipo: TipoCampo.escolha,
                obrigatorio: true,
                opcoes: {for (final i in instrutores) '${i['id']}': '${i['nome']} (${(i['categorias'] as List).join(', ')})'}),
            Campo('veiculo', 'Veículo',
                tipo: TipoCampo.escolha,
                obrigatorio: true,
                opcoes: {for (final v in veiculos) '${v['id']}': '${v['placa']} · ${v['modelo']} (${v['categoria']})'}),
            Campo('data', 'Data', tipo: TipoCampo.data, obrigatorio: true, inicial: _iso(_dia)),
            const Campo('hora', 'Horário de início', tipo: TipoCampo.hora, obrigatorio: true),
          ],
          enviar: (v) async => api.post('/aulas', {
                'matriculaId': int.parse(v['matricula']!),
                'instrutorId': int.parse(v['instrutor']!),
                'veiculoId': int.parse(v['veiculo']!),
                'inicio': '${v['data']}T${v['hora']}',
              }));
      if (ok) {
        _dia = DateUtils.dateOnly(DateTime.now());
        _recarregar();
      }
    } catch (e) {
      if (mounted) mostrarErro(context, e);
    }
  }

  Future<void> _cancelar(Map<String, dynamic> a) async {
    final api = ref.read(apiProvider);
    final ok = await abrirForm(context,
        titulo: 'Cancelar aula',
        rotuloBotao: 'Cancelar aula',
        campos: [
          const Campo('motivo', 'Motivo', tipo: TipoCampo.multilinha, obrigatorio: true, max: 300),
          if (_equipe)
            const Campo('isentar', 'Isentar cobrança se for cancelamento tardio?',
                tipo: TipoCampo.escolha, obrigatorio: true, inicial: 'false', opcoes: {'false': 'Não', 'true': 'Sim'}),
        ],
        enviar: (v) async => api.post('/aulas/${a['id']}/cancelamento', {'motivo': v['motivo'], 'isentarCobranca': v['isentar'] == 'true'}));
    if (ok) _recarregar();
  }

  Future<void> _registrar(Map<String, dynamic> a) async {
    final api = ref.read(apiProvider);
    final ok = await abrirForm(context,
        titulo: 'Registrar aula',
        campos: [
          const Campo('resultado', 'Resultado',
              tipo: TipoCampo.escolha, obrigatorio: true, inicial: 'REALIZADA', opcoes: {'REALIZADA': 'Aula realizada', 'FALTA': 'Aluno faltou'}),
          const Campo('kmIni', 'Km inicial do veículo', tipo: TipoCampo.numero, max: 7),
          const Campo('kmFim', 'Km final do veículo', tipo: TipoCampo.numero, max: 7),
          const Campo('conteudo', 'Conteúdo trabalhado (baliza, rua, trânsito...)', max: 300),
          const Campo('obs', 'Observações', tipo: TipoCampo.multilinha, max: 500),
          const Campo('nota', 'Desempenho do aluno (1 a 5)',
              tipo: TipoCampo.escolha, opcoes: {'1': '1', '2': '2', '3': '3', '4': '4', '5': '5'}),
        ],
        enviar: (v) async => api.post('/aulas/${a['id']}/registro', {
              'resultado': v['resultado'],
              'kmInicial': int.tryParse(v['kmIni']!),
              'kmFinal': int.tryParse(v['kmFim']!),
              'conteudo': v['conteudo'],
              'observacoes': v['obs'],
              'nota': int.tryParse(v['nota']!),
            }));
    if (ok) _recarregar();
  }

  Color _cor(String s, ColorScheme c) => switch (s) {
        'REALIZADA' => Colors.green.shade700,
        'FALTA' => c.error,
        'CANCELADA' => c.outline,
        _ => c.primary,
      };

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final perfil = _perfil;
    return Scaffold(
      floatingActionButton: _equipe
          ? FloatingActionButton.extended(onPressed: _agendar, icon: const Icon(Icons.add), label: const Text('Agendar aula'))
          : null,
      body: Column(children: [
        if (_equipe)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            child: Row(children: [
              IconButton(tooltip: 'Dia anterior', icon: const Icon(Icons.chevron_left), onPressed: () => _mudarDia(_dia.subtract(const Duration(days: 1)))),
              Expanded(
                child: TextButton(
                  onPressed: () async {
                    final d = await showDatePicker(context: context, initialDate: _dia, firstDate: DateTime(2020), lastDate: DateTime(2100));
                    if (d != null) _mudarDia(d);
                  },
                  child: Text(Formatos.data(_dia), style: Theme.of(context).textTheme.titleMedium),
                ),
              ),
              TextButton(onPressed: () => _mudarDia(DateTime.now()), child: const Text('Hoje')),
              IconButton(tooltip: 'Próximo dia', icon: const Icon(Icons.chevron_right), onPressed: () => _mudarDia(_dia.add(const Duration(days: 1)))),
            ]),
          )
        else
          Padding(
            padding: const EdgeInsets.all(12),
            child: Align(alignment: Alignment.centerLeft, child: Text('Próximas aulas (30 dias)', style: Theme.of(context).textTheme.titleMedium)),
          ),
        Expanded(
          child: FutureBuilder(
            future: _f,
            builder: (context, snap) {
              if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
              if (!snap.hasData) return const Carregando();
              final itens = snap.data!.cast<Map<String, dynamic>>();
              if (itens.isEmpty) return Center(child: Text(_equipe ? 'Nenhuma aula neste dia.' : 'Nenhuma aula agendada.'));
              return RefreshIndicator(
                onRefresh: () async => _recarregar(),
                child: ListView.builder(
                  padding: const EdgeInsets.only(bottom: 88),
                  itemCount: itens.length,
                  itemBuilder: (_, i) {
                    final a = itens[i];
                    final status = a['status'] as String;
                    final agendada = status == 'AGENDADA';
                    final comecou = !DateTime.parse(a['inicio'] as String).isAfter(DateTime.now());
                    final podeRegistrar = agendada && comecou && (_equipe || perfil == 'INSTRUTOR');
                    final podeCancelar = agendada && !comecou && (_equipe || perfil == 'ALUNO');
                    return Card(
                      margin: const EdgeInsets.fromLTRB(12, 8, 12, 0),
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Row(children: [
                            Text('${_hora(a['inicio'] as String)} – ${_hora(a['fim'] as String)}', style: Theme.of(context).textTheme.titleMedium),
                            if (!_equipe) Text('  ·  ${Formatos.data(DateTime.parse(a['inicio'] as String))}'),
                            const Spacer(),
                            Chip(
                              label: Text(_statusAula[status] ?? status, style: TextStyle(color: _cor(status, cs))),
                              visualDensity: VisualDensity.compact,
                            ),
                          ]),
                          if (perfil != 'ALUNO') Text('Aluno: ${a['alunoNome']}'),
                          if (perfil != 'INSTRUTOR') Text('Instrutor: ${a['instrutorNome']}'),
                          Text('Veículo: ${a['veiculoPlaca']}'),
                          if (a['conteudo'] != null) Text('Conteúdo: ${a['conteudo']}'),
                          if (a['kmInicial'] != null && a['kmFinal'] != null) Text('Km: ${a['kmInicial']} → ${a['kmFinal']}'),
                          if (a['observacoes'] != null) Text('Obs.: ${a['observacoes']}'),
                          if (a['motivoCancelamento'] != null)
                            Text('Cancelada: ${a['motivoCancelamento']}${a['cancelamentoTardio'] == true ? ' (tardio)' : ''}'),
                          if (podeRegistrar || podeCancelar)
                            Align(
                              alignment: Alignment.centerRight,
                              child: Wrap(spacing: 8, children: [
                                if (podeCancelar) OutlinedButton(onPressed: () => _cancelar(a), child: const Text('Cancelar')),
                                if (podeRegistrar) FilledButton(onPressed: () => _registrar(a), child: const Text('Registrar')),
                              ]),
                            ),
                        ]),
                      ),
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
