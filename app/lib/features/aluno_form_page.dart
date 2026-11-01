import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/api.dart';
import '../core/documentos.dart';
import '../core/formatos.dart';
import '../core/sessao.dart';
import 'comum.dart';

/// Cadastro e edição de aluno. [id] nulo = novo.
class AlunoFormPage extends ConsumerStatefulWidget {
  const AlunoFormPage({super.key, this.id});
  final int? id;
  @override
  ConsumerState<AlunoFormPage> createState() => _AlunoFormPageState();
}

class _AlunoFormPageState extends ConsumerState<AlunoFormPage> {
  final _form = GlobalKey<FormState>();
  final _c = {
    for (final k in ['nome', 'cpf', 'rg', 'telefone', 'email', 'cep', 'logradouro', 'numero', 'complemento', 'bairro', 'cidade', 'uf', 'nascimento'])
      k: TextEditingController()
  };
  String? _nascimentoIso;
  bool _carregando = false;
  bool _salvando = false;
  ApiErro? _erro;

  @override
  void initState() {
    super.initState();
    if (widget.id != null) {
      _carregando = true;
      ref.read(apiProvider).get('/alunos/${widget.id}').then((a) {
        final m = a as Map<String, dynamic>;
        for (final k in _c.keys) {
          if (k == 'nascimento' || m[k] == null) continue;
          _c[k]!.text = k == 'cpf' ? Formatos.cpf('${m[k]}') : k == 'telefone' ? Formatos.telefone('${m[k]}') : '${m[k]}';
        }
        _nascimentoIso = m['dataNascimento'] as String?;
        if (_nascimentoIso != null) _c['nascimento']!.text = Formatos.data(DateTime.parse(_nascimentoIso!));
        if (mounted) setState(() => _carregando = false);
      }).catchError((e) {
        if (mounted) {
          setState(() => _carregando = false);
          mostrarErro(context, e);
        }
      });
    }
  }

  @override
  void dispose() {
    for (final c in _c.values) {
      c.dispose();
    }
    super.dispose();
  }

  String? _obrig(String? v) => (v == null || v.trim().isEmpty) ? 'Obrigatório.' : null;

  Widget _t(String k, String rotulo,
      {String? Function(String?)? validar, List<TextInputFormatter>? fmt, TextInputType? teclado, int? max}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextFormField(
        controller: _c[k],
        keyboardType: teclado,
        inputFormatters: fmt,
        maxLength: max,
        validator: validar,
        decoration: InputDecoration(labelText: rotulo, errorText: _erro?.campos[k], counterText: ''),
      ),
    );
  }

  Future<void> _salvar() async {
    setState(() => _erro = null);
    if (!_form.currentState!.validate()) return;
    String? n(String k) {
      final v = _c[k]!.text.trim();
      return v.isEmpty ? null : v;
    }

    String? so(String k) => n(k)?.replaceAll(RegExp(r'\D'), '');
    final corpo = {
      'nome': n('nome'),
      'cpf': so('cpf'),
      'rg': n('rg'),
      'dataNascimento': _nascimentoIso,
      'telefone': so('telefone'),
      'email': n('email'),
      'cep': so('cep') ?? '',
      'logradouro': n('logradouro'),
      'numero': n('numero'),
      'complemento': n('complemento'),
      'bairro': n('bairro'),
      'cidade': n('cidade'),
      'uf': n('uf')?.toUpperCase() ?? '',
    };
    setState(() => _salvando = true);
    try {
      final api = ref.read(apiProvider);
      final r = widget.id == null ? await api.post('/alunos', corpo) : await api.put('/alunos/${widget.id}', corpo);
      if (!mounted) return;
      mostrarOk(context, 'Aluno salvo.');
      if (widget.id == null) {
        context.pushReplacement('/alunos/${r['id']}');
      } else {
        context.pop();
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _erro = ApiErro.de(e);
          _salvando = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.id == null ? 'Novo aluno' : 'Editar aluno')),
      body: _carregando
          ? const Carregando()
          : Form(
              key: _form,
              child: ListView(padding: const EdgeInsets.all(16), children: [
                _t('nome', 'Nome completo', validar: _obrig, max: 120),
                _t('cpf', 'CPF',
                    validar: (v) => cpfValido(v ?? '') ? null : 'CPF inválido.',
                    fmt: [Mascara.cpf],
                    teclado: TextInputType.number),
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: TextFormField(
                    controller: _c['nascimento'],
                    readOnly: true,
                    validator: _obrig,
                    decoration: InputDecoration(
                        labelText: 'Data de nascimento',
                        errorText: _erro?.campos['dataNascimento'],
                        suffixIcon: const Icon(Icons.calendar_today_outlined)),
                    onTap: () async {
                      final d = await showDatePicker(
                        context: context,
                        initialDate: _nascimentoIso == null ? DateTime(2005) : DateTime.parse(_nascimentoIso!),
                        firstDate: DateTime(1900),
                        lastDate: DateTime.now(),
                      );
                      if (d != null) {
                        _nascimentoIso =
                            '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
                        _c['nascimento']!.text = Formatos.data(d);
                      }
                    },
                  ),
                ),
                _t('rg', 'RG (opcional)', max: 20),
                _t('telefone', 'Telefone / WhatsApp', validar: _obrig, fmt: [MascaraTelefone()], teclado: TextInputType.phone),
                _t('email', 'E-mail (opcional)', teclado: TextInputType.emailAddress, max: 160),
                const Divider(),
                _t('cep', 'CEP (opcional)', fmt: [Mascara.cep], teclado: TextInputType.number),
                _t('logradouro', 'Rua / avenida', max: 150),
                Row(children: [
                  Expanded(child: _t('numero', 'Número', max: 10)),
                  const SizedBox(width: 12),
                  Expanded(flex: 2, child: _t('complemento', 'Complemento', max: 60)),
                ]),
                _t('bairro', 'Bairro', max: 80),
                Row(children: [
                  Expanded(flex: 3, child: _t('cidade', 'Cidade', max: 100)),
                  const SizedBox(width: 12),
                  Expanded(child: _t('uf', 'UF', max: 2)),
                ]),
                if (_erro != null)
                  Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Text(_erro!.mensagem, style: TextStyle(color: Theme.of(context).colorScheme.error))),
                FilledButton(
                  onPressed: _salvando ? null : _salvar,
                  child: _salvando
                      ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Text('Salvar'),
                ),
              ]),
            ),
    );
  }
}
