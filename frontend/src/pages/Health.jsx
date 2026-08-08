import { useEffect, useState } from 'react';
import { api } from '../api.js';

export default function Health() {
  const [status, setStatus] = useState(null);
  const [body, setBody] = useState(null);
  const [err, setErr] = useState(null);

  async function check() {
    setErr(null);
    const r = await api.getHealth();
    setStatus(r.status);
    setBody(r.body);
    if (!r.ok) setErr(`Health check returned ${r.status}`);
  }

  useEffect(() => { check(); }, []);

  return (
    <div>
      <h1>Health</h1>
      <div className="card">
        <p>HTTP status: <strong>{status ?? '…'}</strong></p>
        {body && <pre>{JSON.stringify(body, null, 2)}</pre>}
        {err && <p className="error">{err}</p>}
        <button className="secondary" onClick={check}>Refresh</button>
      </div>
    </div>
  );
}