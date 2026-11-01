import 'dart:async';

import 'package:dio/dio.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/formatos.dart';
import '../core/sessao.dart';
import 'comum.dart';

/// Teórico: o aluno faz simulados; a equipe cuida do banco de questões e das aulas teóricas.
class TeoricoPage extends ConsumerWidget {
  const TeoricoPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final perfil = ref.watch(sessaoProvider).valueOrNull?.perfil ?? '';
    if (perfil == 'ALUNO') return const _SimuladosAluno();
    return DefaultTabController(
      length: 2,
      child: Column(children: const [
        TabBar(tabs: [Tab(text: 'Questões'), Tab(text: 'Aulas teóricas')]),
        Expanded(child: TabBarView(children: [_QuestoesTab(), _AulasTeoricasTab()])),
      ]),
    );
  }
}

// ---------------------------------------------------------------- Aluno

class _SimuladosAluno extends ConsumerStatefulWidget {
  const _SimuladosAluno();
  @override
  ConsumerState<_SimuladosAluno> createState() => _SimuladosAlunoState();
}

class _SimuladosAlunoState extends ConsumerState<_SimuladosAluno> {
  late Future<Map<String, dynamic>> _f = _carregar();
  Future<Map<String, dynamic>> _carregar() async => (await ref.read(apiProvider).get('/simulados/meus')) as Map<String, dynamic>;
  void _recarregar() => setState(() => _f = _carregar());

  Future<void> _iniciar() async {
    try {
      final s = (await ref.read(apiProvider).post('/simulados', null)) as Map<String, dynamic>;
      if (!mounted) return;
      await Navigator.of(context).push(MaterialPageRoute(builder: (_) => _ProvaPage(simulado: s)));
      _recarregar();
    } catch (e) {
      if (mounted) mostrarErro(context, e);
    }
  }

  Future<void> _ver(int id) async {
    try {
      final s = (await ref.read(apiProvider).get('/simulados/$id')) as Map<String, dynamic>;
      if (!mounted) return;
      await Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => s['finalizadoEm'] == null ? _ProvaPage(simulado: s) : _ResultadoPage(simulado: s)));
      _recarregar();
    } catch (e) {
      if (mounted) mostrarErro(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(onPressed: _iniciar, icon: const Icon(Icons.play_arrow), label: const Text('Novo simulado')),
      body: FutureBuilder<Map<String, dynamic>>(
        future: _f,
        builder: (context, snap) {
          if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
          if (!snap.hasData) return const Carregando();
          final d = snap.data!;
          final hist = (d['historico'] as List).cast<Map<String, dynamic>>();
          return ListView(padding: const EdgeInsets.only(bottom: 88), children: [
            ListTile(
              leading: const Icon(Icons.quiz_outlined),
              title: Text('${d['simuladosFeitos']} simulados feitos · ${d['aprovados']} aprovações'),
              subtitle: const Text('Treine até acertar o mínimo exigido com folga.'),
            ),
            const Divider(),
            if (hist.isEmpty) const Padding(padding: EdgeInsets.all(24), child: Center(child: Text('Nenhum simulado ainda.'))),
            for (final s in hist)
              ListTile(
                leading: Icon(
                    s['finalizadoEm'] == null ? Icons.timer_outlined : (s['aprovado'] == true ? Icons.check_circle : Icons.cancel),
                    color: s['finalizadoEm'] == null ? null : (s['aprovado'] == true ? Colors.green : Colors.red)),
                title: Text(s['finalizadoEm'] == null
                    ? 'Em andamento'
                    : '${s['acertos']}/${s['qtdQuestoes']} acertos${s['expirado'] == true ? ' (tempo esgotado)' : ''}'),
                subtitle: Text(Formatos.dataHora(DateTime.parse(s['iniciadoEm']))),
                onTap: () => _ver(s['id'] as int),
              ),
          ]);
        },
      ),
    );
  }
}

class _ProvaPage extends ConsumerStatefulWidget {
  const _ProvaPage({required this.simulado});
  final Map<String, dynamic> simulado;
  @override
  ConsumerState<_ProvaPage> createState() => _ProvaPageState();
}

class _ProvaPageState extends ConsumerState<_ProvaPage> {
  final Map<int, String> _respostas = {};
  int _atual = 0;
  Timer? _timer;
  Duration _restante = Duration.zero;
  bool _enviando = false;

  List<Map<String, dynamic>> get _questoes => (widget.simulado['questoes'] as List).cast<Map<String, dynamic>>();
  DateTime get _limite => DateTime.parse(widget.simulado['limiteEm']);

  @override
  void initState() {
    super.initState();
    for (final q in _questoes) {
      if (q['resposta'] != null) _respostas[q['questaoId'] as int] = q['resposta'] as String;
    }
    _tick();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) => _tick());
  }

  void _tick() {
    final r = _limite.difference(DateTime.now());
    if (!mounted) return;
    setState(() => _restante = r.isNegative ? Duration.zero : r);
    if (r.isNegative && !_enviando) _finalizar();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _finalizar({bool pedirConfirmacao = false}) async {
    if (pedirConfirmacao) {
      final faltam = _questoes.length - _respostas.length;
      final ok = await confirmar(context, 'Finalizar simulado', faltam > 0 ? 'Faltam $faltam questões sem resposta. Finalizar mesmo assim?' : 'Finalizar e ver o resultado?');
      if (!ok) return;
    }
    if (_enviando) return;
    setState(() => _enviando = true);
    _timer?.cancel();
    try {
      final r = (await ref.read(apiProvider).post('/simulados/${widget.simulado['id']}/finalizacao', {
        'respostas': {for (final e in _respostas.entries) '${e.key}': e.value},
      })) as Map<String, dynamic>;
      if (!mounted) return;
      Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => _ResultadoPage(simulado: r)));
    } catch (e) {
      if (mounted) {
        setState(() => _enviando = false);
        mostrarErro(context, e);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final q = _questoes[_atual];
    final id = q['questaoId'] as int;
    final mm = _restante.inMinutes.toString().padLeft(2, '0');
    final ss = (_restante.inSeconds % 60).toString().padLeft(2, '0');
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        if (await confirmar(context, 'Sair do simulado', 'O tempo continua correndo. Você pode retomar depois.', rotulo: 'Sair') && mounted) {
          Navigator.of(context).pop();
        }
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text('Questão ${_atual + 1}/${_questoes.length}'),
          actions: [Center(child: Padding(padding: const EdgeInsets.only(right: 16), child: Text('$mm:$ss', style: const TextStyle(fontWeight: FontWeight.bold))))],
        ),
        body: ListView(padding: const EdgeInsets.all(16), children: [
          Text(q['enunciado'] as String, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 12),
          for (final l in ['A', 'B', 'C', 'D'])
            RadioListTile<String>(
              value: l,
              groupValue: _respostas[id],
              onChanged: (v) => setState(() => _respostas[id] = v!),
              title: Text(q['alt$l'] as String),
            ),
        ]),
        bottomNavigationBar: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(8),
            child: Row(children: [
              TextButton(onPressed: _atual > 0 ? () => setState(() => _atual--) : null, child: const Text('Anterior')),
              const Spacer(),
              if (_atual < _questoes.length - 1)
                FilledButton(onPressed: () => setState(() => _atual++), child: const Text('Próxima'))
              else
                FilledButton(onPressed: _enviando ? null : () => _finalizar(pedirConfirmacao: true), child: const Text('Finalizar')),
            ]),
          ),
        ),
      ),
    );
  }
}

class _ResultadoPage extends StatelessWidget {
  const _ResultadoPage({required this.simulado});
  final Map<String, dynamic> simulado;

  @override
  Widget build(BuildContext context) {
    final aprovado = simulado['aprovado'] == true;
    final qs = (simulado['questoes'] as List).cast<Map<String, dynamic>>();
    return Scaffold(
      appBar: AppBar(title: const Text('Resultado')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Card(
          color: aprovado ? Colors.green.shade50 : Colors.red.shade50,
          child: ListTile(
            leading: Icon(aprovado ? Icons.check_circle : Icons.cancel, color: aprovado ? Colors.green : Colors.red, size: 36),
            title: Text(aprovado ? 'Aprovado' : 'Reprovado'),
            subtitle: Text('${simulado['acertos']} de ${simulado['qtdQuestoes']} acertos (mínimo ${simulado['acertosMinimos']})'
                '${simulado['expirado'] == true ? '\nO tempo se esgotou antes do envio.' : ''}'),
          ),
        ),
        const SizedBox(height: 8),
        for (final q in qs)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  Icon(q['acertou'] == true ? Icons.check : Icons.close, color: q['acertou'] == true ? Colors.green : Colors.red, size: 18),
                  const SizedBox(width: 6),
                  Expanded(child: Text('${q['ordem']}. ${q['enunciado']}', style: const TextStyle(fontWeight: FontWeight.w600))),
                ]),
                const SizedBox(height: 6),
                Text('Sua resposta: ${q['resposta'] == null ? 'em branco' : '${q['resposta']}) ${q['alt${q['resposta']}']}'}'),
                Text('Correta: ${q['correta']}) ${q['alt${q['correta']}']}'),
                if (q['explicacao'] != null) Padding(padding: const EdgeInsets.only(top: 4), child: Text('${q['explicacao']}', style: Theme.of(context).textTheme.bodySmall)),
              ]),
            ),
          ),
      ]),
    );
  }
}

// ---------------------------------------------------------------- Equipe: questões

class _QuestoesTab extends ConsumerStatefulWidget {
  const _QuestoesTab();
  @override
  ConsumerState<_QuestoesTab> createState() => _QuestoesTabState();
}

class _QuestoesTabState extends ConsumerState<_QuestoesTab> {
  late Future<List<dynamic>> _f = _carregar();
  Future<List<dynamic>> _carregar() async => (await ref.read(apiProvider).get('/questoes')) as List<dynamic>;
  void _recarregar() => setState(() => _f = _carregar());

  List<Campo> _campos(Map<String, dynamic>? q) => [
        Campo('tema', 'Tema', obrigatorio: true, inicial: q?['tema'], max: 60),
        Campo('enunciado', 'Enunciado', tipo: TipoCampo.multilinha, obrigatorio: true, inicial: q?['enunciado'], max: 1000),
        Campo('altA', 'Alternativa A', obrigatorio: true, inicial: q?['altA'], max: 400),
        Campo('altB', 'Alternativa B', obrigatorio: true, inicial: q?['altB'], max: 400),
        Campo('altC', 'Alternativa C', obrigatorio: true, inicial: q?['altC'], max: 400),
        Campo('altD', 'Alternativa D', obrigatorio: true, inicial: q?['altD'], max: 400),
        Campo('correta', 'Resposta correta', tipo: TipoCampo.escolha, obrigatorio: true, inicial: q?['correta'], opcoes: const {'A': 'A', 'B': 'B', 'C': 'C', 'D': 'D'}),
        Campo('explicacao', 'Explicação (opcional)', tipo: TipoCampo.multilinha, inicial: q?['explicacao'], max: 600),
      ];

  Future<void> _editar([Map<String, dynamic>? q]) async {
    final api = ref.read(apiProvider);
    final ok = await abrirForm(context,
        titulo: q == null ? 'Nova questão' : 'Editar questão',
        campos: _campos(q),
        enviar: (v) async => q == null ? api.post('/questoes', v) : api.put('/questoes/${q['id']}', v));
    if (ok) _recarregar();
  }

  Future<void> _importar() async {
    final r = await FilePicker.platform.pickFiles(type: FileType.custom, allowedExtensions: ['csv', 'txt'], withData: true);
    final f = r?.files.single;
    if (f == null || f.bytes == null) return;
    try {
      final res = (await ref.read(apiProvider).postForm(
              '/questoes/importacao', FormData.fromMap({'arquivo': MultipartFile.fromBytes(f.bytes!, filename: f.name)})))
          as Map<String, dynamic>;
      if (!mounted) return;
      final erros = (res['erros'] as List).cast<String>();
      await showDialog<void>(
        context: context,
        builder: (_) => AlertDialog(
          title: Text('${res['importadas']} questões importadas'),
          content: SingleChildScrollView(child: Text(erros.isEmpty ? 'Sem erros.' : erros.take(15).join('\n'))),
          actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('OK'))],
        ),
      );
      _recarregar();
    } catch (e) {
      if (mounted) mostrarErro(context, e);
    }
  }

  void _ajudaImportacao() => showDialog<void>(
        context: context,
        builder: (_) => AlertDialog(
          title: const Text('Formato do CSV'),
          content: const Text('Arquivo UTF-8, separado por ponto e vírgula, uma questão por linha:\n\n'
              'tema;enunciado;A;B;C;D;correta;explicacao\n\n'
              'Use apenas questões próprias ou com licença de uso. O sistema não traz questões oficiais.'),
          actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('OK'))],
        ),
      );

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(onPressed: () => _editar(), icon: const Icon(Icons.add), label: const Text('Nova questão')),
      body: FutureBuilder<List<dynamic>>(
        future: _f,
        builder: (context, snap) {
          if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
          if (!snap.hasData) return const Carregando();
          final qs = snap.data!.cast<Map<String, dynamic>>();
          return ListView(padding: const EdgeInsets.only(bottom: 88), children: [
            Padding(
              padding: const EdgeInsets.all(8),
              child: Row(children: [
                Expanded(child: Text('${qs.where((q) => q['ativa'] == true).length} questões ativas')),
                TextButton.icon(onPressed: _importar, icon: const Icon(Icons.upload_file), label: const Text('Importar CSV')),
                IconButton(onPressed: _ajudaImportacao, icon: const Icon(Icons.help_outline)),
              ]),
            ),
            for (final q in qs)
              ListTile(
                title: Text(q['enunciado'] as String, maxLines: 2, overflow: TextOverflow.ellipsis),
                subtitle: Text('${q['tema']} · correta ${q['correta']}${q['ativa'] == true ? '' : ' · inativa'}'),
                onTap: () => _editar(q),
                trailing: Switch(
                  value: q['ativa'] == true,
                  onChanged: (v) async {
                    try {
                      await ref.read(apiProvider).patch('/questoes/${q['id']}/status?ativa=$v', null);
                      _recarregar();
                    } catch (e) {
                      if (mounted) mostrarErro(context, e);
                    }
                  },
                ),
              ),
          ]);
        },
      ),
    );
  }
}

// ---------------------------------------------------------------- Equipe: aulas teóricas

class _AulasTeoricasTab extends ConsumerStatefulWidget {
  const _AulasTeoricasTab();
  @override
  ConsumerState<_AulasTeoricasTab> createState() => _AulasTeoricasTabState();
}

class _AulasTeoricasTabState extends ConsumerState<_AulasTeoricasTab> {
  late Future<List<dynamic>> _f = _carregar();
  Future<List<dynamic>> _carregar() async {
    final hoje = DateTime.now();
    String iso(DateTime d) => d.toIso8601String().substring(0, 10);
    return (await ref.read(apiProvider).get('/aulas-teoricas',
        query: {'de': iso(hoje.subtract(const Duration(days: 30))), 'ate': iso(hoje.add(const Duration(days: 60)))})) as List<dynamic>;
  }

  void _recarregar() => setState(() => _f = _carregar());

  Future<void> _agendar() async {
    final api = ref.read(apiProvider);
    final hoje = DateTime.now().add(const Duration(days: 1)).toIso8601String().substring(0, 10);
    final ok = await abrirForm(context,
        titulo: 'Nova aula teórica',
        campos: [
          const Campo('tema', 'Tema', obrigatorio: true, max: 120),
          Campo('data', 'Data', tipo: TipoCampo.data, obrigatorio: true, inicial: hoje),
          const Campo('ini', 'Início', tipo: TipoCampo.hora, obrigatorio: true),
          const Campo('fim', 'Fim', tipo: TipoCampo.hora, obrigatorio: true),
          const Campo('local', 'Local', max: 120),
        ],
        enviar: (v) async => api.post('/aulas-teoricas', {
              'tema': v['tema'],
              'inicio': '${v['data']}T${v['ini']}',
              'fim': '${v['data']}T${v['fim']}',
              'local': v['local'],
            }));
    if (ok) _recarregar();
  }

  Future<void> _presenca(Map<String, dynamic> a) async {
    final api = ref.read(apiProvider);
    try {
      final alunos = ((await api.get('/alunos', query: {'tamanho': 100}))['itens'] as List).cast<Map<String, dynamic>>();
      final atuais = ((await api.get('/aulas-teoricas/${a['id']}/presencas')) as List).cast<Map<String, dynamic>>();
      if (!mounted) return;
      final marcados = {for (final p in atuais) if (p['presente'] == true) p['alunoId'] as int};
      final salvar = await showDialog<Set<int>>(
        context: context,
        builder: (_) => StatefulBuilder(
          builder: (ctx, setS) => AlertDialog(
            title: Text('Presença · ${a['tema']}'),
            content: SizedBox(
              width: 400,
              child: ListView(shrinkWrap: true, children: [
                for (final al in alunos)
                  CheckboxListTile(
                    value: marcados.contains(al['id']),
                    title: Text(al['nome'] as String),
                    onChanged: (v) => setS(() => v == true ? marcados.add(al['id'] as int) : marcados.remove(al['id'])),
                  ),
              ]),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancelar')),
              FilledButton(onPressed: () => Navigator.pop(ctx, marcados), child: const Text('Salvar')),
            ],
          ),
        ),
      );
      if (salvar == null) return;
      await api.put('/aulas-teoricas/${a['id']}/presencas', {
        'itens': [for (final al in alunos) {'alunoId': al['id'], 'presente': salvar.contains(al['id'])}],
      });
      _recarregar();
    } catch (e) {
      if (mounted) mostrarErro(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(onPressed: _agendar, icon: const Icon(Icons.add), label: const Text('Nova aula')),
      body: FutureBuilder<List<dynamic>>(
        future: _f,
        builder: (context, snap) {
          if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
          if (!snap.hasData) return const Carregando();
          final as_ = snap.data!.cast<Map<String, dynamic>>();
          if (as_.isEmpty) return const Center(child: Text('Nenhuma aula teórica no período.'));
          return ListView(padding: const EdgeInsets.only(bottom: 88), children: [
            for (final a in as_)
              ListTile(
                title: Text(a['tema'] as String),
                subtitle: Text('${Formatos.dataHora(DateTime.parse(a['inicio']))} · ${{'AGENDADA': 'Agendada', 'REALIZADA': 'Realizada', 'CANCELADA': 'Cancelada'}[a['status']]}'),
                trailing: a['status'] == 'CANCELADA'
                    ? null
                    : PopupMenuButton<String>(
                        onSelected: (v) async {
                          if (v == 'presenca') return _presenca(a);
                          try {
                            await ref.read(apiProvider).post('/aulas-teoricas/${a['id']}/cancelamento', null);
                            _recarregar();
                          } catch (e) {
                            if (mounted) mostrarErro(context, e);
                          }
                        },
                        itemBuilder: (_) => [
                          const PopupMenuItem(value: 'presenca', child: Text('Lançar presença')),
                          if (a['status'] == 'AGENDADA') const PopupMenuItem(value: 'cancelar', child: Text('Cancelar aula')),
                        ],
                      ),
              ),
          ]);
        },
      ),
    );
  }
}
