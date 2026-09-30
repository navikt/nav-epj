import { useId, type ReactNode } from "react";
import { Button } from "./Button";
import { Dialog } from "./Dialog";
import { TechnicalDetails } from "./TechnicalDetails";
import { XpIcon, type IconName } from "./XpIcon";

export type MessageBoxVariant = "info" | "advarsel" | "feil" | "sporsmal";

export type MessageBoxButton = {
  label: string;
  onClick: () => void;
  isDefault?: boolean;
};

type Props = {
  title: string;
  heading: string;
  variant?: MessageBoxVariant;
  wide?: boolean;
  details?: {
    items: ReadonlyArray<readonly [string, string]>;
    onCopy: () => void;
  };
  buttons: MessageBoxButton[];
  onClose: () => void;
  children?: ReactNode;
};

const icons: Record<MessageBoxVariant, IconName> = {
  info: "info",
  advarsel: "advarsel",
  feil: "feil",
  sporsmal: "sporsmal",
};

export function MessageBox({
  title,
  heading,
  variant = "info",
  wide,
  details,
  buttons,
  onClose,
  children,
}: Props) {
  const headingId = useId();
  const bodyId = useId();
  return (
    <Dialog
      title={title}
      titleIcon={icons[variant]}
      role={variant === "info" ? "dialog" : "alertdialog"}
      labelledBy={headingId}
      describedBy={children ? bodyId : undefined}
      wide={wide}
      onClose={onClose}
    >
      <div className="xp-msg">
        <span className="xp-msg-icon">
          <XpIcon name={icons[variant]} size={48} />
        </span>
        <div className="xp-msg-main">
          <h2 className="xp-msg-head" id={headingId}>
            {heading}
          </h2>
          {children && <div id={bodyId}>{children}</div>}
          {details && (
            <TechnicalDetails items={details.items} onCopy={details.onCopy} />
          )}
        </div>
      </div>
      <div className="xp-msg-actions">
        {buttons.map((button) => (
          <Button
            key={button.label}
            isDefault={button.isDefault}
            onClick={button.onClick}
          >
            {button.label}
          </Button>
        ))}
      </div>
    </Dialog>
  );
}
