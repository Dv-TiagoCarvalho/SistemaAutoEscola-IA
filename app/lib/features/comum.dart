import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';

import '../core/api.dart';
import '../core/documentos.dart';
import '../core/formatos.dart';

/// Mostra erro em snackbar.
void mostrarErro(BuildContext context, Object e) {
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e is String ? e : ApiErro.de(e).mensagem)));
}

void mostrarOk(BuildContext context, String msg) {
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
}

/// Mensagem de erro de campo vinda da validação do back-end.
String? erroCampo(Object? erro, String campo) => erro is ApiErro ? erro.campos[campo] : null;

class Carregando extends StatelessWidget {
  const Carregando({super.key});
  @override
  Widget build(BuildContext context) => const Center(child: CircularProgressIndicator());
}

class ErroComRetry extends StatelessWidget {
  const ErroComRetry({super.key, required this.erro, required this.tentarDeNovo});
  final Object erro;
  final VoidCallback tentarDeNovo;
  @override
  Widget build(BuildContext context) => Center(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Text(ApiErro.de(erro).mensagem),
          const SizedBox(height: 12),
          OutlinedButton(onPressed: tentarDeNovo, child: const Text('Tentar de novo')),
        ]),
      );
}

// ---------- Formulário genérico em diálogo ----------

enum TipoCampo { texto, multilinha, numero, valor, data, hora, senha, escolha, multi }

class Campo {
  const Campo(this.chave, this.rotulo,
      {this.tipo = TipoCampo.texto, this.inicial, this.obrigatorio = false, this.opcoes, this.formatters, this.max});
  final String chave;
  final String rotulo;
  final TipoCampo tipo;
  final String? inicial; // data: ISO yyyy-MM-dd; hora: HH:mm; multi: valores separados por vírgula
  final bool obrigatorio;
  final Map<String, String>? opcoes; // escolha/multi: valor -> rótulo
  final List<TextInputFormatter>? formatters;
  final int? max;
}

/// Abre um diálogo de formulário. [enviar] recebe os valores (data em ISO, valor com ponto decimal).
/// Retorna true se salvou.
Future<bool> abrirForm(BuildContext context,
    {required String titulo,
    required List<Campo> campos,
    required Future<void> Function(Map<String, String> valores) enviar,
    String rotuloBotao = 'Salvar'}) async {
  final r = await showDialog<bool>(
    context: context,
    builder: (_) => _FormDialog(titulo: titulo, campos: campos, enviar: enviar, rotuloBotao: rotuloBotao),
  );
  return r == true;
}

class _FormDialog extends StatefulWidget {
  const _FormDialog({required this.titulo, required this.campos, required this.enviar, required this.rotuloBotao});
  final String titulo;
  final List<Campo> campos;
  final Future<void> Function(Map<String, String>) enviar;
  final String rotuloBotao;
  @override
  State<_FormDialog> createState() => _FormDialogState();
}

class _FormDialogState extends State<_FormDialog> {
  final _form = GlobalKey<FormState>();
  final _ctrl = <String, TextEditingController>{};
  final _escolhas = <String, String?>{};
  final _datas = <String, String?>{};
  final _multi = <String, Set<String>>{};
  bool _salvando = false;
  ApiErro? _erro;

  @override
  void initState() {
    super.initState();
    for (final c in widget.campos) {
      if (c.tipo == TipoCampo.escolha) {
        _escolhas[c.chave] = c.inicial;
      } else if (c.tipo == TipoCampo.multi) {
        _multi[c.chave] = {...?c.inicial?.split(',').where((e) => e.isNotEmpty)};
      } else if (c.tipo == TipoCampo.hora) {
        _datas[c.chave] = c.inicial;
        _ctrl[c.chave] = TextEditingController(text: c.inicial ?? '');
      } else if (c.tipo == TipoCampo.data) {
        _datas[c.chave] = c.inicial;
        _ctrl[c.chave] = TextEditingController(text: c.inicial == null ? '' : Formatos.data(DateTime.parse(c.inicial!)));
      } else {
        _ctrl[c.chave] = TextEditingController(text: c.inicial ?? '');
      }
    }
  }

  @override
  void dispose() {
    for (final c in _ctrl.values) {
      c.dispose();
    }
    super.dispose();
  }

  Widget _campo(Campo c) {
    final servidor = _erro?.campos[c.chave];
    String? validar(String? v) {
      if (c.obrigatorio && (v == null || v.trim().isEmpty)) return 'Obrigatório.';
      if (c.tipo == TipoCampo.valor && v != null && v.trim().isNotEmpty && lerValor(v) == null) return 'Valor inválido.';
      return null;
    }

    switch (c.tipo) {
      case TipoCampo.escolha:
        return DropdownButtonFormField<String>(
          value: _escolhas[c.chave],
          isExpanded: true,
          decoration: InputDecoration(labelText: c.rotulo, errorText: servidor),
          items: [for (final e in c.opcoes!.entries) DropdownMenuItem(value: e.key, child: Text(e.value, overflow: TextOverflow.ellipsis))],
          onChanged: (v) => setState(() => _escolhas[c.chave] = v),
          validator: (v) => c.obrigatorio && v == null ? 'Obrigatório.' : null,
        );
      case TipoCampo.multi:
        return FormField<Set<String>>(
          initialValue: _multi[c.chave],
          validator: (_) => c.obrigatorio && _multi[c.chave]!.isEmpty ? 'Escolha ao menos uma opção.' : null,
          builder: (st) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(c.rotulo, style: Theme.of(context).textTheme.bodySmall),
            Wrap(spacing: 8, children: [
              for (final e in c.opcoes!.entries)
                FilterChip(
                  label: Text(e.value),
                  selected: _multi[c.chave]!.contains(e.key),
                  onSelected: (v) => setState(() => v ? _multi[c.chave]!.add(e.key) : _multi[c.chave]!.remove(e.key)),
                ),
            ]),
            if (st.hasError) Text(st.errorText!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
            if (servidor != null) Text(servidor, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ]),
        );
      case TipoCampo.hora:
        return TextFormField(
          controller: _ctrl[c.chave],
          readOnly: true,
          decoration: InputDecoration(labelText: c.rotulo, errorText: servidor, suffixIcon: const Icon(Icons.schedule)),
          validator: validar,
          onTap: () async {
            final atual = _datas[c.chave];
            final t = await showTimePicker(
              context: context,
              initialTime: atual == null
                  ? const TimeOfDay(hour: 8, minute: 0)
                  : TimeOfDay(hour: int.parse(atual.substring(0, 2)), minute: int.parse(atual.substring(3, 5))),
              builder: (ctx, w) => MediaQuery(data: MediaQuery.of(ctx).copyWith(alwaysUse24HourFormat: true), child: w!),
            );
            if (t != null) {
              final txt = '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';
              _datas[c.chave] = txt;
              _ctrl[c.chave]!.text = txt;
            }
          },
        );
      case TipoCampo.data:
        return TextFormField(
          controller: _ctrl[c.chave],
          readOnly: true,
          decoration: InputDecoration(labelText: c.rotulo, errorText: servidor, suffixIcon: const Icon(Icons.calendar_today_outlined)),
          validator: validar,
          onTap: () async {
            final atual = _datas[c.chave] == null ? DateTime.now() : DateTime.parse(_datas[c.chave]!);
            final d = await showDatePicker(
                context: context, initialDate: atual, firstDate: DateTime(1900), lastDate: DateTime(2100));
            if (d != null) {
              _datas[c.chave] = DateFormat('yyyy-MM-dd').format(d);
              _ctrl[c.chave]!.text = Formatos.data(d);
            }
          },
        );
      default:
        return TextFormField(
          controller: _ctrl[c.chave],
          obscureText: c.tipo == TipoCampo.senha,
          maxLines: c.tipo == TipoCampo.multilinha ? 3 : 1,
          maxLength: c.max,
          keyboardType: switch (c.tipo) {
            TipoCampo.numero => TextInputType.number,
            TipoCampo.valor => const TextInputType.numberWithOptions(decimal: true),
            _ => null,
          },
          inputFormatters: c.formatters ?? (c.tipo == TipoCampo.numero ? [FilteringTextInputFormatter.digitsOnly] : null),
          decoration: InputDecoration(labelText: c.rotulo, errorText: servidor, counterText: ''),
          validator: validar,
        );
    }
  }

  Future<void> _salvar() async {
    setState(() => _erro = null);
    if (!_form.currentState!.validate()) return;
    final valores = <String, String>{};
    for (final c in widget.campos) {
      switch (c.tipo) {
        case TipoCampo.escolha:
          valores[c.chave] = _escolhas[c.chave] ?? '';
        case TipoCampo.multi:
          valores[c.chave] = (_multi[c.chave]!.toList()..sort()).join(',');
        case TipoCampo.data:
        case TipoCampo.hora:
          valores[c.chave] = _datas[c.chave] ?? '';
        case TipoCampo.valor:
          final n = lerValor(_ctrl[c.chave]!.text);
          valores[c.chave] = n == null ? '' : '$n';
        default:
          valores[c.chave] = _ctrl[c.chave]!.text.trim();
      }
    }
    setState(() => _salvando = true);
    try {
      await widget.enviar(valores);
      if (mounted) Navigator.pop(context, true);
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
    return AlertDialog(
      title: Text(widget.titulo),
      content: SizedBox(
        width: 420,
        child: SingleChildScrollView(
          child: Form(
            key: _form,
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              for (final c in widget.campos) Padding(padding: const EdgeInsets.only(bottom: 8), child: _campo(c)),
              if (_erro != null)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text(_erro!.mensagem, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                ),
            ]),
          ),
        ),
      ),
      actions: [
        TextButton(onPressed: _salvando ? null : () => Navigator.pop(context, false), child: const Text('Cancelar')),
        FilledButton(
          onPressed: _salvando ? null : _salvar,
          child: _salvando
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : Text(widget.rotuloBotao),
        ),
      ],
    );
  }
}

Future<bool> confirmar(BuildContext context, String titulo, String texto, {String rotulo = 'Confirmar'}) async {
  final r = await showDialog<bool>(
    context: context,
    builder: (_) => AlertDialog(
      title: Text(titulo),
      content: Text(texto),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Voltar')),
        FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(rotulo)),
      ],
    ),
  );
  return r == true;
}
