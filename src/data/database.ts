import * as SQLite from 'expo-sqlite';
import type { Project } from '../types';

let database: SQLite.SQLiteDatabase | null = null;

export async function initializeDatabase() {
  database = await SQLite.openDatabaseAsync('seamwork-studio.db');
  await database.execAsync(`
    PRAGMA journal_mode = WAL;
    CREATE TABLE IF NOT EXISTS projects (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      description TEXT NOT NULL DEFAULT '',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );
  `);
}

function db() {
  if (!database) throw new Error('Project storage has not been initialized.');
  return database;
}

function mapProject(row: { id: number; name: string; description: string; created_at: string; updated_at: string }): Project {
  return { id: row.id, name: row.name, description: row.description, createdAt: row.created_at, updatedAt: row.updated_at };
}

export async function getProjects(): Promise<Project[]> {
  const rows = await db().getAllAsync<{ id: number; name: string; description: string; created_at: string; updated_at: string }>(
    'SELECT id, name, description, created_at, updated_at FROM projects ORDER BY updated_at DESC'
  );
  return rows.map(mapProject);
}

export async function createProject(name: string, description = ''): Promise<Project> {
  const now = new Date().toISOString();
  const result = await db().runAsync(
    'INSERT INTO projects (name, description, created_at, updated_at) VALUES (?, ?, ?, ?)',
    name.trim(), description.trim(), now, now
  );
  return { id: Number(result.lastInsertRowId), name: name.trim(), description: description.trim(), createdAt: now, updatedAt: now };
}

export async function deleteProject(id: number) {
  await db().runAsync('DELETE FROM projects WHERE id = ?', id);
}
