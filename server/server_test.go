package main

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/cookiejar"
	"net/http/httptest"
	"net/url"
	"path/filepath"
	"regexp"
	"strings"
	"testing"
	"time"
)

type harness struct {
	t      *testing.T
	app    *App
	srv    *httptest.Server
	client *http.Client
	store  *Store
	now    time.Time
}

func newHarness(t *testing.T) *harness {
	t.Helper()
	st, err := OpenStore(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { st.Close() })
	cfg := Config{SecureCookies: false, TrustProxy: false}
	app, err := NewApp(cfg, st, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatal(err)
	}
	h := &harness{t: t, app: app, store: st, now: time.Date(2026, 11, 24, 6, 0, 0, 0, time.UTC)}
	app.now = func() time.Time { return h.now }
	h.srv = httptest.NewServer(app.Handler())
	t.Cleanup(h.srv.Close)
	jar, _ := cookiejar.New(nil)
	h.client = &http.Client{Jar: jar, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	hash, err := HashPassword("correct horse battery")
	if err != nil {
		t.Fatal(err)
	}
	if err := st.CreateAdmin(context.Background(), "admin", hash, h.now); err != nil {
		t.Fatal(err)
	}
	return h
}

func (h *harness) post(path string, form url.Values, origin bool) *http.Response {
	req, _ := http.NewRequest(http.MethodPost, h.srv.URL+path, strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	if origin {
		req.Header.Set("Origin", h.srv.URL)
	}
	resp, err := h.client.Do(req)
	if err != nil {
		h.t.Fatal(err)
	}
	return resp
}

func (h *harness) get(path string) (*http.Response, string) {
	resp, err := h.client.Get(h.srv.URL + path)
	if err != nil {
		h.t.Fatal(err)
	}
	b, _ := io.ReadAll(resp.Body)
	resp.Body.Close()
	return resp, string(b)
}

func (h *harness) login() string {
	resp := h.post("/admin/login", url.Values{"username": {"admin"}, "password": {"correct horse battery"}}, true)
	if resp.StatusCode != http.StatusSeeOther {
		h.t.Fatalf("login status %d", resp.StatusCode)
	}
	_, body := h.get("/admin")
	m := regexp.MustCompile(`name="csrf" value="([^"]+)"`).FindStringSubmatch(body)
	if m == nil {
		h.t.Fatal("no csrf token on admin page")
	}
	return m[1]
}

func validForm(csrf string) url.Values {
	return url.Values{
		"csrf": {csrf}, "kind": {"banner"}, "lang": {"all"},
		"title_en": {"Ramadan reminders"}, "body_en": {"Set up suhoor and iftar reminders."},
		"title_bn": {"রমজানের রিমাইন্ডার"}, "body_bn": {"সেহরি ও ইফতারের রিমাইন্ডার সেট করুন।"},
		"cta_label_en": {"Open"}, "cta_label_bn": {"খুলুন"}, "cta_url": {"hayat://ibadah"},
		"starts_at": {"2026-11-24T10:00"}, "priority": {"10"}, "min_version": {"0"}, "action": {"publish"},
	}
}

func TestAdminPagesRequireLogin(t *testing.T) {
	h := newHarness(t)
	for _, p := range []string{"/admin", "/admin/new", "/admin/password"} {
		resp, _ := h.get(p)
		if resp.StatusCode != http.StatusSeeOther || resp.Header.Get("Location") != "/admin/login" {
			t.Errorf("%s: got %d %s", p, resp.StatusCode, resp.Header.Get("Location"))
		}
	}
}

func TestLoginFailureAndLockout(t *testing.T) {
	h := newHarness(t)
	for i := 0; i < 5; i++ {
		resp := h.post("/admin/login", url.Values{"username": {"admin"}, "password": {"wrong password!!"}}, true)
		if resp.StatusCode != http.StatusUnauthorized {
			t.Fatalf("attempt %d: %d", i, resp.StatusCode)
		}
	}
	// Even the correct password is refused while locked out.
	resp := h.post("/admin/login", url.Values{"username": {"admin"}, "password": {"correct horse battery"}}, true)
	if resp.StatusCode != http.StatusTooManyRequests {
		t.Fatalf("expected lockout, got %d", resp.StatusCode)
	}
	h.now = h.now.Add(16 * time.Minute)
	resp = h.post("/admin/login", url.Values{"username": {"admin"}, "password": {"correct horse battery"}}, true)
	if resp.StatusCode != http.StatusSeeOther {
		t.Fatalf("expected login after window, got %d", resp.StatusCode)
	}
}

func TestUnknownUserSameMessage(t *testing.T) {
	h := newHarness(t)
	resp := h.post("/admin/login", url.Values{"username": {"nobody"}, "password": {"whatever password"}}, true)
	b, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusUnauthorized || !strings.Contains(string(b), "Wrong username or password") {
		t.Fatalf("got %d", resp.StatusCode)
	}
}

func TestSessionCookieFlags(t *testing.T) {
	h := newHarness(t)
	h.app.cfg.SecureCookies = true // check production flags
	req := httptest.NewRequest(http.MethodPost, "/admin/login", nil)
	w := httptest.NewRecorder()
	if err := h.app.startSession(w, req, 1); err != nil {
		t.Fatal(err)
	}
	c := w.Result().Cookies()[0]
	if c.Name != "__Host-hayat_admin" || !c.HttpOnly || !c.Secure || c.SameSite != http.SameSiteStrictMode || c.Path != "/" {
		t.Fatalf("bad cookie: %+v", c)
	}
	// Only the token's hash is stored, never the token.
	var n int
	h.store.db.QueryRow(`SELECT COUNT(*) FROM sessions WHERE token_hash = ?`, c.Value).Scan(&n)
	if n != 0 {
		t.Fatal("raw session token stored in database")
	}
}

func TestCSRFAndOriginRequired(t *testing.T) {
	h := newHarness(t)
	csrf := h.login()
	form := validForm("wrong-token")
	if resp := h.post("/admin/new", form, true); resp.StatusCode != http.StatusForbidden {
		t.Fatalf("bad csrf accepted: %d", resp.StatusCode)
	}
	h.app.cfg.SecureCookies = true // production: Origin must match
	req, _ := http.NewRequest(http.MethodPost, h.srv.URL+"/admin/new", strings.NewReader(validForm(csrf).Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("Origin", "https://evil.example")
	// The cookie name changes with SecureCookies; reuse the dev cookie value under the prod name.
	u, _ := url.Parse(h.srv.URL)
	for _, c := range h.client.Jar.Cookies(u) {
		req.AddCookie(&http.Cookie{Name: "__Host-hayat_admin", Value: c.Value})
	}
	resp, _ := http.DefaultClient.Do(req)
	if resp.StatusCode != http.StatusForbidden {
		t.Fatalf("cross-origin POST accepted: %d", resp.StatusCode)
	}
	items, _ := h.store.ListAnnouncements(context.Background())
	if len(items) != 0 {
		t.Fatal("announcement created despite CSRF failure")
	}
}

func TestCreatePublishAndFeed(t *testing.T) {
	h := newHarness(t)
	csrf := h.login()
	resp := h.post("/admin/new", validForm(csrf), true)
	if resp.StatusCode != http.StatusSeeOther {
		b, _ := io.ReadAll(resp.Body)
		t.Fatalf("create: %d %s", resp.StatusCode, b)
	}
	// starts_at 10:00 Asia/Dhaka = 04:00 UTC, now is 06:00 UTC → live
	r, body := h.get("/api/v1/announcements")
	if r.StatusCode != 200 || r.Header.Get("ETag") == "" || r.Header.Get("Set-Cookie") != "" {
		t.Fatalf("feed: %d etag=%q cookie=%q", r.StatusCode, r.Header.Get("ETag"), r.Header.Get("Set-Cookie"))
	}
	var feed struct {
		Version int `json:"version"`
		Items   []struct {
			Title  string `json:"title_en"`
			TitleB string `json:"title_bn"`
			CtaURL string `json:"cta_url"`
		} `json:"items"`
	}
	if err := json.Unmarshal([]byte(body), &feed); err != nil {
		t.Fatal(err)
	}
	if feed.Version != 1 || len(feed.Items) != 1 || feed.Items[0].Title != "Ramadan reminders" || feed.Items[0].CtaURL != "hayat://ibadah" {
		t.Fatalf("unexpected feed: %s", body)
	}
	if strings.Contains(body, "published") || strings.Contains(body, "created_at") {
		t.Fatal("feed leaks admin-only fields")
	}
	// Conditional request → 304.
	req, _ := http.NewRequest(http.MethodGet, h.srv.URL+"/api/v1/announcements", nil)
	req.Header.Set("If-None-Match", r.Header.Get("ETag"))
	r2, _ := http.DefaultClient.Do(req)
	if r2.StatusCode != http.StatusNotModified {
		t.Fatalf("expected 304, got %d", r2.StatusCode)
	}
	// Unpublish removes it from the feed.
	items, _ := h.store.ListAnnouncements(context.Background())
	h.post("/admin/a/"+itoa(int(items[0].ID))+"/unpublish", url.Values{"csrf": {csrf}}, true)
	_, body = h.get("/api/v1/announcements")
	if !strings.Contains(body, `"items":[]`) {
		t.Fatalf("unpublished item still in feed: %s", body)
	}
}

func TestScheduleWindow(t *testing.T) {
	h := newHarness(t)
	ctx := context.Background()
	end := h.now.Add(2 * time.Hour)
	a := Announcement{Kind: KindBanner, Lang: "en", TitleEn: "Soon", BodyEn: "x", StartsAt: h.now.Add(time.Hour), EndsAt: &end, Published: true}
	if err := a.Validate(); err != nil {
		t.Fatal(err)
	}
	h.store.SaveAnnouncement(ctx, &a, h.now)
	for _, c := range []struct {
		at   time.Duration
		live bool
	}{{0, false}, {90 * time.Minute, true}, {3 * time.Hour, false}} {
		got, _ := h.store.LiveAnnouncements(ctx, h.now.Add(c.at))
		if (len(got) == 1) != c.live {
			t.Errorf("at +%v live=%v want %v", c.at, len(got) == 1, c.live)
		}
	}
}

func TestValidationRejectsUnsafeLinks(t *testing.T) {
	base := Announcement{Kind: KindBanner, Lang: "en", TitleEn: "t", BodyEn: "b", StartsAt: time.Now(), CtaLabelEn: "Go"}
	for _, bad := range []string{"javascript:alert(1)", "http://example.com", "hayat://evil", "https://user:pw@example.com", "data:text/html,x"} {
		a := base
		a.CtaURL = bad
		if a.Validate() == nil {
			t.Errorf("accepted cta_url %q", bad)
		}
	}
	a := base
	a.CtaURL = "https://hayat.app/blog"
	if err := a.Validate(); err != nil {
		t.Errorf("rejected valid https link: %v", err)
	}
	b := base
	b.ImageURL = "http://insecure.example/x.png"
	if b.Validate() == nil {
		t.Error("accepted non-https image")
	}
	c := base
	c.Lang = "all" // requires Bengali text too
	if c.Validate() == nil {
		t.Error("accepted 'all' without Bengali text")
	}
}

func TestFormErrorsReRender(t *testing.T) {
	h := newHarness(t)
	csrf := h.login()
	f := validForm(csrf)
	f.Set("title_en", "")
	f.Set("cta_url", "javascript:alert(1)")
	resp := h.post("/admin/new", f, true)
	b, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusUnprocessableEntity || !strings.Contains(string(b), "Use an https:// link") {
		t.Fatalf("got %d", resp.StatusCode)
	}
	// Submitted values are HTML-escaped when re-rendered.
	f.Set("title_en", `<script>alert(1)</script>`)
	resp = h.post("/admin/new", f, true)
	b, _ = io.ReadAll(resp.Body)
	if strings.Contains(string(b), "<script>alert(1)</script>") {
		t.Fatal("unescaped user input in page")
	}
}

func TestPasswordChangeRevokesSessions(t *testing.T) {
	h := newHarness(t)
	csrf := h.login()
	other := newClient()
	h.loginWith(other)
	resp := h.post("/admin/password", url.Values{"csrf": {csrf}, "current": {"correct horse battery"}, "new": {"another long secret"}, "repeat": {"another long secret"}}, true)
	if resp.StatusCode != http.StatusSeeOther {
		t.Fatalf("password change: %d", resp.StatusCode)
	}
	r, _ := other.Get(h.srv.URL + "/admin")
	if r.StatusCode != http.StatusSeeOther {
		t.Fatal("other session still valid after password change")
	}
	if r, _ := h.get("/admin"); r.StatusCode != http.StatusOK {
		t.Fatal("current session should be renewed")
	}
}

func newClient() *http.Client {
	jar, _ := cookiejar.New(nil)
	return &http.Client{Jar: jar, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
}

func (h *harness) loginWith(c *http.Client) {
	req, _ := http.NewRequest(http.MethodPost, h.srv.URL+"/admin/login", strings.NewReader(url.Values{"username": {"admin"}, "password": {"correct horse battery"}}.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("Origin", h.srv.URL)
	if r, _ := c.Do(req); r.StatusCode != http.StatusSeeOther {
		h.t.Fatal("second login failed")
	}
}

func TestSecurityHeadersAndFeedRateLimit(t *testing.T) {
	h := newHarness(t)
	r, _ := h.get("/admin/login")
	for _, k := range []string{"Content-Security-Policy", "X-Content-Type-Options", "X-Frame-Options", "Referrer-Policy"} {
		if r.Header.Get(k) == "" {
			t.Errorf("missing %s", k)
		}
	}
	limited := false
	for i := 0; i < 40; i++ {
		if r, _ := h.get("/api/v1/announcements"); r.StatusCode == http.StatusTooManyRequests {
			limited = true
			break
		}
	}
	if !limited {
		t.Error("feed was never rate limited")
	}
}

func TestSiteHandlerHidesDotfilesAndListings(t *testing.T) {
	dir := t.TempDir()
	mustWrite(t, filepath.Join(dir, "index.html"), "home")
	mustWrite(t, filepath.Join(dir, ".env"), "secret")
	mustWrite(t, filepath.Join(dir, "assets", "a.css"), "css")
	srv := httptest.NewServer(siteHandler(dir))
	defer srv.Close()
	for path, want := range map[string]int{"/": 200, "/.env": 404, "/assets/": 404, "/assets/a.css": 200} {
		r, _ := http.Get(srv.URL + path)
		if r.StatusCode != want {
			t.Errorf("%s: %d want %d", path, r.StatusCode, want)
		}
	}
}
