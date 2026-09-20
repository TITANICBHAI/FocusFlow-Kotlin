import type { ReactNode } from "react";
import "./styles.css";

export type Tab = "today" | "focus" | "insights" | "defense";

export function Glyph({
  children,
  tone = "muted",
}: {
  children: ReactNode;
  tone?: "muted" | "primary" | "success" | "warning" | "danger";
}) {
  return <span className={`glyph glyph-${tone}`} aria-hidden="true">{children}</span>;
}

export function StatusPill({
  children,
  tone = "success",
}: {
  children: ReactNode;
  tone?: "success" | "warning" | "danger" | "neutral";
}) {
  return <span className={`status-pill status-${tone}`}><span className="status-dot" />{children}</span>;
}

export function ProgressBar({ value, tone = "primary" }: { value: number; tone?: "primary" | "success" | "warning" }) {
  return <div className="progress-track" aria-label={`${Math.round(value * 100)} percent`}><span className={`progress-fill fill-${tone}`} style={{ width: `${value * 100}%` }} /></div>;
}

export function BottomNav({ active, onChange }: { active: Tab; onChange?: (tab: Tab) => void }) {
  const items: Array<{ id: Tab; label: string; mark: string }> = [
    { id: "today", label: "Today", mark: "T" },
    { id: "focus", label: "Focus", mark: "F" },
    { id: "insights", label: "Insights", mark: "I" },
    { id: "defense", label: "Defense", mark: "D" },
  ];
  return (
    <nav className="bottom-nav" aria-label="Primary navigation">
      {items.map((item) => (
        <button
          key={item.id}
          className={`nav-item ${active === item.id ? "nav-active" : ""}`}
          onClick={() => onChange?.(item.id)}
          aria-current={active === item.id ? "page" : undefined}
        >
          <span className="nav-mark">{item.mark}</span>
          <span>{item.label}</span>
        </button>
      ))}
    </nav>
  );
}

export function AppFrame({
  children,
  active,
  onTabChange,
  eyebrow,
  title,
  action,
}: {
  children: ReactNode;
  active: Tab;
  onTabChange?: (tab: Tab) => void;
  eyebrow?: string;
  title?: string;
  action?: ReactNode;
}) {
  return (
    <main className="phone-shell">
      <div className="phone-status"><span>9:41</span><span className="status-icons">••• )))</span></div>
      {(eyebrow || title || action) && (
        <header className="topbar">
          <div>
            {eyebrow && <p className="eyebrow">{eyebrow}</p>}
            {title && <h1>{title}</h1>}
          </div>
          {action}
        </header>
      )}
      <section className="screen-content">{children}</section>
      <BottomNav active={active} onChange={onTabChange} />
    </main>
  );
}

export function SectionLabel({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return <div className="section-label"><h2>{children}</h2>{action}</div>;
}

export function Metric({ value, label, accent = false }: { value: string; label: string; accent?: boolean }) {
  return <div className="metric"><strong className={accent ? "text-primary" : ""}>{value}</strong><span>{label}</span></div>;
}

export function AppAvatar({ label, color = "indigo" }: { label: string; color?: "indigo" | "blue" | "green" | "orange" }) {
  return <span className={`app-avatar avatar-${color}`}>{label.slice(0, 1)}</span>;
}