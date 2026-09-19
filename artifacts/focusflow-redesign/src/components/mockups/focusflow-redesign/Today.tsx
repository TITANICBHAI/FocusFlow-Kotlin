/*
 * Compose map: Scaffold -> page shell + BottomNav; TopAppBar -> TodayHeader;
 * LazyColumn -> timeline/task rows; Button onStart -> FocusSessionViewModel.startFocusMode(taskId);
 * callbacks/state: selected task, completed task ids, quick-add sheet visibility.
 */
import { useState } from 'react';
import { Bell, Check, ChevronRight, Clock3, MoreHorizontal, Plus, ShieldCheck, Sparkles, Timer } from 'lucide-react';
import { BottomNav } from './_shared/BottomNav';
import './_shared/focusflow.css';
import type { FocusFlowTab } from './_shared/tokens';

const tasks = [
  { id: 'research', time: '09:00', end: '10:30', title: 'Research launch notes', meta: 'Deep work · 90 min', tone: 'indigo' },
  { id: 'break', time: '10:30', end: '10:45', title: 'Walk away from the desk', meta: 'Break · 15 min', tone: 'quiet' },
  { id: 'outline', time: '11:00', end: '12:00', title: 'Outline Q3 planning brief', meta: 'Writing · 60 min', tone: 'amber' },
  { id: 'lunch', time: '12:00', end: '13:00', title: 'Lunch and open time', meta: 'Personal · 60 min', tone: 'quiet' },
];

export function Today() {
  const [activeTab, setActiveTab] = useState<FocusFlowTab>('home');
  const [completed, setCompleted] = useState<string[]>(['break']);
  const [quickAdd, setQuickAdd] = useState(false);
  const [toast, setToast] = useState('');
  const doneCount = completed.length;

  const announce = (message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(''), 2200);
  };

  return (
    <main className="ff-root">
      <div className="ff-page">
        <div className="ff-scroll">
          <header className="ff-topbar">
            <div>
              <div className="ff-eyebrow">Wednesday · 24 April</div>
              <h1 className="ff-title">A steady day, Mira.</h1>
              <p className="ff-subtitle">One clear next step is enough.</p>
            </div>
            <button className="ff-icon-button" aria-label="Notifications" onClick={() => announce('No new reminders')}>
              <Bell size={19} />
            </button>
          </header>

          <section className="ff-card ff-card-pad" style={{ borderColor: '#383d88', background: 'linear-gradient(145deg, #1a2140, #151c2e)' }}>
            <div className="ff-row ff-between">
              <div>
                <div className="ff-chip" style={{ color: '#aeb1ff', background: '#2a2d66' }}><Sparkles size={13} /> NEXT UP</div>
                <h2 style={{ margin: '14px 0 5px', fontSize: 21, letterSpacing: '-.03em' }}>Research launch notes</h2>
                <div className="ff-row ff-muted ff-small"><Clock3 size={14} /> 09:00 – 10:30 · 90 min</div>
              </div>
              <div className="ff-mono" style={{ color: '#aeb1ff', fontSize: 13 }}>01</div>
            </div>
            <div className="ff-row" style={{ marginTop: 20 }}>
              <button className="ff-button ff-button-primary ff-grow" onClick={() => announce('Focus session ready to start') }><Timer size={17} /> Start focus</button>
              <button className="ff-icon-button" aria-label="More task options" onClick={() => announce('Task options opened')}><MoreHorizontal size={19} /></button>
            </div>
          </section>

          <div className="ff-row ff-between" style={{ marginTop: 25 }}>
            <div>
              <div className="ff-section-label" style={{ margin: 0 }}>Today</div>
              <div className="ff-muted ff-small" style={{ marginTop: 4 }}>{doneCount} of {tasks.length} blocks completed</div>
            </div>
            <div className="ff-mono ff-small" style={{ color: '#aeb1ff' }}>32%</div>
          </div>
          <div className="ff-progress" style={{ marginTop: 10 }}><span style={{ width: '32%' }} /></div>

          <div className="ff-section-label">Your timeline</div>
          <section className="ff-card" style={{ overflow: 'hidden' }}>
            {tasks.map((task, index) => {
              const isDone = completed.includes(task.id);
              return (
                <div key={task.id} className="ff-row" style={{ padding: '15px 16px', alignItems: 'flex-start', opacity: isDone ? .58 : 1 }}>
                  <div className="ff-mono ff-small ff-muted" style={{ width: 43, paddingTop: 3 }}>{task.time}</div>
                  <div style={{ width: 2, alignSelf: 'stretch', minHeight: 43, background: index === 0 ? '#6366f1' : '#303b55', borderRadius: 2 }} />
                  <button
                    onClick={() => !isDone && setCompleted([...completed, task.id])}
                    aria-label={isDone ? `${task.title} completed` : `Complete ${task.title}`}
                    style={{ width: 22, height: 22, marginTop: 1, borderRadius: 8, border: `1px solid ${isDone ? '#34d399' : '#45516c'}`, background: isDone ? '#123d38' : 'transparent', color: '#34d399', display: 'grid', placeItems: 'center', cursor: isDone ? 'default' : 'pointer' }}
                  >{isDone && <Check size={14} />}</button>
                  <div className="ff-grow">
                    <div className="ff-row ff-between">
                      <strong style={{ fontSize: 14, fontWeight: 600, textDecoration: isDone ? 'line-through' : 'none' }}>{task.title}</strong>
                      {index === 0 && !isDone && <span className="ff-chip" style={{ padding: '4px 7px', color: '#aeb1ff', background: '#272b65' }}>Next</span>}
                    </div>
                    <div className="ff-muted ff-small" style={{ marginTop: 4 }}>{task.end} · {task.meta}</div>
                  </div>
                  <ChevronRight size={16} color="#68758d" style={{ marginTop: 4 }} />
                </div>
              );
            })}
          </section>

          <button className="ff-button ff-button-secondary ff-button-wide" style={{ marginTop: 14 }} onClick={() => setQuickAdd(true)}><Plus size={17} /> Add task</button>

          <section className="ff-card ff-card-pad" style={{ marginTop: 18 }}>
            <div className="ff-row">
              <ShieldCheck size={19} color="#34d399" />
              <div className="ff-grow">
                <strong style={{ fontSize: 14 }}>Protection is ready</strong>
                <div className="ff-muted ff-small" style={{ marginTop: 3 }}>FocusFlow can block selected apps when you start.</div>
              </div>
              <span className="ff-chip ff-chip-green">Ready</span>
            </div>
          </section>
        </div>
        <BottomNav active={activeTab} onChange={setActiveTab} />
        {quickAdd && (
          <div className="ff-toast" role="dialog">
            <div className="ff-row ff-between"><strong>Quick add</strong><button className="ff-button ff-button-quiet" onClick={() => setQuickAdd(false)}>Close</button></div>
            <div className="ff-muted ff-small" style={{ margin: '8px 0 12px' }}>A task can be added from the Android schedule sheet.</div>
            <button className="ff-button ff-button-primary ff-button-wide" onClick={() => { setQuickAdd(false); announce('Quick add opened'); }}>Continue</button>
          </div>
        )}
        {toast && <div className="ff-toast">{toast}</div>}
      </div>
    </main>
  );
}