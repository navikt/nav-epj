import { useId, type InputHTMLAttributes, type Ref } from "react";

type Props = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "className" | "id" | "aria-invalid" | "aria-describedby"
> & {
  label: string;
  hint?: string;
  error?: string;
  inputRef?: Ref<HTMLInputElement>;
};

export function TextField({ label, hint, error, inputRef, ...rest }: Props) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-err`;
  const describedBy =
    [hint && hintId, error && errorId].filter(Boolean).join(" ") || undefined;
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
      <input
        {...rest}
        id={id}
        ref={inputRef}
        className="xp-input"
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
      />
      {error && (
        <span className="xp-err" id={errorId}>
          ✖ {error}
        </span>
      )}
    </div>
  );
}
