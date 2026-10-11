// VINTA Flutter test app: paste your app key (VINTA console → Publisher →
// Apps & ad units), tap Start, and try every ad unit of your app.
import 'package:flutter/material.dart';
import 'package:vinta_ads/vinta_ads.dart';

void main() => runApp(const MaterialApp(home: VintaTestPage()));

class VintaTestPage extends StatefulWidget {
  const VintaTestPage({super.key});

  @override
  State<VintaTestPage> createState() => _VintaTestPageState();
}

class _VintaTestPageState extends State<VintaTestPage> {
  final _key = TextEditingController();
  VintaConfig? _config;
  final _log = <String>[];

  void _add(String m) => setState(() => _log.insert(0, '${TimeOfDay.now().format(context)}  $m'));

  Future<void> _start() async {
    final c = await Vinta.init(_key.text.trim(), test: true);
    setState(() => _config = c);
    if (c == null) return _add('init failed: wrong app key or no internet');
    _add('"${c.name}" (${c.status}), ${c.units.length} unit(s), country ${c.country ?? "unknown"}');
    _add('device id: ${await Vinta.deviceId()}');
  }

  Future<void> _show(VintaUnit u) async {
    final r = switch (u.format) {
      'interstitial' => await Vinta.showInterstitial(u.id),
      'app_open' => await Vinta.showAppOpen(u.id),
      'rewarded' => await Vinta.showRewarded(u.id, userId: 'flutter-test-user', customData: 'flutter-test'),
      _ => await Vinta.showRewardedInterstitial(u.id, userId: 'flutter-test-user', customData: 'flutter-test'),
    };
    _add('${u.format} "${u.name}": shown=${r.shown} rewarded=${r.rewarded}'
        '${r.rewardAmount != null ? " (${r.rewardAmount} ${r.rewardName})" : ""}${r.error != null ? " error=${r.error}" : ""}');
  }

  @override
  Widget build(BuildContext context) {
    final units = _config?.units ?? const <VintaUnit>[];
    return Scaffold(
      appBar: AppBar(title: const Text('VINTA Flutter test')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(controller: _key, decoration: const InputDecoration(labelText: 'App key (va_...)')),
          const SizedBox(height: 8),
          FilledButton(onPressed: _start, child: const Text('Start')),
          OutlinedButton(
            onPressed: () async {
              final list = await Vinta.showInterestPicker();
              _add('interests: ${list.isEmpty ? "none" : list.join(", ")}');
            },
            child: const Text('Choose interests'),
          ),
          for (final u in units)
            if (u.format == 'banner')
              Padding(padding: const EdgeInsets.only(top: 12), child: VintaBanner(unitId: u.id, height: 60))
            else if (u.format == 'native')
              _NativeCard(unit: u, log: _add)
            else
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: OutlinedButton(onPressed: () => _show(u), child: Text('${u.format.replaceAll('_', ' ')}: ${u.name}')),
              ),
          const SizedBox(height: 16),
          const Text('Log', style: TextStyle(fontWeight: FontWeight.bold)),
          for (final l in _log) Text(l, style: const TextStyle(fontFamily: 'monospace', fontSize: 11)),
        ],
      ),
    );
  }
}

class _NativeCard extends StatefulWidget {
  const _NativeCard({required this.unit, required this.log});

  final VintaUnit unit;
  final void Function(String) log;

  @override
  State<_NativeCard> createState() => _NativeCardState();
}

class _NativeCardState extends State<_NativeCard> {
  VintaNativeAd? _ad;

  @override
  void initState() {
    super.initState();
    Vinta.loadNative(widget.unit.id).then((ad) {
      if (!mounted) return;
      setState(() => _ad = ad);
      widget.log('native "${widget.unit.name}": ${ad == null ? "no fill" : "loaded"}');
    });
  }

  @override
  Widget build(BuildContext context) {
    final ad = _ad;
    if (ad == null) return const SizedBox.shrink();
    // Laid out in this app's own style: that's the point of native ads.
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: VintaNativeAdView(
        ad: ad,
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(children: [
                  if (ad.iconUrl != null) Image.network(ad.iconUrl!, width: 36, height: 36),
                  const SizedBox(width: 8),
                  Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(ad.title, style: const TextStyle(fontWeight: FontWeight.bold)),
                    Text(ad.advertiser, style: const TextStyle(fontSize: 12, color: Colors.grey)),
                  ]),
                ]),
                const SizedBox(height: 8),
                Image.network(ad.imageUrl),
                const SizedBox(height: 8),
                Text(ad.body),
                const SizedBox(height: 8),
                FilledButton(onPressed: null, child: Text(ad.cta)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
