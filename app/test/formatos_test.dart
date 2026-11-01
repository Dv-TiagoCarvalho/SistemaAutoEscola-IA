import 'package:autoescola_app/core/formatos.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';

void main() {
  setUpAll(() => initializeDateFormatting('pt_BR'));

  test('data no formato brasileiro', () {
    expect(Formatos.data(DateTime(2026, 3, 9)), '09/03/2026');
  });

  test('moeda em reais', () {
    expect(Formatos.moeda(1234.5).replaceAll(' ', ' '), r'R$ 1.234,50');
  });

  test('cnpj e cpf formatados', () {
    expect(Formatos.cnpj('11222333000181'), '11.222.333/0001-81');
    expect(Formatos.cnpj('12ABC34501DE35'), '12.ABC.345/01DE-35');
    expect(Formatos.cpf('52998224725'), '529.982.247-25');
  });

  test('nome do perfil', () {
    expect(Formatos.perfil('SECRETARIA'), 'Secretaria');
  });
}
