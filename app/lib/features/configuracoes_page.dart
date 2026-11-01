import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/sessao.dart';
import 'comum.dart';

/// Configurações operacionais e regras por categoria (ADMIN).
class ConfiguracoesPage extends ConsumerStatefulWidget {
  const ConfiguracoesPage({super.key});
  @override
  ConsumerState<ConfiguracoesPage> createState() => _ConfiguracoesPageState();
}

class _ConfiguracoesPageState extends ConsumerState<ConfiguracoesPage> {
  late Future<(Map<String, dynamic>, List<dynamic>)> _dados = _carregar();

  Future<(Map<String, dynamic>, List<dynamic>)> _carregar() async {
    final api = ref.read(apiProvider);
    final cfg = await api.get('/configuracoes');
    final regras = await api.get('/configuracoes/regras-categoria');
    return (cfg as Map<String, dynamic>, regras as List<dynamic>);
  }

  void _recarregar() => setState(() => _dados = _carregar());

  @override
  Widget build(BuildContext context) {
    return FutureBuilder(
      future: _dados,
      builder: (context, snap) {
        if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
        if (!snap.hasData) return const Carregando();
        final (cfg, regras) = snap.data!;
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _FormConfiguracao(key: ValueKey(cfg['version']), cfg: cfg, aoSalvar: _recarregar),
            const SizedBox(height: 24),
            Text('Regras por categoria', style: Theme.of(context).textTheme.titleLarge),
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 8),
              child: Text('Confira os valores com a legislação vigente do seu estado: as regras do Contran mudaram recentemente.'),
            ),
            Card(
              child: Column(children: [
                for (final r in regras.cast<Map<String, dynamic>>())
                  ListTile(
                    leading: CircleAvatar(child: Text(r['categoria'] as String)),
                    title: Text('${r['horasPraticasMinimas']} h práticas · ${r['horasTeoricasMinimas']} h teóricas'),
                    subtitle: Text('Idade mínima ${r['idadeMinima']} anos${r['ativa'] == true ? '' : ' · inativa'}'),
                    trailing: const Icon(Icons.edit_outlined),
                    onTap: () async {
                      final ok = await showDialog<bool>(context: context, builder: (_) => _RegraDialog(regra: r));
                      if (ok == true) _recarregar();
                    },
                  ),
              ]),
            ),
          ],
        );
      },
    );
  }
}

class _FormConfiguracao extends ConsumerStatefulWidget {
  const _FormConfiguracao({super.key, required this.cfg, required this.aoSalvar});
  final Map<String, dynamic> cfg;
  final VoidCallback aoSalvar;
  @override
  ConsumerState<_FormConfiguracao> createState() => _FormConfiguracaoState();
}

class _FormConfiguracaoState extends ConsumerState<_FormConfiguracao> {
  static const _inteiros = {
    'duracaoAulaMin': 'Duração da aula (min)',
    'maxAulasDiaAluno': 'Máx. de aulas por dia por aluno',
    'antecedenciaAgendamentoHoras': 'Antecedência mín. para agendar (h)',
    'antecedenciaCancelamentoHoras': 'Antecedência mín. para cancelar (h)',
    'lembreteHorasAntes': 'Lembrete da aula (h antes)',
    'simuladoQtdQuestoes': 'Questões por simulado',
    'simuladoTempoMin': 'Tempo do simulado (min)',
    'simuladoAcertosMinimos': 'Acertos mínimos no simulado',
  };
  final _form = GlobalKey<FormState>();
  late final _c = {for (final k in [..._inteiros.keys, 'valorFalta', 'fusoHorario']) k: TextEditingController(text: '${widget.cfg[k]}')};
  late bool _cobrarFalta = widget.cfg['cobrarFalta'] == true;
  bool _salvando = false;
  Object? _erro;

  Future<void> _salvar() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _salvando = true;
      _erro = null;
    });
    try {
      await ref.read(apiProvider).put('/configuracoes', {
        for (final k in _inteiros.keys) k: int.parse(_c[k]!.text),
        'cobrarFalta': _cobrarFalta,
        'valorFalta': double.parse(_c['valorFalta']!.text.replaceAll(',', '.')),
        'fusoHorario': _c['fusoHorario']!.text.trim(),
        'version': widget.cfg['version'],
      });
      if (mounted) mostrarOk(context, 'Configurações salvas.');
      widget.aoSalvar();
    } catch (e) {
      setState(() => _erro = e);
      if (mounted) mostrarErro(context, e);
    } finally {
      if (mounted) setState(() => _salvando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Form(
      key: _form,
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('Regras da autoescola', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 16),
        Wrap(spacing: 12, runSpacing: 12, children: [
          for (final e in _inteiros.entries)
            SizedBox(
              width: 260,
              child: TextFormField(
                controller: _c[e.key],
                keyboardType: TextInputType.number,
                decoration: InputDecoration(labelText: e.value, errorText: erroCampo(_erro, e.key)),
                validator: (v) => int.tryParse(v ?? '') == null ? 'Número inteiro.' : null,
              ),
            ),
          SizedBox(
            width: 260,
            child: TextFormField(
              controller: _c['fusoHorario'],
              decoration: InputDecoration(labelText: 'Fuso horário', errorText: erroCampo(_erro, 'fusoHorario')),
            ),
          ),
        ]),
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: const Text('Cobrar falta do aluno'),
          value: _cobrarFalta,
          onChanged: (v) => setState(() => _cobrarFalta = v),
        ),
        if (_cobrarFalta)
          SizedBox(
            width: 260,
            child: TextFormField(
              controller: _c['valorFalta'],
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: const InputDecoration(labelText: 'Valor da falta', prefixText: r'R$ '),
              validator: (v) => double.tryParse((v ?? '').replaceAll(',', '.')) == null ? 'Valor inválido.' : null,
            ),
          ),
        const SizedBox(height: 16),
        FilledButton.icon(
          onPressed: _salvando ? null : _salvar,
          icon: const Icon(Icons.save_outlined),
          label: const Text('Salvar'),
        ),
      ]),
    );
  }
}

class _RegraDialog extends ConsumerStatefulWidget {
  const _RegraDialog({required this.regra});
  final Map<String, dynamic> regra;
  @override
  ConsumerState<_RegraDialog> createState() => _RegraDialogState();
}

class _RegraDialogState extends ConsumerState<_RegraDialog> {
  late final _praticas = TextEditingController(text: '${widget.regra['horasPraticasMinimas']}');
  late final _teoricas = TextEditingController(text: '${widget.regra['horasTeoricasMinimas']}');
  late final _idade = TextEditingController(text: '${widget.regra['idadeMinima']}');
  late bool _ativa = widget.regra['ativa'] == true;

  Future<void> _salvar() async {
    try {
      await ref.read(apiProvider).put('/configuracoes/regras-categoria/${widget.regra['categoria']}', {
        'horasPraticasMinimas': double.parse(_praticas.text.replaceAll(',', '.')),
        'horasTeoricasMinimas': double.parse(_teoricas.text.replaceAll(',', '.')),
        'idadeMinima': int.parse(_idade.text),
        'ativa': _ativa,
      });
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) mostrarErro(context, e is FormatException ? 'Preencha números válidos.' : e);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text('Categoria ${widget.regra['categoria']}'),
      content: Column(mainAxisSize: MainAxisSize.min, children: [
        TextField(controller: _praticas, decoration: const InputDecoration(labelText: 'Horas práticas mínimas')),
        const SizedBox(height: 12),
        TextField(controller: _teoricas, decoration: const InputDecoration(labelText: 'Horas teóricas mínimas')),
        const SizedBox(height: 12),
        TextField(controller: _idade, decoration: const InputDecoration(labelText: 'Idade mínima')),
        SwitchListTile(contentPadding: EdgeInsets.zero, title: const Text('Ativa'), value: _ativa, onChanged: (v) => setState(() => _ativa = v)),
      ]),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')),
        FilledButton(onPressed: _salvar, child: const Text('Salvar')),
      ],
    );
  }
}
