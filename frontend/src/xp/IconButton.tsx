import type { ButtonHTMLAttributes } from "react";
import { XpIcon, type IconName } from "./XpIcon";

type Props = Omit<
  ButtonHTMLAttributes<HTMLButtonElement>,
  "className" | "children" | "aria-label"
> & {
  icon: IconName;
  label: string;
};

export function IconButton({ icon, label, type = "button", ...rest }: Props) {
  return (
    <button
      type={type}
      className="xp-iconbtn"
      aria-label={label}
      title={label}
      {...rest}
    >
      <XpIcon name={icon} size={16} />
    </button>
  );
}
