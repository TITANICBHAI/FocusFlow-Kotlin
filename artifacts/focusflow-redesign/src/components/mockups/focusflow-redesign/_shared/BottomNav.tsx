import { BarChart3, CalendarDays, Shield, SlidersHorizontal, Timer } from 'lucide-react';
import type { FocusFlowTab } from './tokens';

type BottomNavProps = { active: FocusFlowTab; onChange: (tab: FocusFlowTab) => void };

export function BottomNav({ active, onChange }: BottomNavProps) {
  const items: Array<{ id: FocusFlowTab; label: string; icon: typeof Timer }> = [
    { id: 'home', label: 'Today', icon: CalendarDays },
    { id: 'focus', label: 'Focus', icon: Timer },
    { id: 'stats', label: 'Stats', icon: BarChart3 },
    { id: 'defense', label: 'Defense', icon: Shield },
    { id: 'settings', label: 'Settings', icon: SlidersHorizontal },
  ];
  return (
    <nav className="ff-nav" aria-label="Primary navigation">
      {items.map(({ id, label, icon: Icon }) => (
        <button key={id} className={`ff-nav-item ${active === id ? 'active' : ''}`} onClick={() => onChange(id)} aria-label={label}>
          <Icon strokeWidth={active === id ? 2.5 : 1.8} />
          <span>{label}</span>
        </button>
      ))}
    </nav>
  );
}