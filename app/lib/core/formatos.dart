import 'package:intl/intl.dart';

/// Formatos brasileiros usados em todo o app.
class Formatos {
  static final _data = DateFormat('dd/MM/yyyy', 'pt_BR');
  static final _dataHora = DateFormat('dd/MM/yyyy HH:mm', 'pt_BR');
  static final _moeda = NumberFormat.currency(locale: 'pt_BR', symbol: r'R$');

  static String data(DateTime? d) => d == null ? '-' : _data.format(d.toLocal());
  static String dataHora(DateTime? d) => d == null ? '-' : _dataHora.format(d.toLocal());
  static String moeda(num? v) => v == null ? '-' : _moeda.format(v);

  static String cnpj(String? v) {
    if (v == null || v.length != 14) return v ?? '-';
    return '${v.substring(0, 2)}.${v.substring(2, 5)}.${v.substring(5, 8)}/${v.substring(8, 12)}-${v.substring(12)}';
  }

  static String cpf(String? v) {
    if (v == null || v.length != 11) return v ?? '-';
    return '${v.substring(0, 3)}.${v.substring(3, 6)}.${v.substring(6, 9)}-${v.substring(9)}';
  }

  static String perfil(String p) => const {
        'SUPER_ADMIN': 'Plataforma',
        'ADMIN': 'Administrador',
        'SECRETARIA': 'Secretaria',
        'INSTRUTOR': 'Instrutor',
        'ALUNO': 'Aluno',
      }[p] ??
      p;
}
