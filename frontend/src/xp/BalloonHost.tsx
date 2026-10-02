import { useBalloonStore } from "./balloonStore";
import { BalloonTip } from "./BalloonTip";

export function BalloonHost() {
  const balloon = useBalloonStore((s) => s.balloon);
  const dismiss = useBalloonStore((s) => s.dismiss);
  if (!balloon) return null;
  return (
    <BalloonTip
      key={balloon.id}
      title={balloon.title}
      icon={balloon.icon}
      onClose={() => dismiss(balloon.id)}
    >
      <span>{balloon.body}</span>
    </BalloonTip>
  );
}
