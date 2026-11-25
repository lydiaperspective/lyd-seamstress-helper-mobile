import React, { useCallback, useEffect, useState } from 'react';
import { Alert, FlatList, KeyboardAvoidingView, Modal, Platform, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../App';
import { createProject, deleteProject, getProjects } from '../data/database';
import type { Project } from '../types';

type Navigation = NativeStackNavigationProp<RootStackParamList, 'Projects'>;

export function ProjectsScreen() {
  const navigation = useNavigation<Navigation>();
  const [projects, setProjects] = useState<Project[]>([]);
  const [modalVisible, setModalVisible] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);

  const refresh = useCallback(async () => setProjects(await getProjects()), []);
  useFocusEffect(useCallback(() => { refresh(); }, [refresh]));

  const addProject = async () => {
    if (!name.trim() || saving) return;
    setSaving(true);
    try {
      const project = await createProject(name, description);
      setName('');
      setDescription('');
      setModalVisible(false);
      await refresh();
      navigation.navigate('Project', { project });
    } catch {
      Alert.alert('Could not create project', 'Please try again.');
    } finally {
      setSaving(false);
    }
  };

  const confirmDelete = (project: Project) => Alert.alert(
    'Delete project?',
    `“${project.name}” will be removed from this device.`,
    [{ text: 'Cancel', style: 'cancel' }, { text: 'Delete', style: 'destructive', onPress: async () => { await deleteProject(project.id); refresh(); } }]
  );

  return (
    <View style={styles.screen}>
      <View style={styles.topBar}>
        <View style={styles.brandMark}><Text style={styles.brandMarkText}>S</Text></View>
        <View style={styles.brandCopy}>
          <Text style={styles.eyebrow}>YOUR CREATIVE WORKROOM</Text>
          <Text style={styles.brand}>Seamwork <Text style={styles.brandLight}>Studio</Text></Text>
        </View>
        <View style={styles.topRight}><Text style={styles.deviceLabel}>MADE FOR THE MAKING</Text><Text style={styles.deviceSub}>A calm space for garment design</Text></View>
      </View>

      <View style={styles.headingRow}>
        <View>
          <Text style={styles.kicker}>THE WORKROOM</Text>
          <Text style={styles.heading}>Your projects</Text>
          <Text style={styles.intro}>Every garment begins with an idea.</Text>
        </View>
        <Pressable accessibilityRole="button" style={styles.primaryButton} onPress={() => setModalVisible(true)}>
          <Text style={styles.plus}>＋</Text><Text style={styles.primaryButtonText}>New project</Text>
        </Pressable>
      </View>

      {projects.length === 0 ? (
        <View style={styles.empty}>
          <View style={styles.emptyIllustration}>
            <View style={styles.paperBack} /><View style={styles.paperFront}>
              <Text style={styles.paperLine}>✳</Text><View style={styles.paperRule} /><View style={styles.paperRuleShort} /><Text style={styles.paperNeedle}>／</Text>
            </View>
          </View>
          <Text style={styles.emptyTitle}>A fresh page awaits</Text>
          <Text style={styles.emptyText}>Create a project for your next garment. Your inspiration, sketches and patterns will live together here.</Text>
          <Pressable style={styles.secondaryButton} onPress={() => setModalVisible(true)}><Text style={styles.secondaryButtonText}>＋  Create your first project</Text></Pressable>
        </View>
      ) : (
        <FlatList
          data={projects}
          keyExtractor={(item) => String(item.id)}
          numColumns={2}
          columnWrapperStyle={styles.projectRow}
          contentContainerStyle={styles.projectList}
          renderItem={({ item, index }) => (
            <Pressable style={styles.projectCard} onPress={() => navigation.navigate('Project', { project: item })} onLongPress={() => confirmDelete(item)}>
              <View style={[styles.cardArt, index % 3 === 0 ? styles.cardArtSage : index % 3 === 1 ? styles.cardArtClay : styles.cardArtBlue]}>
                <Text style={styles.cardArtGlyph}>{['✳', '⌁', '◌'][index % 3]}</Text>
                <Text style={styles.cardArtLabel}>GARMENT PROJECT</Text>
              </View>
              <View style={styles.cardBody}>
                <Text style={styles.cardTitle} numberOfLines={1}>{item.name}</Text>
                <Text style={styles.cardDescription} numberOfLines={2}>{item.description || 'No notes yet'}</Text>
                <Text style={styles.cardDate}>UPDATED {new Date(item.updatedAt).toLocaleDateString()}</Text>
              </View>
            </Pressable>
          )}
        />
      )}

      <Modal visible={modalVisible} transparent animationType="fade" onRequestClose={() => setModalVisible(false)}>
        <KeyboardAvoidingView style={styles.modalBackdrop} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setModalVisible(false)} />
          <View style={styles.modalCard}>
            <Text style={styles.kicker}>START SOMETHING NEW</Text>
            <Text style={styles.modalTitle}>Create a project</Text>
            <Text style={styles.fieldLabel}>PROJECT NAME</Text>
            <TextInput autoFocus value={name} onChangeText={setName} placeholder="e.g. Linen summer dress" placeholderTextColor="#A6A197" style={styles.input} returnKeyType="next" />
            <Text style={styles.fieldLabel}>NOTES <Text style={styles.optional}>(OPTIONAL)</Text></Text>
            <TextInput value={description} onChangeText={setDescription} placeholder="A little about what you’re making…" placeholderTextColor="#A6A197" style={[styles.input, styles.notesInput]} multiline />
            <View style={styles.modalActions}>
              <Pressable style={styles.cancelButton} onPress={() => setModalVisible(false)}><Text style={styles.cancelText}>Cancel</Text></Pressable>
              <Pressable disabled={!name.trim() || saving} style={[styles.primaryButton, (!name.trim() || saving) && styles.disabledButton]} onPress={addProject}><Text style={styles.primaryButtonText}>{saving ? 'Creating…' : 'Create project  →'}</Text></Pressable>
            </View>
          </View>
        </KeyboardAvoidingView>
      </Modal>
      <Text style={styles.footer}>MADE WITH CARE · BUILT FOR YOUR NEXT CREATION</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F5F1EA', paddingHorizontal: 34, paddingTop: 18 },
  topBar: { height: 66, flexDirection: 'row', alignItems: 'center', borderBottomWidth: 1, borderBottomColor: '#E6DED3' },
  brandMark: { width: 38, height: 38, borderRadius: 12, backgroundColor: '#657765', alignItems: 'center', justifyContent: 'center' },
  brandMarkText: { color: '#FFFDF8', fontFamily: 'Georgia', fontSize: 24, fontStyle: 'italic' },
  brandCopy: { marginLeft: 11 },
  eyebrow: { fontSize: 8, letterSpacing: 2, color: '#858276', fontWeight: '700' },
  brand: { color: '#34352F', fontSize: 21, fontFamily: 'Georgia' },
  brandLight: { color: '#7B806F', fontStyle: 'italic' },
  topRight: { marginLeft: 'auto', alignItems: 'flex-end' },
  deviceLabel: { color: '#7A806F', fontSize: 9, letterSpacing: 1.5, fontWeight: '700' },
  deviceSub: { color: '#969185', fontSize: 11, marginTop: 4 },
  headingRow: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', marginTop: 34, marginBottom: 22 },
  kicker: { color: '#8B897D', fontSize: 9, fontWeight: '700', letterSpacing: 2 },
  heading: { color: '#34352F', fontSize: 34, fontFamily: 'Georgia', marginTop: 5 },
  intro: { color: '#858176', fontSize: 13, marginTop: 5 },
  primaryButton: { backgroundColor: '#657765', borderRadius: 9, paddingHorizontal: 17, paddingVertical: 13, flexDirection: 'row', alignItems: 'center', gap: 7 },
  plus: { color: '#FFFDF8', fontSize: 18, lineHeight: 19 },
  primaryButtonText: { color: '#FFFDF8', fontSize: 13, fontWeight: '600' },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingBottom: 42 },
  emptyIllustration: { width: 150, height: 126, marginBottom: 23, alignItems: 'center', justifyContent: 'center' },
  paperBack: { position: 'absolute', width: 83, height: 106, backgroundColor: '#E5DDD0', borderRadius: 4, transform: [{ rotate: '9deg' }], left: 35, top: 10 },
  paperFront: { width: 83, height: 106, backgroundColor: '#FFFCF6', borderRadius: 4, borderWidth: 1, borderColor: '#E8E0D4', alignItems: 'center', paddingTop: 10, transform: [{ rotate: '-5deg' }], elevation: 2 },
  paperLine: { fontSize: 26, color: '#A1AA96' },
  paperRule: { width: 48, height: 1, backgroundColor: '#D8D2C7', marginTop: 7 },
  paperRuleShort: { width: 34, height: 1, backgroundColor: '#E4DED4', marginTop: 6 },
  paperNeedle: { fontSize: 27, color: '#B27D67', marginTop: 1 },
  emptyTitle: { color: '#393A33', fontSize: 24, fontFamily: 'Georgia' },
  emptyText: { color: '#898579', fontSize: 13, textAlign: 'center', lineHeight: 20, maxWidth: 390, marginTop: 8 },
  secondaryButton: { marginTop: 21, paddingHorizontal: 18, paddingVertical: 12, borderRadius: 8, borderWidth: 1, borderColor: '#A8B09F' },
  secondaryButtonText: { color: '#657765', fontSize: 13, fontWeight: '600' },
  projectList: { paddingBottom: 25 },
  projectRow: { gap: 18, marginBottom: 18 },
  projectCard: { flex: 1, backgroundColor: '#FFFCF7', borderRadius: 12, overflow: 'hidden', borderWidth: 1, borderColor: '#E9E2D8' },
  cardArt: { height: 112, alignItems: 'center', justifyContent: 'center' },
  cardArtSage: { backgroundColor: '#E5E9DE' },
  cardArtClay: { backgroundColor: '#EFE1D7' },
  cardArtBlue: { backgroundColor: '#E1E7E5' },
  cardArtGlyph: { fontSize: 39, color: '#7D8978', fontFamily: 'Georgia' },
  cardArtLabel: { position: 'absolute', bottom: 10, fontSize: 8, letterSpacing: 2, color: '#858579', fontWeight: '700' },
  cardBody: { padding: 15 },
  cardTitle: { color: '#373830', fontSize: 18, fontFamily: 'Georgia' },
  cardDescription: { color: '#898579', fontSize: 12, marginTop: 5, minHeight: 30 },
  cardDate: { color: '#9B968A', fontSize: 8, letterSpacing: 1.2, marginTop: 12, fontWeight: '700' },
  footer: { textAlign: 'center', color: '#A29C90', fontSize: 8, letterSpacing: 1.5, paddingVertical: 12 },
  modalBackdrop: { flex: 1, backgroundColor: 'rgba(38,38,32,0.38)', alignItems: 'center', justifyContent: 'center', padding: 24 },
  modalCard: { width: '100%', maxWidth: 480, backgroundColor: '#FBF9F5', borderRadius: 16, padding: 27, elevation: 8 },
  modalTitle: { fontFamily: 'Georgia', fontSize: 27, color: '#35362F', marginTop: 7, marginBottom: 22 },
  fieldLabel: { color: '#858276', fontSize: 9, fontWeight: '700', letterSpacing: 1.4, marginBottom: 7, marginTop: 11 },
  optional: { color: '#AAA497', fontWeight: '400', letterSpacing: 0 },
  input: { borderWidth: 1, borderColor: '#E1DACE', backgroundColor: '#FFFDF9', borderRadius: 8, paddingHorizontal: 12, paddingVertical: 12, color: '#373830', fontSize: 14 },
  notesInput: { height: 76, textAlignVertical: 'top' },
  modalActions: { flexDirection: 'row', justifyContent: 'flex-end', alignItems: 'center', gap: 12, marginTop: 23 },
  cancelButton: { paddingHorizontal: 15, paddingVertical: 12 },
  cancelText: { color: '#77756B', fontSize: 13 },
  disabledButton: { opacity: 0.45 },
});
