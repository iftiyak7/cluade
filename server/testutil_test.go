package main

import (
	"database/sql"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func mustWrite(t *testing.T, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
}

// newTestStore returns a fresh store: PostgreSQL when HAYAT_TEST_PG is set to an admin URL
// (a throwaway database is created per test), SQLite otherwise.
func newTestStore(t *testing.T) *Store {
	t.Helper()
	admin := os.Getenv("HAYAT_TEST_PG")
	if admin == "" {
		st, err := OpenStore(filepath.Join(t.TempDir(), "test.db"))
		if err != nil {
			t.Fatal(err)
		}
		t.Cleanup(func() { st.Close() })
		return st
	}
	db, err := sql.Open("pgx", admin)
	if err != nil {
		t.Fatal(err)
	}
	name := "hayat_t_" + strings.ToLower(randomToken(6))
	name = strings.NewReplacer("-", "_").Replace(name)
	if _, err := db.Exec(`CREATE DATABASE ` + name); err != nil {
		t.Fatal(err)
	}
	url := strings.Replace(admin, "/postgres?", "/"+name+"?", 1)
	st, err := OpenStore(url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		st.Close()
		db.Exec(`DROP DATABASE IF EXISTS ` + name + ` WITH (FORCE)`)
		db.Close()
	})
	return st
}
