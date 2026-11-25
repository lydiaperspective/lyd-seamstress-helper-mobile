export type WorkspaceKey = 'inspiration' | 'sketchbook' | 'patterns' | 'layout' | 'print';

export type Project = {
  id: number;
  name: string;
  description: string;
  createdAt: string;
  updatedAt: string;
};

export const WORKSPACES: { key: WorkspaceKey; title: string; shortTitle: string; subtitle: string; symbol: string }[] = [
  { key: 'inspiration', title: 'Inspiration', shortTitle: 'Ideas', subtitle: 'References, images & moodboards', symbol: '✳' },
  { key: 'sketchbook', title: 'Sketchbook', shortTitle: 'Sketch', subtitle: 'Draw the garment from every angle', symbol: '✎' },
  { key: 'patterns', title: 'Pattern designer', shortTitle: 'Patterns', subtitle: 'Draft precise, editable pattern pieces', symbol: '⌗' },
  { key: 'layout', title: 'Fabric layout', shortTitle: 'Layout', subtitle: 'Arrange pieces and estimate yardage', symbol: '▧' },
  { key: 'print', title: 'Print & export', shortTitle: 'Export', subtitle: 'Prepare print sheets and projector files', symbol: '⇧' },
];
