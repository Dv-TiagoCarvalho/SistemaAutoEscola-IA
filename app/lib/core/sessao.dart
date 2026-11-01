import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'api.dart';

class Usuario {
  Usuario.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        nome = j['nome'] as String,
        email = j['email'] as String,
        perfil = j['perfil'] as String,
        autoescolaNome = j['autoescolaNome'] as String?;
  final int id;
  final String nome;
  final String email;
  final String perfil;
  final String? autoescolaNome;
}

/// null = deslogado.
class SessaoController extends AsyncNotifier<Usuario?> {
  @override
  Future<Usuario?> build() async {
    // Ao abrir o app, tenta restaurar a sessão com o refresh token salvo.
    final dados = await ref.read(apiProvider).renovarSessao();
    return dados == null ? null : Usuario.fromJson(dados);
  }

  Future<void> login(String email, String senha) async {
    final dados = await ref.read(apiProvider).login(email, senha);
    state = AsyncData(Usuario.fromJson(dados));
  }

  Future<void> logout() async {
    await ref.read(apiProvider).logout();
    state = const AsyncData(null);
  }

  void expirou() => state = const AsyncData(null);
}

final sessaoProvider = AsyncNotifierProvider<SessaoController, Usuario?>(SessaoController.new);

final apiProvider = Provider<Api>((ref) {
  return Api(TokenStore(), aoExpirarSessao: () => ref.read(sessaoProvider.notifier).expirou());
});
