// VINTA React Native test app. Copy over a fresh app's App.tsx, run, paste
// your app key (VINTA console → Publisher → Apps & ad units), tap Start.
import React, { useState } from "react";
import { Button, Image, SafeAreaView, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { Vinta, VintaBanner, VintaNativeAdView, useVintaNative, type VintaConfig, type VintaUnit } from "react-native-vinta";

function NativeCard({ unit, log }: { unit: VintaUnit; log: (m: string) => void }) {
  const ad = useVintaNative(unit.id);
  if (!ad) return <Text style={styles.muted}>native "{unit.name}": loading or no fill</Text>;
  return (
    <VintaNativeAdView ad={ad} style={styles.card}>
      <View style={styles.row}>
        {ad.iconUrl ? <Image source={{ uri: ad.iconUrl }} style={styles.icon} /> : null}
        <View>
          <Text style={styles.title}>{ad.title}</Text>
          <Text style={styles.muted}>{ad.advertiser}</Text>
        </View>
      </View>
      <Image source={{ uri: ad.imageUrl }} style={styles.image} resizeMode="cover" onLoad={() => log(`native "${unit.name}": shown`)} />
      <Text>{ad.body}</Text>
      <Text style={styles.cta}>{ad.cta}</Text>
    </VintaNativeAdView>
  );
}

export default function App() {
  const [appKey, setAppKey] = useState("");
  const [config, setConfig] = useState<VintaConfig | null>(null);
  const [lines, setLines] = useState<string[]>([]);
  const log = (m: string) => setLines((l) => [`${new Date().toLocaleTimeString()}  ${m}`, ...l]);

  const start = async () => {
    const c = await Vinta.init(appKey.trim(), { test: true });
    setConfig(c);
    if (!c) return log("init failed: wrong app key or no internet");
    log(`"${c.name}" (${c.status}), ${c.units.length} unit(s), country ${c.country ?? "unknown"}`);
    log(`device id: ${await Vinta.deviceId()}`);
  };

  const show = async (u: VintaUnit) => {
    const opts = { userId: "rn-test-user", customData: "rn-test" };
    const r =
      u.format === "interstitial" ? await Vinta.showInterstitial(u.id)
      : u.format === "app_open" ? await Vinta.showAppOpen(u.id)
      : u.format === "rewarded" ? await Vinta.showRewarded(u.id, opts)
      : await Vinta.showRewardedInterstitial(u.id, opts);
    log(`${u.format} "${u.name}": ${JSON.stringify(r)}`);
  };

  return (
    <SafeAreaView style={{ flex: 1 }}>
      <ScrollView contentContainerStyle={{ padding: 16 }}>
        <Text style={styles.h1}>VINTA React Native test</Text>
        <TextInput style={styles.input} placeholder="App key (va_...)" value={appKey} onChangeText={setAppKey} autoCapitalize="none" />
        <Button title="Start" onPress={start} disabled={!appKey.trim()} />
        <View style={{ marginTop: 8 }}>
          <Button title="Choose interests" onPress={async () => log(`interests: ${(await Vinta.showInterestPicker()).join(", ") || "none"}`)} />
        </View>
        {config?.units.map((u) =>
          u.format === "banner" ? (
            <View key={u.id} style={{ marginTop: 12 }}>
              <Text style={styles.muted}>Banner: {u.name}</Text>
              <VintaBanner unitId={u.id} style={{ height: 60 }} />
            </View>
          ) : u.format === "native" ? (
            <NativeCard key={u.id} unit={u} log={log} />
          ) : (
            <View key={u.id} style={{ marginTop: 8 }}>
              <Button title={`${u.format.replace("_", " ")}: ${u.name}`} onPress={() => show(u)} />
            </View>
          ),
        )}
        <Text style={[styles.h1, { fontSize: 16, marginTop: 16 }]}>Log</Text>
        {lines.map((l, i) => (
          <Text key={i} style={styles.log}>{l}</Text>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  h1: { fontSize: 20, fontWeight: "700", marginBottom: 8 },
  input: { borderWidth: 1, borderColor: "#ccc", borderRadius: 8, padding: 10, marginBottom: 8 },
  muted: { color: "#64748b", fontSize: 12 },
  card: { marginTop: 12, padding: 12, borderRadius: 12, backgroundColor: "#f1f5f9" },
  row: { flexDirection: "row", gap: 8, alignItems: "center" },
  icon: { width: 36, height: 36, borderRadius: 8 },
  title: { fontWeight: "700" },
  image: { width: "100%", aspectRatio: 1.9, borderRadius: 8, marginVertical: 8 },
  cta: { marginTop: 8, backgroundColor: "#10B981", color: "#fff", textAlign: "center", padding: 8, borderRadius: 6, fontWeight: "700" },
  log: { fontFamily: "monospace", fontSize: 11 },
});
