/*
 * Compose map: Scaffold -> DefenseShell; Card/Surface -> protection status and
 * summaries; Switch -> defense controls; Button onEmergencyStop -> guarded
 * FocusSessionViewModel.stopFocusMode. callbacks/state consumed: protection toggles,
 * allowance value, permission notice, destructive confirmation.
 */
import { useState } from 'react';
import { AlertTriangle, Check, ChevronRight, Clock3, LockKeyhole, Shield, ShieldCheck, Smartphone, WifiOff, X } from 'lucide-react';
import { BottomNav } from './_shared/BottomNav';
import './_shared/focusflow.css';
import type { FocusFlowTab } from './_shared/tokens';

export function Defense() {
  const [activeTab, setActiveTab] = useState<FocusFlowTab>('defense');
  const [alwaysOn, setAlwaysOn] = useState(true);
  const [standalone, setStandalone] = useState(false);
  const [allowance, setAllowance] = useState(true);
  const [confirm, setConfirm] = useState(false);
  const [toast, setToast] = useState('');
  const announce = (message: string) => { setToast(message); window.setTimeout(() => setToast(''), 2200); };

  return (
    <main className="ff-root">
      <div className="ff-page">
        <div className="ff-scroll">
          <header className="ff-topbar" style={{ marginBottom: 19 }}>
            <div><div className="ff-eyebrow">Quiet control room</div><h1 className="ff-title">Defense</h1><p className="ff-subtitle">Your protection, at a glance.</p></div>
            <div style={{ width: 42, height: 42, borderRadius: 15, display: 'grid', placeItems: 'center', background: '#123d38', color: '#34d399' }}><Shield size={21} /></div>
          </header>

          <section className="ff-card ff-card-pad" style={{ background: '#142c2b', borderColor: '#246052' }}>
            <div className="ff-row ff-between">
              <div className="ff-row"><div style={{ width: 36, height: 36, borderRadius: 12, background: '#1d5b4e', display: 'grid', placeItems: 'center' }}><ShieldCheck size={20} color="#34d399" /></div><div><strong style={{ fontSize: 18 }}>Protection ready</strong><div className="ff-small" style={{ color: '#8cd6ba', marginTop: 3 }}>No active blocks right now</div></div></div>
              <Check size={19} color="#34d399" />
            </div>
            <div className="ff-row" style={{ marginTop: 18, gap: 8 }}><span className="ff-chip ff-chip-green">Accessibility</span><span className="ff-chip ff-chip-green">Usage Access</span><span className="ff-chip ff-chip-green">VPN</span></div>
          </section>

          <div className="ff-section-label">Active controls</div>
          <section className="ff-card" style={{ overflow: 'hidden' }}>
            <ControlRow icon={<LockKeyhole size={19} color="#aeb1ff" />} title="Always-On blocking" detail="3 apps blocked around the clock" checked={alwaysOn} onChange={setAlwaysOn} />
            <div className="ff-divider" />
            <ControlRow icon={<Clock3 size={19} color="#fbbf24" />} title="Standalone block" detail={standalone ? 'Block running · 58 min left' : 'Block selected apps outside Focus'} checked={standalone} onChange={(value) => { setStandalone(value); announce(value ? 'Standalone block started' : 'Standalone block stopped'); }} />
            <div className="ff-divider" />
            <ControlRow icon={<WifiOff size={19} color="#8183ff" />} title="Network blocking" detail="VPN list follows your blocked apps" checked={true} onChange={() => announce('Network blocking is managed by VPN settings')} />
          </section>

          <div className="ff-section-label">App protection</div>
          <section className="ff-card ff-card-pad">
            <div className="ff-row ff-between"><div className="ff-row"><Smartphone size={18} color="#aeb1ff" /><span style={{ fontWeight: 600, fontSize: 14 }}>Blocked apps</span></div><strong>12</strong></div>
            <div className="ff-row ff-between" style={{ marginTop: 15 }}><div className="ff-row"><ShieldCheck size={18} color="#34d399" /><span style={{ fontWeight: 600, fontSize: 14 }}>Allowed during focus</span></div><strong>2</strong></div>
            <button className="ff-button ff-button-secondary ff-button-wide" style={{ marginTop: 17 }} onClick={() => announce('App list opened')}>Manage app lists <ChevronRight size={16} /></button>
          </section>

          <section className="ff-card ff-card-pad" style={{ marginTop: 14 }}>
            <div className="ff-row ff-between"><div><strong style={{ fontSize: 15 }}>Daily allowance</strong><p className="ff-muted ff-small" style={{ margin: '4px 0 0' }}>Instagram · today</p></div><button className="ff-button ff-button-quiet" onClick={() => setAllowance(!allowance)}>{allowance ? 'On' : 'Off'}</button></div>
            <div className="ff-progress" style={{ marginTop: 14 }}><span style={{ width: allowance ? '62%' : '0%', background: '#fbbf24' }} /></div>
            <div className="ff-row ff-between ff-small" style={{ marginTop: 7 }}><span className="ff-muted">18 min used</span><span style={{ color: '#fbbf24' }}>12 min left</span></div>
          </section>

          <div className="ff-section-label">System access</div>
          <section className="ff-card ff-card-pad">
            <div className="ff-row"><ShieldCheck size={18} color="#34d399" /><div className="ff-grow"><strong style={{ fontSize: 14 }}>All required access granted</strong><p className="ff-muted ff-small" style={{ margin: '4px 0 0', lineHeight: 1.4 }}>FocusFlow can enforce blocks and read local usage.</p></div><ChevronRight size={17} color="#68758d" /></div>
          </section>

          <button className="ff-button ff-button-danger ff-button-wide" style={{ marginTop: 20 }} onClick={() => setConfirm(true)}><AlertTriangle size={16} /> Stop all protection</button>
          <p className="ff-quiet ff-small" style={{ textAlign: 'center', margin: '9px 16px 0', lineHeight: 1.45 }}>Protection changes are intentionally kept secondary, so the safe default stays visible.</p>
        </div>
        <BottomNav active={activeTab} onChange={setActiveTab} />
        {confirm && <div className="ff-toast" role="dialog" style={{ textAlign: 'left', borderColor: '#7b3845' }}><div className="ff-row ff-between"><strong style={{ color: '#f87171' }}>Stop all protection?</strong><button className="ff-button ff-button-quiet" onClick={() => setConfirm(false)}><X size={16} /></button></div><p className="ff-muted ff-small" style={{ lineHeight: 1.5 }}>This disables Always-On and Standalone blocking. Focus sessions will keep their own protection.</p><div className="ff-row"><button className="ff-button ff-button-quiet ff-grow" onClick={() => setConfirm(false)}>Cancel</button><button className="ff-button ff-button-danger ff-grow" onClick={() => { setConfirm(false); setAlwaysOn(false); setStandalone(false); announce('Protection stopped'); }}>Stop protection</button></div></div>}
        {toast && <div className="ff-toast">{toast}</div>}
      </div>
    </main>
  );
}

function ControlRow({ icon, title, detail, checked, onChange }: { icon: React.ReactNode; title: string; detail: string; checked: boolean; onChange: (value: boolean) => void }) {
  return <div className="ff-row" style={{ padding: '16px' }}><div style={{ width: 32, height: 32, borderRadius: 10, background: '#202b43', display: 'grid', placeItems: 'center' }}>{icon}</div><div className="ff-grow"><strong style={{ display: 'block', fontSize: 14 }}>{title}</strong><span className="ff-muted ff-small" style={{ display: 'block', marginTop: 4 }}>{detail}</span></div><button aria-label={`${title} ${checked ? 'on' : 'off'}`} onClick={() => onChange(!checked)} style={{ width: 46, height: 28, padding: 3, border: 0, borderRadius: 99, background: checked ? '#6366f1' : '#303b55', cursor: 'pointer', textAlign: checked ? 'right' : 'left' }}><span style={{ display: 'inline-block', width: 22, height: 22, borderRadius: 99, background: checked ? '#fff' : '#98a5bd' }} /></button></div>;
}