type AlertProps = {
  error?: string;
  ok?: string;
};

export default function Alert({ error, ok }: AlertProps) {
  if (!error && !ok) return null;
  return (
    <div className="alert-stack" role={error ? 'alert' : 'status'}>
      {error ? <p className="form-error">{error}</p> : null}
      {ok ? <p className="form-ok">{ok}</p> : null}
    </div>
  );
}
