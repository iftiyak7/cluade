package main

import (
	"errors"
	"net/url"
	"strings"
	"time"
	"unicode/utf8"
)

// Kind controls where the app shows an announcement.
type Kind string

const (
	KindBanner       Kind = "banner"       // card on the app's Home screen
	KindNotification Kind = "notification" // system notification (once per item)
	KindBoth         Kind = "both"
)

// Announcement is one item an admin publishes. It contains no user data.
type Announcement struct {
	ID         int64      `json:"id"`
	Kind       Kind       `json:"kind"`
	TitleEn    string     `json:"title_en"`
	BodyEn     string     `json:"body_en"`
	TitleBn    string     `json:"title_bn"`
	BodyBn     string     `json:"body_bn"`
	CtaLabelEn string     `json:"cta_label_en,omitempty"`
	CtaLabelBn string     `json:"cta_label_bn,omitempty"`
	CtaURL     string     `json:"cta_url,omitempty"`
	ImageURL   string     `json:"image_url,omitempty"`
	Lang       string     `json:"lang"` // all | en | bn
	MinVersion int        `json:"min_version"`
	Priority   int        `json:"priority"`
	StartsAt   time.Time  `json:"starts_at"`
	EndsAt     *time.Time `json:"ends_at,omitempty"`
	Published  bool       `json:"-"`
	CreatedAt  time.Time  `json:"-"`
	UpdatedAt  time.Time  `json:"updated_at"`
}

// ActiveAt reports whether the item should be visible to apps at t.
func (a Announcement) ActiveAt(t time.Time) bool {
	if !a.Published || t.Before(a.StartsAt) {
		return false
	}
	return a.EndsAt == nil || t.Before(*a.EndsAt)
}

// Status is a human label for the admin list.
func (a Announcement) Status(now time.Time) string {
	switch {
	case !a.Published:
		return "draft"
	case now.Before(a.StartsAt):
		return "scheduled"
	case a.EndsAt != nil && !now.Before(*a.EndsAt):
		return "ended"
	default:
		return "live"
	}
}

// ValidationErrors maps a form field to a message.
type ValidationErrors map[string]string

func (v ValidationErrors) Error() string { return "invalid announcement" }

// allowedDeepLinks are the in-app destinations an announcement may open.
var allowedDeepLinks = map[string]bool{
	"hayat://home": true, "hayat://water": true, "hayat://tasks": true, "hayat://reminders": true,
	"hayat://ibadah": true, "hayat://habits": true, "hayat://progress": true, "hayat://settings": true,
	"hayat://medicine": true, "hayat://notes": true, "hayat://calendar": true,
}

// DeepLinks lists allowed in-app links (for the admin form hint).
func DeepLinks() []string {
	return []string{"hayat://home", "hayat://calendar", "hayat://tasks", "hayat://reminders", "hayat://water",
		"hayat://medicine", "hayat://habits", "hayat://ibadah", "hayat://notes", "hayat://progress", "hayat://settings"}
}

// Validate normalises and checks the announcement. Anything that will be shown in the app or
// opened by it is restricted: plain text only, https URLs or allow-listed hayat:// links.
func (a *Announcement) Validate() error {
	errs := ValidationErrors{}
	trim := func(s *string) { *s = strings.TrimSpace(*s) }
	for _, p := range []*string{&a.TitleEn, &a.BodyEn, &a.TitleBn, &a.BodyBn, &a.CtaLabelEn, &a.CtaLabelBn, &a.CtaURL, &a.ImageURL, &a.Lang} {
		trim(p)
	}
	switch a.Kind {
	case KindBanner, KindNotification, KindBoth:
	default:
		errs["kind"] = "Choose banner, notification or both."
	}
	if a.Lang == "" {
		a.Lang = "all"
	}
	if a.Lang != "all" && a.Lang != "en" && a.Lang != "bn" {
		errs["lang"] = "Language must be all, en or bn."
	}
	needEn, needBn := a.Lang != "bn", a.Lang != "en"
	checkText := func(field, v string, required bool, max int) {
		if required && v == "" {
			errs[field] = "Required."
			return
		}
		if utf8.RuneCountInString(v) > max {
			errs[field] = "Too long (max " + itoa(max) + " characters)."
		}
		if strings.ContainsAny(v, "\x00") {
			errs[field] = "Contains invalid characters."
		}
	}
	checkText("title_en", a.TitleEn, needEn, 80)
	checkText("body_en", a.BodyEn, needEn, 400)
	checkText("title_bn", a.TitleBn, needBn, 80)
	checkText("body_bn", a.BodyBn, needBn, 400)
	checkText("cta_label_en", a.CtaLabelEn, false, 30)
	checkText("cta_label_bn", a.CtaLabelBn, false, 30)
	if a.CtaURL != "" {
		if !allowedDeepLinks[a.CtaURL] && !isHTTPSURL(a.CtaURL) {
			errs["cta_url"] = "Use an https:// link or one of the listed hayat:// links."
		}
		if a.CtaLabelEn == "" && needEn {
			errs["cta_label_en"] = "Add a button label for the link."
		}
		if a.CtaLabelBn == "" && needBn {
			errs["cta_label_bn"] = "Add a button label for the link."
		}
	} else if a.CtaLabelEn != "" || a.CtaLabelBn != "" {
		errs["cta_url"] = "A button label needs a link."
	}
	if a.ImageURL != "" && !isHTTPSURL(a.ImageURL) {
		errs["image_url"] = "Images must use an https:// URL."
	}
	if a.MinVersion < 0 || a.MinVersion > 1_000_000 {
		errs["min_version"] = "Must be between 0 and 1000000."
	}
	if a.Priority < 0 || a.Priority > 100 {
		errs["priority"] = "Must be between 0 and 100."
	}
	if a.StartsAt.IsZero() {
		errs["starts_at"] = "Required."
	}
	if a.EndsAt != nil && !a.EndsAt.After(a.StartsAt) {
		errs["ends_at"] = "Must be after the start."
	}
	if len(errs) > 0 {
		return errs
	}
	return nil
}

func isHTTPSURL(s string) bool {
	if len(s) > 500 {
		return false
	}
	u, err := url.Parse(s)
	return err == nil && u.Scheme == "https" && u.Host != "" && u.User == nil
}

func itoa(n int) string {
	if n == 0 {
		return "0"
	}
	neg := n < 0
	if neg {
		n = -n
	}
	var b [20]byte
	i := len(b)
	for n > 0 {
		i--
		b[i] = byte('0' + n%10)
		n /= 10
	}
	if neg {
		i--
		b[i] = '-'
	}
	return string(b[i:])
}

var errNotFound = errors.New("not found")
