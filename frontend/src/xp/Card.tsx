import type { ReactNode } from "react";

type Props = {
  heading?: string;
  headingId?: string;
  children: ReactNode;
};

export function Card({ heading, headingId, children }: Props) {
  return (
    <section className="xp-card" aria-labelledby={heading ? headingId : undefined}>
      {heading && <h2 id={headingId}>{heading}</h2>}
      {children}
    </section>
  );
}
