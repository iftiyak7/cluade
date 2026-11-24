package main

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"

	_ "modernc.org/sqlite"
)

// Store wraps the SQLite database. All times are stored as Unix seconds (UTC).
type Store struct{ db *sql.DB }

func OpenStore(path string) (*Store, error) {
	dsn := "file:" + path + "?_pragma=busy_timeout(5000)&_pragma=journal_mode(WAL)&_pragma=foreign_keys(1)&_pragma=synchronous(NORMAL)"
	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(1) // SQLite: one writer; keeps behaviour simple and safe
	s := &Store{db: db}
	if err := s.migrate(context.Background()); err != nil {
		db.Close()
		return nil, err
	}
	return s, nil
}

func (s *Store) Close() error { return s.db.Close() }

var migrations = []string{
	`CREATE TABLE admins (
		id INTEGER PRIMARY KEY,
		username TEXT NOT NULL UNIQUE COLLATE NOCASE,
		password_hash TEXT NOT NULL,
		created_at INTEGER NOT NULL,
		updated_at INTEGER NOT NULL
	);
	CREATE TABLE sessions (
		token_hash TEXT PRIMARY KEY,
		admin_id INTEGER NOT NULL REFERENCES admins(id) ON DELETE CASCADE,
		csrf TEXT NOT NULL,
		created_at INTEGER NOT NULL,
		expires_at INTEGER NOT NULL
	);
	CREATE INDEX sessions_expiry ON sessions(expires_at);
	CREATE TABLE announcements (
		id INTEGER PRIMARY KEY,
		kind TEXT NOT NULL,
		title_en TEXT NOT NULL DEFAULT '', body_en TEXT NOT NULL DEFAULT '',
		title_bn TEXT NOT NULL DEFAULT '', body_bn TEXT NOT NULL DEFAULT '',
		cta_label_en TEXT NOT NULL DEFAULT '', cta_label_bn TEXT NOT NULL DEFAULT '',
		cta_url TEXT NOT NULL DEFAULT '', image_url TEXT NOT NULL DEFAULT '',
		lang TEXT NOT NULL DEFAULT 'all',
		min_version INTEGER NOT NULL DEFAULT 0,
		priority INTEGER NOT NULL DEFAULT 0,
		starts_at INTEGER NOT NULL,
		ends_at INTEGER,
		published INTEGER NOT NULL DEFAULT 0,
		created_at INTEGER NOT NULL,
		updated_at INTEGER NOT NULL
	);
	CREATE INDEX announcements_live ON announcements(published, starts_at, ends_at);
	CREATE TABLE audit_log (
		id INTEGER PRIMARY KEY,
		admin_id INTEGER,
		action TEXT NOT NULL,
		target TEXT NOT NULL DEFAULT '',
		at INTEGER NOT NULL
	);`,
}

func (s *Store) migrate(ctx context.Context) error {
	if _, err := s.db.ExecContext(ctx, `CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)`); err != nil {
		return err
	}
	var v int
	if err := s.db.QueryRowContext(ctx, `SELECT COALESCE(MAX(version), 0) FROM schema_version`).Scan(&v); err != nil {
		return err
	}
	for i := v; i < len(migrations); i++ {
		tx, err := s.db.BeginTx(ctx, nil)
		if err != nil {
			return err
		}
		if _, err := tx.ExecContext(ctx, migrations[i]); err != nil {
			tx.Rollback()
			return fmt.Errorf("migration %d: %w", i+1, err)
		}
		if _, err := tx.ExecContext(ctx, `INSERT INTO schema_version(version) VALUES (?)`, i+1); err != nil {
			tx.Rollback()
			return err
		}
		if err := tx.Commit(); err != nil {
			return err
		}
	}
	return nil
}

// ---- admins ----

type Admin struct {
	ID           int64
	Username     string
	PasswordHash string
}

func (s *Store) CreateAdmin(ctx context.Context, username, hash string, now time.Time) error {
	_, err := s.db.ExecContext(ctx, `INSERT INTO admins(username, password_hash, created_at, updated_at) VALUES (?,?,?,?)`, username, hash, now.Unix(), now.Unix())
	return err
}

func (s *Store) SetAdminPassword(ctx context.Context, username, hash string, now time.Time) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	res, err := tx.ExecContext(ctx, `UPDATE admins SET password_hash = ?, updated_at = ? WHERE username = ?`, hash, now.Unix(), username)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return errNotFound
	}
	// A password change signs out every existing session of that admin.
	if _, err := tx.ExecContext(ctx, `DELETE FROM sessions WHERE admin_id = (SELECT id FROM admins WHERE username = ?)`, username); err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Store) AdminByUsername(ctx context.Context, username string) (Admin, error) {
	var a Admin
	err := s.db.QueryRowContext(ctx, `SELECT id, username, password_hash FROM admins WHERE username = ?`, username).Scan(&a.ID, &a.Username, &a.PasswordHash)
	if errors.Is(err, sql.ErrNoRows) {
		return a, errNotFound
	}
	return a, err
}

func (s *Store) AdminCount(ctx context.Context) (int, error) {
	var n int
	err := s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM admins`).Scan(&n)
	return n, err
}

// ---- sessions ----

type Session struct {
	AdminID   int64
	Username  string
	CSRF      string
	ExpiresAt time.Time
}

func (s *Store) CreateSession(ctx context.Context, tokenHash string, adminID int64, csrf string, now, expires time.Time) error {
	_, err := s.db.ExecContext(ctx, `INSERT INTO sessions(token_hash, admin_id, csrf, created_at, expires_at) VALUES (?,?,?,?,?)`, tokenHash, adminID, csrf, now.Unix(), expires.Unix())
	return err
}

func (s *Store) SessionByHash(ctx context.Context, tokenHash string, now time.Time) (Session, error) {
	var ss Session
	var exp int64
	err := s.db.QueryRowContext(ctx, `SELECT s.admin_id, a.username, s.csrf, s.expires_at FROM sessions s JOIN admins a ON a.id = s.admin_id WHERE s.token_hash = ? AND s.expires_at > ?`, tokenHash, now.Unix()).
		Scan(&ss.AdminID, &ss.Username, &ss.CSRF, &exp)
	if errors.Is(err, sql.ErrNoRows) {
		return ss, errNotFound
	}
	ss.ExpiresAt = time.Unix(exp, 0)
	return ss, err
}

func (s *Store) DeleteSession(ctx context.Context, tokenHash string) error {
	_, err := s.db.ExecContext(ctx, `DELETE FROM sessions WHERE token_hash = ?`, tokenHash)
	return err
}

func (s *Store) PurgeExpiredSessions(ctx context.Context, now time.Time) error {
	_, err := s.db.ExecContext(ctx, `DELETE FROM sessions WHERE expires_at <= ?`, now.Unix())
	return err
}

// ---- announcements ----

const annCols = `id, kind, title_en, body_en, title_bn, body_bn, cta_label_en, cta_label_bn, cta_url, image_url, lang, min_version, priority, starts_at, ends_at, published, created_at, updated_at`

func scanAnn(sc interface{ Scan(...any) error }) (Announcement, error) {
	var a Announcement
	var starts, created, updated int64
	var ends sql.NullInt64
	var kind string
	err := sc.Scan(&a.ID, &kind, &a.TitleEn, &a.BodyEn, &a.TitleBn, &a.BodyBn, &a.CtaLabelEn, &a.CtaLabelBn, &a.CtaURL, &a.ImageURL,
		&a.Lang, &a.MinVersion, &a.Priority, &starts, &ends, &a.Published, &created, &updated)
	if err != nil {
		return a, err
	}
	a.Kind = Kind(kind)
	a.StartsAt = time.Unix(starts, 0).UTC()
	if ends.Valid {
		t := time.Unix(ends.Int64, 0).UTC()
		a.EndsAt = &t
	}
	a.CreatedAt = time.Unix(created, 0).UTC()
	a.UpdatedAt = time.Unix(updated, 0).UTC()
	return a, nil
}

func endsArg(t *time.Time) any {
	if t == nil {
		return nil
	}
	return t.Unix()
}

func (s *Store) SaveAnnouncement(ctx context.Context, a *Announcement, now time.Time) error {
	if a.ID == 0 {
		res, err := s.db.ExecContext(ctx, `INSERT INTO announcements(kind, title_en, body_en, title_bn, body_bn, cta_label_en, cta_label_bn, cta_url, image_url, lang, min_version, priority, starts_at, ends_at, published, created_at, updated_at)
			VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`,
			string(a.Kind), a.TitleEn, a.BodyEn, a.TitleBn, a.BodyBn, a.CtaLabelEn, a.CtaLabelBn, a.CtaURL, a.ImageURL, a.Lang, a.MinVersion, a.Priority,
			a.StartsAt.Unix(), endsArg(a.EndsAt), a.Published, now.Unix(), now.Unix())
		if err != nil {
			return err
		}
		a.ID, _ = res.LastInsertId()
		a.CreatedAt, a.UpdatedAt = now, now
		return nil
	}
	res, err := s.db.ExecContext(ctx, `UPDATE announcements SET kind=?, title_en=?, body_en=?, title_bn=?, body_bn=?, cta_label_en=?, cta_label_bn=?, cta_url=?, image_url=?, lang=?, min_version=?, priority=?, starts_at=?, ends_at=?, published=?, updated_at=? WHERE id=?`,
		string(a.Kind), a.TitleEn, a.BodyEn, a.TitleBn, a.BodyBn, a.CtaLabelEn, a.CtaLabelBn, a.CtaURL, a.ImageURL, a.Lang, a.MinVersion, a.Priority,
		a.StartsAt.Unix(), endsArg(a.EndsAt), a.Published, now.Unix(), a.ID)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return errNotFound
	}
	a.UpdatedAt = now
	return nil
}

func (s *Store) Announcement(ctx context.Context, id int64) (Announcement, error) {
	a, err := scanAnn(s.db.QueryRowContext(ctx, `SELECT `+annCols+` FROM announcements WHERE id = ?`, id))
	if errors.Is(err, sql.ErrNoRows) {
		return a, errNotFound
	}
	return a, err
}

func (s *Store) ListAnnouncements(ctx context.Context) ([]Announcement, error) {
	return s.query(ctx, `SELECT `+annCols+` FROM announcements ORDER BY updated_at DESC, id DESC LIMIT 500`)
}

// LiveAnnouncements returns published items active at now, highest priority first.
func (s *Store) LiveAnnouncements(ctx context.Context, now time.Time) ([]Announcement, error) {
	return s.query(ctx, `SELECT `+annCols+` FROM announcements WHERE published = 1 AND starts_at <= ? AND (ends_at IS NULL OR ends_at > ?) ORDER BY priority DESC, starts_at DESC, id DESC LIMIT 50`, now.Unix(), now.Unix())
}

func (s *Store) query(ctx context.Context, q string, args ...any) ([]Announcement, error) {
	rows, err := s.db.QueryContext(ctx, q, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Announcement
	for rows.Next() {
		a, err := scanAnn(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, a)
	}
	return out, rows.Err()
}

func (s *Store) SetPublished(ctx context.Context, id int64, published bool, now time.Time) error {
	res, err := s.db.ExecContext(ctx, `UPDATE announcements SET published = ?, updated_at = ? WHERE id = ?`, published, now.Unix(), id)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return errNotFound
	}
	return nil
}

func (s *Store) DeleteAnnouncement(ctx context.Context, id int64) error {
	res, err := s.db.ExecContext(ctx, `DELETE FROM announcements WHERE id = ?`, id)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return errNotFound
	}
	return nil
}

// ---- audit ----

type AuditEntry struct {
	At       time.Time
	Username string
	Action   string
	Target   string
}

func (s *Store) Audit(ctx context.Context, adminID int64, action, target string, now time.Time) {
	_, _ = s.db.ExecContext(ctx, `INSERT INTO audit_log(admin_id, action, target, at) VALUES (?,?,?,?)`, adminID, action, target, now.Unix())
}

func (s *Store) RecentAudit(ctx context.Context, limit int) ([]AuditEntry, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT l.at, COALESCE(a.username, '?'), l.action, l.target FROM audit_log l LEFT JOIN admins a ON a.id = l.admin_id ORDER BY l.id DESC LIMIT ?`, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []AuditEntry
	for rows.Next() {
		var e AuditEntry
		var at int64
		if err := rows.Scan(&at, &e.Username, &e.Action, &e.Target); err != nil {
			return nil, err
		}
		e.At = time.Unix(at, 0).UTC()
		out = append(out, e)
	}
	return out, rows.Err()
}

func isUniqueViolation(err error) bool {
	return err != nil && strings.Contains(err.Error(), "UNIQUE constraint failed")
}
