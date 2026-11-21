//! Secret redaction. RPC URLs carry API keys in the query string
//! (`?api-key=…`); they must never reach logs, metrics or error messages.

/// Replace every query-parameter value whose name looks secret, and any
/// userinfo (`user:pass@`), with `***`.
pub fn redact_url(url: &str) -> String {
    let (base, query) = match url.split_once('?') {
        Some((b, q)) => (b, Some(q)),
        None => (url, None),
    };
    let base = match base.split_once("://") {
        Some((scheme, rest)) => match rest.split_once('@') {
            Some((_, host)) if !host.contains('/') || rest.find('@') < rest.find('/') => format!("{scheme}://***@{host}"),
            _ => base.to_string(),
        },
        None => base.to_string(),
    };
    let Some(q) = query else { return base };
    let parts: Vec<String> = q
        .split('&')
        .map(|kv| match kv.split_once('=') {
            Some((k, _)) if is_secret_name(k) => format!("{k}=***"),
            _ => kv.to_string(),
        })
        .collect();
    format!("{base}?{}", parts.join("&"))
}

fn is_secret_name(k: &str) -> bool {
    let k = k.to_ascii_lowercase();
    ["key", "token", "secret", "auth", "password", "sig"].iter().any(|s| k.contains(s))
}

/// Host part only — the safe label for an endpoint in logs and metrics.
pub fn host_label(url: &str) -> String {
    url.split("://").nth(1).unwrap_or(url).split(['/', '?']).next().unwrap_or("").rsplit('@').next().unwrap_or("").to_string()
}

/// Scrub any of the given secret URLs out of arbitrary text (error messages
/// from HTTP/WS libraries often embed the full request URL).
pub fn scrub(text: &str, urls: &[String]) -> String {
    let mut out = text.to_string();
    for u in urls {
        if !u.is_empty() {
            out = out.replace(u.as_str(), &redact_url(u));
        }
        if let Some((_, q)) = u.split_once('?') {
            for kv in q.split('&') {
                if let Some((k, v)) = kv.split_once('=') {
                    if is_secret_name(k) && v.len() >= 6 {
                        out = out.replace(v, "***");
                    }
                }
            }
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    const U: &str = "https://mainnet.example-rpc.com/?api-key=00000000-1111-2222-3333-444444444444";

    #[test]
    fn redacts_api_key() {
        let r = redact_url(U);
        assert_eq!(r, "https://mainnet.example-rpc.com/?api-key=***");
        assert!(!r.contains("0000"));
        assert_eq!(redact_url("wss://h/x?a=1&token=abc"), "wss://h/x?a=1&token=***");
        assert_eq!(redact_url("https://u:p@h/x"), "https://***@h/x");
    }

    #[test]
    fn host_label_has_no_secrets() {
        assert_eq!(host_label(U), "mainnet.example-rpc.com");
        assert_eq!(host_label("https://u:p@h.io:8899/x"), "h.io:8899");
    }

    #[test]
    fn scrub_removes_key_from_error_text() {
        let err = format!("error sending request for url ({U}): timeout; key=00000000-1111-2222-3333-444444444444");
        let s = scrub(&err, &[U.to_string()]);
        assert!(!s.contains("00000000-1111"), "{s}");
    }
}
