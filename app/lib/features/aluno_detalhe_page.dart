import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/api.dart';
import '../core/formatos.dart';
import '../core/sessao.dart';
import 'comum.dart';

/// Ficha do aluno: dados, processos, documentos e matrículas.
class AlunoDetalhePage extends ConsumerStatefulWidget {
  const AlunoDetalhePage({super.key, required this.id});
  final int id;
  @override
  ConsumerState<AlunoDetalhePage> createState() => _AlunoDetalhePageState();
}

class _AlunoDetalhePageState extends ConsumerState<AlunoDetalhePage> {
  late Future<Map<String, dynamic>> _aluno = _carregar();

  Future<Map<String, dynamic>> _carregar() async =>
      (await ref.read(apiProvider).get('/alunos/${widget.id}')) as Map<String, dynamic>;

  void _recarregar() => setState(() => _aluno = _carregar());

  @override
  Widget build(BuildContext context) {
    return FutureBuilder(
      future: _aluno,
      builder: (context, snap) {
        if (snap.hasError) {
          return Scaffold(appBar: AppBar(), body: ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar));
        }
        if (!snap.hasData) return Scaffold(appBar: AppBar(), body: const Carregando());
        final a = snap.data!;
        return DefaultTabController(
          length: 4,
          child: Scaffold(
            appBar: AppBar(
              title: Text(a['nome'] as String),
              actions: [
                IconButton(
                  tooltip: 'Editar',
                  icon: const Icon(Icons.edit_outlined),
                  onPressed: () async {
                    await context.push('/alunos/${widget.id}/editar');
                    _recarregar();
                  },
                ),
              ],
              bottom: const TabBar(isScrollable: true, tabs: [
                Tab(text: 'Dados'),
                Tab(text: 'Processos'),
                Tab(text: 'Documentos'),
                Tab(text: 'Matrículas'),
              ]),
            ),
            body: TabBarView(children: [
              _DadosTab(aluno: a, aoMudar: _recarregar),
              _ProcessosTab(alunoId: widget.id),
              _DocumentosTab(alunoId: widget.id),
              _MatriculasTab(alunoId: widget.id),
            ]),
          ),
        );
      },
    );
  }
}

Future<void> _salvarArquivo(String nome, List<int> bytes) async {
  await FilePicker.platform.saveFile(fileName: nome, bytes: Uint8List.fromList(bytes));
}

// ---------------- Dados ----------------

class _DadosTab extends ConsumerStatefulWidget {
  const _DadosTab({required this.aluno, required this.aoMudar});
  final Map<String, dynamic> aluno;
  final VoidCallback aoMudar;
  @override
  ConsumerState<_DadosTab> createState() => _DadosTabState();
}

class _DadosTabState extends ConsumerState<_DadosTab> {
  late Future<List<dynamic>> _consent = _carregarConsent();

  int get _id => widget.aluno['id'] as int;

  Future<List<dynamic>> _carregarConsent() async =>
      (await ref.read(apiProvider).get('/alunos/$_id/consentimentos')) as List<dynamic>;

  Future<void> _acao(Future<void> Function() f, String ok) async {
    try {
      await f();
      if (mounted) mostrarOk(context, ok);
      widget.aoMudar();
      setState(() => _consent = _carregarConsent());
    } catch (e) {
      if (mounted) mostrarErro(context, e);
    }
  }

  Widget _linha(String r, String? v) => ListTile(dense: true, title: Text(r), subtitle: Text(v == null || v.isEmpty ? '-' : v));

  @override
  Widget build(BuildContext context) {
    final a = widget.aluno;
    final api = ref.read(apiProvider);
    final ativo = a['ativo'] == true;
    final endereco = [a['logradouro'], a['numero'], a['bairro'], a['cidade'], a['uf']]
        .where((e) => e != null && '$e'.isNotEmpty)
        .join(', ');
    return ListView(padding: const EdgeInsets.only(bottom: 32), children: [
      _linha('CPF', Formatos.cpf(a['cpf'] as String?)),
      _linha('Nascimento', Formatos.data(DateTime.parse(a['dataNascimento'] as String))),
      _linha('Telefone', Formatos.telefone(a['telefone'] as String?)),
      _linha('E-mail', a['email'] as String?),
      _linha('Endereço', endereco),
      SwitchListTile(
        title: const Text('Aluno ativo'),
        value: ativo,
        onChanged: (v) => _acao(() async => api.patch('/alunos/$_id/status', {'ativo': v}), v ? 'Aluno ativado.' : 'Aluno inativado.'),
      ),
      const Divider(),
      ListTile(
        leading: const Icon(Icons.phone_android),
        title: Text(a['temAcesso'] == true ? 'Tem acesso ao app' : 'Sem acesso ao app'),
        trailing: TextButton(
          child: Text(a['temAcesso'] == true ? 'Nova senha' : 'Criar acesso'),
          onPressed: () async {
            final temAcesso = a['temAcesso'] == true;
            final ok = await abrirForm(context,
                titulo: temAcesso ? 'Redefinir senha do aluno' : 'Criar acesso ao app',
                campos: [
                  if (!temAcesso) Campo('email', 'E-mail de acesso', obrigatorio: true, inicial: a['email'] as String?),
                  Campo('senha', 'Senha (mín. 8, com letras e números)', tipo: TipoCampo.senha, obrigatorio: true),
                ],
                enviar: (v) async => temAcesso
                    ? await api.put('/alunos/$_id/acesso/senha', {'novaSenha': v['senha']})
                    : await api.post('/alunos/$_id/acesso', {'email': v['email'], 'senha': v['senha']}));
            if (ok) widget.aoMudar();
          },
        ),
      ),
      const Divider(),
      ListTile(
        leading: const Icon(Icons.privacy_tip_outlined),
        title: const Text('Consentimento LGPD'),
        trailing: TextButton(
          child: const Text('Registrar'),
          onPressed: () async {
            final ok = await abrirForm(context,
                titulo: 'Registrar consentimento',
                campos: const [Campo('versao', 'Versão do termo aceito', obrigatorio: true, inicial: '1.0', max: 20)],
                enviar: (v) async => api.post('/alunos/$_id/consentimentos', {'versaoTermo': v['versao']}));
            if (ok) setState(() => _consent = _carregarConsent());
          },
        ),
      ),
      FutureBuilder(
        future: _consent,
        builder: (context, snap) {
          if (snap.hasError) return Padding(padding: const EdgeInsets.all(16), child: Text(ApiErro.de(snap.error!).mensagem));
          if (!snap.hasData) return const Padding(padding: EdgeInsets.all(16), child: LinearProgressIndicator());
          final itens = snap.data!;
          if (itens.isEmpty) return const ListTile(dense: true, title: Text('Nenhum consentimento registrado.'));
          return Column(children: [
            for (final c in itens.cast<Map<String, dynamic>>())
              ListTile(
                dense: true,
                title: Text('Termo v${c['versaoTermo']} · ${Formatos.dataHora(DateTime.parse(c['aceitoEm'] as String))}'),
                subtitle: Text(c['vigente'] == true ? 'Vigente' : 'Revogado em ${Formatos.data(DateTime.tryParse('${c['revogadoEm']}'))}'),
                trailing: c['vigente'] == true
                    ? TextButton(
                        child: const Text('Revogar'),
                        onPressed: () async {
                          if (await confirmar(context, 'Revogar consentimento?', 'Esta ação fica registrada.', rotulo: 'Revogar')) {
                            _acao(() async => api.patch('/consentimentos/${c['id']}/revogacao', null), 'Consentimento revogado.');
                          }
                        },
                      )
                    : null,
              ),
          ]);
        },
      ),
    ]);
  }
}

// ---------------- Processos ----------------

class _ProcessosTab extends ConsumerStatefulWidget {
  const _ProcessosTab({required this.alunoId});
  final int alunoId;
  @override
  ConsumerState<_ProcessosTab> createState() => _ProcessosTabState();
}

class _ProcessosTabState extends ConsumerState<_ProcessosTab> {
  late Future<List<dynamic>> _f = _carregar();
  Future<List<dynamic>> _carregar() async =>
      (await ref.read(apiProvider).get('/alunos/${widget.alunoId}/processos')) as List<dynamic>;
  void _recarregar() => setState(() => _f = _carregar());

  Future<void> _novo() async {
    final api = ref.read(apiProvider);
    final ok = await abrirForm(context,
        titulo: 'Novo processo de habilitação',
        campos: [
          Campo('categoria', 'Categoria',
              tipo: TipoCampo.escolha, obrigatorio: true, opcoes: {for (final c in Formatos.categorias) c: c}),
          Campo('tipo', 'Tipo',
              tipo: TipoCampo.escolha,
              obrigatorio: true,
              inicial: 'PRIMEIRA_HABILITACAO',
              opcoes: {for (final t in ['PRIMEIRA_HABILITACAO', 'ADICAO_CATEGORIA', 'MUDANCA_CATEGORIA']) t: Formatos.tipoProcesso(t)}),
          const Campo('renach', 'RENACH (opcional)', max: 20),
        ],
        enviar: (v) async => api.post('/alunos/${widget.alunoId}/processos',
            {'categoria': v['categoria'], 'tipo': v['tipo'], 'renach': v['renach']}));
    if (ok) _recarregar();
  }

  Future<void> _editar(Map<String, dynamic> p) async {
    final api = ref.read(apiProvider);
    final exames = {for (final s in ['PENDENTE', 'APTO', 'INAPTO']) s: Formatos.statusExame(s)};
    final ok = await abrirForm(context,
        titulo: 'Processo ${p['categoria']}',
        campos: [
          Campo('renach', 'RENACH', inicial: p['renach'] as String?, max: 20),
          Campo('medico', 'Exame médico', tipo: TipoCampo.escolha, obrigatorio: true, inicial: p['exameMedico'] as String, opcoes: exames),
          Campo('psico', 'Exame psicológico',
              tipo: TipoCampo.escolha, obrigatorio: true, inicial: p['examePsicologico'] as String, opcoes: exames),
          Campo('ladv', 'Nº da LADV', inicial: p['ladvNumero'] as String?, max: 30),
          Campo('ladvValidade', 'Validade da LADV', tipo: TipoCampo.data, inicial: p['ladvValidade'] as String?),
          Campo('situacao', 'Situação',
              tipo: TipoCampo.escolha,
              obrigatorio: true,
              inicial: p['situacao'] as String,
              opcoes: {for (final s in ['MATRICULADO', 'EM_AULAS', 'AGUARDANDO_EXAME', 'APROVADO', 'DESISTENTE']) s: Formatos.situacao(s)}),
          Campo('obs', 'Observações', tipo: TipoCampo.multilinha, inicial: p['observacoes'] as String?, max: 500),
        ],
        enviar: (v) async => api.put('/processos/${p['id']}', {
              'renach': v['renach'],
              'exameMedico': v['medico'],
              'examePsicologico': v['psico'],
              'ladvNumero': v['ladv'],
              'ladvValidade': v['ladvValidade']!.isEmpty ? null : v['ladvValidade'],
              'situacao': v['situacao'],
              'observacoes': v['obs'],
            }));
    if (ok) _recarregar();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(onPressed: _novo, icon: const Icon(Icons.add), label: const Text('Processo')),
      body: FutureBuilder(
        future: _f,
        builder: (context, snap) {
          if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
          if (!snap.hasData) return const Carregando();
          final itens = snap.data!.cast<Map<String, dynamic>>();
          if (itens.isEmpty) return const Center(child: Text('Nenhum processo. Crie um para poder matricular.'));
          return ListView(padding: const EdgeInsets.only(bottom: 88), children: [
            for (final p in itens)
              Card(
                margin: const EdgeInsets.fromLTRB(12, 8, 12, 0),
                child: ListTile(
                  onTap: () => _editar(p),
                  leading: CircleAvatar(child: Text(p['categoria'] as String)),
                  title: Text('${Formatos.tipoProcesso(p['tipo'] as String)} · ${Formatos.situacao(p['situacao'] as String)}'),
                  subtitle: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text('RENACH: ${p['renach'] ?? '-'}'),
                    Text('Médico: ${Formatos.statusExame(p['exameMedico'] as String)} · Psicológico: ${Formatos.statusExame(p['examePsicologico'] as String)}'),
                    for (final al in (p['alertas'] as List? ?? []))
                      Text('⚠ $al', style: TextStyle(color: Theme.of(context).colorScheme.error)),
                  ]),
                  isThreeLine: true,
                ),
              ),
          ]);
        },
      ),
    );
  }
}

// ---------------- Documentos ----------------

class _DocumentosTab extends ConsumerStatefulWidget {
  const _DocumentosTab({required this.alunoId});
  final int alunoId;
  @override
  ConsumerState<_DocumentosTab> createState() => _DocumentosTabState();
}

class _DocumentosTabState extends ConsumerState<_DocumentosTab> {
  late Future<List<dynamic>> _f = _carregar();
  Future<List<dynamic>> _carregar() async =>
      (await ref.read(apiProvider).get('/alunos/${widget.alunoId}/documentos')) as List<dynamic>;
  void _recarregar() => setState(() => _f = _carregar());

  Future<void> _enviar() async {
    final api = ref.read(apiProvider);
    final ok = await abrirForm(context,
        titulo: 'Enviar documento',
        rotuloBotao: 'Escolher arquivo',
        campos: [
          Campo('tipo', 'Tipo do documento',
              tipo: TipoCampo.escolha,
              obrigatorio: true,
              opcoes: {for (final t in ['FOTO', 'RG', 'COMPROVANTE_RESIDENCIA', 'OUTRO']) t: Formatos.tipoDocumento(t)}),
        ],
        enviar: (v) async {
          final r = await FilePicker.platform
              .pickFiles(withData: true, type: FileType.custom, allowedExtensions: ['jpg', 'jpeg', 'png', 'pdf']);
          if (r == null || r.files.single.bytes == null) throw ApiErro('Nenhum arquivo escolhido.');
          final f = r.files.single;
          await api.postForm('/alunos/${widget.alunoId}/documentos', FormData.fromMap({
            'tipo': v['tipo'],
            'arquivo': MultipartFile.fromBytes(f.bytes!, filename: f.name),
          }));
        });
    if (ok) _recarregar();
  }

  @override
  Widget build(BuildContext context) {
    final api = ref.read(apiProvider);
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(onPressed: _enviar, icon: const Icon(Icons.upload_file), label: const Text('Enviar')),
      body: FutureBuilder(
        future: _f,
        builder: (context, snap) {
          if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
          if (!snap.hasData) return const Carregando();
          final itens = snap.data!.cast<Map<String, dynamic>>();
          if (itens.isEmpty) return const Center(child: Text('Nenhum documento enviado.'));
          return ListView.separated(
            padding: const EdgeInsets.only(bottom: 88),
            itemCount: itens.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (_, i) {
              final d = itens[i];
              final pdf = d['contentType'] == 'application/pdf';
              return ListTile(
                leading: Icon(pdf ? Icons.picture_as_pdf_outlined : Icons.image_outlined),
                title: Text('${Formatos.tipoDocumento(d['tipo'] as String)} · ${d['nomeOriginal']}'),
                subtitle: Text('${((d['tamanho'] as num) / 1024).ceil()} KB · ${Formatos.dataHora(DateTime.parse(d['criadoEm'] as String))}'),
                trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                  IconButton(
                    tooltip: 'Baixar',
                    icon: const Icon(Icons.download_outlined),
                    onPressed: () async {
                      try {
                        await _salvarArquivo(d['nomeOriginal'] as String, await api.baixar('/documentos/${d['id']}/arquivo'));
                      } catch (e) {
                        if (context.mounted) mostrarErro(context, e);
                      }
                    },
                  ),
                  IconButton(
                    tooltip: 'Excluir',
                    icon: const Icon(Icons.delete_outline),
                    onPressed: () async {
                      if (!await confirmar(context, 'Excluir documento?', 'O arquivo será removido.', rotulo: 'Excluir')) return;
                      try {
                        await api.delete('/documentos/${d['id']}');
                        _recarregar();
                      } catch (e) {
                        if (context.mounted) mostrarErro(context, e);
                      }
                    },
                  ),
                ]),
              );
            },
          );
        },
      ),
    );
  }
}

// ---------------- Matrículas ----------------

class _MatriculasTab extends ConsumerStatefulWidget {
  const _MatriculasTab({required this.alunoId});
  final int alunoId;
  @override
  ConsumerState<_MatriculasTab> createState() => _MatriculasTabState();
}

class _MatriculasTabState extends ConsumerState<_MatriculasTab> {
  late Future<List<dynamic>> _f = _carregar();
  Future<List<dynamic>> _carregar() async =>
      (await ref.read(apiProvider).get('/matriculas', query: {'alunoId': widget.alunoId, 'tamanho': 100}))['itens'] as List<dynamic>;
  void _recarregar() => setState(() => _f = _carregar());

  Future<void> _nova() async {
    final api = ref.read(apiProvider);
    try {
      final processos = ((await api.get('/alunos/${widget.alunoId}/processos')) as List)
          .cast<Map<String, dynamic>>()
          .where((p) => p['situacao'] != 'APROVADO' && p['situacao'] != 'DESISTENTE')
          .toList();
      final pacotes = ((await api.get('/pacotes')) as List).cast<Map<String, dynamic>>();
      if (!mounted) return;
      if (processos.isEmpty) return mostrarErro(context, 'Crie um processo de habilitação na aba Processos primeiro.');
      if (pacotes.isEmpty) return mostrarErro(context, 'Cadastre um pacote antes (menu Pacotes).');
      final hoje = DateTime.now().add(const Duration(days: 30));
      final ok = await abrirForm(context,
          titulo: 'Nova matrícula',
          campos: [
            Campo('processo', 'Processo',
                tipo: TipoCampo.escolha,
                obrigatorio: true,
                opcoes: {for (final p in processos) '${p['id']}': '${p['categoria']} · ${Formatos.tipoProcesso(p['tipo'] as String)}'}),
            Campo('pacote', 'Pacote',
                tipo: TipoCampo.escolha,
                obrigatorio: true,
                opcoes: {for (final p in pacotes) '${p['id']}': '[${p['categoria']}] ${p['nome']} — ${Formatos.moeda(p['preco'] as num)}'}),
            const Campo('desconto', 'Desconto (R\$)', tipo: TipoCampo.valor),
            const Campo('entrada', 'Entrada (R\$)', tipo: TipoCampo.valor),
            const Campo('parcelas', 'Nº de parcelas (após a entrada)', tipo: TipoCampo.numero, obrigatorio: true, inicial: '1'),
            Campo('venc', '1º vencimento',
                tipo: TipoCampo.data,
                inicial: '${hoje.year}-${hoje.month.toString().padLeft(2, '0')}-${hoje.day.toString().padLeft(2, '0')}'),
          ],
          enviar: (v) async => api.post('/matriculas', {
                'processoId': int.parse(v['processo']!),
                'pacoteId': int.parse(v['pacote']!),
                'desconto': v['desconto']!.isEmpty ? null : num.parse(v['desconto']!),
                'entrada': v['entrada']!.isEmpty ? null : num.parse(v['entrada']!),
                'numParcelas': int.tryParse(v['parcelas']!),
                'primeiroVencimento': v['venc']!.isEmpty ? null : v['venc'],
              }));
      if (ok) _recarregar();
    } catch (e) {
      if (mounted) mostrarErro(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(onPressed: _nova, icon: const Icon(Icons.add), label: const Text('Matricular')),
      body: FutureBuilder(
        future: _f,
        builder: (context, snap) {
          if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
          if (!snap.hasData) return const Carregando();
          final itens = snap.data!.cast<Map<String, dynamic>>();
          if (itens.isEmpty) return const Center(child: Text('Nenhuma matrícula.'));
          return ListView.separated(
            padding: const EdgeInsets.only(bottom: 88),
            itemCount: itens.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (_, i) {
              final m = itens[i];
              return ListTile(
                leading: CircleAvatar(child: Text(m['categoria'] as String)),
                title: Text('${m['pacoteNome']} · ${Formatos.moeda(m['valorTotal'] as num)}'),
                subtitle: Text('${Formatos.statusMatricula(m['status'] as String)} · ${Formatos.data(DateTime.parse(m['dataMatricula'] as String))}'),
                trailing: const Icon(Icons.chevron_right),
                onTap: () async {
                  await showDialog(context: context, builder: (_) => _MatriculaDialog(id: m['id'] as int));
                  _recarregar();
                },
              );
            },
          );
        },
      ),
    );
  }
}

class _MatriculaDialog extends ConsumerStatefulWidget {
  const _MatriculaDialog({required this.id});
  final int id;
  @override
  ConsumerState<_MatriculaDialog> createState() => _MatriculaDialogState();
}

class _MatriculaDialogState extends ConsumerState<_MatriculaDialog> {
  late Future<Map<String, dynamic>> _f = _carregar();
  Future<Map<String, dynamic>> _carregar() async =>
      (await ref.read(apiProvider).get('/matriculas/${widget.id}')) as Map<String, dynamic>;
  void _recarregar() => setState(() => _f = _carregar());

  @override
  Widget build(BuildContext context) {
    final api = ref.read(apiProvider);
    return AlertDialog(
      title: const Text('Matrícula'),
      content: SizedBox(
        width: 460,
        child: FutureBuilder(
          future: _f,
          builder: (context, snap) {
            if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
            if (!snap.hasData) return const SizedBox(height: 120, child: Carregando());
            final d = snap.data!;
            final m = d['matricula'] as Map<String, dynamic>;
            final titulos = (d['titulos'] as List).cast<Map<String, dynamic>>();
            final ativa = m['status'] == 'ATIVA';
            return SingleChildScrollView(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('${m['pacoteNome']} (${m['categoria']})', style: Theme.of(context).textTheme.titleMedium),
                Text('${Formatos.statusMatricula(m['status'] as String)} · ${d['aulasContratadas']} aulas práticas'),
                Text('Total: ${Formatos.moeda(m['valorTotal'] as num)}'
                    '${(m['desconto'] as num? ?? 0) > 0 ? ' (desconto ${Formatos.moeda(m['desconto'] as num)})' : ''}'),
                if (m['motivoCancelamento'] != null) Text('Motivo do cancelamento: ${m['motivoCancelamento']}'),
                const SizedBox(height: 12),
                Text('Parcelas', style: Theme.of(context).textTheme.titleSmall),
                for (final t in titulos)
                  ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    title: Text('${t['descricao']} · ${Formatos.moeda(t['valor'] as num)}'),
                    subtitle: Text('Vence ${Formatos.data(DateTime.parse(t['vencimento'] as String))} · ${Formatos.statusTitulo(t['status'] as String)}${t['vencido'] == true ? ' · VENCIDA' : ''}'),
                  ),
              ]),
            );
          },
        ),
      ),
      actions: [
        TextButton(
          onPressed: () async {
            try {
              await _salvarArquivo('contrato-matricula-${widget.id}.pdf', await api.baixar('/matriculas/${widget.id}/contrato'));
            } catch (e) {
              if (context.mounted) mostrarErro(context, e);
            }
          },
          child: const Text('Contrato (PDF)'),
        ),
        TextButton(
          onPressed: () async {
            final ok = await abrirForm(context,
                titulo: 'Aulas avulsas',
                campos: [
                  const Campo('qtd', 'Quantidade', tipo: TipoCampo.numero, obrigatorio: true, inicial: '1'),
                  const Campo('valor', 'Valor de cada aula (R\$)', tipo: TipoCampo.valor, obrigatorio: true),
                  Campo('venc', 'Vencimento', tipo: TipoCampo.data, obrigatorio: true,
                      inicial: DateTime.now().add(const Duration(days: 7)).toIso8601String().substring(0, 10)),
                ],
                enviar: (v) async => api.post('/matriculas/${widget.id}/aulas-avulsas',
                    {'quantidade': int.tryParse(v['qtd']!), 'valorUnitario': num.tryParse(v['valor']!), 'vencimento': v['venc']}));
            if (ok) _recarregar();
          },
          child: const Text('Aula avulsa'),
        ),
        TextButton(
          onPressed: () async {
            final ok = await abrirForm(context,
                titulo: 'Cancelar matrícula',
                rotuloBotao: 'Cancelar matrícula',
                campos: const [Campo('motivo', 'Motivo', tipo: TipoCampo.multilinha, obrigatorio: true, max: 300)],
                enviar: (v) async => api.post('/matriculas/${widget.id}/cancelamento', {'motivo': v['motivo']}));
            if (ok) _recarregar();
          },
          child: const Text('Cancelar matrícula'),
        ),
        FilledButton(onPressed: () => Navigator.pop(context), child: const Text('Fechar')),
      ],
    );
  }
}
