import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/documentos.dart';
import '../core/formatos.dart';
import '../core/sessao.dart';
import 'comum.dart';
import 'pacotes_page.dart';
import 'usuarios_page.dart';

/// Cadastros de apoio: pacotes, instrutores, veículos e (para o ADMIN) equipe.
class CadastrosPage extends ConsumerWidget {
  const CadastrosPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final admin = ref.watch(sessaoProvider).valueOrNull?.perfil == 'ADMIN';
    final abas = [
      ('Pacotes', const PacotesPage()),
      ('Instrutores', const InstrutoresPage()),
      ('Veículos', const VeiculosPage()),
      if (admin) ('Equipe', const UsuariosPage()),
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

// ---------------- Instrutores ----------------

class InstrutoresPage extends ConsumerStatefulWidget {
  const InstrutoresPage({super.key});
  @override
  ConsumerState<InstrutoresPage> createState() => _InstrutoresPageState();
}

class _InstrutoresPageState extends ConsumerState<InstrutoresPage> {
  late Future<List<dynamic>> _lista = _carregar();

  Future<List<dynamic>> _carregar() async =>
      (await ref.read(apiProvider).get('/instrutores', query: {'incluirInativos': true})) as List<dynamic>;
  void _recarregar() => setState(() => _lista = _carregar());

  Future<void> _form([Map<String, dynamic>? i]) async {
    final api = ref.read(apiProvider);
    final novo = i == null;
    final ok = await abrirForm(context,
        titulo: novo ? 'Novo instrutor' : 'Editar instrutor',
        campos: [
          Campo('nome', 'Nome', obrigatorio: true, inicial: i?['nome'] as String?, max: 120),
          Campo('telefone', 'Telefone', inicial: i?['telefone'] as String?, formatters: [MascaraTelefone()]),
          Campo('cnh', 'Validade da CNH', tipo: TipoCampo.data, inicial: i?['cnhValidade'] as String?),
          Campo('categorias', 'Categorias que ensina',
              tipo: TipoCampo.multi,
              obrigatorio: true,
              inicial: i == null ? null : (i['categorias'] as List).join(','),
              opcoes: {for (final c in Formatos.categorias) c: c}),
          if (novo) const Campo('email', 'E-mail de acesso ao app (opcional)'),
          if (novo) const Campo('senha', 'Senha de acesso (se informou e-mail)', tipo: TipoCampo.senha),
        ],
        enviar: (v) async {
          final corpo = {
            'nome': v['nome'],
            'telefone': v['telefone']!.replaceAll(RegExp(r'\D'), ''),
            'cnhValidade': v['cnh']!.isEmpty ? null : v['cnh'],
            'categorias': v['categorias']!.split(','),
            if (novo) 'email': v['email']!.isEmpty ? null : v['email'],
            if (novo) 'senha': v['senha']!.isEmpty ? null : v['senha'],
          };
          novo ? await api.post('/instrutores', corpo) : await api.put('/instrutores/${i['id']}', corpo);
        });
    if (ok) _recarregar();
  }

  Future<void> _alternar(Map<String, dynamic> i) async {
    try {
      await ref.read(apiProvider).patch('/instrutores/${i['id']}/status', {'ativo': i['ativo'] != true});
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
          ? FloatingActionButton.extended(onPressed: _form, icon: const Icon(Icons.add), label: const Text('Novo instrutor'))
          : null,
      body: FutureBuilder(
        future: _lista,
        builder: (context, snap) {
          if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
          if (!snap.hasData) return const Carregando();
          final itens = snap.data!.cast<Map<String, dynamic>>();
          if (itens.isEmpty) return const Center(child: Text('Nenhum instrutor cadastrado.'));
          return RefreshIndicator(
            onRefresh: () async => _recarregar(),
            child: ListView.separated(
              padding: const EdgeInsets.only(bottom: 88),
              itemCount: itens.length,
              separatorBuilder: (_, __) => const Divider(height: 1),
              itemBuilder: (_, k) {
                final i = itens[k];
                final ativo = i['ativo'] == true;
                return ListTile(
                  leading: CircleAvatar(child: Text((i['nome'] as String).characters.first.toUpperCase())),
                  title: Text(i['nome'] as String),
                  subtitle: Text(
                      'Categorias: ${(i['categorias'] as List).join(', ')}${i['temAcesso'] == true ? ' · tem acesso ao app' : ''}${ativo ? '' : ' · inativo'}'),
                  trailing: admin ? Switch(value: ativo, onChanged: (_) => _alternar(i)) : null,
                  onTap: admin ? () => _form(i) : null,
                );
              },
            ),
          );
        },
      ),
    );
  }
}

// ---------------- Veículos ----------------

class VeiculosPage extends ConsumerStatefulWidget {
  const VeiculosPage({super.key});
  @override
  ConsumerState<VeiculosPage> createState() => _VeiculosPageState();
}

class _VeiculosPageState extends ConsumerState<VeiculosPage> {
  late Future<List<dynamic>> _lista = _carregar();

  Future<List<dynamic>> _carregar() async =>
      (await ref.read(apiProvider).get('/veiculos', query: {'incluirInativos': true})) as List<dynamic>;
  void _recarregar() => setState(() => _lista = _carregar());

  Future<void> _form([Map<String, dynamic>? v]) async {
    final api = ref.read(apiProvider);
    final ok = await abrirForm(context,
        titulo: v == null ? 'Novo veículo' : 'Editar veículo',
        campos: [
          Campo('placa', 'Placa', obrigatorio: true, inicial: v?['placa'] as String?, max: 8),
          Campo('modelo', 'Modelo', obrigatorio: true, inicial: v?['modelo'] as String?, max: 80),
          Campo('ano', 'Ano', tipo: TipoCampo.numero, obrigatorio: true, inicial: v == null ? null : '${v['ano']}', max: 4),
          Campo('categoria', 'Categoria',
              tipo: TipoCampo.escolha,
              obrigatorio: true,
              inicial: v?['categoria'] as String?,
              opcoes: {for (final c in Formatos.categorias) c: c}),
          Campo('km', 'Km atual', tipo: TipoCampo.numero, inicial: v == null ? null : '${v['kmAtual']}', max: 7),
          Campo('lic', 'Validade do licenciamento', tipo: TipoCampo.data, inicial: v?['licenciamentoValidade'] as String?),
          Campo('seg', 'Validade do seguro', tipo: TipoCampo.data, inicial: v?['seguroValidade'] as String?),
          Campo('rev', 'Próxima revisão (km)', tipo: TipoCampo.numero, inicial: v?['proximaRevisaoKm'] == null ? null : '${v?['proximaRevisaoKm']}', max: 7),
        ],
        enviar: (d) async {
          final corpo = {
            'placa': d['placa'],
            'modelo': d['modelo'],
            'ano': int.tryParse(d['ano']!),
            'categoria': d['categoria'],
            'kmAtual': int.tryParse(d['km']!),
            'licenciamentoValidade': d['lic']!.isEmpty ? null : d['lic'],
            'seguroValidade': d['seg']!.isEmpty ? null : d['seg'],
            'proximaRevisaoKm': int.tryParse(d['rev']!),
          };
          v == null ? await api.post('/veiculos', corpo) : await api.put('/veiculos/${v['id']}', corpo);
        });
    if (ok) _recarregar();
  }

  Future<void> _manutencoes(Map<String, dynamic> v) async {
    final api = ref.read(apiProvider);
    final admin = ref.read(sessaoProvider).valueOrNull?.perfil == 'ADMIN';
    try {
      final itens = ((await api.get('/veiculos/${v['id']}/manutencoes')) as List).cast<Map<String, dynamic>>();
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: Text('Manutenções · ${v['placa']}'),
          content: SizedBox(
            width: 420,
            child: itens.isEmpty
                ? const Text('Nenhuma manutenção registrada.')
                : ListView(shrinkWrap: true, children: [
                    for (final m in itens)
                      ListTile(
                        dense: true,
                        title: Text('${m['descricao']} · ${Formatos.moeda(m['valor'] as num)}'),
                        subtitle: Text('${Formatos.data(DateTime.parse(m['data'] as String))}${m['km'] != null ? ' · ${m['km']} km' : ''}'),
                      ),
                  ]),
          ),
          actions: [
            if (admin)
              TextButton(
                onPressed: () async {
                  Navigator.pop(ctx);
                  final ok = await abrirForm(context,
                      titulo: 'Registrar manutenção',
                      campos: [
                        Campo('data', 'Data', tipo: TipoCampo.data, obrigatorio: true, inicial: DateTime.now().toIso8601String().substring(0, 10)),
                        const Campo('descricao', 'O que foi feito', obrigatorio: true, max: 200),
                        const Campo('km', 'Km do veículo', tipo: TipoCampo.numero, max: 7),
                        const Campo('valor', 'Valor (R\$)', tipo: TipoCampo.valor, obrigatorio: true, inicial: '0'),
                      ],
                      enviar: (d) async => api.post('/veiculos/${v['id']}/manutencoes',
                          {'data': d['data'], 'descricao': d['descricao'], 'km': int.tryParse(d['km']!), 'valor': num.tryParse(d['valor']!)}));
                  if (ok) _recarregar();
                },
                child: const Text('Registrar nova'),
              ),
            FilledButton(onPressed: () => Navigator.pop(ctx), child: const Text('Fechar')),
          ],
        ),
      );
    } catch (e) {
      if (mounted) mostrarErro(context, e);
    }
  }

  Future<void> _alternar(Map<String, dynamic> v) async {
    try {
      await ref.read(apiProvider).patch('/veiculos/${v['id']}/status', {'ativo': v['ativo'] != true});
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
          ? FloatingActionButton.extended(onPressed: _form, icon: const Icon(Icons.add), label: const Text('Novo veículo'))
          : null,
      body: FutureBuilder(
        future: _lista,
        builder: (context, snap) {
          if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
          if (!snap.hasData) return const Carregando();
          final itens = snap.data!.cast<Map<String, dynamic>>();
          if (itens.isEmpty) return const Center(child: Text('Nenhum veículo cadastrado.'));
          return RefreshIndicator(
            onRefresh: () async => _recarregar(),
            child: ListView.separated(
              padding: const EdgeInsets.only(bottom: 88),
              itemCount: itens.length,
              separatorBuilder: (_, __) => const Divider(height: 1),
              itemBuilder: (_, k) {
                final v = itens[k];
                final ativo = v['ativo'] == true;
                return ListTile(
                  leading: CircleAvatar(child: Text(v['categoria'] as String)),
                  title: Text('${v['placa']} · ${v['modelo']}'),
                  subtitle: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text('${v['ano']} · ${v['kmAtual']} km${ativo ? '' : ' · inativo'}'),
                    for (final al in (v['alertas'] as List? ?? []))
                      Text('⚠ $al', style: TextStyle(color: Theme.of(context).colorScheme.error)),
                  ]),
                  trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                    IconButton(tooltip: 'Manutenções', icon: const Icon(Icons.build_outlined), onPressed: () => _manutencoes(v)),
                    if (admin) Switch(value: ativo, onChanged: (_) => _alternar(v)),
                  ]),
                  onTap: admin ? () => _form(v) : null,
                );
              },
            ),
          );
        },
      ),
    );
  }
}
