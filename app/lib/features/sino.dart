import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/formatos.dart';
import '../core/sessao.dart';
import 'comum.dart';

/// Sino de notificações da barra superior. Consulta a contagem a cada 60 s enquanto o app está aberto.
class Sino extends ConsumerStatefulWidget {
  const Sino({super.key});
  @override
  ConsumerState<Sino> createState() => _SinoState();
}

class _SinoState extends ConsumerState<Sino> {
  int _naoLidas = 0;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _atualizar();
    _timer = Timer.periodic(const Duration(seconds: 60), (_) => _atualizar());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _atualizar() async {
    try {
      final r = await ref.read(apiProvider).get('/notificacoes/nao-lidas');
      if (mounted) setState(() => _naoLidas = (r['naoLidas'] as num).toInt());
    } catch (_) {
      // Sem rede ou sessão expirada: mantém o último valor, sem incomodar o usuário.
    }
  }

  Future<void> _abrir() async {
    final api = ref.read(apiProvider);
    try {
      final lista = (await api.get('/notificacoes') as List).cast<Map<String, dynamic>>();
      if (!mounted) return;
      await showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        builder: (_) => DraggableScrollableSheet(
          expand: false,
          initialChildSize: 0.6,
          builder: (ctx, controller) => Column(children: [
            ListTile(
              title: const Text('Notificações'),
              trailing: TextButton(
                onPressed: () async {
                  await api.post('/notificacoes/leitura', null);
                  if (ctx.mounted) Navigator.pop(ctx);
                },
                child: const Text('Marcar todas como lidas'),
              ),
            ),
            const Divider(height: 1),
            Expanded(
              child: lista.isEmpty
                  ? const Center(child: Text('Nada por aqui.'))
                  : ListView(controller: controller, children: [
                      for (final n in lista)
                        ListTile(
                          leading: Icon(n['lida'] == true ? Icons.notifications_none : Icons.notifications_active, color: n['lida'] == true ? null : Colors.orange),
                          title: Text(n['titulo'] as String),
                          subtitle: Text('${n['mensagem']}\n${Formatos.dataHora(DateTime.parse(n['criadoEm']))}'),
                          isThreeLine: true,
                        ),
                    ]),
            ),
          ]),
        ),
      );
    } catch (e) {
      if (mounted) mostrarErro(context, e);
    }
    _atualizar();
  }

  @override
  Widget build(BuildContext context) {
    return IconButton(
      tooltip: 'Notificações',
      onPressed: _abrir,
      icon: Badge(isLabelVisible: _naoLidas > 0, label: Text('$_naoLidas'), child: const Icon(Icons.notifications_outlined)),
    );
  }
}
