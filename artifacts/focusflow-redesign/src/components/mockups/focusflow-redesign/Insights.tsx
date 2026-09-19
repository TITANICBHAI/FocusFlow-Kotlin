/*
 * Compose map: Scaffold + TopAppBar -> InsightsHeader; LazyColumn -> insight cards and
 * outcomes; LinearProgressIndicator -> permission/data-health notice; Canvas draw bars ->
 * seven-day focus chart. callbacks/state: selected range, notice dismissal, retry usage access.
 */
import { useState } from 'react';
import { BarChart3, CheckCircle2, Clock3, Flame, Info, RotateCcw, ShieldAlert, X } from 'lucide-react';
import { BottomNav } from './_shared/BottomNav';
import './_shared/focusflow.css';
import type { FocusFlowTab } from './_shared/tokens';

const bars = [
  { day: 'M', value: 48 }, { day: 'T', value: 75 }, { day: 'W', value: 35 }, { day: 'T', value: 92 },
  { day: 'F', value: 61 }, { day: 'S', value: 22 }, { day: 'S', value: 0 },
];

export function Insights() {
  const [activeTab, setActiveTab] = useState<FocusFlowTab>('stats');
  const [range, setRange] = useState('This week');
  const [noticeVisible, setNoticeVisible] = useState(true);
  const [toast, setToast] = useState('');
  const announce = (message: string) => { setToast(message); window.setTimeout(() => setToast(''), 2200); };

  return (
    <main className="ff-root">
      <div className="ff-page">
        <div className="ff-scroll">
          <header className="ff-topbar">
            <div><div className="ff-eyebrow">Private by default</div><h1 className="ff-title">Insights</h1><p className="ff-subtitle">A useful look back, not a scorecard.</p></div>
            <button className="ff-icon-button" aria-label="Refresh insights" onClick={() => announce('Insights refreshed')}><RotateCcw size={18} /></button>
          </header>

          <div className="ff-row" style={{ gap: 7, marginBottom: 18 }}>
            {['Yesterday', 'This week', '3 months'].map((item) => <button key={item} className={`ff-button ${range === item ? 'ff-button-primary' : 'ff-button-secondary'}`} style={{ minHeight: 36, paddingInline: 11, fontSize: 12 }} onClick={() => setRange(item)}>{item}</button>)}
          </div>

          {noticeVisible && (
            <section className="ff-card ff-card-pad" style={{ borderColor: '#6c5522', background: '#201d19' }}>
              <div className="ff-row" style={{ alignItems: 'flex-start' }}>
                <ShieldAlert size={20} color="#fbbf24" />
                <div className="ff-grow"><strong style={{ fontSize: 14 }}>Usage Access is off</strong><p className="ff-muted ff-small" style={{ lineHeight: 1.45, margin: '5px 0 10px' }}>FocusFlow can show focus sessions, but app-use data will stay incomplete until this permission is enabled.</p><button className="ff-button ff-button-secondary" style={{ minHeight: 34, fontSize: 12 }} onClick={() => announce('Permission settings opened')}>Open permissions</button></div>
                <button className="ff-button ff-button-quiet" style={{ minHeight: 24 }} onClick={() => setNoticeVisible(false)} aria-label="Dismiss notice"><X size={15} /></button>
              </div>
            </section>
          )}

          <div className="ff-section-label">The week so far</div>
          <section className="ff-card ff-card-pad">
            <div className="ff-row ff-between"><div><div className="ff-muted ff-small">Focus time</div><strong className="ff-mono" style={{ display: 'block', fontSize: 25, marginTop: 5 }}>6h 42m</strong></div><span className="ff-chip ff-chip-green">+48m</span></div>
            <div style={{ height: 130, display: 'flex', alignItems: 'end', gap: 10, padding: '22px 6px 0' }}>
              {bars.map((bar, i) => <div key={`${bar.day}-${i}`} style={{ flex: 1, height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'end', alignItems: 'center', gap: 7 }}><div title={`${bar.value} minutes`} style={{ width: '100%', maxWidth: 24, height: `${Math.max(bar.value, 4)}%`, minHeight: 5, borderRadius: '7px 7px 3px 3px', background: i === 3 ? '#8183ff' : '#44498d' }} /><span className="ff-quiet ff-small">{bar.day}</span></div>)}
            </div>
          </section>

          <div className="ff-row" style={{ gap: 10, marginTop: 12 }}>
            <Metric icon={<CheckCircle2 size={18} color="#34d399" />} label="Completed" value="11 tasks" />
            <Metric icon={<Flame size={18} color="#fbbf24" />} label="Streak" value="4 days" />
          </div>

          <div className="ff-section-label">Recent outcomes</div>
          <section className="ff-card" style={{ overflow: 'hidden' }}>
            <Outcome icon={<CheckCircle2 size={18} color="#34d399" />} title="Research launch notes" detail="Completed · 1h 28m focused" />
            <div className="ff-divider" />
            <Outcome icon={<Clock3 size={18} color="#fbbf24" />} title="Outline Q3 planning brief" detail="Partial · 34m focused" />
            <div className="ff-divider" />
            <Outcome icon={<BarChart3 size={18} color="#8183ff" />} title="Review roadmap" detail="Completed · yesterday" />
          </section>

          <section className="ff-card ff-card-pad" style={{ marginTop: 16 }}>
            <div className="ff-row"><Info size={18} color="#aeb1ff" /><strong style={{ fontSize: 14 }}>Your data stays here</strong></div>
            <p className="ff-muted ff-small" style={{ lineHeight: 1.5, margin: '8px 0 0 27px' }}>Stats are calculated on this device from your local focus history. There is no account or sync layer.</p>
          </section>
        </div>
        <BottomNav active={activeTab} onChange={setActiveTab} />
        {toast && <div className="ff-toast">{toast}</div>}
      </div>
    </main>
  );
}

function Metric({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return <div className="ff-card ff-card-pad ff-grow"><div className="ff-row">{icon}<span className="ff-muted ff-small">{label}</span></div><strong style={{ display: 'block', marginTop: 10, fontSize: 17 }}>{value}</strong></div>;
}

function Outcome({ icon, title, detail }: { icon: React.ReactNode; title: string; detail: string }) {
  return <div className="ff-row" style={{ padding: '15px 16px' }}><div style={{ width: 30, height: 30, borderRadius: 10, background: '#202b43', display: 'grid', placeItems: 'center' }}>{icon}</div><div className="ff-grow"><strong style={{ display: 'block', fontSize: 14 }}>{title}</strong><span className="ff-muted ff-small" style={{ display: 'block', marginTop: 3 }}>{detail}</span></div></div>;
}