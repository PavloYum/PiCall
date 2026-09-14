import pg from "pg";

export type UserRecord = { piCallId: string; displayName: string; passwordHash: string };

export class Database {
  private readonly pool: pg.Pool;

  constructor(connectionString: string) {
    this.pool = new pg.Pool({ connectionString, max: 10 });
  }

  async migrate(): Promise<void> {
    await this.pool.query(`
      CREATE TABLE IF NOT EXISTS users (
        picall_id VARCHAR(12) PRIMARY KEY,
        display_name VARCHAR(64) NOT NULL,
        password_hash TEXT NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
      )
    `);
  }

  async createUser(user: UserRecord): Promise<boolean> {
    const result = await this.pool.query(
      `INSERT INTO users (picall_id, display_name, password_hash)
       VALUES ($1, $2, $3) ON CONFLICT (picall_id) DO NOTHING`,
      [user.piCallId, user.displayName, user.passwordHash],
    );
    return result.rowCount === 1;
  }

  async findUser(piCallId: string): Promise<UserRecord | null> {
    const result = await this.pool.query<{
      picall_id: string; display_name: string; password_hash: string;
    }>("SELECT picall_id, display_name, password_hash FROM users WHERE picall_id = $1", [piCallId]);
    const row = result.rows[0];
    return row ? { piCallId: row.picall_id, displayName: row.display_name, passwordHash: row.password_hash } : null;
  }

  async close(): Promise<void> {
    await this.pool.end();
  }
}

