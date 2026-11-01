import 'package:autoescola_app/core/documentos.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('cpfValido', () {
    expect(cpfValido('529.982.247-25'), isTrue);
    expect(cpfValido('52998224725'), isTrue);
    expect(cpfValido('529.982.247-24'), isFalse);
    expect(cpfValido('111.111.111-11'), isFalse);
    expect(cpfValido('123'), isFalse);
  });

  test('lerValor', () {
    expect(lerValor('1.234,56'), 1234.56);
    expect(lerValor('R\$ 85,5'), 85.5);
    expect(lerValor('200'), 200);
    expect(lerValor(''), isNull);
    expect(lerValor('abc'), isNull);
  });

  test('máscaras', () {
    const v = TextEditingValue.empty;
    expect(Mascara.cpf.formatEditUpdate(v, const TextEditingValue(text: '52998224725')).text, '529.982.247-25');
    expect(Mascara.cep.formatEditUpdate(v, const TextEditingValue(text: '30140071')).text, '30140-071');
    expect(MascaraTelefone().formatEditUpdate(v, const TextEditingValue(text: '31999990000')).text, '(31) 99999-0000');
    expect(MascaraTelefone().formatEditUpdate(v, const TextEditingValue(text: '3133334444')).text, '(31) 3333-4444');
  });
}
