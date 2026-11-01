import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/formatos.dart';
import '../core/sessao.dart';
import 'comum.dart';

const _perfisEquipe = ['ADMIN', 'SECRETARIA', 'INSTRUTOR'];

/// Equipe da autoescola (ADMIN).
class UsuariosPage extends ConsumerStatefulWidget {
  const UsuariosPage({super.key});
  @override
  ConsumerState<UsuariosPage> createState() => _UsuariosPageState();
}

class _UsuariosPageState extends ConsumerState<UsuariosPage> {
  late Future<List<dynamic>> _lista = _carregar();

  Future<List<dynamic>> _carregar() async {
    final r = await ref.read(apiProvider).get('/usuarios', query: {'tamanho': 100});
    return r['itens'] as List<dynamic>;
  }

  void _recarregar() => setState(() => _lista = _carregar());

  Future<void> _abrirForm([Map<String, dynamic>? usuario]) async {
    final salvou = await showDialog<bool>(context: context, builder: (_) => _UsuarioDialog(usuario: usuario));
    if (salvou == true) _recarregar();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _abrirForm(),
        icon: const Icon(Icons.person_add_alt),
        label: const Text('Novo usuário'),
      ),
      body: FutureBuilder(
        future: _lista,
        builder: (context, snap) {
          if (snap.hasError) return ErroComRetry(erro: snap.error!, tentarDeNovo: _recarregar);
          if (!snap.hasData) return const Carregando();
          final itens = snap.data!;
          if (itens.isEmpty) return const Center(child: Text('Nenhum usuário.'));
          return RefreshIndicator(
            onRefresh: () async => _recarregar(),
            child: ListView.separated(
              padding: const EdgeInsets.only(bottom: 88),
              itemCount: itens.length,
              separatorBuilder: (_, __) => const Divider(height: 1),
              itemBuilder: (_, i) {
                final u = itens[i] as Map<String, dynamic>;
                final ativo = u['ativo'] == true;
                return ListTile(
                  leading: CircleAvatar(child: Text((u['nome'] as String).characters.first.toUpperCase())),
                  title: Text(u['nome'] as String, style: ativo ? null : const TextStyle(decoration: TextDecoration.lineThrough)),
                  subtitle: Text('${u['email']} · ${Formatos.perfil(u['perfil'] as String)}${ativo ? '' : ' · inativo'}'),
                  trailing: const Icon(Icons.edit_outlined),
                  onTap: () => _abrirForm(u),
                );
              },
            ),
          );
        },
      ),
    );
  }
}

class _UsuarioDialog extends ConsumerStatefulWidget {
  const _UsuarioDialog({this.usuario});
  final Map<String, dynamic>? usuario;
  @override
  ConsumerState<_UsuarioDialog> createState() => _UsuarioDialogState();
}

class _UsuarioDialogState extends ConsumerState<_UsuarioDialog> {
  final _form = GlobalKey<FormState>();
  late final _nome = TextEditingController(text: widget.usuario?['nome'] as String?);
  late final _email = TextEditingController(text: widget.usuario?['email'] as String?);
  final _senha = TextEditingController();
  late String _perfil = (widget.usuario?['perfil'] as String?) ?? 'SECRETARIA';
  late bool _ativo = (widget.usuario?['ativo'] as bool?) ?? true;
  bool _salvando = false;
  Object? _erro;

  bool get _novo => widget.usuario == null;

  Future<void> _salvar() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _salvando = true;
      _erro = null;
    });
    final api = ref.read(apiProvider);
    try {
      if (_novo) {
        await api.post('/usuarios', {'nome': _nome.text, 'email': _email.text, 'senha': _senha.text, 'perfil': _perfil});
      } else {
        final id = widget.usuario!['id'];
        await api.put('/usuarios/$id', {'nome': _nome.text, 'perfil': _perfil, 'ativo': _ativo});
        if (_senha.text.isNotEmpty) await api.put('/usuarios/$id/senha', {'novaSenha': _senha.text});
      }
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      setState(() => _erro = e);
    } finally {
      if (mounted) setState(() => _salvando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(_novo ? 'Novo usuário' : 'Editar usuário'),
      content: SizedBox(
        width: 420,
        child: Form(
          key: _form,
          child: SingleChildScrollView(
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              TextFormField(
                controller: _nome,
                decoration: InputDecoration(labelText: 'Nome', errorText: erroCampo(_erro, 'nome')),
                validator: (v) => (v == null || v.trim().isEmpty) ? 'Informe o nome.' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _email,
                enabled: _novo,
                keyboardType: TextInputType.emailAddress,
                decoration: InputDecoration(labelText: 'E-mail', errorText: erroCampo(_erro, 'email')),
                validator: (v) => (v == null || !v.contains('@')) ? 'Informe um e-mail válido.' : null,
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: _perfil,
                decoration: const InputDecoration(labelText: 'Perfil'),
                items: [for (final p in _perfisEquipe) DropdownMenuItem(value: p, child: Text(Formatos.perfil(p)))],
                onChanged: (v) => setState(() => _perfil = v!),
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _senha,
                obscureText: true,
                decoration: InputDecoration(
                  labelText: _novo ? 'Senha inicial' : 'Nova senha (deixe em branco para manter)',
                  helperText: 'Mínimo 8 caracteres, com letra e número.',
                  errorText: erroCampo(_erro, 'senha') ?? erroCampo(_erro, 'novaSenha'),
                ),
                validator: (v) {
                  if (!_novo && (v == null || v.isEmpty)) return null;
                  if (v == null || v.length < 8 || !RegExp(r'\d').hasMatch(v) || !RegExp(r'[A-Za-z]').hasMatch(v)) {
                    return 'Mínimo 8 caracteres, com letra e número.';
                  }
                  return null;
                },
              ),
              if (!_novo)
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Ativo'),
                  value: _ativo,
                  onChanged: (v) => setState(() => _ativo = v),
                ),
              if (_erro != null) ...[
                const SizedBox(height: 8),
                Text('$_erro', style: TextStyle(color: Theme.of(context).colorScheme.error)),
              ],
            ]),
          ),
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')),
        FilledButton(onPressed: _salvando ? null : _salvar, child: const Text('Salvar')),
      ],
    );
  }
}
