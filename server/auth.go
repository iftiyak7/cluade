package main

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"
	"unicode/utf8"

	"golang.org/x/crypto/bcrypt"
)

const (
	bcryptCost     = 12
	sessionTTL     = 12 * time.Hour
	minPasswordLen = 12
)

// dummyHash is compared against when a username does not exist, so a login attempt takes the
// same time whether or not the account exists (no username enumeration by timing).
var dummyHash, _ = bcrypt.GenerateFromPassword([]byte("hayat-dummy-password-for-timing"), bcryptCost)

func HashPassword(pw string) (string, error) {
	if err := CheckPasswordStrength(pw); err != nil {
		return "", err
	}
	h, err := bcrypt.GenerateFromPassword([]byte(pw), bcryptCost)
	return string(h), err
}

func CheckPasswordStrength(pw string) error {
	if utf8.RuneCountInString(pw) < minPasswordLen {
		return errors.New("password must be at least 12 characters")
	}
	if len(pw) > 72 { // bcrypt only uses the first 72 bytes
		return errors.New("password must be at most 72 bytes")
	}
	return nil
}

func ValidUsername(u string) bool {
	if len(u) < 3 || len(u) > 32 {
		return false
	}
	for _, r := range u {
		if !(r >= 'a' && r <= 'z' || r >= 'A' && r <= 'Z' || r >= '0' && r <= '9' || r == '_' || r == '-' || r == '.') {
			return false
		}
	}
	return true
}

func randomToken(n int) string {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		panic(err) // crypto/rand failing is unrecoverable
	}
	return base64.RawURLEncoding.EncodeToString(b)
}

func hashToken(t string) string {
	h := sha256.Sum256([]byte(t))
	return hex.EncodeToString(h[:])
}

// ---- rate limiting ----

// LoginLimiter allows `max` failed attempts per key within `window`, then blocks the key until
// the window has passed. Keys are client IP and username, so neither spraying one account from
// many IPs nor many accounts from one IP gets unlimited guesses.
type LoginLimiter struct {
	mu     sync.Mutex
	max    int
	window time.Duration
	fails  map[string][]time.Time
}

func NewLoginLimiter(max int, window time.Duration) *LoginLimiter {
	return &LoginLimiter{max: max, window: window, fails: map[string][]time.Time{}}
}

func (l *LoginLimiter) prune(key string, now time.Time) []time.Time {
	kept := l.fails[key][:0]
	for _, t := range l.fails[key] {
		if now.Sub(t) < l.window {
			kept = append(kept, t)
		}
	}
	if len(kept) == 0 {
		delete(l.fails, key)
	} else {
		l.fails[key] = kept
	}
	return kept
}

func (l *LoginLimiter) Blocked(now time.Time, keys ...string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	for _, k := range keys {
		if len(l.prune(k, now)) >= l.max {
			return true
		}
	}
	return false
}

func (l *LoginLimiter) Fail(now time.Time, keys ...string) {
	l.mu.Lock()
	defer l.mu.Unlock()
	for _, k := range keys {
		l.fails[k] = append(l.prune(k, now), now)
	}
	if len(l.fails) > 50_000 { // memory bound under a flood
		for k := range l.fails {
			l.prune(k, now)
		}
	}
}

func (l *LoginLimiter) Reset(keys ...string) {
	l.mu.Lock()
	defer l.mu.Unlock()
	for _, k := range keys {
		delete(l.fails, k)
	}
}

// RateLimiter is a simple per-key token bucket used for the public feed.
type RateLimiter struct {
	mu      sync.Mutex
	rate    float64 // tokens per second
	burst   float64
	buckets map[string]*bucket
}

type bucket struct {
	tokens float64
	last   time.Time
}

func NewRateLimiter(perMinute, burst int) *RateLimiter {
	return &RateLimiter{rate: float64(perMinute) / 60, burst: float64(burst), buckets: map[string]*bucket{}}
}

func (r *RateLimiter) Allow(key string, now time.Time) bool {
	r.mu.Lock()
	defer r.mu.Unlock()
	b, ok := r.buckets[key]
	if !ok {
		if len(r.buckets) > 100_000 {
			for k, v := range r.buckets {
				if now.Sub(v.last) > time.Minute {
					delete(r.buckets, k)
				}
			}
		}
		b = &bucket{tokens: r.burst, last: now}
		r.buckets[key] = b
	}
	b.tokens = min(r.burst, b.tokens+now.Sub(b.last).Seconds()*r.rate)
	b.last = now
	if b.tokens < 1 {
		return false
	}
	b.tokens--
	return true
}

// ---- sessions & CSRF ----

type ctxKey int

const sessionKey ctxKey = 1

func sessionFrom(ctx context.Context) (Session, bool) {
	s, ok := ctx.Value(sessionKey).(Session)
	return s, ok
}

func (app *App) cookieName() string {
	if app.cfg.SecureCookies {
		return "__Host-hayat_admin" // __Host- requires Secure, Path=/ and no Domain: cannot be set by subdomains
	}
	return "hayat_admin"
}

func (app *App) startSession(w http.ResponseWriter, r *http.Request, adminID int64) error {
	token := randomToken(32)
	now := app.now()
	if err := app.store.CreateSession(r.Context(), hashToken(token), adminID, randomToken(32), now, now.Add(sessionTTL)); err != nil {
		return err
	}
	http.SetCookie(w, &http.Cookie{
		Name: app.cookieName(), Value: token, Path: "/", HttpOnly: true, Secure: app.cfg.SecureCookies,
		SameSite: http.SameSiteStrictMode, MaxAge: int(sessionTTL.Seconds()),
	})
	return nil
}

func (app *App) endSession(w http.ResponseWriter, r *http.Request) {
	if c, err := r.Cookie(app.cookieName()); err == nil {
		_ = app.store.DeleteSession(r.Context(), hashToken(c.Value))
	}
	http.SetCookie(w, &http.Cookie{Name: app.cookieName(), Value: "", Path: "/", HttpOnly: true, Secure: app.cfg.SecureCookies, SameSite: http.SameSiteStrictMode, MaxAge: -1})
}

// requireAdmin loads the session; unauthenticated requests are sent to the login page.
// State-changing requests must carry the session's CSRF token and a same-origin Origin header.
func (app *App) requireAdmin(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		c, err := r.Cookie(app.cookieName())
		if err != nil || c.Value == "" {
			http.Redirect(w, r, "/admin/login", http.StatusSeeOther)
			return
		}
		sess, err := app.store.SessionByHash(r.Context(), hashToken(c.Value), app.now())
		if err != nil {
			app.endSession(w, r)
			http.Redirect(w, r, "/admin/login", http.StatusSeeOther)
			return
		}
		if r.Method != http.MethodGet && r.Method != http.MethodHead {
			if !app.sameOrigin(r) || !validCSRF(r, sess.CSRF) {
				http.Error(w, "Invalid or missing security token. Reload the page and try again.", http.StatusForbidden)
				return
			}
		}
		w.Header().Set("Cache-Control", "no-store")
		next(w, r.WithContext(context.WithValue(r.Context(), sessionKey, sess)))
	}
}

func validCSRF(r *http.Request, want string) bool {
	got := r.PostFormValue("csrf")
	return got != "" && subtle.ConstantTimeCompare([]byte(got), []byte(want)) == 1
}

// sameOrigin checks the Origin (or Referer) header of a state-changing request against the
// request host. Browsers always send Origin on cross-site POSTs, so a mismatch means CSRF.
func (app *App) sameOrigin(r *http.Request) bool {
	origin := r.Header.Get("Origin")
	if origin == "" {
		origin = r.Header.Get("Referer")
	}
	if origin == "" {
		return !app.cfg.SecureCookies // only lenient for local development without TLS
	}
	scheme := "http://"
	if app.cfg.SecureCookies {
		scheme = "https://"
	}
	host := r.Host
	// Behind a local reverse proxy that rewrites Host, trust its X-Forwarded-Host.
	if fh := r.Header.Get("X-Forwarded-Host"); fh != "" && app.cfg.TrustProxy {
		if peer, _, err := net.SplitHostPort(r.RemoteAddr); err == nil && isLocalProxy(peer) {
			host = strings.TrimSpace(strings.Split(fh, ",")[0])
		}
	}
	return origin == scheme+host || strings.HasPrefix(origin, scheme+host+"/")
}

func bcryptCompare(hash []byte, pw string) error {
	return bcrypt.CompareHashAndPassword(hash, []byte(pw))
}
