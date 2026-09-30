package main

import (
	"errors"
	"testing"
	"time"
)

const (
	secret = "whsec_test"
	ts     = int64(1700000000)
	body   = `{"id":"evt_1"}`
	// Same vector as backend WebhookSignatureTest.knownVector: Java and Go must agree byte for byte.
	want = "t=1700000000,v1=c89214b5b5da833daed6f0b8c5bb6bd58cea9022bd80ccc78230f3942d632925"
)

func TestKnownVectorMatchesJava(t *testing.T) {
	if got := Sign(secret, ts, []byte(body)); got != want {
		t.Fatalf("got %s want %s", got, want)
	}
}

func TestVerify(t *testing.T) {
	at := func(s int64) time.Time { return time.Unix(s, 0) }
	cases := []struct {
		name, secret, header, body string
		now                        time.Time
		want                       error
	}{
		{"valid", secret, want, body, at(ts + 10), nil},
		{"tampered body", secret, want, `{"id":"evt_2"}`, at(ts), ErrMismatch},
		{"wrong secret", "other", want, body, at(ts), ErrMismatch},
		{"moved timestamp", secret, "t=1700000060,v1=c89214b5b5da833daed6f0b8c5bb6bd58cea9022bd80ccc78230f3942d632925", body, at(ts + 60), ErrMismatch},
		{"too old", secret, want, body, at(ts + 301), ErrStale},
		{"from the future", secret, want, body, at(ts - 301), ErrStale},
		{"missing v1", secret, "t=1700000000", body, at(ts), ErrMalformed},
		{"garbage", secret, "hello", body, at(ts), ErrMalformed},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			err := Verify(c.secret, c.header, []byte(c.body), c.now, 5*time.Minute)
			if !errors.Is(err, c.want) {
				t.Fatalf("got %v want %v", err, c.want)
			}
		})
	}
}
