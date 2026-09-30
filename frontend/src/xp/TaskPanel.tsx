import { useId, type ReactNode } from "react";

type Props = {
  title: string;
  primary?: boolean;
  children: ReactNode;
};

export function TaskPanel({ title, primary, children }: Props) {
  const headingId = useId();
  return (
    <section
      className={primary ? "xp-tp-panel primary" : "xp-tp-panel"}
      aria-labelledby={headingId}
    >
      <h2 id={headingId} className="xp-tp-head">
        {title}
      </h2>
      <div className="xp-tp-body">{children}</div>
    </section>
  );
}
