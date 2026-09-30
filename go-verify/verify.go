// Package main verifies misc-lab payment webhook signatures.
//
// Scheme (same as backend WebhookSigner): header "t=<unix seconds>,v1=<hex HMAC-SHA256(secret, "<t>.<body>")>".
package main

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"strconv"
	"strings"
	"time"
)

var (
	ErrMalformed = errors.New("malformed signature header")
	ErrMismatch  = errors.New("signature mismatch")
	ErrStale     = errors.New("timestamp outside tolerance")
)

// Sign returns the header value for body at time t.
func Sign(secret string, t int64, body []byte) string {
	return "t=" + strconv.FormatInt(t, 10) + ",v1=" + mac(secret, t, body)
}

func mac(secret string, t int64, body []byte) string {
	m := hmac.New(sha256.New, []byte(secret))
	m.Write([]byte(strconv.FormatInt(t, 10) + "."))
	m.Write(body)
	return hex.EncodeToString(m.Sum(nil))
}

// Verify checks the signature (constant-time) and that t is within tolerance of now.
func Verify(secret, header string, body []byte, now time.Time, tolerance time.Duration) error {
	var t int64 = -1
	var v1 string
	for _, part := range strings.Split(header, ",") {
		k, v, ok := strings.Cut(strings.TrimSpace(part), "=")
		if !ok {
			continue
		}
		switch k {
		case "t":
			if n, err := strconv.ParseInt(v, 10, 64); err == nil {
				t = n
			}
		case "v1":
			v1 = v
		}
	}
	if t < 0 || v1 == "" {
		return ErrMalformed
	}
	if !hmac.Equal([]byte(mac(secret, t, body)), []byte(v1)) {
		return ErrMismatch
	}
	d := now.Unix() - t
	if d < 0 {
		d = -d
	}
	if time.Duration(d)*time.Second > tolerance {
		return ErrStale
	}
	return nil
}
