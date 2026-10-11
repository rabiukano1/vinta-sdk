/// VINTA (halal ad network) for Flutter. Android today; on other platforms
/// every call returns "no fill" so the same app code runs everywhere.
library;

import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const MethodChannel _channel = MethodChannel('vinta_ads');

bool get _supported => !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

/// One ad unit of your app, as configured in the VINTA console.
class VintaUnit {
  VintaUnit._(this.id, this.name, this.format, this.rewardName, this.rewardAmount);

  factory VintaUnit._fromMap(Map<Object?, Object?> m) => VintaUnit._(
        m['id'] as String,
        m['name'] as String,
        m['format'] as String,
        m['rewardName'] as String?,
        m['rewardAmount'] as int?,
      );

  final String id;
  final String name;

  /// banner | interstitial | rewarded | app_open | rewarded_interstitial | native
  final String format;
  final String? rewardName;
  final int? rewardAmount;
}

/// App config returned by [Vinta.init].
class VintaConfig {
  VintaConfig._(this.name, this.status, this.country, this.units);

  factory VintaConfig._fromMap(Map<Object?, Object?> m) => VintaConfig._(
        m['name'] as String,
        m['status'] as String,
        m['country'] as String?,
        ((m['units'] as List<Object?>?) ?? const [])
            .map((u) => VintaUnit._fromMap(u! as Map<Object?, Object?>))
            .toList(),
      );

  final String name;

  /// pending (test ads only) | approved | rejected
  final String status;

  /// Country VINTA detected for this device, for checking targeting.
  final String? country;
  final List<VintaUnit> units;
}

/// Result of showing a full-screen ad. Never throws: failures come back here.
class VintaShowResult {
  VintaShowResult._({required this.shown, this.rewarded = false, this.rewardName, this.rewardAmount, this.error});

  factory VintaShowResult._fromMap(Map<Object?, Object?>? m) {
    if (m == null) return VintaShowResult._(shown: false, error: 'no result');
    final reward = m['reward'] as Map<Object?, Object?>?;
    return VintaShowResult._(
      shown: m['shown'] == true,
      rewarded: m['rewarded'] == true,
      rewardName: reward?['name'] as String?,
      rewardAmount: reward?['amount'] as int?,
      error: m['error'] as String?,
    );
  }

  static final _noFill = VintaShowResult._(shown: false, error: 'not supported on this platform yet');

  final bool shown;

  /// UI hint only: grant the reward from your SSV callback.
  final bool rewarded;
  final String? rewardName;
  final int? rewardAmount;
  final String? error;
}

/// A loaded native ad: lay out its parts yourself inside [VintaNativeAdView].
class VintaNativeAd {
  VintaNativeAd._fromMap(Map<Object?, Object?> m)
      : token = m['token'] as String,
        title = m['title'] as String,
        body = m['body'] as String,
        cta = m['cta'] as String,
        advertiser = m['advertiser'] as String,
        iconUrl = m['iconUrl'] as String?,
        imageUrl = m['imageUrl'] as String,
        isTest = m['test'] == true;

  final String token;
  final String title;
  final String body;
  final String cta;
  final String advertiser;
  final String? iconUrl;
  final String imageUrl;
  final bool isTest;

  Future<void> _click() => _channel.invokeMethod('nativeClick', {'token': token});
  Future<void> _impression() => _channel.invokeMethod('nativeImpression', {'token': token});
  Future<void> _dispose() => _channel.invokeMethod('destroyNative', {'token': token});
}

/// Entry point. Every method is safe to call on any platform and never throws.
class Vinta {
  Vinta._();

  static Future<T?> _call<T>(String method, [Map<String, Object?>? args]) async {
    if (!_supported) return null;
    try {
      return await _channel.invokeMethod<T>(method, args);
    } catch (e) {
      debugPrint('[VINTA] $method failed: $e');
      return null;
    }
  }

  /// Start the SDK. Returns the app config, or null (unknown key / offline / not Android).
  /// [childDirected]: COPPA, family-safe ads only.
  static Future<VintaConfig?> init(String appKey, {bool test = false, bool childDirected = false}) async {
    final m = await _call<Map<Object?, Object?>>('init', {'appKey': appKey, 'test': test, 'childDirected': childDirected});
    return m == null ? null : VintaConfig._fromMap(m);
  }

  /// COPPA: true for child-directed users/apps.
  static Future<void> setChildDirected(bool value) async => _call<void>('setChildDirected', {'value': value});

  /// User-chosen interests (saved on the device only, never on VINTA's server).
  static Future<List<String>> getInterests() async =>
      (await _call<List<Object?>>('getInterests'))?.whereType<String>().toList() ?? const [];

  /// Save interests from your own settings screen; unknown ids are dropped.
  static Future<List<String>> setInterests(List<String> ids) async =>
      (await _call<List<Object?>>('setInterests', {'ids': ids}))?.whereType<String>().toList() ?? const [];

  /// VINTA's ready-made "Choose your interests" dialog. Returns the saved list.
  static Future<List<String>> showInterestPicker() async =>
      (await _call<List<Object?>>('showInterestPicker'))?.whereType<String>().toList() ?? const [];

  /// This install's VINTA id, for privacy deletion requests.
  static Future<String?> deviceId() => _call<String>('deviceId');

  static Future<VintaShowResult> _show(String method, Map<String, Object?> args) async {
    if (!_supported) return VintaShowResult._noFill;
    return VintaShowResult._fromMap(await _call<Map<Object?, Object?>>(method, args));
  }

  static Future<VintaShowResult> showInterstitial(String unitId) => _show('showInterstitial', {'unitId': unitId});

  /// On launch / return to the app. Keep it rare (e.g. once per session).
  static Future<VintaShowResult> showAppOpen(String unitId) => _show('showAppOpen', {'unitId': unitId});

  /// [userId] / [customData] are sent to your server in the signed SSV callback.
  static Future<VintaShowResult> showRewarded(String unitId, {String? userId, String? customData}) =>
      _show('showRewarded', {'unitId': unitId, 'userId': userId, 'customData': customData});

  /// Rewarded at a natural break: VINTA shows a 3-second intro with Skip first.
  static Future<VintaShowResult> showRewardedInterstitial(String unitId, {String? userId, String? customData}) =>
      _show('showRewardedInterstitial', {'unitId': unitId, 'userId': userId, 'customData': customData});

  /// Native ad parts, or null on no fill.
  static Future<VintaNativeAd?> loadNative(String unitId) async {
    final m = await _call<Map<Object?, Object?>>('loadNative', {'unitId': unitId});
    return m == null ? null : VintaNativeAd._fromMap(m);
  }
}

/// Banner. 320x50 needs height 50; 300x250 needs 250.
class VintaBanner extends StatelessWidget {
  const VintaBanner({super.key, required this.unitId, this.height = 50});

  final String unitId;
  final double height;

  @override
  Widget build(BuildContext context) {
    if (!_supported) return const SizedBox.shrink();
    return SizedBox(
      height: height,
      width: double.infinity,
      child: AndroidView(
        viewType: 'vinta_ads/banner',
        creationParams: {'unitId': unitId},
        creationParamsCodec: const StandardMessageCodec(),
      ),
    );
  }
}

/// Wrap your own native-ad layout in this. It adds the required
/// "Ad · VINTA" label, reports the impression once at least half of it has
/// been on screen for 1 second (MRC), and handles taps.
class VintaNativeAdView extends StatefulWidget {
  const VintaNativeAdView({super.key, required this.ad, required this.child});

  final VintaNativeAd ad;
  final Widget child;

  @override
  State<VintaNativeAdView> createState() => _VintaNativeAdViewState();
}

class _VintaNativeAdViewState extends State<VintaNativeAdView> {
  Timer? _timer;
  DateTime? _visibleSince;
  bool _impressed = false;

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(milliseconds: 250), (_) => _check());
  }

  double _visibleFraction() {
    final box = context.findRenderObject();
    if (box is! RenderBox || !box.attached || !box.hasSize || box.size.isEmpty) return 0;
    final rect = box.localToGlobal(Offset.zero) & box.size;
    final screen = Offset.zero & MediaQuery.sizeOf(context);
    final inter = rect.intersect(screen);
    if (inter.width <= 0 || inter.height <= 0) return 0;
    return (inter.width * inter.height) / (rect.width * rect.height);
  }

  void _check() {
    if (_impressed || !mounted) return;
    final foreground = WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;
    final onTop = ModalRoute.of(context)?.isCurrent ?? true;
    if (foreground && onTop && _visibleFraction() >= 0.5) {
      _visibleSince ??= DateTime.now();
      if (DateTime.now().difference(_visibleSince!) >= const Duration(seconds: 1)) {
        _impressed = true;
        _timer?.cancel();
        widget.ad._impression();
      }
    } else {
      _visibleSince = null;
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    widget.ad._dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        GestureDetector(behavior: HitTestBehavior.opaque, onTap: widget.ad._click, child: widget.child),
        Positioned(
          top: 4,
          right: 4,
          child: IgnorePointer(
            child: DecoratedBox(
              decoration: BoxDecoration(color: const Color(0x8C000000), borderRadius: BorderRadius.circular(4)),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                child: Text(
                  widget.ad.isTest ? 'Test ad · VINTA' : 'Ad · VINTA',
                  style: const TextStyle(color: Colors.white, fontSize: 9, fontWeight: FontWeight.w600),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
