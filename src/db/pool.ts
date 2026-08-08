import { Pool, PoolClient, PoolConfig } from 'pg';

let _pool: Pool | undefined;

export function setPool(p: Pool | undefined): void {
  _pool = p;
}

export function getPool(): Pool {
  if (!_pool) {
    throw new Error('PG pool not initialized. Call initPool() or setPool() first.');
  }
  return _pool;
}

export function initPool(cfg: PoolConfig): Pool {
  _pool = new Pool(cfg);
  return _pool;
}

/**
 * Run fn inside a transaction. Rolls back on throw. Commits on resolve.
 * Caller MUST NOT perform slow / network IO inside fn; the tx wraps DB ops only.
 */
export async function withTx<T>(fn: (client: PoolClient) => Promise<T>): Promise<T> {
  const client = await getPool().connect();
  try {
    await client.query('BEGIN');
    const out = await fn(client);
    await client.query('COMMIT');
    return out;
  } catch (err) {
    try { await client.query('ROLLBACK'); } catch { /* ignore */ }
    throw err;
  } finally {
    client.release();
  }
}

export async function closePool(): Promise<void> {
  if (_pool) {
    await _pool.end();
    _pool = undefined;
  }
}