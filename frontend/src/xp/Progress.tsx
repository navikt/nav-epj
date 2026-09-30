type Props = { label: string };

export function Progress({ label }: Props) {
  return (
    <div className="xp-progress ind" role="progressbar" aria-label={label}>
      <span />
    </div>
  );
}
