package main

import (
	"context"
	"crypto/sha256"
	"crypto/subtle"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/mail"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/golang-jwt/jwt/v5"
)

// REST API for the Hayat app: accounts, JWT access + rotating refresh tokens, and snapshot sync.
//
//   GET    /api/health
//   POST   /api/auth/register   {email, password}          (only when HAYAT_SIGNUP=open)
//   POST   /api/auth/login      {email, password}
//   POST   /api/auth/key        {access_key}
//   POST   /api/auth/refresh    {refresh_token}
//   POST   /api/auth/logout     {refresh_token}
//   GET    /api/me                                          (Bearer)
//   DELETE /api/me                                          (Bearer) deletes the account and its data
//   GET    /api/sync/snapshot   If-None-Match: "<rev>"      (Bearer)
//   PUT    /api/sync/snapshot   If-Match: "<rev>" | "none" | "*"  (Bearer)

const (
	accessTTL        = 15 * time.Minute
	refreshTTL       = 60 * 24 * time.Hour
	maxSnapshotBytes = 10 << 20
	keepRevisions    = 5
	serverVersion    = "3"
)

type apiError struct {
	Error   string `json:"error"`
	Message string `json:"message"`
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func apiErr(w http.ResponseWriter, status int, code, msg string) {
	writeJSON(w, status, apiError{Error: code, Message: msg})
}

func decodeJSON(w http.ResponseWriter, r *http.Request, dst any, max int64) bool {
	r.Body = http.MaxBytesReader(w, r.Body, max)
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(dst); err != nil {
		apiErr(w, http.StatusBadRequest, "invalid_json", "Request body is not valid JSON.")
		return false
	}
	return true
}

// ---- store: users, refresh tokens, snapshots ----

type User struct {
	ID         int64
	Email      string
	PwHash     string
	Label      string
	CreatedAt  time.Time
	LastSeenAt time.Time
	Revision   int64
	Size       int64
}

func (u User) Account() string {
	if u.Email != "" {
		return u.Email
	}
	if u.Label != "" {
		return u.Label
	}
	return "user-" + strconv.FormatInt(u.ID, 10)
}

func sha(s string) string { h := sha256.Sum256([]byte(s)); return hex.EncodeToString(h[:]) }

func (s *Store) CreateUser(ctx context.Context, email, pwHash, keyHash, label string, now time.Time) (int64, error) {
	var id int64
	var e, p, k any
	if email != "" {
		e = email
	}
	if pwHash != "" {
		p = pwHash
	}
	if keyHash != "" {
		k = keyHash
	}
	err := s.row(ctx, `INSERT INTO users(email, password_hash, access_key_hash, label, created_at) VALUES (?,?,?,?,?) RETURNING id`, e, p, k, label, now.Unix()).Scan(&id)
	return id, err
}

func (s *Store) scanUser(r *sql.Row) (User, error) {
	var u User
	var email, pw sql.NullString
	var created, seen int64
	err := r.Scan(&u.ID, &email, &pw, &u.Label, &created, &seen)
	if errors.Is(err, sql.ErrNoRows) {
		return u, errNotFound
	}
	u.Email, u.PwHash = email.String, pw.String
	u.CreatedAt, u.LastSeenAt = time.Unix(created, 0).UTC(), time.Unix(seen, 0).UTC()
	return u, err
}

const userCols = `id, email, password_hash, label, created_at, last_seen_at`

func (s *Store) UserByEmail(ctx context.Context, email string) (User, error) {
	return s.scanUser(s.row(ctx, `SELECT `+userCols+` FROM users WHERE lower(email) = lower(?)`, email))
}
func (s *Store) UserByKeyHash(ctx context.Context, h string) (User, error) {
	return s.scanUser(s.row(ctx, `SELECT `+userCols+` FROM users WHERE access_key_hash = ?`, h))
}
func (s *Store) UserByID(ctx context.Context, id int64) (User, error) {
	return s.scanUser(s.row(ctx, `SELECT `+userCols+` FROM users WHERE id = ?`, id))
}
func (s *Store) TouchUser(ctx context.Context, id int64, now time.Time) {
	_, _ = s.exec(ctx, `UPDATE users SET last_seen_at = ? WHERE id = ?`, now.Unix(), id)
}
func (s *Store) DeleteUser(ctx context.Context, id int64) error {
	// Children first: works whether or not foreign keys cascade (SQLite pragma, Postgres FK).
	for _, q := range []string{`DELETE FROM snapshots WHERE user_id = ?`, `DELETE FROM refresh_tokens WHERE user_id = ?`, `DELETE FROM users WHERE id = ?`} {
		if _, err := s.exec(ctx, q, id); err != nil {
			return err
		}
	}
	return nil
}

func (s *Store) ListUsers(ctx context.Context) ([]User, error) {
	rs, err := s.rows(ctx, `SELECT u.id, COALESCE(u.email, ''), u.label, u.created_at, u.last_seen_at,
		COALESCE((SELECT MAX(revision) FROM snapshots sn WHERE sn.user_id = u.id), 0),
		COALESCE((SELECT size FROM snapshots sn WHERE sn.user_id = u.id ORDER BY revision DESC LIMIT 1), 0)
		FROM users u ORDER BY u.id DESC LIMIT 1000`)
	if err != nil {
		return nil, err
	}
	defer rs.Close()
	var out []User
	for rs.Next() {
		var u User
		var c, l int64
		if err := rs.Scan(&u.ID, &u.Email, &u.Label, &c, &l, &u.Revision, &u.Size); err != nil {
			return nil, err
		}
		u.CreatedAt, u.LastSeenAt = time.Unix(c, 0).UTC(), time.Unix(l, 0).UTC()
		out = append(out, u)
	}
	return out, rs.Err()
}

func (s *Store) SaveRefresh(ctx context.Context, hash string, userID int64, family string, now time.Time) error {
	_, err := s.exec(ctx, `INSERT INTO refresh_tokens(token_hash, user_id, family, created_at, expires_at) VALUES (?,?,?,?,?)`, hash, userID, family, now.Unix(), now.Add(refreshTTL).Unix())
	return err
}

// UseRefresh atomically marks a refresh token as used. Reusing an already-used token revokes the
// whole family (stolen-token detection) and fails.
func (s *Store) UseRefresh(ctx context.Context, hash string, now time.Time) (userID int64, family string, err error) {
	var exp int64
	var used sql.NullInt64
	err = s.row(ctx, `SELECT user_id, family, expires_at, used_at FROM refresh_tokens WHERE token_hash = ?`, hash).Scan(&userID, &family, &exp, &used)
	if errors.Is(err, sql.ErrNoRows) {
		return 0, "", errNotFound
	}
	if err != nil {
		return 0, "", err
	}
	if used.Valid {
		_, _ = s.exec(ctx, `DELETE FROM refresh_tokens WHERE family = ?`, family)
		return 0, "", errors.New("refresh token reused")
	}
	if exp <= now.Unix() {
		return 0, "", errNotFound
	}
	res, err := s.exec(ctx, `UPDATE refresh_tokens SET used_at = ? WHERE token_hash = ? AND used_at IS NULL`, now.Unix(), hash)
	if err != nil {
		return 0, "", err
	}
	if n, _ := res.RowsAffected(); n != 1 { // lost a race with a concurrent refresh
		return 0, "", errors.New("refresh token reused")
	}
	return userID, family, nil
}

func (s *Store) RevokeFamily(ctx context.Context, hash string) {
	_, _ = s.exec(ctx, `DELETE FROM refresh_tokens WHERE family = (SELECT family FROM refresh_tokens WHERE token_hash = ?)`, hash)
}

func (s *Store) PurgeExpiredRefresh(ctx context.Context, now time.Time) {
	_, _ = s.exec(ctx, `DELETE FROM refresh_tokens WHERE expires_at <= ? OR (used_at IS NOT NULL AND used_at <= ?)`, now.Unix(), now.Add(-24*time.Hour).Unix())
}

func (s *Store) LatestRevision(ctx context.Context, userID int64) (int64, error) {
	var rev int64
	err := s.row(ctx, `SELECT COALESCE(MAX(revision), 0) FROM snapshots WHERE user_id = ?`, userID).Scan(&rev)
	return rev, err
}

func (s *Store) Snapshot(ctx context.Context, userID, revision int64) ([]byte, error) {
	var data []byte
	err := s.row(ctx, `SELECT data FROM snapshots WHERE user_id = ? AND revision = ?`, userID, revision).Scan(&data)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, errNotFound
	}
	return data, err
}

var errConflict = errors.New("revision conflict")

// PutSnapshot stores a new revision when base matches the latest one (base -1 = force). Keeps the
// last [keepRevisions] revisions so an overwritten version can be recovered by the admin.
func (s *Store) PutSnapshot(ctx context.Context, userID, base int64, data []byte, now time.Time) (int64, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return 0, err
	}
	defer tx.Rollback()
	if s.pg { // serialise writers for this user
		if _, err := tx.ExecContext(ctx, `SELECT id FROM users WHERE id = $1 FOR UPDATE`, userID); err != nil {
			return 0, err
		}
	}
	var latest int64
	if err := tx.QueryRowContext(ctx, s.q(`SELECT COALESCE(MAX(revision), 0) FROM snapshots WHERE user_id = ?`), userID).Scan(&latest); err != nil {
		return 0, err
	}
	if base >= 0 && base != latest {
		return latest, errConflict
	}
	next := latest + 1
	if _, err := tx.ExecContext(ctx, s.q(`INSERT INTO snapshots(user_id, revision, data, size, created_at) VALUES (?,?,?,?,?)`), userID, next, data, len(data), now.Unix()); err != nil {
		return 0, err
	}
	if _, err := tx.ExecContext(ctx, s.q(`DELETE FROM snapshots WHERE user_id = ? AND revision <= ?`), userID, next-keepRevisions); err != nil {
		return 0, err
	}
	return next, tx.Commit()
}

// ---- tokens ----

type tokenResponse struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	TokenType    string `json:"token_type"`
	ExpiresIn    int    `json:"expires_in"`
	Account      string `json:"account"`
}

func (app *App) issueTokens(ctx context.Context, u User, family string) (tokenResponse, error) {
	now := app.now()
	claims := jwt.RegisteredClaims{
		Subject:   strconv.FormatInt(u.ID, 10),
		Issuer:    "hayat",
		Audience:  jwt.ClaimStrings{"hayat-app"},
		IssuedAt:  jwt.NewNumericDate(now),
		ExpiresAt: jwt.NewNumericDate(now.Add(accessTTL)),
	}
	access, err := jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString(app.jwtKey)
	if err != nil {
		return tokenResponse{}, err
	}
	if family == "" {
		family = randomToken(16)
	}
	refresh := randomToken(32)
	if err := app.store.SaveRefresh(ctx, sha(refresh), u.ID, family, now); err != nil {
		return tokenResponse{}, err
	}
	app.store.TouchUser(ctx, u.ID, now)
	return tokenResponse{AccessToken: access, RefreshToken: refresh, TokenType: "Bearer", ExpiresIn: int(accessTTL.Seconds()), Account: u.Account()}, nil
}

func (app *App) parseAccess(r *http.Request) (int64, bool) {
	h := r.Header.Get("Authorization")
	if !strings.HasPrefix(h, "Bearer ") {
		return 0, false
	}
	tok, err := jwt.ParseWithClaims(strings.TrimPrefix(h, "Bearer "), &jwt.RegisteredClaims{}, func(t *jwt.Token) (any, error) {
		return app.jwtKey, nil
	}, jwt.WithValidMethods([]string{"HS256"}), jwt.WithIssuer("hayat"), jwt.WithAudience("hayat-app"), jwt.WithTimeFunc(app.now), jwt.WithExpirationRequired())
	if err != nil || !tok.Valid {
		return 0, false
	}
	sub, _ := tok.Claims.GetSubject()
	id, err := strconv.ParseInt(sub, 10, 64)
	return id, err == nil && id > 0
}

func (app *App) requireUser(next func(http.ResponseWriter, *http.Request, User)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id, ok := app.parseAccess(r)
		if !ok {
			w.Header().Set("WWW-Authenticate", `Bearer realm="hayat"`)
			apiErr(w, http.StatusUnauthorized, "unauthorized", "Sign in again.")
			return
		}
		if !app.apiLimit.Allow("u:"+strconv.FormatInt(id, 10), app.now()) {
			w.Header().Set("Retry-After", "30")
			apiErr(w, http.StatusTooManyRequests, "rate_limited", "Too many requests. Try again shortly.")
			return
		}
		u, err := app.store.UserByID(r.Context(), id)
		if err != nil {
			apiErr(w, http.StatusUnauthorized, "unauthorized", "Account not found.")
			return
		}
		next(w, r, u)
	}
}

// authLimited applies the per-IP limit for unauthenticated auth endpoints.
func (app *App) authLimited(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if !app.authLimit.Allow("ip:"+app.clientIP(r), app.now()) {
			w.Header().Set("Retry-After", "60")
			apiErr(w, http.StatusTooManyRequests, "rate_limited", "Too many attempts. Try again in a minute.")
			return
		}
		next(w, r)
	}
}

// ---- handlers ----

func (app *App) handleHealth(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancel()
	if err := app.store.Ping(ctx); err != nil {
		writeJSON(w, http.StatusServiceUnavailable, map[string]any{"status": "degraded", "service": "hayat", "version": serverVersion})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"status": "ok", "service": "hayat", "version": serverVersion, "signup": app.signupOpen})
}

type credentials struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

func normEmail(e string) (string, bool) {
	e = strings.TrimSpace(e)
	if len(e) > 254 || utf8.RuneCountInString(e) < 3 {
		return "", false
	}
	a, err := mail.ParseAddress(e)
	if err != nil || a.Address != e || !strings.Contains(e[strings.LastIndex(e, "@")+1:], ".") {
		return "", false
	}
	return strings.ToLower(e), true
}

func (app *App) handleRegister(w http.ResponseWriter, r *http.Request) {
	if !app.signupOpen {
		apiErr(w, http.StatusForbidden, "signup_closed", "New accounts are created by the server owner. Use an access key.")
		return
	}
	var c credentials
	if !decodeJSON(w, r, &c, 4<<10) {
		return
	}
	email, ok := normEmail(c.Email)
	if !ok {
		apiErr(w, http.StatusUnprocessableEntity, "invalid_email", "Enter a valid email address.")
		return
	}
	hash, err := HashPassword(c.Password)
	if err != nil {
		apiErr(w, http.StatusUnprocessableEntity, "weak_password", "Password must be 12–72 characters.")
		return
	}
	id, err := app.store.CreateUser(r.Context(), email, hash, "", "", app.now())
	if err != nil {
		if isUniqueViolation(err) {
			apiErr(w, http.StatusConflict, "email_taken", "An account with this email already exists. Sign in instead.")
			return
		}
		apiErr(w, http.StatusInternalServerError, "server_error", "Could not create the account.")
		return
	}
	u, _ := app.store.UserByID(r.Context(), id)
	app.respondTokens(w, r, u, "", http.StatusCreated)
}

func (app *App) handleAPILogin(w http.ResponseWriter, r *http.Request) {
	var c credentials
	if !decodeJSON(w, r, &c, 4<<10) {
		return
	}
	email, _ := normEmail(c.Email)
	now := app.now()
	keys := []string{"api-ip:" + app.clientIP(r), "api-user:" + email}
	if app.logins.Blocked(now, keys...) {
		apiErr(w, http.StatusTooManyRequests, "rate_limited", "Too many failed attempts. Try again in 15 minutes.")
		return
	}
	u, err := app.store.UserByEmail(r.Context(), email)
	hash := dummyHash
	if err == nil && u.PwHash != "" {
		hash = []byte(u.PwHash)
	}
	if bcryptCompare(hash, c.Password) != nil || err != nil || u.PwHash == "" {
		app.logins.Fail(now, keys...)
		apiErr(w, http.StatusUnauthorized, "wrong_credentials", "Wrong email or password.")
		return
	}
	app.logins.Reset(keys...)
	app.respondTokens(w, r, u, "", http.StatusOK)
}

func (app *App) handleKeyLogin(w http.ResponseWriter, r *http.Request) {
	var body struct {
		AccessKey string `json:"access_key"`
	}
	if !decodeJSON(w, r, &body, 2<<10) {
		return
	}
	now := app.now()
	keys := []string{"api-ip:" + app.clientIP(r)}
	if app.logins.Blocked(now, keys...) {
		apiErr(w, http.StatusTooManyRequests, "rate_limited", "Too many failed attempts. Try again in 15 minutes.")
		return
	}
	k := strings.TrimSpace(body.AccessKey)
	u, err := app.store.UserByKeyHash(r.Context(), sha(k))
	if err != nil || len(k) < 20 {
		app.logins.Fail(now, keys...)
		apiErr(w, http.StatusUnauthorized, "wrong_credentials", "This access key is not valid.")
		return
	}
	app.respondTokens(w, r, u, "", http.StatusOK)
}

func (app *App) respondTokens(w http.ResponseWriter, r *http.Request, u User, family string, status int) {
	t, err := app.issueTokens(r.Context(), u, family)
	if err != nil {
		apiErr(w, http.StatusInternalServerError, "server_error", "Could not sign in.")
		return
	}
	writeJSON(w, status, t)
}

type refreshBody struct {
	RefreshToken string `json:"refresh_token"`
}

func (app *App) handleRefresh(w http.ResponseWriter, r *http.Request) {
	var b refreshBody
	if !decodeJSON(w, r, &b, 2<<10) {
		return
	}
	uid, family, err := app.store.UseRefresh(r.Context(), sha(b.RefreshToken), app.now())
	if err != nil {
		apiErr(w, http.StatusUnauthorized, "wrong_credentials", "Session expired. Sign in again.")
		return
	}
	u, err := app.store.UserByID(r.Context(), uid)
	if err != nil {
		apiErr(w, http.StatusUnauthorized, "wrong_credentials", "Account not found.")
		return
	}
	app.respondTokens(w, r, u, family, http.StatusOK)
}

func (app *App) handleAPILogout(w http.ResponseWriter, r *http.Request) {
	var b refreshBody
	if !decodeJSON(w, r, &b, 2<<10) {
		return
	}
	app.store.RevokeFamily(r.Context(), sha(b.RefreshToken))
	w.WriteHeader(http.StatusNoContent)
}

func (app *App) handleMe(w http.ResponseWriter, r *http.Request, u User) {
	rev, _ := app.store.LatestRevision(r.Context(), u.ID)
	writeJSON(w, http.StatusOK, map[string]any{"account": u.Account(), "revision": rev, "created_at": u.CreatedAt})
}

func (app *App) handleDeleteMe(w http.ResponseWriter, r *http.Request, u User) {
	if err := app.store.DeleteUser(r.Context(), u.ID); err != nil {
		apiErr(w, http.StatusInternalServerError, "server_error", "Could not delete the account.")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func etagRev(rev int64) string { return `"` + strconv.FormatInt(rev, 10) + `"` }

func parseRevTag(s string) (int64, bool) {
	s = strings.TrimSpace(strings.TrimPrefix(s, "W/"))
	if len(s) < 3 || s[0] != '"' || s[len(s)-1] != '"' {
		return 0, false
	}
	n, err := strconv.ParseInt(s[1:len(s)-1], 10, 64)
	return n, err == nil && n >= 0
}

func (app *App) handleGetSnapshot(w http.ResponseWriter, r *http.Request, u User) {
	rev, err := app.store.LatestRevision(r.Context(), u.ID)
	if err != nil {
		apiErr(w, http.StatusInternalServerError, "server_error", "Try again later.")
		return
	}
	if rev == 0 {
		apiErr(w, http.StatusNotFound, "no_snapshot", "Nothing synced yet.")
		return
	}
	w.Header().Set("ETag", etagRev(rev))
	w.Header().Set("Cache-Control", "no-store")
	if known, ok := parseRevTag(r.Header.Get("If-None-Match")); ok && known == rev {
		w.WriteHeader(http.StatusNotModified)
		return
	}
	data, err := app.store.Snapshot(r.Context(), u.ID, rev)
	if err != nil {
		apiErr(w, http.StatusInternalServerError, "server_error", "Try again later.")
		return
	}
	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("X-Hayat-Revision", strconv.FormatInt(rev, 10))
	w.Write(data)
}

func (app *App) handlePutSnapshot(w http.ResponseWriter, r *http.Request, u User) {
	base := int64(-2)
	switch m := strings.TrimSpace(r.Header.Get("If-Match")); {
	case m == "*":
		base = -1
	case m == `"none"` || m == "none":
		base = 0
	default:
		if n, ok := parseRevTag(m); ok {
			base = n
		}
	}
	if base == -2 {
		apiErr(w, http.StatusPreconditionRequired, "if_match_required", `Send If-Match with the last revision, "none" or *.`)
		return
	}
	data, err := io.ReadAll(http.MaxBytesReader(w, r.Body, maxSnapshotBytes+1))
	if err != nil || len(data) > maxSnapshotBytes {
		apiErr(w, http.StatusRequestEntityTooLarge, "too_large", "Data is larger than 10 MB.")
		return
	}
	if len(data) == 0 {
		apiErr(w, http.StatusBadRequest, "empty", "Empty snapshot.")
		return
	}
	rev, err := app.store.PutSnapshot(r.Context(), u.ID, base, data, app.now())
	if errors.Is(err, errConflict) {
		w.Header().Set("ETag", etagRev(rev))
		writeJSON(w, http.StatusConflict, map[string]any{"error": "conflict", "message": "The server has a newer version.", "revision": rev})
		return
	}
	if err != nil {
		apiErr(w, http.StatusInternalServerError, "server_error", "Could not save. Try again.")
		return
	}
	app.store.TouchUser(r.Context(), u.ID, app.now())
	w.Header().Set("ETag", etagRev(rev))
	writeJSON(w, http.StatusOK, map[string]any{"revision": rev})
}

// cors allows browser clients from configured origins only (the Android app does not need CORS).
func (app *App) cors(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		origin := r.Header.Get("Origin")
		allowed := false
		for _, o := range app.corsOrigins {
			if o != "" && subtle.ConstantTimeCompare([]byte(o), []byte(origin)) == 1 {
				allowed = true
			}
		}
		if allowed {
			h := w.Header()
			h.Set("Access-Control-Allow-Origin", origin)
			h.Set("Vary", "Origin")
			h.Set("Access-Control-Allow-Headers", "Authorization, Content-Type, If-Match, If-None-Match")
			h.Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
			h.Set("Access-Control-Expose-Headers", "ETag, X-Hayat-Revision")
			h.Set("Access-Control-Max-Age", "600")
		}
		if r.Method == http.MethodOptions {
			if allowed {
				w.WriteHeader(http.StatusNoContent)
			} else {
				w.WriteHeader(http.StatusForbidden)
			}
			return
		}
		next.ServeHTTP(w, r)
	})
}

func (app *App) apiRoutes(mux *http.ServeMux) {
	mux.HandleFunc("GET /api/health", app.handleHealth)
	mux.HandleFunc("POST /api/auth/register", app.authLimited(app.handleRegister))
	mux.HandleFunc("POST /api/auth/login", app.authLimited(app.handleAPILogin))
	mux.HandleFunc("POST /api/auth/key", app.authLimited(app.handleKeyLogin))
	mux.HandleFunc("POST /api/auth/refresh", app.authLimited(app.handleRefresh))
	mux.HandleFunc("POST /api/auth/logout", app.authLimited(app.handleAPILogout))
	mux.HandleFunc("GET /api/me", app.requireUser(app.handleMe))
	mux.HandleFunc("DELETE /api/me", app.requireUser(app.handleDeleteMe))
	mux.HandleFunc("GET /api/sync/snapshot", app.requireUser(app.handleGetSnapshot))
	mux.HandleFunc("PUT /api/sync/snapshot", app.requireUser(app.handlePutSnapshot))
	mux.HandleFunc("OPTIONS /api/", func(w http.ResponseWriter, r *http.Request) {}) // answered by cors()
}
