import React, { useState } from 'react';
import { Alert, Image, Linking, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { addInspiration, deleteInspiration, getInspirations, type InspirationItem } from '../data/database';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RouteProp } from '@react-navigation/native';
import type { RootStackParamList } from '../../App';
import { WORKSPACES, type WorkspaceKey } from '../types';

type ProjectRoute = RouteProp<RootStackParamList, 'Project'>;
type Navigation = NativeStackNavigationProp<RootStackParamList, 'Project'>;

const workspaceDetails: Record<WorkspaceKey, { headline: string; description: string; next: string[] }> = {
  inspiration: { headline: 'Collect the feeling.', description: 'Build a visual direction for this garment. Add reference photos, fabric ideas, colour notes and inspiration links here.', next: ['Import reference images', 'Paste a Pinterest or image link', 'Group references into collections'] },
  sketchbook: { headline: 'Give the idea a shape.', description: 'Explore the garment from every angle with a multi-page sketchbook and movable reference overlays.', next: ['Create front, back and side pages', 'Bring in inspiration as translucent guides', 'Draw with your S Pen'] },
  patterns: { headline: 'Make every millimetre count.', description: 'Draft editable vector pattern pieces on a real-world measurement canvas.', next: ['Set canvas dimensions and grid spacing', 'Draw lines, curves and pattern shapes', 'Add seam allowance and custom fabric tags'] },
  layout: { headline: 'Plan the cut.', description: 'Arrange pattern pieces on fabric width and see how much material each fabric group needs.', next: ['Choose fabric width', 'Arrange pieces manually or automatically', 'Review estimated fabric requirements'] },
  print: { headline: 'Ready for the real world.', description: 'Prepare accurately scaled pattern sheets for home printing, large-format printing or projector use.', next: ['Choose Letter, A4 or A0', 'Generate tiled sheets and a calibration square', 'Export a print-ready PDF'] },
};

export function ProjectScreen() {
  const route = useRoute<ProjectRoute>();
  const navigation = useNavigation<Navigation>();
  const { project } = route.params;
  const [active, setActive] = useState<WorkspaceKey>('inspiration');
  const [inspirations, setInspirations] = useState<InspirationItem[]>([]);
  const [referenceLink, setReferenceLink] = useState('');
  const [savingReference, setSavingReference] = useState(false);

  const refreshInspirations = async () => setInspirations(await getInspirations(project.id));
  React.useEffect(() => { void refreshInspirations(); }, [project.id]);

  const importReferencePhotos = async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) return Alert.alert('Photo access needed', 'Allow photo access to add garment references.');
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], allowsMultipleSelection: true, quality: 0.9 });
    if (result.canceled) return;
    setSavingReference(true);
    try {
      for (const asset of result.assets) await addInspiration(project.id, 'image', asset.uri, asset.fileName ?? 'Reference photo');
      await refreshInspirations();
    } catch { Alert.alert('Import failed', 'Please try again.'); }
    finally { setSavingReference(false); }
  };

  const saveReferenceLink = async () => {
    const raw = referenceLink.trim();
    if (!raw) return;
    const url = raw.startsWith('http') ? raw : 'https://' + raw;
    try {
      const parsed = new URL(url);
      if (!parsed.hostname.includes('.')) throw new Error();
      setSavingReference(true);
      await addInspiration(project.id, 'link', parsed.toString(), parsed.hostname.replace(/^www\\./, ''));
      setReferenceLink('');
      await refreshInspirations();
    } catch { Alert.alert('Invalid link', 'Enter a website address or Pinterest pin URL.'); }
    finally { setSavingReference(false); }
  };

  const removeReference = (item: InspirationItem) => Alert.alert('Remove reference?', 'Remove this item from the project?', [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Remove', style: 'destructive', onPress: async () => { await deleteInspiration(item.id); await refreshInspirations(); } },
  ]);
  const detail = workspaceDetails[active];
  const selected = WORKSPACES.find((item) => item.key === active)!;

  return (
    <View style={styles.screen}>
      <View style={styles.header}>
        <Pressable style={styles.back} onPress={() => navigation.goBack()}><Text style={styles.backText}>‹  Projects</Text></Pressable>
        <View style={styles.projectIdentity}>
          <Text style={styles.projectEyebrow}>CURRENT PROJECT</Text>
          <Text style={styles.projectName} numberOfLines={1}>{project.name}</Text>
        </View>
        <View style={styles.saveStatus}><View style={styles.statusDot} /><Text style={styles.saveText}>ON THIS DEVICE</Text></View>
      </View>

      <View style={styles.body}>
        <View style={styles.sidebar}>
          <Text style={styles.sideLabel}>WORKSPACES</Text>
          {WORKSPACES.map((item, index) => (
            <Pressable key={item.key} onPress={() => setActive(item.key)} style={[styles.navItem, active === item.key && styles.navItemActive]}>
              <Text style={[styles.navNumber, active === item.key && styles.navTextActive]}>{String(index + 1).padStart(2, '0')}</Text>
              <View style={styles.navCopy}>
                <Text style={[styles.navTitle, active === item.key && styles.navTextActive]}>{item.title}</Text>
                <Text style={styles.navSubtitle}>{item.subtitle}</Text>
              </View>
              {active === item.key && <View style={styles.activeIndicator} />}
            </Pressable>
          ))}
          <View style={styles.sidebarNote}>
            <Text style={styles.noteGlyph}>✳</Text>
            <Text style={styles.noteTitle}>A note to self</Text>
            <Text style={styles.noteText}>Good design starts with curiosity. Let the process take shape.</Text>
          </View>
        </View>

        <ScrollView style={styles.content} contentContainerStyle={styles.contentInner}>
          <Text style={styles.contentKicker}>WORKSPACE  /  {selected.title.toUpperCase()}</Text>
          <Text style={styles.contentTitle}>{detail.headline}</Text>
          <Text style={styles.contentDescription}>{detail.description}</Text>

          {active === 'inspiration' && <View style={styles.inspirationArea}>
            <Pressable style={styles.importButton} onPress={() => void importReferencePhotos()} disabled={savingReference}>
              <Text style={styles.importButtonText}>{savingReference ? 'Saving…' : '＋  Import photos'}</Text>
            </Pressable>
            <View style={styles.referenceLinkRow}>
              <TextInput value={referenceLink} onChangeText={setReferenceLink} placeholder="Paste a Pinterest or website link" autoCapitalize="none" autoCorrect={false} keyboardType="url" style={styles.referenceInput} onSubmitEditing={() => void saveReferenceLink()} />
              <Pressable style={styles.referenceSave} onPress={() => void saveReferenceLink()} disabled={!referenceLink.trim() || savingReference}><Text style={styles.referenceSaveText}>Save link</Text></Pressable>
            </View>
            <Text style={styles.referenceCount}>YOUR REFERENCES · {inspirations.length}</Text>
            <View style={styles.referenceGrid}>{inspirations.map((item) => <View key={item.id} style={styles.referenceCard}>
              {item.kind === 'image' ? <Pressable onPress={() => void Linking.openURL(item.uri)}><Image source={{ uri: item.uri }} style={styles.referenceImage} /></Pressable> :
                <Pressable style={styles.referenceLinkCard} onPress={() => void Linking.openURL(item.uri)}><Text style={styles.referenceDomain}>{item.title}</Text><Text style={styles.referenceOpen}>OPEN LINK ↗</Text></Pressable>}
              <Pressable onPress={() => removeReference(item)} style={styles.referenceRemove}><Text style={styles.referenceRemoveText}>Remove</Text></Pressable>
            </View>)}</View>
          </View>}

          {active !== 'inspiration' && <><View style={styles.featurePanel}>
            <View style={styles.panelTop}>
              <View style={styles.panelIcon}><Text style={styles.panelIconText}>{selected.symbol}</Text></View>
              <View style={styles.panelHeading}>
                <Text style={styles.panelTitle}>{selected.title}</Text>
                <Text style={styles.panelSub}>YOUR {selected.shortTitle.toUpperCase()} WORKSPACE</Text>
              </View>
              <View style={styles.comingSoon}><Text style={styles.comingSoonText}>WORKSPACE SETUP</Text></View>
            </View>
            <View style={styles.panelRule} />
            <Text style={styles.checklistHeading}>WHAT BELONGS HERE</Text>
            {detail.next.map((item, index) => (
              <View style={styles.checkRow} key={item}>
                <View style={styles.checkCircle}><Text style={styles.checkMark}>{String(index + 1).padStart(2, '0')}</Text></View>
                <Text style={styles.checkText}>{item}</Text>
              </View>
            ))}
            <View style={styles.panelFooter}>
              <Text style={styles.panelFooterText}>This workspace is part of {project.name}.</Text>
              <Text style={styles.panelFooterSymbol}>✳</Text>
            </View>
          </View>
          <View style={styles.tip}>
            <Text style={styles.tipLabel}>DESIGN PRINCIPLE</Text>
            <Text style={styles.tipText}>Your project stays together from first reference to finished pattern.</Text>
          </View></>}
        </ScrollView>
      </View>

      <View style={styles.bottomNav}>
        {WORKSPACES.map((item) => (
          <Pressable key={item.key} onPress={() => setActive(item.key)} style={[styles.bottomItem, active === item.key && styles.bottomItemActive]}>
            <Text style={[styles.bottomSymbol, active === item.key && styles.bottomActiveText]}>{item.symbol}</Text>
            <Text style={[styles.bottomLabel, active === item.key && styles.bottomActiveText]}>{item.shortTitle}</Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F5F1EA' },
  inspirationArea: { marginTop: 22 },
  importButton: { backgroundColor: '#71816D', borderRadius: 8, padding: 13, alignItems: 'center' },
  importButtonText: { color: '#FFF', fontSize: 12, fontWeight: '700' },
  referenceLinkRow: { flexDirection: 'row', gap: 8, marginTop: 10 },
  referenceInput: { flex: 1, minHeight: 44, backgroundColor: '#FBF9F5', borderRadius: 8, paddingHorizontal: 12, color: '#44443C', fontSize: 12 },
  referenceSave: { backgroundColor: '#E5E9DF', borderRadius: 8, paddingHorizontal: 18, justifyContent: 'center' },
  referenceSaveText: { color: '#5E705A', fontWeight: '700', fontSize: 11 },
  referenceCount: { color: '#777568', fontSize: 9, letterSpacing: 1.3, fontWeight: '700', marginTop: 22, marginBottom: 10 },
  referenceGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  referenceCard: { width: '31%', backgroundColor: '#FBF9F5', borderRadius: 8, overflow: 'hidden', borderWidth: 1, borderColor: '#E9E2D8' },
  referenceImage: { width: '100%', height: 115, backgroundColor: '#EAE6DE' },
  referenceLinkCard: { height: 115, backgroundColor: '#E8EBE2', padding: 12, justifyContent: 'center' },
  referenceDomain: { color: '#596653', fontFamily: 'Georgia', fontSize: 15 },
  referenceOpen: { color: '#84917C', fontSize: 8, marginTop: 8 },
  referenceRemove: { padding: 8, alignItems: 'flex-end' },
  referenceRemoveText: { color: '#A17F72', fontSize: 9 },
  header: { height: 70, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 25, borderBottomWidth: 1, borderBottomColor: '#E6DED3', backgroundColor: '#FBF9F5' },
  back: { paddingVertical: 10, paddingRight: 18 },
  backText: { color: '#657765', fontSize: 13, fontWeight: '600' },
  projectIdentity: { borderLeftWidth: 1, borderLeftColor: '#E5DED3', paddingLeft: 18 },
  projectEyebrow: { color: '#989184', fontSize: 8, letterSpacing: 1.5, fontWeight: '700' },
  projectName: { color: '#35362F', fontFamily: 'Georgia', fontSize: 19, marginTop: 2, maxWidth: 300 },
  saveStatus: { marginLeft: 'auto', flexDirection: 'row', alignItems: 'center', gap: 7 },
  statusDot: { width: 7, height: 7, borderRadius: 4, backgroundColor: '#82947C' },
  saveText: { color: '#8C8A7F', fontSize: 8, letterSpacing: 1.3, fontWeight: '700' },
  body: { flex: 1, flexDirection: 'row' },
  sidebar: { width: 265, backgroundColor: '#F0ECE4', borderRightWidth: 1, borderRightColor: '#E5DED3', paddingHorizontal: 16, paddingTop: 25 },
  sideLabel: { color: '#999386', fontSize: 9, letterSpacing: 1.7, fontWeight: '700', marginLeft: 10, marginBottom: 13 },
  navItem: { minHeight: 65, borderRadius: 8, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 10, marginBottom: 5, gap: 10, position: 'relative' },
  navItemActive: { backgroundColor: '#FBF9F5' },
  navNumber: { color: '#A7A194', fontSize: 10, fontWeight: '700', width: 20 },
  navCopy: { flex: 1 },
  navTitle: { color: '#55564E', fontSize: 13, fontWeight: '600' },
  navTextActive: { color: '#596E5A' },
  navSubtitle: { color: '#999487', fontSize: 9, marginTop: 4 },
  activeIndicator: { width: 3, height: 25, borderRadius: 2, backgroundColor: '#738571', position: 'absolute', right: 0 },
  sidebarNote: { marginTop: 'auto', marginBottom: 20, backgroundColor: '#E5E8DE', padding: 15, borderRadius: 9 },
  noteGlyph: { color: '#87947F', fontSize: 20 },
  noteTitle: { color: '#5E6B5A', fontFamily: 'Georgia', fontSize: 15, marginTop: 6 },
  noteText: { color: '#858B7C', fontSize: 10, lineHeight: 15, marginTop: 5 },
  content: { flex: 1 },
  contentInner: { paddingHorizontal: 38, paddingTop: 34, paddingBottom: 30, maxWidth: 850, width: '100%', alignSelf: 'center' },
  contentKicker: { color: '#999386', fontSize: 9, letterSpacing: 1.8, fontWeight: '700' },
  contentTitle: { color: '#373830', fontFamily: 'Georgia', fontSize: 35, marginTop: 10 },
  contentDescription: { color: '#858176', fontSize: 13, lineHeight: 21, maxWidth: 570, marginTop: 8 },
  featurePanel: { backgroundColor: '#FBF9F5', borderRadius: 13, borderWidth: 1, borderColor: '#E9E2D8', padding: 23, marginTop: 28 },
  panelTop: { flexDirection: 'row', alignItems: 'center' },
  panelIcon: { width: 47, height: 47, borderRadius: 12, backgroundColor: '#E7EBE1', alignItems: 'center', justifyContent: 'center' },
  panelIconText: { color: '#71806D', fontSize: 25 },
  panelHeading: { marginLeft: 13 },
  panelTitle: { color: '#3D3E36', fontFamily: 'Georgia', fontSize: 20 },
  panelSub: { color: '#9A9589', fontSize: 8, letterSpacing: 1.3, marginTop: 4 },
  comingSoon: { marginLeft: 'auto', borderWidth: 1, borderColor: '#D9DFD3', borderRadius: 20, paddingHorizontal: 10, paddingVertical: 6 },
  comingSoonText: { color: '#788574', fontSize: 8, letterSpacing: 1, fontWeight: '700' },
  panelRule: { height: 1, backgroundColor: '#EAE4DA', marginVertical: 21 },
  checklistHeading: { color: '#999386', fontSize: 9, letterSpacing: 1.5, fontWeight: '700', marginBottom: 12 },
  checkRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 9, gap: 12 },
  checkCircle: { width: 25, height: 25, borderRadius: 13, borderWidth: 1, borderColor: '#D9DFD3', alignItems: 'center', justifyContent: 'center' },
  checkMark: { color: '#7E8B76', fontSize: 8, fontWeight: '700' },
  checkText: { color: '#66665D', fontSize: 12 },
  panelFooter: { borderTopWidth: 1, borderTopColor: '#EAE4DA', marginTop: 17, paddingTop: 15, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  panelFooterText: { color: '#A09A8E', fontSize: 10 },
  panelFooterSymbol: { color: '#A3AD98', fontSize: 18 },
  tip: { marginTop: 17, backgroundColor: '#EAECE4', borderRadius: 9, paddingHorizontal: 17, paddingVertical: 14, flexDirection: 'row', alignItems: 'center', gap: 17 },
  tipLabel: { color: '#788572', fontSize: 8, letterSpacing: 1.2, fontWeight: '700' },
  tipText: { color: '#7F8576', fontSize: 11, flex: 1 },
  bottomNav: { height: 61, backgroundColor: '#FBF9F5', borderTopWidth: 1, borderTopColor: '#E5DED3', flexDirection: 'row', alignItems: 'stretch', justifyContent: 'space-around', paddingHorizontal: 15 },
  bottomItem: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 3, borderTopWidth: 2, borderTopColor: 'transparent' },
  bottomItemActive: { borderTopColor: '#71816D' },
  bottomSymbol: { color: '#9A968A', fontSize: 17 },
  bottomLabel: { color: '#969184', fontSize: 9, fontWeight: '600' },
  bottomActiveText: { color: '#62755F' },
});
