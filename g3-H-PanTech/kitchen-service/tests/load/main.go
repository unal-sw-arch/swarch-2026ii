// load mide GET /queue con 50 pedidos y varios clientes; falla si hay respuestas incorrectas o el p95 es alto.
// Uso: go run ./tests/load [-gateway URL -token JWT]
package main

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"net/http"
	"os"
	"sort"
	"sync"
	"sync/atomic"
	"time"

	"github.com/google/uuid"
)

type view struct {
	OrderID       string  `json:"order_id"`
	Status        string  `json:"status"`
	PriorityScore float64 `json:"priority_score"`
	Position      int     `json:"position"`
	EnqueuedAt    string  `json:"enqueued_at"`
}

type queueResp struct {
	Items    []view `json:"items"`
	Capacity struct {
		Max int `json:"max"`
	} `json:"capacity"`
}

func main() {
	kitchen := flag.String("kitchen", "http://localhost:8001", "URL directa de Kitchen (siembra y consulta)")
	gateway := flag.String("gateway", "", "si se indica, GET /queue se hace por el Gateway con -token")
	token := flag.String("token", "", "JWT con kitchen:read (solo con -gateway)")
	serviceToken := flag.String("service-token", os.Getenv("SERVICE_TOKEN"), "SERVICE_TOKEN para encolar")
	orders := flag.Int("orders", 50, "pedidos a encolar")
	concurrency := flag.Int("concurrency", 10, "clientes en paralelo")
	requests := flag.Int("requests", 500, "consultas totales a GET /queue")
	maxP95 := flag.Duration("max-p95", 500*time.Millisecond, "latencia p95 máxima aceptada")
	flag.Parse()
	if *serviceToken == "" {
		*serviceToken = "cambia-este-secreto-en-tu-entorno-local"
	}
	if *gateway != "" && *token == "" {
		fmt.Println("FAIL con -gateway hace falta -token")
		os.Exit(2)
	}

	client := &http.Client{Timeout: 10 * time.Second, Transport: &http.Transport{MaxIdleConnsPerHost: *concurrency}}
	if err := seed(client, *kitchen, *serviceToken, *orders); err != nil {
		fmt.Println("FAIL no se pudo sembrar la cola:", err)
		os.Exit(1)
	}

	target, headers := *kitchen+"/queue", map[string]string{"X-User-Id": uuid.NewString(), "X-Permissions": "kitchen:read"}
	if *gateway != "" {
		target, headers = *gateway+"/queue", map[string]string{"Authorization": "Bearer " + *token}
	}

	var (
		wg        sync.WaitGroup
		next      atomic.Int64
		mu        sync.Mutex
		latencies = make([]time.Duration, 0, *requests)
		failures  = map[string]int{}
	)
	record := func(d time.Duration, problem string) {
		mu.Lock()
		defer mu.Unlock()
		latencies = append(latencies, d)
		if problem != "" {
			failures[problem]++
		}
	}

	start := time.Now()
	for w := 0; w < *concurrency; w++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for next.Add(1) <= int64(*requests) {
				d, problem := query(client, target, headers, *orders)
				record(d, problem)
			}
		}()
	}
	wg.Wait()
	elapsed := time.Since(start)

	sort.Slice(latencies, func(i, j int) bool { return latencies[i] < latencies[j] })
	pct := func(p float64) time.Duration { return latencies[min(len(latencies)-1, int(float64(len(latencies))*p))] }
	total := len(latencies)
	bad := 0
	for _, n := range failures {
		bad += n
	}

	fmt.Printf("destino            %s\n", target)
	fmt.Printf("pedidos en la cola %d\n", *orders)
	fmt.Printf("consultas          %d con %d clientes en paralelo\n", total, *concurrency)
	fmt.Printf("duración           %s (%.0f consultas/s)\n", elapsed.Round(time.Millisecond), float64(total)/elapsed.Seconds())
	fmt.Printf("latencia           p50=%s p95=%s p99=%s max=%s\n", pct(0.50).Round(time.Microsecond), pct(0.95).Round(time.Microsecond), pct(0.99).Round(time.Microsecond), latencies[total-1].Round(time.Microsecond))
	fmt.Printf("respuestas malas   %d de %d\n", bad, total)
	for problem, n := range failures {
		fmt.Printf("  %d x %s\n", n, problem)
	}

	ok := true
	if bad > 0 {
		fmt.Println("FAIL hubo respuestas incorrectas o con error")
		ok = false
	}
	if pct(0.95) > *maxP95 {
		fmt.Printf("FAIL el p95 (%s) supera el máximo aceptado (%s)\n", pct(0.95), *maxP95)
		ok = false
	}
	if !ok {
		os.Exit(1)
	}
	fmt.Println("PASS respuesta estable con", *orders, "pedidos")
}

func seed(client *http.Client, kitchen, serviceToken string, n int) error {
	ns := uuid.MustParse("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
	customer := uuid.NewSHA1(ns, []byte("load-customer")).String()
	for i := 0; i < n; i++ {
		body, _ := json.Marshal(map[string]any{
			"order_id":    uuid.NewSHA1(ns, []byte(fmt.Sprintf("load-order-%d", i))).String(),
			"customer_id": customer,
			"order_type":  "para_llevar",
			"paid_at":     time.Now().UTC().Format(time.RFC3339),
			"items": []map[string]any{{
				"product_id": uuid.NewSHA1(ns, []byte("load-product")).String(), "name": "Pan", "quantity": 1, "prep_minutes": 1 + i%20,
			}},
		})
		req, _ := http.NewRequest("POST", kitchen+"/queue/orders", bytes.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("X-Service-Token", serviceToken)
		res, err := client.Do(req)
		if err != nil {
			return err
		}
		raw, _ := io.ReadAll(res.Body)
		res.Body.Close()
		if res.StatusCode != 201 && res.StatusCode != 200 {
			return fmt.Errorf("pedido %d: %d %s", i, res.StatusCode, bytes.TrimSpace(raw))
		}
	}
	return nil
}

func query(client *http.Client, url string, headers map[string]string, want int) (time.Duration, string) {
	req, _ := http.NewRequest("GET", url, nil)
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	t0 := time.Now()
	res, err := client.Do(req)
	if err != nil {
		return time.Since(t0), "error de red: " + err.Error()
	}
	raw, _ := io.ReadAll(res.Body)
	res.Body.Close()
	d := time.Since(t0)
	if res.StatusCode != 200 {
		return d, fmt.Sprintf("HTTP %d", res.StatusCode)
	}

	var q queueResp
	if err := json.Unmarshal(raw, &q); err != nil {
		return d, "JSON inválido"
	}
	if len(q.Items) < want {
		return d, fmt.Sprintf("la cola trae %d pedidos, se esperaban al menos %d", len(q.Items), want)
	}
	for i, it := range q.Items {
		if it.Position != i+1 {
			return d, "posiciones no consecutivas"
		}
		if i > 0 {
			prev := q.Items[i-1]
			if prev.Status == "pendiente" && it.Status == "pendiente" {
				if prev.PriorityScore > it.PriorityScore+0.011 {
					return d, "pendientes fuera de orden de priority_score"
				}
				if prev.PriorityScore == it.PriorityScore && prev.EnqueuedAt > it.EnqueuedAt {
					return d, "empate sin desempate FIFO"
				}
			}
		}
	}
	return d, ""
}
