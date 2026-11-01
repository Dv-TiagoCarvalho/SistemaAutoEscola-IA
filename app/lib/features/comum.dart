import 'package:flutter/material.dart';

import '../core/api.dart';

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
