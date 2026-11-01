import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// URL da API. Em produção web o nginx serve app e API na mesma origem (/api).
/// No celular/emulador: flutter run --dart-define=API_URL=http://10.0.2.2:8080
const apiUrl = String.fromEnvironment('API_URL', defaultValue: '');

/// Erro da API com a mensagem em português vinda do back-end (ProblemDetail).
class ApiErro implements Exception {
  ApiErro(this.mensagem, {this.status, this.campos = const {}});
  final String mensagem;
  final int? status;
  final Map<String, String> campos;

  factory ApiErro.de(Object e) {
    if (e is ApiErro) return e;
    if (e is DioException) {
      final data = e.response?.data;
      if (data is Map) {
        final campos = <String, String>{};
        if (data['campos'] is Map) {
          (data['campos'] as Map).forEach((k, v) => campos['$k'] = '$v');
        }
        return ApiErro('${data['detail'] ?? data['title'] ?? 'Erro na requisição.'}',
            status: e.response?.statusCode, campos: campos);
      }
      if (e.type == DioExceptionType.connectionError || e.type == DioExceptionType.connectionTimeout) {
        return ApiErro('Sem conexão com o servidor. Verifique sua internet.');
      }
    }
    return ApiErro('Erro inesperado. Tente novamente.');
  }

  @override
  String toString() => mensagem;
}

/// Guarda os tokens: refresh no armazenamento seguro, access só em memória.
class TokenStore {
  TokenStore([FlutterSecureStorage? storage]) : _storage = storage ?? const FlutterSecureStorage();
  final FlutterSecureStorage _storage;
  static const _chave = 'refresh_token';
  String? accessToken;

  Future<String?> lerRefresh() => _storage.read(key: _chave);
  Future<void> salvarRefresh(String token) => _storage.write(key: _chave, value: token);
  Future<void> limpar() async {
    accessToken = null;
    await _storage.delete(key: _chave);
  }
}

/// Cliente HTTP com renovação automática do token.
/// Se várias requisições recebem 401 ao mesmo tempo, só UMA chamada de refresh é feita
/// (o back-end revoga tudo se o mesmo refresh token for usado duas vezes).
class Api {
  Api(this.tokens, {required this.aoExpirarSessao}) {
    final opcoes = BaseOptions(
      baseUrl: '$apiUrl/api',
      connectTimeout: const Duration(seconds: 15),
      receiveTimeout: const Duration(seconds: 30),
      contentType: 'application/json',
    );
    dio = Dio(opcoes);
    // Sem interceptors: usado no refresh e na repetição, para não travar a fila do QueuedInterceptorsWrapper.
    _cru = Dio(opcoes);
    dio.interceptors.add(QueuedInterceptorsWrapper(
      onRequest: (options, handler) {
        final t = tokens.accessToken;
        if (t != null) options.headers['Authorization'] = 'Bearer $t';
        handler.next(options);
      },
      onError: (e, handler) async {
        final req = e.requestOptions;
        final ehAuth = req.path.startsWith('/auth/');
        if (e.response?.statusCode != 401 || ehAuth || req.extra['repetida'] == true) {
          return handler.next(e);
        }
        final ok = await renovar();
        if (!ok) {
          aoExpirarSessao();
          return handler.next(e);
        }
        req.headers['Authorization'] = 'Bearer ${tokens.accessToken}';
        req.extra['repetida'] = true;
        try {
          handler.resolve(await _cru.fetch(req));
        } on DioException catch (e2) {
          handler.next(e2);
        }
      },
    ));
  }

  final TokenStore tokens;
  final void Function() aoExpirarSessao;
  late final Dio dio;
  late final Dio _cru;
  Future<Map<String, dynamic>?>? _renovando;

  /// Troca o refresh token por um novo par. Retorna os dados do usuário ou null.
  Future<Map<String, dynamic>?> renovarSessao() {
    return _renovando ??= _fazerRefresh().whenComplete(() => _renovando = null);
  }

  Future<bool> renovar() async => (await renovarSessao()) != null;

  Future<Map<String, dynamic>?> _fazerRefresh() async {
    final refresh = await tokens.lerRefresh();
    if (refresh == null) return null;
    try {
      final r = await _cru.post('/auth/refresh', data: {'refreshToken': refresh});
      return _guardar(r.data as Map<String, dynamic>);
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) await tokens.limpar();
      return null;
    }
  }

  Future<Map<String, dynamic>> login(String email, String senha) async {
    try {
      final r = await _cru.post('/auth/login', data: {'email': email, 'senha': senha});
      return _guardar(r.data as Map<String, dynamic>);
    } catch (e) {
      throw ApiErro.de(e);
    }
  }

  Future<void> logout() async {
    final refresh = await tokens.lerRefresh();
    if (refresh != null) {
      try {
        await _cru.post('/auth/logout', data: {'refreshToken': refresh});
      } catch (_) {}
    }
    await tokens.limpar();
  }

  Future<Map<String, dynamic>> _guardar(Map<String, dynamic> data) async {
    tokens.accessToken = data['accessToken'] as String;
    await tokens.salvarRefresh(data['refreshToken'] as String);
    return data['usuario'] as Map<String, dynamic>;
  }

  // Atalhos que já convertem erros em ApiErro.
  Future<dynamic> get(String path, {Map<String, dynamic>? query}) => _req(() => dio.get(path, queryParameters: query));
  Future<dynamic> post(String path, Object? body) => _req(() => dio.post(path, data: body));
  Future<dynamic> put(String path, Object? body) => _req(() => dio.put(path, data: body));
  Future<dynamic> patch(String path, Object? body) => _req(() => dio.patch(path, data: body));

  Future<dynamic> _req(Future<Response> Function() f) async {
    try {
      return (await f()).data;
    } catch (e) {
      throw ApiErro.de(e);
    }
  }
}
