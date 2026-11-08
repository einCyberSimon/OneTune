package music

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"golang.org/x/time/rate"
)

const (
	maxResponseBytes = 4 << 20
	userAgent        = "onetune-resolver/0.1"
)

// upstream wraps one third-party API with its own rate limit, so a burst of
// resolves cannot get the server banned.
type upstream struct {
	http    *http.Client
	limiter *rate.Limiter
}

func newUpstream(requestsPerSecond float64, burst int) *upstream {
	return &upstream{
		http:    &http.Client{Timeout: 10 * time.Second},
		limiter: rate.NewLimiter(rate.Limit(requestsPerSecond), burst),
	}
}

func (u *upstream) do(ctx context.Context, method, target string, body []byte, header http.Header) ([]byte, error) {
	if err := u.limiter.Wait(ctx); err != nil {
		return nil, err
	}
	request, err := http.NewRequestWithContext(ctx, method, target, bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	request.Header.Set("User-Agent", userAgent)
	for key, values := range header {
		request.Header[key] = values
	}
	response, err := u.http.Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode == http.StatusNotFound {
		return nil, ErrNotFound
	}
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("upstream status %d", response.StatusCode)
	}
	return io.ReadAll(io.LimitReader(response.Body, maxResponseBytes))
}

func (u *upstream) getJSON(ctx context.Context, target string, header http.Header, out any) error {
	data, err := u.do(ctx, http.MethodGet, target, nil, header)
	if err != nil {
		return err
	}
	return json.Unmarshal(data, out)
}

func (u *upstream) postJSON(ctx context.Context, target string, payload any, out any) error {
	body, err := json.Marshal(payload)
	if err != nil {
		return err
	}
	header := http.Header{"Content-Type": {"application/json"}}
	data, err := u.do(ctx, http.MethodPost, target, body, header)
	if err != nil {
		return err
	}
	return json.Unmarshal(data, out)
}
