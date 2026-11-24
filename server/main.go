// Command hayat-server serves the Hayat landing page, the public announcements feed used by the
// app, and the admin panel used to publish announcements.
//
// Privacy: the feed is anonymous and read-only. No accounts, device IDs or analytics; request IPs
// are used only in memory for rate limiting and are never logged or stored.
package main

import (
	"bufio"
	"context"
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"golang.org/x/term"
)

type Config struct {
	Addr          string
	DBPath        string
	SiteDir       string
	SecureCookies bool
	TrustProxy    bool // trust X-Forwarded-For from a local reverse proxy (Caddy/nginx)
}

func envOr(k, def string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return def
}

func loadConfig() Config {
	return Config{
		Addr:          envOr("HAYAT_ADDR", "127.0.0.1:8080"),
		DBPath:        envOr("HAYAT_DATABASE_URL", envOr("HAYAT_DB", "hayat.db")),
		SiteDir:       envOr("HAYAT_SITE_DIR", ""),
		SecureCookies: envOr("HAYAT_INSECURE_DEV", "") != "1",
		TrustProxy:    envOr("HAYAT_TRUST_PROXY", "1") == "1",
	}
}

func main() {
	log := slog.New(slog.NewTextHandler(os.Stderr, nil))
	cfg := loadConfig()
	if len(os.Args) < 2 {
		usage()
		os.Exit(2)
	}
	var err error
	switch os.Args[1] {
	case "serve":
		err = serve(cfg, log)
	case "backup":
		fs := flag.NewFlagSet("backup", flag.ExitOnError)
		out := fs.String("out", "", "path of the backup file to create")
		_ = fs.Parse(os.Args[2:])
		err = backupCommand(cfg, *out)
	case "create-admin", "set-password":
		fs := flag.NewFlagSet(os.Args[1], flag.ExitOnError)
		user := fs.String("user", "", "admin username")
		_ = fs.Parse(os.Args[2:])
		err = adminCommand(cfg, os.Args[1], *user)
	default:
		usage()
		os.Exit(2)
	}
	if err != nil {
		log.Error("fatal", "err", err)
		os.Exit(1)
	}
}

func usage() {
	fmt.Fprintln(os.Stderr, `usage:
  hayat-server serve
  hayat-server create-admin -user NAME   (password is read from the terminal)
  hayat-server set-password -user NAME
  hayat-server backup -out FILE          (consistent online copy of the database)

environment:
  HAYAT_ADDR          listen address (default 127.0.0.1:8080 — put a TLS proxy in front)
  HAYAT_DATABASE_URL  PostgreSQL URL (postgres://user:pass@host:5432/db?sslmode=disable)
  HAYAT_DB            SQLite database path when HAYAT_DATABASE_URL is not set (default hayat.db)
  HAYAT_JWT_SECRET    secret for app access tokens (min 32 characters)
  HAYAT_SIGNUP        open (default) = anyone can create an app account; closed = access keys only
  HAYAT_CORS_ORIGINS  comma-separated browser origins allowed to call /api (not needed for the app)
  HAYAT_SITE_DIR      directory with the static landing page (optional)
  HAYAT_TRUST_PROXY   1 = use X-Forwarded-For from the local proxy for rate limiting (default 1)
  HAYAT_INSECURE_DEV  1 = allow plain-HTTP cookies for local development only`)
}

func readPassword(prompt string) (string, error) {
	fmt.Fprint(os.Stderr, prompt)
	if term.IsTerminal(int(os.Stdin.Fd())) {
		b, err := term.ReadPassword(int(os.Stdin.Fd()))
		fmt.Fprintln(os.Stderr)
		return string(b), err
	}
	line, err := bufio.NewReader(os.Stdin).ReadString('\n') // allows piping in automation
	return strings.TrimRight(line, "\r\n"), err
}

func adminCommand(cfg Config, cmd, user string) error {
	if !ValidUsername(user) {
		return errors.New("-user must be 3-32 characters: letters, digits, . _ -")
	}
	st, err := OpenStore(cfg.DBPath)
	if err != nil {
		return err
	}
	defer st.Close()
	pw, err := readPassword("Password (min 12 characters): ")
	if err != nil {
		return err
	}
	if term.IsTerminal(int(os.Stdin.Fd())) {
		again, err := readPassword("Repeat password: ")
		if err != nil {
			return err
		}
		if again != pw {
			return errors.New("passwords do not match")
		}
	}
	hash, err := HashPassword(pw)
	if err != nil {
		return err
	}
	ctx := context.Background()
	if cmd == "create-admin" {
		if err := st.CreateAdmin(ctx, user, hash, time.Now()); err != nil {
			if isUniqueViolation(err) {
				return errors.New("that admin already exists; use set-password")
			}
			return err
		}
		fmt.Fprintln(os.Stderr, "Admin created.")
		return nil
	}
	if err := st.SetAdminPassword(ctx, user, hash, time.Now()); err != nil {
		return err
	}
	fmt.Fprintln(os.Stderr, "Password changed; existing sessions signed out.")
	return nil
}

func backupCommand(cfg Config, out string) error {
	if out == "" {
		return errors.New("-out is required")
	}
	if _, err := os.Stat(out); err == nil {
		return errors.New("refusing to overwrite an existing file")
	}
	st, err := OpenStore(cfg.DBPath)
	if err != nil {
		return err
	}
	defer st.Close()
	return st.Backup(context.Background(), out)
}

func serve(cfg Config, log *slog.Logger) error {
	st, err := OpenStore(cfg.DBPath)
	if err != nil {
		return err
	}
	defer st.Close()
	app, err := NewApp(cfg, st, log)
	if err != nil {
		return err
	}
	if n, _ := st.AdminCount(context.Background()); n == 0 {
		log.Warn("no admin account yet: run `hayat-server create-admin -user NAME`")
	}
	srv := &http.Server{
		Addr:              cfg.Addr,
		Handler:           app.Handler(),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       60 * time.Second,
		MaxHeaderBytes:    32 << 10,
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	go func() {
		t := time.NewTicker(time.Hour)
		defer t.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-t.C:
				_ = st.PurgeExpiredSessions(context.Background(), time.Now())
				st.PurgeExpiredRefresh(context.Background(), time.Now())
			}
		}
	}()
	errc := make(chan error, 1)
	go func() {
		log.Info("listening", "addr", cfg.Addr, "site", cfg.SiteDir != "")
		errc <- srv.ListenAndServe()
	}()
	select {
	case err := <-errc:
		if errors.Is(err, http.ErrServerClosed) {
			return nil
		}
		return err
	case <-ctx.Done():
		shutdown, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		return srv.Shutdown(shutdown)
	}
}

// clientIP returns the address used for rate limiting. Only the right-most X-Forwarded-For
// entry is used (the one our own proxy appended), and only when the direct peer is a local proxy:
// loopback, or a private address such as Caddy's container or the Docker gateway in front of a
// host proxy (CloudPanel/nginx). The API port is never published publicly, so such peers are ours.
func (app *App) clientIP(r *http.Request) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		host = r.RemoteAddr
	}
	if app.cfg.TrustProxy {
		if isLocalProxy(host) {
			if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
				parts := strings.Split(xff, ",")
				if c := strings.TrimSpace(parts[len(parts)-1]); net.ParseIP(c) != nil {
					return c
				}
			}
		}
	}
	return host
}

func isLocalProxy(host string) bool {
	ip := net.ParseIP(host)
	return ip != nil && (ip.IsLoopback() || ip.IsPrivate())
}
