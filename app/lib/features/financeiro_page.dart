import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/formatos.dart';
import '../core/sessao.dart';
import 'comum.dart';

const _formas = {
  'DINHEIRO': 'Dinheiro',
  'PIX': 'Pix',
  'CARTAO_CREDITO': 'Cartão de crédito',
  'CARTAO_DEBITO': 'Cartão de débito',
  'BOLETO': 'Boleto',
  'TRANSFERENCIA': 'Transferência',
};

String _isoHoje() => DateTime.now().toIso8601String().substring(0, 10);

/// Financeiro: contas a receber, despesas (ADMIN) e resumo (ADMIN).
class FinanceiroPage extends ConsumerWidget {
  const FinanceiroPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final admin = ref.watch(sessaoProvider).valueOrNull?.perfil == 'ADMIN';
    final abas = [
      ('A receber', const _TitulosTab()),
      if (admin) ('Despesas', const _DespesasTab()),
      if (admin) ('Resumo', const _ResumoTab()),
    ];
    return DefaultTabController(
      length: abas.length,
      child: Column(children: [
        TabBar(isScrollable: true, tabs: [for (final a in abas) Tab(text: a.$1)]),
        Expanded(child: TabBarView(children: [for (final a in abas) a.$2])),
      ]),
    );
  }
}

// ---------------- A receber ----------------

class _TitulosTab extends ConsumerStatefulWidget {
  const _TitulosTab();
  @override
  ConsumerState<_TitulosTab> createState() => _TitulosTabState();
}

class _TitulosTabState extends ConsumerState<_TitulosTab> {
  String _filtro = 'ABERTO'; // ABERTO | VENCIDOS | PAGO
  late Future<List<dynamic>> _f = _carregar();

  Future<List<dynamic>> _carregar() async => (await ref.read(apiProvider).get('/titulos', query: {
        if (_filtro == 'VENCIDOS') 'apenasVencidos': true else 'status': _filtro,
      })) as List<dynamic>;

  void _recarregar() => setState(() => _f = _carregar());

  Future<void> _receber(Map<String, dynamic> t) async {
    final api = ref.read(apiProvider);
    final ok = await abrirForm(context,
        titulo: 'Receber ${Formatos.moeda(t['valor'] as num)}',
        rotuloBotao: 'Confirmar recebimento',
        campos: [
          Campo('forma', 'Forma de pagamento', tipo: TipoCampo.escolha, obrigatorio: true, inicial: 'PIX', opcoes: _formas),
          Campo('data', 'Data do pagamento', tipo: TipoCampo.data, obrigatorio: true, inicial: _isoHoje()),
          const Campo('acrescimo', 'Multa/juros (R\$)', tipo: TipoCampo.valor),
          const Campo('desconto', 'Desconto (R\$)', tipo: TipoCampo.valor),
        ],
        enviar: (v) async => api.post('/titulos/${t['id']}/baixa', {
              'forma': v['forma'],
              'dataPagamento': v['data'],
              'acrescimo': v['acrescimo']!.isEmpty ? null : num.parse(v['acrescimo']!),
              'desconto': v['desconto']!.isEmpty ? null : num.parse(v['desconto']!),
            }));
    if (ok) _recarregar();
  }

  Future<void> _motivo(String titulo, String url) async {
    final api = ref.read(apiProvider);
    final ok = await abrirForm(context,
        titulo: titulo,
        campos: const [Campo('motivo', 'Motivo', tipo: TipoCampo.multilinha, obrigatorio: true, max: 300)],
        enviar: (v) async => api.post(url, {'motivo': v['motivo']}));
    if (ok) _recarregar();
  }

  Future<void> _novo() async {
    final api = ref.read(apiProvider);
    try {
      final alunos = ((await api.get('/alunos', query: {'tamanho': 100}))['itens'] as List).cast<Map<String, dynamic>>();
      if (!mounted) return;
      final ok = await abrirForm(context,
          titulo: 'Novo título avulso',
          campos: [
            Campo('aluno', 'Aluno', tipo: TipoCampo.escolha, obrigatorio: true, opcoes: {for (final a in alunos) '${a['id']}': '${a['nome']}'}),
            const Campo('descricao', 'Descrição', obrigatorio: true, max: 120),
            const Campo('valor', 'Valor (R\$)', tipo: TipoCampo.valor, obrigatorio: true),
            Campo('venc', 'Vencimento', tipo: TipoCampo.data, obrigatorio: true, inicial: _isoHoje()),
          ],
          enviar: (v) async => api.post('/titulos', {
                'alunoId': int.parse(v['aluno']!),
                'descricao': v['descricao'],
                'valor': num.tryParse(v['valor']!),
                'vencimento': v['venc'],
              }));
      if (ok) _recarregar();
    } catch (e) {
      if (mounted) mostrarErro(context, e);
    }
  }

  Future<void> _recibo(Map<String, dynamic> t) async {
    try {
      await salvarArquivo('recibo-${t['reciboNumero']}.pdf', await ref.read(apiProvider).baixar('/pagamentos/${t['pagamentoId']}/recibo'));
    } catch (e) {
      if (mounted) mostrarErro(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final admin = ref.watch(sessaoProvider).valueOrNull?.perfil == 'ADMIN';
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(onPressed: _novo, icon: const Icon(Icons.add), label: const Text('Título avulso')),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.all(8),
          child: SegmentedButton<String>(
            segments: const [
              ButtonSegment(value: 'ABERTO', label: Text('Em aberto')),
              ButtonSegment(value: 'VENCIDOS', label: Text('Vencidos')),
              ButtonSegment(value: 'PAGO', label: Text('Pagos')),
            ],
            selected: {_filtro},
            onSelectionChanged: (s) {
              _filtro = s.first;
              _recarregar();
            },
          ),
        ),
        Expanded(
          child: FutureBuilder(
            future: _f,
            builder: (context, snap) {
              if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
              if (!snap.hasData) return const Carregando();
              final itens = snap.data!.cast<Map<String, dynamic>>();
              if (itens.isEmpty) return const Center(child: Text('Nada por aqui.'));
              return RefreshIndicator(
                onRefresh: () async => _recarregar(),
                child: ListView.separated(
                  padding: const EdgeInsets.only(bottom: 88),
                  itemCount: itens.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (_, i) {
                    final t = itens[i];
                    final status = t['status'] as String;
                    final vencido = t['vencido'] == true;
                    return ListTile(
                      title: Text('${t['alunoNome']} · ${t['descricao']}'),
                      subtitle: Text(
                        status == 'PAGO'
                            ? 'Pago em ${Formatos.data(DateTime.tryParse('${t['dataPagamento']}'))} · recibo ${t['reciboNumero']}'
                            : '${vencido ? 'VENCIDO em ' : 'Vence em '}${Formatos.data(DateTime.parse(t['vencimento'] as String))}${status == 'CANCELADO' ? ' · cancelado' : ''}',
                        style: vencido ? TextStyle(color: cs.error) : null,
                      ),
                      leading: Text(Formatos.moeda(t['valor'] as num), style: Theme.of(context).textTheme.titleSmall),
                      trailing: PopupMenuButton<String>(
                        onSelected: (v) {
                          switch (v) {
                            case 'receber':
                              _receber(t);
                            case 'cancelar':
                              _motivo('Cancelar título', '/titulos/${t['id']}/cancelamento');
                            case 'recibo':
                              _recibo(t);
                            case 'estornar':
                              _motivo('Estornar pagamento', '/pagamentos/${t['pagamentoId']}/estorno');
                          }
                        },
                        itemBuilder: (_) => [
                          if (status == 'ABERTO') const PopupMenuItem(value: 'receber', child: Text('Receber')),
                          if (status == 'ABERTO') const PopupMenuItem(value: 'cancelar', child: Text('Cancelar título')),
                          if (status == 'PAGO') const PopupMenuItem(value: 'recibo', child: Text('Baixar recibo (PDF)')),
                          if (status == 'PAGO' && admin) const PopupMenuItem(value: 'estornar', child: Text('Estornar pagamento')),
                        ],
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

// ---------------- Despesas ----------------

class _DespesasTab extends ConsumerStatefulWidget {
  const _DespesasTab();
  @override
  ConsumerState<_DespesasTab> createState() => _DespesasTabState();
}

class _DespesasTabState extends ConsumerState<_DespesasTab> {
  DateTime _mes = DateTime(DateTime.now().year, DateTime.now().month);
  late Future<List<dynamic>> _f = _carregar();

  String get _mesIso => '${_mes.year}-${_mes.month.toString().padLeft(2, '0')}';

  Future<List<dynamic>> _carregar() async => (await ref.read(apiProvider).get('/despesas', query: {'mes': _mesIso})) as List<dynamic>;
  void _recarregar() => setState(() => _f = _carregar());

  Future<void> _nova() async {
    final api = ref.read(apiProvider);
    final ok = await abrirForm(context,
        titulo: 'Nova despesa',
        campos: [
          const Campo('descricao', 'Descrição', obrigatorio: true, max: 120),
          const Campo('categoria', 'Categoria',
              tipo: TipoCampo.escolha,
              obrigatorio: true,
              inicial: 'OUTROS',
              opcoes: {'ALUGUEL': 'Aluguel', 'SALARIO': 'Salários', 'COMBUSTIVEL': 'Combustível', 'MANUTENCAO': 'Manutenção', 'IMPOSTO': 'Impostos', 'OUTROS': 'Outros'}),
          const Campo('valor', 'Valor (R\$)', tipo: TipoCampo.valor, obrigatorio: true),
          Campo('venc', 'Vencimento', tipo: TipoCampo.data, obrigatorio: true, inicial: _isoHoje()),
        ],
        enviar: (v) async => api.post('/despesas',
            {'descricao': v['descricao'], 'categoria': v['categoria'], 'valor': num.tryParse(v['valor']!), 'vencimento': v['venc']}));
    if (ok) _recarregar();
  }

  Future<void> _acao(Future<dynamic> Function() f) async {
    try {
      await f();
      _recarregar();
    } catch (e) {
      if (mounted) mostrarErro(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final api = ref.read(apiProvider);
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(onPressed: _nova, icon: const Icon(Icons.add), label: const Text('Despesa')),
      body: Column(children: [
        Row(children: [
          IconButton(icon: const Icon(Icons.chevron_left), onPressed: () { _mes = DateTime(_mes.year, _mes.month - 1); _recarregar(); }),
          Expanded(child: Center(child: Text('${_mes.month.toString().padLeft(2, '0')}/${_mes.year}', style: Theme.of(context).textTheme.titleMedium))),
          IconButton(icon: const Icon(Icons.chevron_right), onPressed: () { _mes = DateTime(_mes.year, _mes.month + 1); _recarregar(); }),
        ]),
        Expanded(
          child: FutureBuilder(
            future: _f,
            builder: (context, snap) {
              if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
              if (!snap.hasData) return const Carregando();
              final itens = snap.data!.cast<Map<String, dynamic>>();
              if (itens.isEmpty) return const Center(child: Text('Nenhuma despesa neste mês.'));
              return ListView.separated(
                padding: const EdgeInsets.only(bottom: 88),
                itemCount: itens.length,
                separatorBuilder: (_, __) => const Divider(height: 1),
                itemBuilder: (_, i) {
                  final d = itens[i];
                  final status = d['status'] as String;
                  return ListTile(
                    title: Text('${d['descricao']} · ${Formatos.moeda(d['valor'] as num)}'),
                    subtitle: Text('${status == 'PAGA' ? 'Paga em ${Formatos.data(DateTime.tryParse('${d['dataPagamento']}'))}' : status == 'CANCELADA' ? 'Cancelada' : '${d['vencida'] == true ? 'VENCIDA em ' : 'Vence em '}${Formatos.data(DateTime.parse(d['vencimento'] as String))}'} · ${d['categoria']}'),
                    trailing: status == 'ABERTA'
                        ? PopupMenuButton<String>(
                            onSelected: (v) => _acao(() => v == 'pagar'
                                ? api.post('/despesas/${d['id']}/pagamento', {})
                                : api.post('/despesas/${d['id']}/cancelamento', {})),
                            itemBuilder: (_) => const [
                              PopupMenuItem(value: 'pagar', child: Text('Marcar como paga')),
                              PopupMenuItem(value: 'cancelar', child: Text('Cancelar')),
                            ],
                          )
                        : null,
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

// ---------------- Resumo ----------------

class _ResumoTab extends ConsumerStatefulWidget {
  const _ResumoTab();
  @override
  ConsumerState<_ResumoTab> createState() => _ResumoTabState();
}

class _ResumoTabState extends ConsumerState<_ResumoTab> {
  late Future<Map<String, dynamic>> _f = _carregar();
  Future<Map<String, dynamic>> _carregar() async => (await ref.read(apiProvider).get('/financeiro/resumo')) as Map<String, dynamic>;
  void _recarregar() => setState(() => _f = _carregar());

  Widget _card(BuildContext c, String titulo, num v, {Color? cor}) => Card(
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(titulo, style: Theme.of(c).textTheme.bodySmall),
            const SizedBox(height: 4),
            Text(Formatos.moeda(v), style: Theme.of(c).textTheme.titleLarge?.copyWith(color: cor)),
          ]),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return FutureBuilder(
      future: _f,
      builder: (context, snap) {
        if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
        if (!snap.hasData) return const Carregando();
        final r = snap.data!;
        final serie = (r['ultimosMeses'] as List).cast<Map<String, dynamic>>();
        final maximo = serie.fold<double>(1, (m, x) => [m, (x['recebido'] as num).toDouble(), (x['despesas'] as num).toDouble()].reduce((a, b) => a > b ? a : b));
        return RefreshIndicator(
          onRefresh: () async => _recarregar(),
          child: ListView(padding: const EdgeInsets.all(12), children: [
            Wrap(spacing: 8, runSpacing: 8, children: [
              for (final c in [
                ('A receber (total)', r['aReceber'] as num, null),
                ('Vencido (${r['titulosVencidos']})', r['vencido'] as num, cs.error),
                ('Recebido no mês', r['recebidoNoMes'] as num, null),
                ('Despesas pagas no mês', r['despesasPagasNoMes'] as num, null),
                ('Despesas em aberto', r['despesasEmAberto'] as num, null),
                ('Saldo do mês', r['saldoDoMes'] as num, (r['saldoDoMes'] as num) < 0 ? cs.error : Colors.green.shade700),
              ])
                SizedBox(width: 220, child: _card(context, c.$1, c.$2, cor: c.$3)),
            ]),
            const SizedBox(height: 16),
            Text('Últimos 6 meses', style: Theme.of(context).textTheme.titleMedium),
            for (final m in serie)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 6),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('${(m['mes'] as String).substring(5)}/${(m['mes'] as String).substring(0, 4)}'),
                  LinearProgressIndicator(value: (m['recebido'] as num).toDouble() / maximo, color: Colors.green.shade700, minHeight: 8),
                  const SizedBox(height: 2),
                  LinearProgressIndicator(value: (m['despesas'] as num).toDouble() / maximo, color: cs.error, minHeight: 8),
                  Text('Recebido ${Formatos.moeda(m['recebido'] as num)} · Despesas ${Formatos.moeda(m['despesas'] as num)}', style: Theme.of(context).textTheme.bodySmall),
                ]),
              ),
          ]),
        );
      },
    );
  }
}
