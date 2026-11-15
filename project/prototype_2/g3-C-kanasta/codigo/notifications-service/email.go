package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"html/template"
	"io"
	"log"
	"net/http"
	"strings"
	"time"
)

type OrderItem struct {
	Name     string  `json:"name"`
	Quantity float64 `json:"quantity"`
	Unit     string  `json:"unit"`
	Price    int     `json:"price"`
	Subtotal int     `json:"subtotal"`
}

type OrderData struct {
	OrderID   string      `json:"orderId"`
	Items     []OrderItem `json:"items"`
	Total     int         `json:"total"`
	Address   string      `json:"address"`
	Phone     string      `json:"phone"`
	Notes     string      `json:"notes"`
	Estimated bool        `json:"estimated"`
}

type Store struct {
	Name    string `json:"name"`
	Address string `json:"address"`
	Phone   string `json:"phone"`
}

// Store name and contact data, asked to the Catalog Service (REST)
func loadStore() Store {
	store := Store{Name: "Kanasta"}
	response, err := http.Get(env("CATALOG_URL", "http://localhost:8001") + "/api/catalog/store")
	if err == nil {
		defer response.Body.Close()
		json.NewDecoder(response.Body).Decode(&store)
	}
	return store
}

// 12500 -> "$ 12.500"
func formatPrice(value int) string {
	text := fmt.Sprintf("%d", value)
	var parts []string
	for len(text) > 3 {
		parts = append([]string{text[len(text)-3:]}, parts...)
		text = text[:len(text)-3]
	}
	return "$ " + strings.Join(append([]string{text}, parts...), ".")
}

// 1.5 kg -> "1,5 kg", 2 unidad -> "2"
func formatQuantity(quantity float64, unit string) string {
	text := strings.Replace(strings.TrimRight(strings.TrimRight(fmt.Sprintf("%.2f", quantity), "0"), "."), ".", ",", 1)
	switch unit {
	case "kg":
		return text + " kg"
	case "litro":
		return text + " L"
	}
	return text
}

var page = template.Must(template.New("email").Funcs(template.FuncMap{
	"price": formatPrice, "quantity": formatQuantity,
}).Parse(`
<div style="font-family: Arial, sans-serif; max-width: 560px; margin: auto; color: #1f2a25;">
  <div style="background: #1f8a5b; color: white; padding: 18px 22px; border-radius: 10px 10px 0 0;">
    <h2 style="margin: 0;">{{.Store.Name}}</h2>
  </div>
  <div style="border: 1px solid #dfe6e2; border-top: 0; padding: 22px; border-radius: 0 0 10px 10px;">
    <p>Hola, {{.Name}}:</p>
    {{if eq .Type "ACCOUNT_CREATED"}}
      <p>¡Bienvenido! Tu cuenta en <b>{{.Store.Name}}</b> quedó creada con el correo <b>{{.To}}</b>.</p>
      <p>Ya puedes hacer pedidos y seguir su estado desde la tienda.</p>
    {{else if eq .Type "PASSWORD_CHANGED"}}
      <p>Te contamos que la contraseña de tu cuenta se cambió hace un momento.</p>
      <p>Si no fuiste tú, comunícate con la tienda lo antes posible.</p>
    {{else if eq .Type "ORDER_PLACED"}}
      <p>Recibimos tu pedido <b>#{{.Data.OrderID}}</b>. Este es tu recibo:</p>
      <table style="width: 100%; border-collapse: collapse; font-size: 14px;">
        <tr style="background: #eef4f1;"><th align="left" style="padding: 6px;">Producto</th><th align="right" style="padding: 6px;">Cantidad</th><th align="right" style="padding: 6px;">Valor</th></tr>
        {{range .Data.Items}}
        <tr><td style="padding: 6px; border-bottom: 1px solid #dfe6e2;">{{.Name}}</td>
            <td align="right" style="padding: 6px; border-bottom: 1px solid #dfe6e2;">{{quantity .Quantity .Unit}}</td>
            <td align="right" style="padding: 6px; border-bottom: 1px solid #dfe6e2;">{{price .Subtotal}}</td></tr>
        {{end}}
        <tr><td colspan="2" align="right" style="padding: 8px;"><b>Total</b></td><td align="right" style="padding: 8px;"><b>{{price .Data.Total}}</b></td></tr>
      </table>
      {{if .Data.Estimated}}
      <p style="background: #fff4d6; padding: 10px; border-radius: 8px; font-size: 13px;">
        Tu pedido tiene productos que se venden por peso o por volumen. El valor final puede cambiar un poco,
        porque no siempre es posible pesar o medir la cantidad exacta que pediste.</p>
      {{end}}
      <p><b>Entrega en:</b> {{.Data.Address}}<br><b>Teléfono:</b> {{.Data.Phone}}{{if .Data.Notes}}<br><b>Notas:</b> {{.Data.Notes}}{{end}}</p>
      <p>Pagas al recibir tu pedido.</p>
    {{else}}
      <p>Este es un correo de prueba. Si lo recibes, el correo de la tienda quedó bien configurado.</p>
    {{end}}
    <p style="color: #64746d; font-size: 13px;">{{.Store.Name}} · {{.Store.Address}} · {{.Store.Phone}}</p>
  </div>
</div>`))

var subjects = map[string]string{
	"ACCOUNT_CREATED":  "Bienvenido a %s",
	"PASSWORD_CHANGED": "Cambiaste tu contraseña en %s",
	"ORDER_PLACED":     "Recibo de tu pedido en %s",
	"TEST":             "Correo de prueba de %s",
}

// Builds the email for one event, sends it and saves the result in the history
func process(event Event) EmailLog {
	store := loadStore()
	format, ok := subjects[event.Type]
	if !ok {
		format = "Mensaje de %s"
	}
	entry := EmailLog{Type: event.Type, To: event.To, Subject: fmt.Sprintf(format, store.Name), CreatedAt: time.Now()}

	var html bytes.Buffer
	page.Execute(&html, map[string]any{"Type": event.Type, "To": event.To, "Name": event.Name, "Data": event.Data, "Store": store})

	settings, _ := loadSettings()
	switch {
	case !settings.Enabled || settings.APIKey == "":
		entry.Status, entry.Detail = "SKIPPED", "El envío de correos no está configurado"
	default:
		if err := sendWithResend(settings, entry.To, entry.Subject, html.String()); err != nil {
			entry.Status, entry.Detail = "FAILED", err.Error()
		} else {
			entry.Status = "SENT"
		}
	}

	log.Printf("Email %s to %s: %s %s", entry.Type, entry.To, entry.Status, entry.Detail)
	saveEmailLog(entry)
	return entry
}

// Resend API: https://resend.com/docs/api-reference/emails/send-email
func sendWithResend(settings Settings, to, subject, html string) error {
	body, _ := json.Marshal(map[string]any{
		"from":    fmt.Sprintf("%s <%s>", settings.FromName, settings.FromEmail),
		"to":      []string{to},
		"subject": subject,
		"html":    html,
	})
	request, _ := http.NewRequest("POST", "https://api.resend.com/emails", bytes.NewReader(body))
	request.Header.Set("Authorization", "Bearer "+settings.APIKey)
	request.Header.Set("Content-Type", "application/json")

	response, err := http.DefaultClient.Do(request)
	if err != nil {
		return fmt.Errorf("no fue posible conectar con Resend: %v", err)
	}
	defer response.Body.Close()
	if response.StatusCode >= 300 {
		answer, _ := io.ReadAll(response.Body)
		var resendError struct {
			Message string `json:"message"`
		}
		if json.Unmarshal(answer, &resendError) != nil || resendError.Message == "" {
			resendError.Message = strings.TrimSpace(string(answer))
		}
		return fmt.Errorf("Resend respondió %d: %s", response.StatusCode, resendError.Message)
	}
	return nil
}
