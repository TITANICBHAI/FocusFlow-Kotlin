/*
 * Compose map: Scaffold -> FocusShell; TopAppBar -> FocusHeader; CircularProgressIndicator
 * / Canvas drawArc -> remaining-time ring; Button onComplete/onStop -> FocusSessionViewModel;
 * ModalBottomSheet -> emergency override; callbacks/state consumed: isActive, remainingMinutes,
 * showOverride, extendMinutes.
 */
import { useState } from 'react';
import { Check, ChevronDown, LockKeyhole, ShieldCheck, Siren, Square, TimerReset, Unlock, X } from 'lucide-react';
import { BottomNav } from './_shared/BottomNav';
import './_shared/focusflow.css';
import type { FocusFlowTab } from './_shared/tokens';

export function Focus() {
  const [activeTab, setActiveTab] = useState<FocusFlowTab>('focus');
  const [isActive, setIsActive] = useState(true);
  const [showOverride, setShowOverride] = useState(false);
  const [showExtend, setShowExtend] = useState(false);
  const [toast, setToast] = useState('');
  const totalSeconds = 90 * 60;
  const remainingSeconds = 54 * 60 + 18;
  const progress = remainingSeconds / totalSeconds;
  const dash = 2 * Math.PI * 106;

  const announce = (message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(''), 2200);
  };

  return (
    <main className="ff-root">
      <div className="ff-page">
        <div className="ff-scroll">
          <header className="ff-topbar" style={{ marginBottom: 18 }}>
            <div>
              <div className="ff-eyebrow">Focus session · Active</div>
              <h1 className="ff-title">Stay with the work.</h1>
            </div>
            <span className="ff-chip ff-chip-green"><span style={{ width: 7, height: 7, borderRadius: 99, background: '#34d399' }} /> Locked</span>
          </header>

          {!isActive ? (
            <section className="ff-card ff-card-pad" style={{ textAlign: 'center', paddingBlock: 28 }}>
              <div className="ff-chip ff-chip-amber">READY TO START</div>
              <h2 style={{ fontSize: 23, letterSpacing: '-.03em', margin: '15px 0 7px' }}>Research launch notes</h2>
              <p className="ff-muted" style={{ margin: '0 auto 20px', maxWidth: 250 }}>90 minutes of protected time. Selected apps will be blocked.</p>
              <button className="ff-button ff-button-primary" onClick={() => setIsActive(true)}><LockKeyhole size={17} /> Start focus</button>
            </section>
          ) : (
            <>
              <section className="ff-card ff-card-pad" style={{ textAlign: 'center', paddingTop: 24 }}>
                <div className="ff-chip" style={{ color: '#aeb1ff', background: '#272b65' }}>DEEP WORK</div>
                <h2 style={{ fontSize: 23, letterSpacing: '-.03em', margin: '14px 0 4px' }}>Research launch notes</h2>
                <p className="ff-muted ff-small" style={{ margin: 0 }}>Wednesday · 09:00 – 10:30</p>
                <div style={{ position: 'relative', width: 248, height: 248, margin: '24px auto 20px' }}>
                  <svg width="248" height="248" viewBox="0 0 248 248" role="img" aria-label="54 minutes remaining">
                    <circle cx="124" cy="124" r="106" fill="none" stroke="#27334d" strokeWidth="12" />
                    <circle cx="124" cy="124" r="106" fill="none" stroke="#6366f1" strokeWidth="12" strokeLinecap="round" strokeDasharray={dash} strokeDashoffset={dash * (1 - progress)} transform="rotate(-90 124 124)" />
                  </svg>
                  <div style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center' }}>
                    <div>
                      <div className="ff-mono" style={{ fontSize: 35, letterSpacing: '-.08em' }}>54:18</div>
                      <div className="ff-muted ff-small" style={{ marginTop: 6 }}>remaining</div>
                    </div>
                  </div>
                </div>
                <div className="ff-row" style={{ justifyContent: 'center', gap: 8 }}>
                  <span className="ff-chip ff-chip-green"><LockKeyhole size={13} /> Apps are blocked</span>
                  <span className="ff-chip">36 min focused</span>
                </div>
              </section>

              <section className="ff-card" style={{ marginTop: 14, overflow: 'hidden' }}>
                <div className="ff-row ff-between" style={{ padding: '16px 16px 13px' }}>
                  <div className="ff-row"><ShieldCheck size={20} color="#34d399" /><strong style={{ fontSize: 15 }}>Protection is on</strong></div>
                  <ChevronDown size={17} color="#68758d" />
                </div>
                <div className="ff-divider" />
                <div className="ff-row ff-between" style={{ padding: '14px 16px' }}>
                  <span className="ff-muted ff-small">Blocked during this session</span>
                  <span style={{ fontWeight: 700, fontSize: 14 }}>12 apps</span>
                </div>
                <div className="ff-row ff-between" style={{ padding: '0 16px 15px' }}>
                  <span className="ff-muted ff-small">Allowed</span>
                  <span style={{ fontWeight: 700, fontSize: 14, color: '#aeb1ff' }}>Phone · Messages</span>
                </div>
              </section>

              <div className="ff-row" style={{ marginTop: 16 }}>
                <button className="ff-button ff-button-secondary ff-grow" onClick={() => setShowExtend(true)}><TimerReset size={17} /> Extend</button>
                <button className="ff-button ff-button-primary ff-grow" onClick={() => { setIsActive(false); announce('Focus session completed'); }}><Check size={17} /> Complete</button>
              </div>
              <button className="ff-button ff-button-quiet ff-button-wide" style={{ marginTop: 10 }} onClick={() => announce('Stop confirmation opened')}><Square size={14} /> Stop session</button>
              <button className="ff-button ff-button-danger ff-button-wide" style={{ marginTop: 20 }} onClick={() => setShowOverride(true)}><Siren size={16} /> Emergency override</button>
              <p className="ff-quiet ff-small" style={{ textAlign: 'center', lineHeight: 1.4, margin: '8px 20px 0' }}>Only use this if you need immediate access. The override is recorded in your local history.</p>
            </>
          )}
        </div>
        <BottomNav active={activeTab} onChange={setActiveTab} />

        {showExtend && (
          <div className="ff-toast" role="dialog">
            <div className="ff-row ff-between"><strong>Extend session</strong><button className="ff-button ff-button-quiet" onClick={() => setShowExtend(false)}><X size={16} /></button></div>
            <div className="ff-row" style={{ marginTop: 13 }}>
              {['15 min', '30 min', '60 min'].map((value) => <button key={value} className="ff-button ff-button-secondary ff-grow" onClick={() => { setShowExtend(false); announce(`Session extended by ${value}`); }}>{value}</button>)}
            </div>
          </div>
        )}
        {showOverride && (
          <div className="ff-toast" role="dialog" style={{ borderColor: '#7b3845', textAlign: 'left' }}>
            <div className="ff-row ff-between"><strong style={{ color: '#f87171' }}>Leave focus mode?</strong><button className="ff-button ff-button-quiet" onClick={() => setShowOverride(false)}><X size={16} /></button></div>
            <p className="ff-muted ff-small" style={{ lineHeight: 1.5 }}>This will unlock blocked apps and add an override to your session history.</p>
            <div className="ff-row"><button className="ff-button ff-button-quiet ff-grow" onClick={() => setShowOverride(false)}>Keep working</button><button className="ff-button ff-button-danger ff-grow" onClick={() => { setShowOverride(false); setIsActive(false); announce('Focus mode ended safely'); }}><Unlock size={15} /> Override</button></div>
          </div>
        )}
        {toast && <div className="ff-toast">{toast}</div>}
      </div>
    </main>
  );
}