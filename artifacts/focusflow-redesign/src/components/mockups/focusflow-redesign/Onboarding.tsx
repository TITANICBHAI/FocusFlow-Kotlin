/*
 * Compose map: Column + LinearProgressIndicator -> setup progress, LazyColumn ->
 * permission cards, Button -> open system settings, TextButton -> continue later.
 * State: SettingsViewModel privacy/onboarding flags and Android permission status.
 */
import { useState } from "react";
import { Glyph, ProgressBar, StatusPill } from "./_shared/ui";

export function Onboarding() {
  const [usage, setUsage] = useState(false);
  const [vpn, setVpn] = useState(true);
  return (
    <main className="phone-shell">
      <div className="phone-status"><span>9:41</span><span className="status-icons">••• )))</span></div>
      <div className="screen-content">
        <div className="onboard-wrap">
          <span className="onboard-mark">F</span>
          <p className="eyebrow">A calmer way to focus</p>
          <h2>Set up your focus boundary.</h2>
          <p>FocusFlow needs a small amount of system access to pause distracting apps at the right time. Your tasks and usage data stay on this device.</p>
          <div className="row"><span className="step-copy" style={{ margin: 0 }}>2 of 3 ready</span><span className="small muted">{usage && vpn ? "Almost there" : "Takes about a minute"}</span></div>
          <div style={{ margin: "8px 0 19px" }}><ProgressBar value={usage && vpn ? 1 : .66} tone="primary" /></div>

          <p className="step-copy">Required access</p>
          <div className="card permission-card"><span className="control-icon">U</span><span className="control-copy"><strong>Usage access</strong><span>Lets FocusFlow know which app is open</span></span><button onClick={() => setUsage(true)}>{usage ? "Allowed" : "Allow"}</button></div>
          <div className="card permission-card"><span className="control-icon">V</span><span className="control-copy"><strong>Blocking service</strong><span>Applies your rules while a block is active</span></span><button onClick={() => setVpn(true)}>{vpn ? "Ready" : "Set up"}</button></div>
          <div className="card permission-card"><span className="control-icon">H</span><span className="control-copy"><strong>Home launcher</strong><span>Optional: make your home screen quieter</span></span><button>Later</button></div>

          <div className="onboard-footer">
            <button className="primary-button wide">{usage && vpn ? "Continue to FocusFlow" : "Continue setup"}</button>
            <button className="later">I’ll do this later</button>
            <div className="safe-note" style={{ marginTop: 18 }}><Glyph tone="success">✓</Glyph><span>You can change these permissions any time in Settings.</span></div>
          </div>
        </div>
      </div>
    </main>
  );
}

export default Onboarding;