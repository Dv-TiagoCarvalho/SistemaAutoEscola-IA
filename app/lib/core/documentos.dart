import 'package:flutter/services.dart';

/// Valida CPF (dígitos verificadores). Aceita com ou sem máscara.
bool cpfValido(String valor) {
  final d = valor.replaceAll(RegExp(r'\D'), '');
  if (d.length != 11 || RegExp(r'^(\d)\1{10}$').hasMatch(d)) return false;
  int digito(int n) {
    var soma = 0;
    for (var i = 0; i < n; i++) {
      soma += int.parse(d[i]) * (n + 1 - i);
    }
    final r = (soma * 10) % 11;
    return r == 10 ? 0 : r;
  }

  return digito(9) == int.parse(d[9]) && digito(10) == int.parse(d[10]);
}

/// Converte "1.234,56" ou "1234.56" em número; null se inválido.
num? lerValor(String? texto) {
  if (texto == null || texto.trim().isEmpty) return null;
  var t = texto.trim().replaceAll('R\$', '').replaceAll(' ', '');
  if (t.contains(',')) t = t.replaceAll('.', '').replaceAll(',', '.');
  return num.tryParse(t);
}

/// Máscara por padrão: '#' = dígito. Ex.: '###.###.###-##'.
class Mascara extends TextInputFormatter {
  Mascara(this.padrao);
  final String padrao;

  static final cpf = Mascara('###.###.###-##');
  static final cep = Mascara('#####-###');
  static final data = Mascara('##/##/####');

  @override
  TextEditingValue formatEditUpdate(TextEditingValue antigo, TextEditingValue novo) {
    final d = novo.text.replaceAll(RegExp(r'\D'), '');
    final b = StringBuffer();
    var k = 0;
    for (var i = 0; i < padrao.length && k < d.length; i++) {
      if (padrao[i] == '#') {
        b.write(d[k++]);
      } else {
        b.write(padrao[i]);
      }
    }
    final t = b.toString();
    return TextEditingValue(text: t, selection: TextSelection.collapsed(offset: t.length));
  }
}

/// Telefone BR: (11) 99999-9999 ou (11) 9999-9999.
class MascaraTelefone extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(TextEditingValue antigo, TextEditingValue novo) {
    final d = novo.text.replaceAll(RegExp(r'\D'), '');
    final padrao = d.length > 10 ? '(##) #####-####' : '(##) ####-####';
    return Mascara(padrao).formatEditUpdate(antigo, novo);
  }
}
