package main

import (
	"flag"
	"fmt"
	"io"
	"os"
	"time"
)

// Usage: go-verify -secret whsec_... -header 't=...,v1=...' < body.json
// Exit 0 = valid, 1 = rejected, 2 = usage error.
func main() {
	secret := flag.String("secret", os.Getenv("MISCLAB_WEBHOOK_SECRET"), "shared webhook secret")
	header := flag.String("header", "", "Misclab-Signature header value")
	tol := flag.Duration("tolerance", 5*time.Minute, "allowed clock skew / age")
	flag.Parse()
	if *secret == "" || *header == "" {
		fmt.Fprintln(os.Stderr, "need -secret (or MISCLAB_WEBHOOK_SECRET) and -header; body on stdin")
		os.Exit(2)
	}
	body, err := io.ReadAll(os.Stdin)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(2)
	}
	if err := Verify(*secret, *header, body, time.Now(), *tol); err != nil {
		fmt.Println("REJECTED:", err)
		os.Exit(1)
	}
	fmt.Println("OK")
}
