import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/formatos.dart';
import '../core/sessao.dart';
import 'comum.dart';

/// Início: painel de números para a equipe; atalhos simples para instrutor e aluno.
class InicioPage extends ConsumerStatefulWidget {
  const InicioPage({super.key});
  @override
  ConsumerState<InicioPage> createState() => _InicioPageState();
}

class _InicioPageState extends ConsumerState<InicioPage> {
  Future<Map<String, dynamic>>? _f;

  bool get _equipe {
    final p = ref.read(sessaoProvider).valueOrNull?.perfil;
    return p == 'ADMIN' || p == 'SECRETARIA';
  }

  @override
  void initState() {
    super.initState();
    if (_equipe) _f = _carregar();
  }

  Future<Map<String, dynamic>> _carregar() async => (await ref.read(apiProvider).get('/dashboard')) as Map<String, dynamic>;
  void _recarregar() => setState(() => _f = _carregar());

  Future<void> _exportar(String caminho, String nome, {Map<String, dynamic>? query}) async {
    try {
      final bytes = await ref.read(apiProvider).baixar(caminho);
      await salvarArquivo(nome, bytes);
    } catch (e) {
      if (mounted) mostrarErro(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final u = ref.watch(sessaoProvider).valueOrNull;
    final saudacao = [
      Text('Olá, ${u?.nome.split(' ').first ?? ''}!', style: Theme.of(context).textTheme.headlineSmall),
      const SizedBox(height: 4),
      Text('Você entrou como ${Formatos.perfil(u?.perfil ?? '')}.'),
      const SizedBox(height: 16),
    ];
    if (!_equipe) {
      return ListView(padding: const EdgeInsets.all(24), children: [
        ...saudacao,
        Card(child: ListTile(leading: const Icon(Icons.calendar_month_outlined), title: const Text('Minha agenda'), onTap: () => context.go('/agenda'))),
        if (u?.perfil == 'ALUNO')
          Card(child: ListTile(leading: const Icon(Icons.quiz_outlined), title: const Text('Fazer simulado'), onTap: () => context.go('/teorico'))),
      ]);
    }
    return RefreshIndicator(
      onRefresh: () async => _recarregar(),
      child: FutureBuilder<Map<String, dynamic>>(
        future: _f,
        builder: (context, snap) {
          if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
          if (!snap.hasData) return const Carregando();
          final d = snap.data!;
          final alertas = (d['veiculosComAlerta'] as List).cast<Map<String, dynamic>>();
          return ListView(padding: const EdgeInsets.all(16), children: [
            ...saudacao,
            Wrap(spacing: 12, runSpacing: 12, children: [
              _Kpi('Alunos ativos', '${d['alunosAtivos']}', Icons.school_outlined, () => context.go('/alunos')),
              _Kpi('Aulas hoje', '${d['aulasHoje']}', Icons.today_outlined, () => context.go('/agenda')),
              _Kpi('Aulas na semana', '${d['aulasAgendadasSemana']}', Icons.date_range_outlined, () => context.go('/agenda')),
              _Kpi('Exames em 7 dias', '${d['examesProximos7Dias']}', Icons.fact_check_outlined, () => context.go('/exames')),
              _Kpi('Recebido no mês', Formatos.moeda(d['recebidoNoMes'] as num), Icons.trending_up, () => context.go('/financeiro')),
              _Kpi('Saldo do mês', Formatos.moeda(d['saldoDoMes'] as num), Icons.account_balance_wallet_outlined, () => context.go('/financeiro')),
              _Kpi('Vencido (${d['titulosVencidos']})', Formatos.moeda(d['valorVencido'] as num), Icons.warning_amber_outlined, () => context.go('/financeiro'),
                  alerta: (d['titulosVencidos'] as num) > 0),
            ]),
            if (alertas.isNotEmpty) ...[
              const SizedBox(height: 16),
              Text('Veículos com pendência', style: Theme.of(context).textTheme.titleMedium),
              for (final v in alertas)
                ListTile(
                  leading: const Icon(Icons.directions_car_outlined),
                  title: Text(v['placa'] as String),
                  subtitle: Text((v['alertas'] as List).join(' · ')),
                  onTap: () => context.go('/cadastros'),
                ),
            ],
            const SizedBox(height: 16),
            Text('Relatórios', style: Theme.of(context).textTheme.titleMedium),
            Wrap(spacing: 8, children: [
              ActionChip(avatar: const Icon(Icons.download, size: 18), label: const Text('Alunos (CSV)'), onPressed: () => _exportar('/relatorios/alunos.csv', 'alunos.csv')),
              ActionChip(avatar: const Icon(Icons.download, size: 18), label: const Text('Títulos (CSV)'), onPressed: () => _exportar('/relatorios/titulos.csv', 'titulos.csv')),
              ActionChip(avatar: const Icon(Icons.download, size: 18), label: const Text('Aulas do mês (CSV)'), onPressed: () => _exportar('/relatorios/aulas.csv', 'aulas.csv')),
              ActionChip(avatar: const Icon(Icons.picture_as_pdf_outlined, size: 18), label: const Text('Financeiro (PDF)'), onPressed: () => _exportar('/relatorios/financeiro.pdf', 'financeiro.pdf')),
            ]),
          ]);
        },
      ),
    );
  }
}

class _Kpi extends StatelessWidget {
  const _Kpi(this.titulo, this.valor, this.icone, this.aoToque, {this.alerta = false});
  final String titulo;
  final String valor;
  final IconData icone;
  final VoidCallback aoToque;
  final bool alerta;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 170,
      child: Card(
        color: alerta ? Colors.red.shade50 : null,
        child: InkWell(
          onTap: aoToque,
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Icon(icone, color: alerta ? Colors.red : Theme.of(context).colorScheme.primary),
              const SizedBox(height: 8),
              Text(valor, style: Theme.of(context).textTheme.titleLarge),
              Text(titulo, style: Theme.of(context).textTheme.bodySmall),
            ]),
          ),
        ),
      ),
    );
  }
}
