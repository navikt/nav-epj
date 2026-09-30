import { useId, type TextareaHTMLAttributes } from "react";

type Props = Omit<
  TextareaHTMLAttributes<HTMLTextAreaElement>,
  "className" | "id" | "aria-describedby"
> & {
  label: string;
  hint?: string;
};

export function TextArea({ label, hint, ...rest }: Props) {
  const id = useId();
  const hintId = `${id}-hint`;
  return (
    <div className="xp-field">
      <label className="xp-label" htmlFor={id}>
        {label}
      </label>
      {hint && (
        <span className="xp-hint" id={hintId}>
          {hint}
        </span>
      )}
      <textarea
        {...rest}
        id={id}
        className="xp-textarea"
        aria-describedby={hint ? hintId : undefined}
      />
    </div>
  );
}
