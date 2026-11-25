import React, { useCallback, useEffect, useState } from 'react';
import { Alert, FlatList, Image, Linking, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { addInspiration, deleteInspiration, getInspirations, type InspirationItem } from '../data/database';

export function InspirationWorkspace({ projectId, projectName }: { projectId: number; projectName: string }) {
  const [items, setItems] = useState<InspirationItem[]>([]);
  const [link, setLink] = useState('');
  const [busy, setBusy] = useState(false);
  const refresh = useCallback(async () => setItems(await getInspirations(projectId)), [projectId]);
  useEffect(() => { void refresh(); }, [refresh]);

  const importPhotos = async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('Photo access needed', 'Allow photo access to add reference images to this project.');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], allowsMultipleSelection: true, quality: 0.9 });
    if (result.canceled) return;
    setBusy(true);
    try {
      for (const asset of result.assets) await addInspiration(projectId, 'image', asset.uri, asset.fileName ?? 'Reference photo');
      await refresh();
    } catch { Alert.alert('Import failed', 'Please try adding those photos again.'); }
    finally { setBusy(false); }
  };

  const saveLink = async () => {
    const raw = link.trim();
    if (!raw) return;
    const url = raw.startsWith('http://') || raw.startsWith('https://') ? raw : 'https://' + raw;
    try {
      const parsed = new URL(url);
      if (!parsed.hostname.includes('.')) throw new Error('Invalid URL');
      setBusy(true);
      await addInspiration(projectId, 'link', parsed.toString(), parsed.hostname.replace(/^www\./, ''));
      setLink('');
      await refresh();
    } catch { Alert.alert('Check that link', 'Enter a website address or Pinterest pin URL.'); }
    finally { setBusy(false); }
  };

  const remove = (item: InspirationItem) => Alert.alert('Remove reference?', 'This removes it from this project.', [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Remove', style: 'destructive', onPress: async () => { await deleteInspiration(item.id); await refresh(); } },
  ]);

  return <View>
    <Text style={s.kicker}>WORKSPACE / INSPIRATION</Text>
    <Text style={s.title}>Collect the feeling.</Text>
    <Text style={s.description}>Gather images, fabric ideas and links that give {projectName} its visual direction. Your references stay with this project on this device.</Text>
    <View style={s.panel}>
      <Text style={s.label}>ADD TO YOUR COLLECTION</Text>
      <Pressable style={s.photoButton} onPress={() => void importPhotos()} disabled={busy}>
        <Text style={s.photoText}>{busy ? 'Saving…' : '＋  Import photos'}</Text>
      </Pressable>
      <View style={s.linkRow}>
        <TextInput value={link} onChangeText={setLink} placeholder="Paste a Pinterest or website link" placeholderTextColor="#A39D91" autoCapitalize="none" autoCorrect={false} keyboardType="url" style={s.input} onSubmitEditing={() => void saveLink()} />
        <Pressable style={s.saveButton} onPress={() => void saveLink()} disabled={!link.trim() || busy}><Text style={s.saveText}>Save link</Text></Pressable>
      </View>
      <Text style={s.hint}>Links are saved as tappable reference cards. Open a card to visit the original page.</Text>
    </View>
    <View style={s.headingRow}><Text style={s.sectionTitle}>Your references</Text><Text style={s.count}>{items.length} ITEMS</Text></View>
    {items.length === 0 ? <View style={s.empty}>
      <Text style={s.star}>✳</Text><Text style={s.emptyTitle}>A blank moodboard, full of possibility.</Text>
      <Text style={s.emptyText}>Import a photo or save a link to start your collection.</Text>
    </View> : <FlatList data={items} keyExtractor={(item) => String(item.id)} numColumns={3} scrollEnabled={false} columnWrapperStyle={s.gridRow} contentContainerStyle={s.grid} renderItem={({ item }) =>
      <View style={s.card}>
        {item.kind === 'image' ? <Pressable onPress={() => void Linking.openURL(item.uri)}><Image source={{ uri: item.uri }} style={s.image} resizeMode="cover" /></Pressable> :
          <Pressable style={s.linkCard} onPress={() => void Linking.openURL(item.uri)}><Text style={s.linkGlyph}>↗</Text><Text style={s.domain} numberOfLines={2}>{item.title || 'Website'}</Text><Text style={s.openLabel}>OPEN REFERENCE</Text></Pressable>}
        <View style={s.cardFooter}><Text style={s.kind}>{item.kind === 'image' ? 'PHOTO' : 'WEB LINK'}</Text><Pressable onPress={() => remove(item)} hitSlop={8}><Text style={s.remove}>Remove</Text></Pressable></View>
      </View>
    } />}
  </View>;
}

const s = StyleSheet.create({
  kicker: { color: '#999386', fontSize: 9, letterSpacing: 1.8, fontWeight: '700' },
  title: { color: '#373830', fontFamily: 'Georgia', fontSize: 35, marginTop: 10 },
  description: { color: '#858176', fontSize: 13, lineHeight: 21, maxWidth: 620, marginTop: 8 },
  panel: { backgroundColor: '#FBF9F5', borderRadius: 13, borderWidth: 1, borderColor: '#E9E2D8', padding: 20, marginTop: 25 },
  label: { color: '#777568', fontSize: 9, letterSpacing: 1.5, fontWeight: '700', marginBottom: 13 },
  photoButton: { backgroundColor: '#71816D', borderRadius: 8, alignItems: 'center', paddingVertical: 13 },
  photoText: { color: '#FFF', fontSize: 12, fontWeight: '700' },
  linkRow: { flexDirection: 'row', gap: 9, marginTop: 11 },
  input: { flex: 1, minHeight: 45, backgroundColor: '#F3F0E9', borderRadius: 8, paddingHorizontal: 13, color: '#44443C', fontSize: 12 },
  saveButton: { backgroundColor: '#E5E9DF', borderRadius: 8, paddingHorizontal: 17, justifyContent: 'center' },
  saveText: { color: '#5E705A', fontSize: 11, fontWeight: '700' },
  hint: { color: '#A19B8F', fontSize: 9, marginTop: 10 },
  headingRow: { flexDirection: 'row', alignItems: 'center', gap: 14, marginTop: 27, marginBottom: 14 },
  sectionTitle: { color: '#3D3E36', fontFamily: 'Georgia', fontSize: 21 },
  count: { color: '#9A9589', fontSize: 8, letterSpacing: 1.3 },
  empty: { backgroundColor: '#EBEDE6', borderRadius: 12, padding: 25, alignItems: 'center', minHeight: 145, justifyContent: 'center' },
  star: { color: '#819078', fontSize: 24 },
  emptyTitle: { color: '#626E5E', fontFamily: 'Georgia', fontSize: 16, marginTop: 8, textAlign: 'center' },
  emptyText: { color: '#8A8E80', fontSize: 10, marginTop: 6, textAlign: 'center' },
  grid: { paddingBottom: 20 },
  gridRow: { gap: 13, marginBottom: 13 },
  card: { flex: 1, minWidth: 0, backgroundColor: '#FBF9F5', borderRadius: 9, overflow: 'hidden', borderWidth: 1, borderColor: '#E9E2D8' },
  image: { width: '100%', height: 145, backgroundColor: '#EAE6DE' },
  linkCard: { height: 145, backgroundColor: '#E8EBE2', padding: 15, justifyContent: 'center' },
  linkGlyph: { color: '#71816D', fontSize: 24 },
  domain: { color: '#596653', fontFamily: 'Georgia', fontSize: 16, marginTop: 8 },
  openLabel: { color: '#84917C', fontSize: 8, letterSpacing: 1.1, marginTop: 8 },
  cardFooter: { minHeight: 35, paddingHorizontal: 10, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  kind: { color: '#9B9589', fontSize: 8, letterSpacing: 1 },
  remove: { color: '#A17F72', fontSize: 9 },
});
