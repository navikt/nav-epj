import type { ButtonHTMLAttributes } from "react";

type Props = Omit<ButtonHTMLAttributes<HTMLButtonElement>, "className"> & {
  variant?: "small" | "link";
  isDefault?: boolean;
};

export function Button({
  variant,
  isDefault,
  type = "button",
  children,
  ...rest
}: Props) {
  const className = ["xp-btn", variant, isDefault && "is-default"]
    .filter(Boolean)
    .join(" ");
  return (
    <button type={type} className={className} {...rest}>
      <span className="xp-btn-in">{children}</span>
    </button>
  );
}
