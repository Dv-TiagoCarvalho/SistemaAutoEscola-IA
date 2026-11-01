import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/formatos.dart';
import '../core/sessao.dart';
import 'comum.dart';

/// Autoescolas clientes (SUPER_ADMIN).
class AutoescolasPage extends ConsumerStatefulWidget {
  const AutoescolasPage({super.key});
  @override
  ConsumerState<AutoescolasPage> createState() => _AutoescolasPageState();
}

class _AutoescolasPageState extends ConsumerState<AutoescolasPage> {
  late Future<List<dynamic>> _lista = _carregar();

  Future<List<dynamic>> _carregar() async {
    final r = await ref.read(apiProvider).get('/plataforma/autoescolas', query: {'tamanho': 100});
    return r['itens'] as List<dynamic>;
  }

  void _recarregar() => setState(() => _lista = _carregar());

  Future<void> _alternarStatus(Map<String, dynamic> a) async {
    final ativo = a['ativo'] == true;
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: Text(ativo ? 'Suspender autoescola?' : 'Reativar autoescola?'),
        content: Text(ativo ? 'Ninguém da ${a['nome']} conseguirá entrar no sistema.' : 'A equipe da ${a['nome']} voltará a ter acesso.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(ativo ? 'Suspender' : 'Reativar')),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    try {
      await ref.read(apiProvider).patch('/plataforma/autoescolas/${a['id']}/status', {'ativo': !ativo});
      _recarregar();
    } catch (e) {
      if (mounted) mostrarErro(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          final ok = await showDialog<bool>(context: context, builder: (_) => const _NovaAutoescolaDialog());
          if (ok == true) _recarregar();
        },
        icon: const Icon(Icons.add_business_outlined),
        label: const Text('Nova autoescola'),
      ),
      body: FutureBuilder(
        future: _lista,
        builder: (context, snap) {
          if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
          if (!snap.hasData) return const Carregando();
          final itens = snap.data!;
          if (itens.isEmpty) return const Center(child: Text('Nenhuma autoescola cadastrada.'));
          return ListView.separated(
            padding: const EdgeInsets.only(bottom: 88),
            itemCount: itens.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (_, i) {
              final a = itens[i] as Map<String, dynamic>;
              final ativo = a['ativo'] == true;
              return ListTile(
                title: Text(a['nome'] as String),
                subtitle: Text('${Formatos.cnpj(a['cnpj'] as String?)} · ${a['cidade']}/${a['uf']}'
                    ' · desde ${Formatos.data(DateTime.tryParse('${a['criadoEm']}'))}'),
                trailing: Chip(
                  label: Text(ativo ? 'Ativa' : 'Suspensa'),
                  backgroundColor: ativo ? Colors.green.shade50 : Colors.red.shade50,
                ),
                onTap: () => _alternarStatus(a),
              );
            },
          );
        },
      ),
    );
  }
}

class _NovaAutoescolaDialog extends ConsumerStatefulWidget {
  const _NovaAutoescolaDialog();
  @override
  ConsumerState<_NovaAutoescolaDialog> createState() => _NovaAutoescolaDialogState();
}

class _NovaAutoescolaDialogState extends ConsumerState<_NovaAutoescolaDialog> {
  final _form = GlobalKey<FormState>();
  final _c = {for (final k in ['nome', 'cnpj', 'uf', 'cidade', 'telefone', 'email', 'adminNome', 'adminEmail', 'adminSenha']) k: TextEditingController()};
  bool _salvando = false;
  Object? _erro;

  Future<void> _salvar() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _salvando = true;
      _erro = null;
    });
    String? t(String k) => _c[k]!.text.trim().isEmpty ? null : _c[k]!.text.trim();
    try {
      await ref.read(apiProvider).post('/plataforma/autoescolas', {
        'nome': t('nome'),
        'cnpj': t('cnpj'),
        'uf': t('uf')?.toUpperCase(),
        'cidade': t('cidade'),
        'telefone': t('telefone'),
        'email': t('email'),
        'admin': {'nome': t('adminNome'), 'email': t('adminEmail'), 'senha': _c['adminSenha']!.text},
      });
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      setState(() => _erro = e);
    } finally {
      if (mounted) setState(() => _salvando = false);
    }
  }

  Widget _campo(String k, String label, {bool obrigatorio = true, bool senha = false, String? erroApi}) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: TextFormField(
          controller: _c[k],
          obscureText: senha,
          decoration: InputDecoration(labelText: label, errorText: erroCampo(_erro, erroApi ?? k)),
          validator: (v) => obrigatorio && (v == null || v.trim().isEmpty) ? 'Obrigatório.' : null,
        ),
      );

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Nova autoescola'),
      content: SizedBox(
        width: 480,
        child: Form(
          key: _form,
          child: SingleChildScrollView(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
              _campo('nome', 'Nome'),
              _campo('cnpj', 'CNPJ'),
              Row(children: [
                SizedBox(width: 90, child: _campo('uf', 'UF')),
                const SizedBox(width: 12),
                Expanded(child: _campo('cidade', 'Cidade')),
              ]),
              _campo('telefone', 'Telefone', obrigatorio: false),
              _campo('email', 'E-mail da autoescola', obrigatorio: false),
              const SizedBox(height: 8),
              Text('Administrador inicial', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 12),
              _campo('adminNome', 'Nome', erroApi: 'admin.nome'),
              _campo('adminEmail', 'E-mail de login', erroApi: 'admin.email'),
              _campo('adminSenha', 'Senha inicial', senha: true, erroApi: 'admin.senha'),
              if (_erro != null) Text('$_erro', style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ]),
          ),
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')),
        FilledButton(onPressed: _salvando ? null : _salvar, child: const Text('Cadastrar')),
      ],
    );
  }
}
