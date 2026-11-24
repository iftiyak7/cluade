package main

import (
	"bytes"
	"crypto/sha256"
	"embed"
	"encoding/hex"
	"encoding/json"
	"errors"
	"html/template"
	"io/fs"
	"log/slog"
	"net/http"
	"os"
	"path"
	"strconv"
	"strings"
	"time"
)

//go:embed web/templates/*.html web/static/*
var webFS embed.FS

type App struct {
	cfg      Config
	store    *Store
	log      *slog.Logger
	tmpl     map[string]*template.Template
	logins   *LoginLimiter
	feed     *RateLimiter
	adminTZ  *time.Location
	now      func() time.Time
	staticFS http.Handler
}

func NewApp(cfg Config, st *Store, log *slog.Logger) (*App, error) {
	tz, err := time.LoadLocation(envOr("HAYAT_ADMIN_TZ", "Asia/Dhaka"))
	if err != nil {
		tz = time.UTC
	}
	app := &App{
		cfg: cfg, store: st, log: log,
		logins:  NewLoginLimiter(5, 15*time.Minute),
		feed:    NewRateLimiter(60, 30),
		adminTZ: tz,
		now:     time.Now,
	}
	funcs := template.FuncMap{
		"local":     func(t time.Time) string { return t.In(app.adminTZ).Format("02 Jan 2006, 15:04") },
		"inputdt":   func(t time.Time) string { return t.In(app.adminTZ).Format("2006-01-02T15:04") },
		"status":    func(a Announcement) string { return a.Status(app.now()) },
		"tzname":    func() string { return app.adminTZ.String() },
		"deeplinks": DeepLinks,
	}
	app.tmpl = map[string]*template.Template{}
	for _, page := range []string{"login", "list", "edit", "password"} {
		t, err := template.New("layout.html").Funcs(funcs).ParseFS(webFS, "web/templates/layout.html", "web/templates/"+page+".html")
		if err != nil {
			return nil, err
		}
		app.tmpl[page] = t
	}
	static, _ := fs.Sub(webFS, "web/static")
	app.staticFS = http.StripPrefix("/admin/static/", http.FileServer(http.FS(static)))
	return app, nil
}

func (app *App) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/v1/announcements", app.handleFeed)
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, r *http.Request) { w.Write([]byte("ok")) })

	mux.HandleFunc("GET /admin/login", app.handleLoginPage)
	mux.HandleFunc("POST /admin/login", app.handleLogin)
	mux.HandleFunc("POST /admin/logout", app.requireAdmin(app.handleLogout))
	mux.HandleFunc("GET /admin", app.requireAdmin(app.handleList))
	mux.HandleFunc("GET /admin/{$}", func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, "/admin", http.StatusMovedPermanently)
	})
	mux.HandleFunc("GET /admin/new", app.requireAdmin(app.handleEditPage))
	mux.HandleFunc("POST /admin/new", app.requireAdmin(app.handleSave))
	mux.HandleFunc("GET /admin/a/{id}", app.requireAdmin(app.handleEditPage))
	mux.HandleFunc("POST /admin/a/{id}", app.requireAdmin(app.handleSave))
	mux.HandleFunc("POST /admin/a/{id}/publish", app.requireAdmin(app.handlePublish(true)))
	mux.HandleFunc("POST /admin/a/{id}/unpublish", app.requireAdmin(app.handlePublish(false)))
	mux.HandleFunc("POST /admin/a/{id}/delete", app.requireAdmin(app.handleDelete))
	mux.HandleFunc("GET /admin/password", app.requireAdmin(app.handlePasswordPage))
	mux.HandleFunc("POST /admin/password", app.requireAdmin(app.handlePassword))
	mux.Handle("GET /admin/static/", app.staticFS)

	if app.cfg.SiteDir != "" {
		mux.Handle("GET /", siteHandler(app.cfg.SiteDir))
	} else {
		mux.HandleFunc("GET /{$}", func(w http.ResponseWriter, r *http.Request) { w.Write([]byte("Hayat")) })
	}
	return app.recoverer(app.securityHeaders(mux))
}

// ---- middleware ----

func (app *App) securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		h.Set("Content-Security-Policy", "default-src 'self'; img-src 'self' https: data:; script-src 'self'; style-src 'self'; font-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'; object-src 'none'")
		h.Set("X-Content-Type-Options", "nosniff")
		h.Set("X-Frame-Options", "DENY")
		h.Set("Referrer-Policy", "strict-origin-when-cross-origin")
		h.Set("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), interest-cohort=()")
		h.Set("Cross-Origin-Opener-Policy", "same-origin")
		if app.cfg.SecureCookies {
			h.Set("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
		}
		if strings.HasPrefix(r.URL.Path, "/admin") {
			h.Set("X-Robots-Tag", "noindex, nofollow")
		}
		next.ServeHTTP(w, r)
	})
}

func (app *App) recoverer(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if v := recover(); v != nil {
				app.log.Error("panic", "path", r.URL.Path, "err", v)
				http.Error(w, "Internal error", http.StatusInternalServerError)
			}
		}()
		next.ServeHTTP(w, r)
	})
}

// siteHandler serves the static landing page without directory listings or dotfiles.
func siteHandler(dir string) http.Handler {
	files := http.FileServer(http.Dir(dir))
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		p := path.Clean("/" + r.URL.Path)
		for _, seg := range strings.Split(p, "/") {
			if strings.HasPrefix(seg, ".") {
				http.NotFound(w, r)
				return
			}
		}
		full := dir + p
		if st, err := os.Stat(full); err == nil && st.IsDir() {
			if _, err := os.Stat(full + "/index.html"); err != nil {
				http.NotFound(w, r)
				return
			}
		}
		if strings.HasPrefix(p, "/assets/") {
			w.Header().Set("Cache-Control", "public, max-age=604800")
		} else {
			w.Header().Set("Cache-Control", "public, max-age=300")
		}
		files.ServeHTTP(w, r)
	})
}

// ---- public feed ----

type feedResponse struct {
	Version     int            `json:"version"`
	GeneratedAt time.Time      `json:"generated_at"`
	Items       []Announcement `json:"items"`
}

// handleFeed returns live announcements. It takes no parameters (the app filters by language and
// version locally), sets no cookies and logs nothing about the caller.
func (app *App) handleFeed(w http.ResponseWriter, r *http.Request) {
	if !app.feed.Allow(app.clientIP(r), app.now()) {
		w.Header().Set("Retry-After", "60")
		http.Error(w, "Too many requests", http.StatusTooManyRequests)
		return
	}
	items, err := app.store.LiveAnnouncements(r.Context(), app.now())
	if err != nil {
		app.log.Error("feed", "err", err)
		http.Error(w, "Unavailable", http.StatusServiceUnavailable)
		return
	}
	if items == nil {
		items = []Announcement{}
	}
	// generated_at is truncated to the minute so the ETag stays stable between polls.
	body, _ := json.Marshal(feedResponse{Version: 1, GeneratedAt: app.now().UTC().Truncate(time.Minute), Items: items})
	sum := sha256.Sum256(body)
	etag := `"` + hex.EncodeToString(sum[:12]) + `"`
	h := w.Header()
	h.Set("ETag", etag)
	h.Set("Cache-Control", "public, max-age=300")
	h.Set("Content-Type", "application/json; charset=utf-8")
	if match := r.Header.Get("If-None-Match"); match != "" && strings.Contains(match, etag) {
		w.WriteHeader(http.StatusNotModified)
		return
	}
	w.Write(body)
}

// ---- admin pages ----

type pageData struct {
	Title     string
	Session   *Session
	CSRF      string
	Error     string
	Notice    string
	Items     []Announcement
	Audit     []AuditEntry
	Item      Announcement
	Errors    ValidationErrors
	IsNew     bool
	Username  string
	EndsInput string
}

func (app *App) render(w http.ResponseWriter, r *http.Request, page string, status int, d pageData) {
	if s, ok := sessionFrom(r.Context()); ok {
		d.Session = &s
		d.CSRF = s.CSRF
	}
	var buf bytes.Buffer
	if err := app.tmpl[page].Execute(&buf, d); err != nil {
		app.log.Error("render", "page", page, "err", err)
		http.Error(w, "Internal error", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	buf.WriteTo(w)
}

func (app *App) handleLoginPage(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	app.render(w, r, "login", http.StatusOK, pageData{Title: "Sign in"})
}

func (app *App) handleLogin(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	r.Body = http.MaxBytesReader(w, r.Body, 8<<10)
	if !app.sameOrigin(r) {
		http.Error(w, "Forbidden", http.StatusForbidden)
		return
	}
	user := strings.TrimSpace(r.PostFormValue("username"))
	pw := r.PostFormValue("password")
	now := app.now()
	keys := []string{"ip:" + app.clientIP(r), "user:" + strings.ToLower(user)}
	fail := func(msg string, status int) {
		app.render(w, r, "login", status, pageData{Title: "Sign in", Error: msg, Username: user})
	}
	if app.logins.Blocked(now, keys...) {
		fail("Too many failed attempts. Try again in 15 minutes.", http.StatusTooManyRequests)
		return
	}
	admin, err := app.store.AdminByUsername(r.Context(), user)
	hash := dummyHash
	if err == nil {
		hash = []byte(admin.PasswordHash)
	}
	pwErr := bcryptCompare(hash, pw)
	if err != nil || pwErr != nil {
		app.logins.Fail(now, keys...)
		app.log.Warn("admin login failed") // no username or IP in logs
		fail("Wrong username or password.", http.StatusUnauthorized)
		return
	}
	app.logins.Reset(keys...)
	if err := app.startSession(w, r, admin.ID); err != nil {
		fail("Could not sign in. Try again.", http.StatusInternalServerError)
		return
	}
	app.store.Audit(r.Context(), admin.ID, "login", "", now)
	http.Redirect(w, r, "/admin", http.StatusSeeOther)
}

func (app *App) handleLogout(w http.ResponseWriter, r *http.Request) {
	app.endSession(w, r)
	http.Redirect(w, r, "/admin/login", http.StatusSeeOther)
}

func (app *App) handleList(w http.ResponseWriter, r *http.Request) {
	items, err := app.store.ListAnnouncements(r.Context())
	if err != nil {
		http.Error(w, "Internal error", http.StatusInternalServerError)
		return
	}
	audit, _ := app.store.RecentAudit(r.Context(), 15)
	notice := map[string]string{"saved": "Saved.", "published": "Published — apps will pick it up within about an hour.", "unpublished": "Unpublished.", "deleted": "Deleted.", "password": "Password changed."}[r.URL.Query().Get("ok")]
	app.render(w, r, "list", http.StatusOK, pageData{Title: "Announcements", Items: items, Audit: audit, Notice: notice})
}

func (app *App) idParam(r *http.Request) (int64, bool) {
	id, err := strconv.ParseInt(r.PathValue("id"), 10, 64)
	return id, err == nil && id > 0
}

func (app *App) handleEditPage(w http.ResponseWriter, r *http.Request) {
	d := pageData{Title: "New announcement", IsNew: true, Item: Announcement{Kind: KindBanner, Lang: "all", StartsAt: app.now()}}
	if r.PathValue("id") != "" {
		id, ok := app.idParam(r)
		if !ok {
			http.NotFound(w, r)
			return
		}
		a, err := app.store.Announcement(r.Context(), id)
		if err != nil {
			http.NotFound(w, r)
			return
		}
		d = pageData{Title: "Edit announcement", Item: a}
	}
	if d.Item.EndsAt != nil {
		d.EndsInput = d.Item.EndsAt.In(app.adminTZ).Format("2006-01-02T15:04")
	}
	app.render(w, r, "edit", http.StatusOK, d)
}

func (app *App) parseLocal(v string) (time.Time, bool) {
	t, err := time.ParseInLocation("2006-01-02T15:04", strings.TrimSpace(v), app.adminTZ)
	return t, err == nil
}

func (app *App) handleSave(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 64<<10)
	if err := r.ParseForm(); err != nil {
		http.Error(w, "Bad request", http.StatusBadRequest)
		return
	}
	sess, _ := sessionFrom(r.Context())
	a := Announcement{}
	isNew := r.PathValue("id") == ""
	if !isNew {
		id, ok := app.idParam(r)
		if !ok {
			http.NotFound(w, r)
			return
		}
		existing, err := app.store.Announcement(r.Context(), id)
		if err != nil {
			http.NotFound(w, r)
			return
		}
		a = existing
	}
	f := r.PostForm
	a.Kind = Kind(f.Get("kind"))
	a.Lang = f.Get("lang")
	a.TitleEn, a.BodyEn, a.TitleBn, a.BodyBn = f.Get("title_en"), f.Get("body_en"), f.Get("title_bn"), f.Get("body_bn")
	a.CtaLabelEn, a.CtaLabelBn, a.CtaURL, a.ImageURL = f.Get("cta_label_en"), f.Get("cta_label_bn"), f.Get("cta_url"), f.Get("image_url")
	a.MinVersion, _ = strconv.Atoi(strings.TrimSpace(f.Get("min_version")))
	a.Priority, _ = strconv.Atoi(strings.TrimSpace(f.Get("priority")))
	errs := ValidationErrors{}
	if t, ok := app.parseLocal(f.Get("starts_at")); ok {
		a.StartsAt = t.UTC()
	} else {
		a.StartsAt = time.Time{}
	}
	a.EndsAt = nil
	endsInput := strings.TrimSpace(f.Get("ends_at"))
	if endsInput != "" {
		if t, ok := app.parseLocal(endsInput); ok {
			u := t.UTC()
			a.EndsAt = &u
		} else {
			errs["ends_at"] = "Invalid date."
		}
	}
	a.Published = f.Get("action") == "publish" || (f.Get("action") == "save" && a.Published)
	if err := a.Validate(); err != nil {
		var ve ValidationErrors
		if errors.As(err, &ve) {
			for k, v := range ve {
				errs[k] = v
			}
		}
	}
	if len(errs) > 0 {
		title := "Edit announcement"
		if isNew {
			title = "New announcement"
		}
		app.render(w, r, "edit", http.StatusUnprocessableEntity, pageData{Title: title, Item: a, Errors: errs, IsNew: isNew, Error: "Please fix the highlighted fields.", EndsInput: endsInput})
		return
	}
	now := app.now()
	if err := app.store.SaveAnnouncement(r.Context(), &a, now); err != nil {
		http.Error(w, "Could not save", http.StatusInternalServerError)
		return
	}
	action := "update"
	if isNew {
		action = "create"
	}
	app.store.Audit(r.Context(), sess.AdminID, action, "#"+strconv.FormatInt(a.ID, 10)+" "+a.TitleEn, now)
	ok := "saved"
	if a.Published {
		ok = "published"
	}
	http.Redirect(w, r, "/admin?ok="+ok, http.StatusSeeOther)
}

func (app *App) handlePublish(publish bool) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id, ok := app.idParam(r)
		if !ok {
			http.NotFound(w, r)
			return
		}
		sess, _ := sessionFrom(r.Context())
		if err := app.store.SetPublished(r.Context(), id, publish, app.now()); err != nil {
			http.NotFound(w, r)
			return
		}
		action, okp := "unpublish", "unpublished"
		if publish {
			action, okp = "publish", "published"
		}
		app.store.Audit(r.Context(), sess.AdminID, action, "#"+strconv.FormatInt(id, 10), app.now())
		http.Redirect(w, r, "/admin?ok="+okp, http.StatusSeeOther)
	}
}

func (app *App) handleDelete(w http.ResponseWriter, r *http.Request) {
	id, ok := app.idParam(r)
	if !ok {
		http.NotFound(w, r)
		return
	}
	sess, _ := sessionFrom(r.Context())
	if err := app.store.DeleteAnnouncement(r.Context(), id); err != nil {
		http.NotFound(w, r)
		return
	}
	app.store.Audit(r.Context(), sess.AdminID, "delete", "#"+strconv.FormatInt(id, 10), app.now())
	http.Redirect(w, r, "/admin?ok=deleted", http.StatusSeeOther)
}

func (app *App) handlePasswordPage(w http.ResponseWriter, r *http.Request) {
	app.render(w, r, "password", http.StatusOK, pageData{Title: "Change password"})
}

func (app *App) handlePassword(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 8<<10)
	sess, _ := sessionFrom(r.Context())
	fail := func(msg string, status int) {
		app.render(w, r, "password", status, pageData{Title: "Change password", Error: msg})
	}
	admin, err := app.store.AdminByUsername(r.Context(), sess.Username)
	if err != nil {
		fail("Account not found.", http.StatusInternalServerError)
		return
	}
	if bcryptCompare([]byte(admin.PasswordHash), r.PostFormValue("current")) != nil {
		fail("Current password is wrong.", http.StatusUnauthorized)
		return
	}
	next := r.PostFormValue("new")
	if next != r.PostFormValue("repeat") {
		fail("New passwords do not match.", http.StatusUnprocessableEntity)
		return
	}
	hash, err := HashPassword(next)
	if err != nil {
		fail(strings.ToUpper(err.Error()[:1])+err.Error()[1:]+".", http.StatusUnprocessableEntity)
		return
	}
	if err := app.store.SetAdminPassword(r.Context(), admin.Username, hash, app.now()); err != nil {
		fail("Could not change the password.", http.StatusInternalServerError)
		return
	}
	app.store.Audit(r.Context(), admin.ID, "password", "", app.now())
	// All sessions (including this one) were revoked; sign in again with a fresh session.
	if err := app.startSession(w, r, admin.ID); err != nil {
		http.Redirect(w, r, "/admin/login", http.StatusSeeOther)
		return
	}
	http.Redirect(w, r, "/admin?ok=password", http.StatusSeeOther)
}
