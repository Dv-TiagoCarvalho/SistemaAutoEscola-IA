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

  static const categorias = ['ACC', 'A', 'B', 'AB', 'C', 'D', 'E'];

  static String _de(Map<String, String> m, String? v) => v == null ? '-' : (m[v] ?? v);

  static String situacao(String? v) => _de(const {
        'MATRICULADO': 'Matriculado',
        'EM_AULAS': 'Em aulas',
        'AGUARDANDO_EXAME': 'Aguardando exame',
        'APROVADO': 'Aprovado',
        'DESISTENTE': 'Desistente',
      }, v);
  static String tipoProcesso(String? v) => _de(const {
        'PRIMEIRA_HABILITACAO': 'Primeira habilitação',
        'ADICAO_CATEGORIA': 'Adição de categoria',
        'MUDANCA_CATEGORIA': 'Mudança de categoria',
      }, v);
  static String statusExame(String? v) => _de(const {'PENDENTE': 'Pendente', 'APTO': 'Apto', 'INAPTO': 'Inapto'}, v);
  static String tipoDocumento(String? v) => _de(const {
        'FOTO': 'Foto',
        'RG': 'RG',
        'COMPROVANTE_RESIDENCIA': 'Comprovante de residência',
        'OUTRO': 'Outro',
      }, v);
  static String statusMatricula(String? v) =>
      _de(const {'ATIVA': 'Ativa', 'CONCLUIDA': 'Concluída', 'CANCELADA': 'Cancelada'}, v);
  static String statusTitulo(String? v) => _de(const {'ABERTO': 'Em aberto', 'PAGO': 'Pago', 'CANCELADO': 'Cancelado'}, v);

  static String telefone(String? v) {
    if (v == null) return '-';
    if (v.length == 11) return '(${v.substring(0, 2)}) ${v.substring(2, 7)}-${v.substring(7)}';
    if (v.length == 10) return '(${v.substring(0, 2)}) ${v.substring(2, 6)}-${v.substring(6)}';
    return v;
  }
}
