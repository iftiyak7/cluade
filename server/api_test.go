package main

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"strings"
	"testing"
	"time"
)

func (h *harness) api(method, path, token string, body any, hdr map[string]string) (int, map[string]any, http.Header, []byte) {
	h.t.Helper()
	var rd io.Reader
	if b, ok := body.([]byte); ok {
		rd = bytes.NewReader(b)
	} else if body != nil {
		j, _ := json.Marshal(body)
		rd = bytes.NewReader(j)
	}
	req, _ := http.NewRequest(method, h.srv.URL+path, rd)
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	for k, v := range hdr {
		req.Header.Set(k, v)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		h.t.Fatal(err)
	}
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	var m map[string]any
	_ = json.Unmarshal(raw, &m)
	return resp.StatusCode, m, resp.Header, raw
}

func (h *harness) register(email string) (access, refresh string) {
	code, m, _, raw := h.api("POST", "/api/auth/register", "", map[string]string{"email": email, "password": "a long password 1"}, nil)
	if code != 201 {
		h.t.Fatalf("register: %d %s", code, raw)
	}
	return m["access_token"].(string), m["refresh_token"].(string)
}

func TestHealth(t *testing.T) {
	h := newHarness(t)
	code, m, _, _ := h.api("GET", "/api/health", "", nil, nil)
	if code != 200 || m["status"] != "ok" || m["service"] != "hayat" {
		t.Fatalf("health: %d %v", code, m)
	}
}

func TestRegisterLoginAndValidation(t *testing.T) {
	h := newHarness(t)
	h.register("Ayesha@Example.com")
	// Duplicate (case-insensitive) is rejected.
	if code, m, _, _ := h.api("POST", "/api/auth/register", "", map[string]string{"email": "ayesha@example.com", "password": "a long password 1"}, nil); code != 409 || m["error"] != "email_taken" {
		t.Fatalf("duplicate: %d %v", code, m)
	}
	for _, bad := range []map[string]string{{"email": "not-an-email", "password": "a long password 1"}, {"email": "x@y.com", "password": "short"}} {
		if code, _, _, _ := h.api("POST", "/api/auth/register", "", bad, nil); code != 422 {
			t.Errorf("accepted %v: %d", bad, code)
		}
	}
	if code, _, _, _ := h.api("POST", "/api/auth/register", "", map[string]any{"email": "a@b.co", "password": "a long password 1", "admin": true}, nil); code != 400 {
		t.Error("unknown fields must be rejected")
	}
	if code, m, _, _ := h.api("POST", "/api/auth/login", "", map[string]string{"email": "ayesha@example.com", "password": "a long password 1"}, nil); code != 200 || m["access_token"] == nil || m["account"] != "ayesha@example.com" {
		t.Fatalf("login: %d %v", code, m)
	}
	if code, m, _, _ := h.api("POST", "/api/auth/login", "", map[string]string{"email": "ayesha@example.com", "password": "wrong password!!"}, nil); code != 401 || m["error"] != "wrong_credentials" {
		t.Fatalf("wrong password: %d %v", code, m)
	}
}

func TestSignupClosedAndAccessKey(t *testing.T) {
	h := newHarness(t)
	h.app.signupOpen = false
	if code, m, _, _ := h.api("POST", "/api/auth/register", "", map[string]string{"email": "a@b.co", "password": "a long password 1"}, nil); code != 403 || m["error"] != "signup_closed" {
		t.Fatalf("signup should be closed: %d %v", code, m)
	}
	key := "hk_" + randomToken(24)
	if _, err := h.store.CreateUser(context.Background(), "", "", sha(key), "Phone", h.now); err != nil {
		t.Fatal(err)
	}
	if code, m, _, _ := h.api("POST", "/api/auth/key", "", map[string]string{"access_key": key}, nil); code != 200 || m["account"] != "Phone" {
		t.Fatalf("key login: %d %v", code, m)
	}
	if code, _, _, _ := h.api("POST", "/api/auth/key", "", map[string]string{"access_key": key + "x"}, nil); code != 401 {
		t.Fatal("wrong key accepted")
	}
}

func TestAccessTokenRules(t *testing.T) {
	h := newHarness(t)
	access, _ := h.register("u@example.com")
	if code, _, _, _ := h.api("GET", "/api/me", access, nil, nil); code != 200 {
		t.Fatal("valid token rejected")
	}
	for _, bad := range []string{"", "garbage", access + "x", strings.Replace(access, ".", "x.", 1)} {
		if code, _, _, _ := h.api("GET", "/api/me", bad, nil, nil); code != 401 {
			t.Errorf("accepted bad token %q", bad)
		}
	}
	// alg=none tokens are rejected.
	none := "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiIxIiwiaXNzIjoiaGF5YXQiLCJhdWQiOlsiaGF5YXQtYXBwIl0sImV4cCI6OTk5OTk5OTk5OX0."
	if code, _, _, _ := h.api("GET", "/api/me", none, nil, nil); code != 401 {
		t.Error("alg=none accepted")
	}
	h.now = h.now.Add(16 * time.Minute)
	if code, _, _, _ := h.api("GET", "/api/me", access, nil, nil); code != 401 {
		t.Error("expired access token accepted")
	}
}

func TestRefreshRotationAndReuseDetection(t *testing.T) {
	h := newHarness(t)
	_, r1 := h.register("rot@example.com")
	code, m, _, _ := h.api("POST", "/api/auth/refresh", "", map[string]string{"refresh_token": r1}, nil)
	if code != 200 {
		t.Fatalf("refresh: %d", code)
	}
	r2 := m["refresh_token"].(string)
	if r2 == r1 {
		t.Fatal("refresh token not rotated")
	}
	// Reusing the old token is treated as theft: it fails and revokes the newer one too.
	if code, _, _, _ := h.api("POST", "/api/auth/refresh", "", map[string]string{"refresh_token": r1}, nil); code != 401 {
		t.Fatal("reused refresh token accepted")
	}
	if code, _, _, _ := h.api("POST", "/api/auth/refresh", "", map[string]string{"refresh_token": r2}, nil); code != 401 {
		t.Fatal("token family not revoked after reuse")
	}
	// Logout revokes.
	_, r3 := h.register("out@example.com")
	h.api("POST", "/api/auth/logout", "", map[string]string{"refresh_token": r3}, nil)
	if code, _, _, _ := h.api("POST", "/api/auth/refresh", "", map[string]string{"refresh_token": r3}, nil); code != 401 {
		t.Fatal("refresh after logout accepted")
	}
}

func TestSnapshotSyncFlow(t *testing.T) {
	h := newHarness(t)
	access, _ := h.register("sync@example.com")
	if code, _, _, _ := h.api("GET", "/api/sync/snapshot", access, nil, nil); code != 404 {
		t.Fatal("expected no snapshot")
	}
	if code, _, _, _ := h.api("PUT", "/api/sync/snapshot", access, []byte("v1"), nil); code != 428 {
		t.Fatal("PUT without If-Match must be refused")
	}
	code, m, hdr, _ := h.api("PUT", "/api/sync/snapshot", access, []byte(`{"v":1}`), map[string]string{"If-Match": `"none"`})
	if code != 200 || m["revision"].(float64) != 1 || hdr.Get("ETag") != `"1"` {
		t.Fatalf("first put: %d %v", code, m)
	}
	// Stale base → conflict with the server revision.
	if code, m, _, _ := h.api("PUT", "/api/sync/snapshot", access, []byte(`{"v":"stale"}`), map[string]string{"If-Match": `"none"`}); code != 409 || m["revision"].(float64) != 1 {
		t.Fatalf("expected conflict: %d %v", code, m)
	}
	h.api("PUT", "/api/sync/snapshot", access, []byte(`{"v":2}`), map[string]string{"If-Match": `"1"`})
	code, _, hdr, raw := h.api("GET", "/api/sync/snapshot", access, nil, nil)
	if code != 200 || string(raw) != `{"v":2}` || hdr.Get("ETag") != `"2"` {
		t.Fatalf("get: %d %s %s", code, raw, hdr.Get("ETag"))
	}
	if code, _, _, _ := h.api("GET", "/api/sync/snapshot", access, nil, map[string]string{"If-None-Match": `"2"`}); code != 304 {
		t.Fatalf("expected 304, got %d", code)
	}
	// Force overwrite and history trimming.
	for i := 0; i < 8; i++ {
		h.api("PUT", "/api/sync/snapshot", access, []byte(`{"f":1}`), map[string]string{"If-Match": "*"})
	}
	var n int
	h.store.db.QueryRow(`SELECT COUNT(*) FROM snapshots`).Scan(&n)
	if n != keepRevisions {
		t.Fatalf("kept %d revisions, want %d", n, keepRevisions)
	}
	// Users cannot see each other's data.
	other, _ := h.register("other@example.com")
	if code, _, _, _ := h.api("GET", "/api/sync/snapshot", other, nil, nil); code != 404 {
		t.Fatal("snapshot leaked across users")
	}
	big := bytes.Repeat([]byte("x"), maxSnapshotBytes+1)
	if code, _, _, _ := h.api("PUT", "/api/sync/snapshot", access, big, map[string]string{"If-Match": "*"}); code != 413 {
		t.Fatalf("oversize accepted: %d", code)
	}
}

func TestDeleteAccountRemovesData(t *testing.T) {
	h := newHarness(t)
	access, refresh := h.register("del@example.com")
	h.api("PUT", "/api/sync/snapshot", access, []byte("data"), map[string]string{"If-Match": `"none"`})
	if code, _, _, _ := h.api("DELETE", "/api/me", access, nil, nil); code != 204 {
		t.Fatal("delete failed")
	}
	var n int
	h.store.db.QueryRow(`SELECT COUNT(*) FROM snapshots`).Scan(&n)
	if n != 0 {
		t.Fatal("snapshots left after account deletion")
	}
	if code, _, _, _ := h.api("POST", "/api/auth/refresh", "", map[string]string{"refresh_token": refresh}, nil); code != 401 {
		t.Fatal("refresh token survived account deletion")
	}
}

func TestCORS(t *testing.T) {
	h := newHarness(t)
	h.app.corsOrigins = []string{"https://app.example.com"}
	code, _, hdr, _ := h.api("OPTIONS", "/api/me", "", nil, map[string]string{"Origin": "https://app.example.com", "Access-Control-Request-Method": "GET"})
	if code != 204 || hdr.Get("Access-Control-Allow-Origin") != "https://app.example.com" {
		t.Fatalf("preflight: %d %v", code, hdr)
	}
	code, _, hdr, _ = h.api("OPTIONS", "/api/me", "", nil, map[string]string{"Origin": "https://evil.example"})
	if code != 403 || hdr.Get("Access-Control-Allow-Origin") != "" {
		t.Fatal("unknown origin allowed")
	}
}

func TestAuthRateLimit(t *testing.T) {
	h := newHarness(t)
	limited := false
	for i := 0; i < 30; i++ {
		if code, _, _, _ := h.api("POST", "/api/auth/login", "", map[string]string{"email": "x@y.com", "password": "nope nope nope"}, nil); code == 429 {
			limited = true
			break
		}
	}
	if !limited {
		t.Fatal("login was never rate limited")
	}
}
