export const ff = {
  bg: '#0d1322',
  surface: '#151c2e',
  surfaceRaised: '#1c253b',
  border: '#263249',
  primary: '#6366f1',
  primarySoft: '#272b65',
  text: '#f4f6fb',
  muted: '#98a5bd',
  quiet: '#68758d',
  green: '#34d399',
  greenSoft: '#123d38',
  amber: '#fbbf24',
  amberSoft: '#44351a',
  red: '#f87171',
  redSoft: '#47242b',
} as const;

export type FocusFlowTab = 'home' | 'focus' | 'stats' | 'defense' | 'settings';