package main

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"time"

	_ "github.com/jackc/pgx/v5/stdlib"
	_ "modernc.org/sqlite"
)

// Store wraps the database: PostgreSQL in production (HAYAT_DATABASE_URL), SQLite for local
// development and tests. All times are stored as Unix seconds (UTC). Queries are written with
// `?` placeholders and rebound to `$n` for PostgreSQL.
type Store struct {
	db *sql.DB
	pg bool
}

func OpenStore(target string) (*Store, error) {
	pg := strings.HasPrefix(target, "postgres://") || strings.HasPrefix(target, "postgresql://")
	var db *sql.DB
	var err error
	if pg {
		db, err = sql.Open("pgx", target)
		if err == nil {
			db.SetMaxOpenConns(10)
			db.SetMaxIdleConns(5)
			db.SetConnMaxLifetime(30 * time.Minute)
		}
	} else {
		dsn := "file:" + target + "?_pragma=busy_timeout(5000)&_pragma=journal_mode(WAL)&_pragma=foreign_keys(1)&_pragma=synchronous(NORMAL)"
		db, err = sql.Open("sqlite", dsn)
		if err == nil {
			db.SetMaxOpenConns(1) // SQLite: one writer; keeps behaviour simple and safe
		}
	}
	if err != nil {
		return nil, err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	if err := db.PingContext(ctx); err != nil {
		db.Close()
		return nil, err
	}
	s := &Store{db: db, pg: pg}
	if err := s.migrate(ctx); err != nil {
		db.Close()
		return nil, err
	}
	return s, nil
}

// q rebinds `?` placeholders for PostgreSQL. Queries never contain literal question marks.
func (s *Store) q(query string) string {
	if !s.pg {
		return query
	}
	var b strings.Builder
	n := 0
	for _, r := range query {
		if r == '?' {
			n++
			b.WriteString("$" + strconv.Itoa(n))
			continue
		}
		b.WriteRune(r)
	}
	return b.String()
}

func (s *Store) exec(ctx context.Context, query string, args ...any) (sql.Result, error) {
	return s.db.ExecContext(ctx, s.q(query), args...)
}
func (s *Store) row(ctx context.Context, query string, args ...any) *sql.Row {
	return s.db.QueryRowContext(ctx, s.q(query), args...)
}
func (s *Store) rows(ctx context.Context, query string, args ...any) (*sql.Rows, error) {
	return s.db.QueryContext(ctx, s.q(query), args...)
}

func (s *Store) Ping(ctx context.Context) error { return s.db.PingContext(ctx) }

func (s *Store) Close() error { return s.db.Close() }

// Backup writes a consistent snapshot of an SQLite database to path (safe while running).
// PostgreSQL deployments use pg_dump (deploy/backup.sh).
func (s *Store) Backup(ctx context.Context, path string) error {
	if s.pg {
		return errors.New("PostgreSQL: use deploy/backup.sh (pg_dump)")
	}
	_, err := s.db.ExecContext(ctx, `VACUUM INTO ?`, path)
	return err
}

var sqliteMigrations = []string{
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
		published BOOLEAN NOT NULL DEFAULT FALSE,
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
	userTablesSQL("INTEGER PRIMARY KEY", "BLOB"),
}

var postgresMigrations = []string{
	`CREATE TABLE admins (
		id BIGSERIAL PRIMARY KEY,
		username TEXT NOT NULL,
		password_hash TEXT NOT NULL,
		created_at BIGINT NOT NULL,
		updated_at BIGINT NOT NULL
	);
	CREATE UNIQUE INDEX admins_username ON admins(lower(username));
	CREATE TABLE sessions (
		token_hash TEXT PRIMARY KEY,
		admin_id BIGINT NOT NULL REFERENCES admins(id) ON DELETE CASCADE,
		csrf TEXT NOT NULL,
		created_at BIGINT NOT NULL,
		expires_at BIGINT NOT NULL
	);
	CREATE INDEX sessions_expiry ON sessions(expires_at);
	CREATE TABLE announcements (
		id BIGSERIAL PRIMARY KEY,
		kind TEXT NOT NULL,
		title_en TEXT NOT NULL DEFAULT '', body_en TEXT NOT NULL DEFAULT '',
		title_bn TEXT NOT NULL DEFAULT '', body_bn TEXT NOT NULL DEFAULT '',
		cta_label_en TEXT NOT NULL DEFAULT '', cta_label_bn TEXT NOT NULL DEFAULT '',
		cta_url TEXT NOT NULL DEFAULT '', image_url TEXT NOT NULL DEFAULT '',
		lang TEXT NOT NULL DEFAULT 'all',
		min_version INTEGER NOT NULL DEFAULT 0,
		priority INTEGER NOT NULL DEFAULT 0,
		starts_at BIGINT NOT NULL,
		ends_at BIGINT,
		published BOOLEAN NOT NULL DEFAULT FALSE,
		created_at BIGINT NOT NULL,
		updated_at BIGINT NOT NULL
	);
	CREATE INDEX announcements_live ON announcements(published, starts_at, ends_at);
	CREATE TABLE audit_log (
		id BIGSERIAL PRIMARY KEY,
		admin_id BIGINT,
		action TEXT NOT NULL,
		target TEXT NOT NULL DEFAULT '',
		at BIGINT NOT NULL
	);`,
	userTablesSQL("BIGSERIAL PRIMARY KEY", "BYTEA"),
}

// userTablesSQL (migration 2): app accounts, rotating refresh tokens and sync snapshots.
func userTablesSQL(serial, blob string) string {
	idRef := "BIGINT"
	return `CREATE TABLE users (
		id ` + serial + `,
		email TEXT,
		password_hash TEXT,
		access_key_hash TEXT,
		label TEXT NOT NULL DEFAULT '',
		created_at BIGINT NOT NULL,
		last_seen_at BIGINT NOT NULL DEFAULT 0
	);
	CREATE UNIQUE INDEX users_email ON users(lower(email));
	CREATE UNIQUE INDEX users_key ON users(access_key_hash);
	CREATE TABLE refresh_tokens (
		token_hash TEXT PRIMARY KEY,
		user_id ` + idRef + ` NOT NULL REFERENCES users(id) ON DELETE CASCADE,
		family TEXT NOT NULL,
		created_at BIGINT NOT NULL,
		expires_at BIGINT NOT NULL,
		used_at BIGINT
	);
	CREATE INDEX refresh_tokens_family ON refresh_tokens(family);
	CREATE INDEX refresh_tokens_user ON refresh_tokens(user_id);
	CREATE TABLE snapshots (
		user_id ` + idRef + ` NOT NULL REFERENCES users(id) ON DELETE CASCADE,
		revision BIGINT NOT NULL,
		data ` + blob + ` NOT NULL,
		size BIGINT NOT NULL,
		created_at BIGINT NOT NULL,
		PRIMARY KEY (user_id, revision)
	);`
}

func (s *Store) migrate(ctx context.Context) error {
	if _, err := s.db.ExecContext(ctx, `CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)`); err != nil {
		return err
	}
	var v int
	if err := s.db.QueryRowContext(ctx, `SELECT COALESCE(MAX(version), 0) FROM schema_version`).Scan(&v); err != nil {
		return err
	}
	migrations := sqliteMigrations
	if s.pg {
		migrations = postgresMigrations
	}
	for i := v; i < len(migrations); i++ {
		tx, err := s.db.BeginTx(ctx, nil)
		if err != nil {
			return err
		}
		if _, err := tx.ExecContext(ctx, migrations[i]); err != nil { // multi-statement DDL (both drivers)
			tx.Rollback()
			return fmt.Errorf("migration %d: %w", i+1, err)
		}
		if _, err := tx.ExecContext(ctx, s.q(`INSERT INTO schema_version(version) VALUES (?)`), i+1); err != nil {
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
	_, err := s.exec(ctx, `INSERT INTO admins(username, password_hash, created_at, updated_at) VALUES (?,?,?,?)`, username, hash, now.Unix(), now.Unix())
	return err
}

func (s *Store) SetAdminPassword(ctx context.Context, username, hash string, now time.Time) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	res, err := tx.ExecContext(ctx, s.q(`UPDATE admins SET password_hash = ?, updated_at = ? WHERE lower(username) = lower(?)`), hash, now.Unix(), username)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return errNotFound
	}
	// A password change signs out every existing session of that admin.
	if _, err := tx.ExecContext(ctx, s.q(`DELETE FROM sessions WHERE admin_id = (SELECT id FROM admins WHERE lower(username) = lower(?))`), username); err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Store) AdminByUsername(ctx context.Context, username string) (Admin, error) {
	var a Admin
	err := s.row(ctx, `SELECT id, username, password_hash FROM admins WHERE lower(username) = lower(?)`, username).Scan(&a.ID, &a.Username, &a.PasswordHash)
	if errors.Is(err, sql.ErrNoRows) {
		return a, errNotFound
	}
	return a, err
}

func (s *Store) AdminCount(ctx context.Context) (int, error) {
	var n int
	err := s.row(ctx, `SELECT COUNT(*) FROM admins`).Scan(&n)
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
	_, err := s.exec(ctx, `INSERT INTO sessions(token_hash, admin_id, csrf, created_at, expires_at) VALUES (?,?,?,?,?)`, tokenHash, adminID, csrf, now.Unix(), expires.Unix())
	return err
}

func (s *Store) SessionByHash(ctx context.Context, tokenHash string, now time.Time) (Session, error) {
	var ss Session
	var exp int64
	err := s.row(ctx, `SELECT s.admin_id, a.username, s.csrf, s.expires_at FROM sessions s JOIN admins a ON a.id = s.admin_id WHERE s.token_hash = ? AND s.expires_at > ?`, tokenHash, now.Unix()).
		Scan(&ss.AdminID, &ss.Username, &ss.CSRF, &exp)
	if errors.Is(err, sql.ErrNoRows) {
		return ss, errNotFound
	}
	ss.ExpiresAt = time.Unix(exp, 0)
	return ss, err
}

func (s *Store) DeleteSession(ctx context.Context, tokenHash string) error {
	_, err := s.exec(ctx, `DELETE FROM sessions WHERE token_hash = ?`, tokenHash)
	return err
}

func (s *Store) PurgeExpiredSessions(ctx context.Context, now time.Time) error {
	_, err := s.exec(ctx, `DELETE FROM sessions WHERE expires_at <= ?`, now.Unix())
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
		err := s.row(ctx, `INSERT INTO announcements(kind, title_en, body_en, title_bn, body_bn, cta_label_en, cta_label_bn, cta_url, image_url, lang, min_version, priority, starts_at, ends_at, published, created_at, updated_at)
			VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id`,
			string(a.Kind), a.TitleEn, a.BodyEn, a.TitleBn, a.BodyBn, a.CtaLabelEn, a.CtaLabelBn, a.CtaURL, a.ImageURL, a.Lang, a.MinVersion, a.Priority,
			a.StartsAt.Unix(), endsArg(a.EndsAt), a.Published, now.Unix(), now.Unix()).Scan(&a.ID)
		if err != nil {
			return err
		}
		a.CreatedAt, a.UpdatedAt = now, now
		return nil
	}
	res, err := s.exec(ctx, `UPDATE announcements SET kind=?, title_en=?, body_en=?, title_bn=?, body_bn=?, cta_label_en=?, cta_label_bn=?, cta_url=?, image_url=?, lang=?, min_version=?, priority=?, starts_at=?, ends_at=?, published=?, updated_at=? WHERE id=?`,
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
	a, err := scanAnn(s.row(ctx, `SELECT `+annCols+` FROM announcements WHERE id = ?`, id))
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
	return s.query(ctx, `SELECT `+annCols+` FROM announcements WHERE published = TRUE AND starts_at <= ? AND (ends_at IS NULL OR ends_at > ?) ORDER BY priority DESC, starts_at DESC, id DESC LIMIT 50`, now.Unix(), now.Unix())
}

func (s *Store) query(ctx context.Context, q string, args ...any) ([]Announcement, error) {
	rows, err := s.rows(ctx, q, args...)
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
	res, err := s.exec(ctx, `UPDATE announcements SET published = ?, updated_at = ? WHERE id = ?`, published, now.Unix(), id)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return errNotFound
	}
	return nil
}

func (s *Store) DeleteAnnouncement(ctx context.Context, id int64) error {
	res, err := s.exec(ctx, `DELETE FROM announcements WHERE id = ?`, id)
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
	_, _ = s.exec(ctx, `INSERT INTO audit_log(admin_id, action, target, at) VALUES (?,?,?,?)`, adminID, action, target, now.Unix())
}

func (s *Store) RecentAudit(ctx context.Context, limit int) ([]AuditEntry, error) {
	rows, err := s.rows(ctx, `SELECT l.at, COALESCE(a.username, '?'), l.action, l.target FROM audit_log l LEFT JOIN admins a ON a.id = l.admin_id ORDER BY l.id DESC LIMIT ?`, limit)
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
	if err == nil {
		return false
	}
	m := err.Error()
	return strings.Contains(m, "UNIQUE constraint failed") || strings.Contains(m, "SQLSTATE 23505") || strings.Contains(m, "duplicate key")
}
