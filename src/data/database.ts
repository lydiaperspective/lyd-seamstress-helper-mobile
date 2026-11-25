import * as SQLite from 'expo-sqlite';
import type { Project } from '../types';

let database: SQLite.SQLiteDatabase | null = null;

export async function initializeDatabase() {
  database = await SQLite.openDatabaseAsync('seamwork-studio.db');
  await database.execAsync(`
    PRAGMA journal_mode = WAL;
    PRAGMA foreign_keys = ON;
    CREATE TABLE IF NOT EXISTS projects (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      description TEXT NOT NULL DEFAULT '',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS inspirations (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      project_id INTEGER NOT NULL,
      kind TEXT NOT NULL CHECK (kind IN ('image', 'link')),
      uri TEXT NOT NULL,
      title TEXT NOT NULL DEFAULT '',
      created_at TEXT NOT NULL,
      FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
    );
    CREATE INDEX IF NOT EXISTS inspirations_project_id_idx ON inspirations(project_id);
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


export type InspirationItem = {
  id: number;
  projectId: number;
  kind: 'image' | 'link';
  uri: string;
  title: string;
  createdAt: string;
};

export async function getInspirations(projectId: number): Promise<InspirationItem[]> {
  const rows = await db().getAllAsync<{
    id: number; project_id: number; kind: 'image' | 'link'; uri: string; title: string; created_at: string;
  }>(
    'SELECT id, project_id, kind, uri, title, created_at FROM inspirations WHERE project_id = ? ORDER BY created_at DESC',
    projectId
  );
  return rows.map((row) => ({
    id: row.id, projectId: row.project_id, kind: row.kind, uri: row.uri, title: row.title, createdAt: row.created_at,
  }));
}

export async function addInspiration(
  projectId: number, kind: 'image' | 'link', uri: string, title = ''
): Promise<InspirationItem> {
  const createdAt = new Date().toISOString();
  const result = await db().runAsync(
    'INSERT INTO inspirations (project_id, kind, uri, title, created_at) VALUES (?, ?, ?, ?, ?)',
    projectId, kind, uri.trim(), title.trim(), createdAt
  );
  return { id: Number(result.lastInsertRowId), projectId, kind, uri: uri.trim(), title: title.trim(), createdAt };
}

export async function deleteInspiration(id: number) {
  await db().runAsync('DELETE FROM inspirations WHERE id = ?', id);
}
