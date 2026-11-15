package main

import (
	"context"
	"log"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
)

// Email account settings, saved by the admin from Kanasta Admin
type Settings struct {
	APIKey    string `bson:"apiKey" json:"apiKey"`
	FromEmail string `bson:"fromEmail" json:"fromEmail"`
	FromName  string `bson:"fromName" json:"fromName"`
	Enabled   bool   `bson:"enabled" json:"enabled"`
}

// One row of the email history
type EmailLog struct {
	Type      string    `bson:"type" json:"type"`
	To        string    `bson:"to" json:"to"`
	Subject   string    `bson:"subject" json:"subject"`
	Status    string    `bson:"status" json:"status"` // SENT, FAILED or SKIPPED
	Detail    string    `bson:"detail" json:"detail"`
	CreatedAt time.Time `bson:"createdAt" json:"createdAt"`
}

var db *mongo.Database

func connectDatabase(url, name string) {
	client, err := mongo.Connect(context.Background(), options.Client().ApplyURI(url))
	if err != nil {
		log.Fatal("Could not connect to MongoDB: ", err)
	}
	db = client.Database(name)
	log.Println("Connected to MongoDB")
}

func loadSettings() (Settings, error) {
	s := Settings{FromEmail: "onboarding@resend.dev", FromName: "Kanasta"}
	err := db.Collection("settings").FindOne(context.Background(), bson.M{"_id": "email"}).Decode(&s)
	if err == mongo.ErrNoDocuments {
		return s, nil
	}
	return s, err
}

// An empty API key means "keep the one that is already saved"
func saveSettings(s Settings) (Settings, error) {
	if s.APIKey == "" {
		current, _ := loadSettings()
		s.APIKey = current.APIKey
	}
	_, err := db.Collection("settings").UpdateOne(context.Background(),
		bson.M{"_id": "email"}, bson.M{"$set": s}, options.Update().SetUpsert(true))
	return s, err
}

// What the admin app sees: the API key is never sent back complete
func (s Settings) public() map[string]any {
	hint := ""
	if len(s.APIKey) > 8 {
		hint = s.APIKey[:3] + "..." + s.APIKey[len(s.APIKey)-4:]
	}
	return map[string]any{
		"fromEmail": s.FromEmail, "fromName": s.FromName, "enabled": s.Enabled,
		"apiKeySet": s.APIKey != "", "apiKeyHint": hint,
	}
}

func saveEmailLog(entry EmailLog) {
	if _, err := db.Collection("emails").InsertOne(context.Background(), entry); err != nil {
		log.Println("Could not save email log:", err)
	}
}

func lastEmails(limit int64) ([]EmailLog, error) {
	opts := options.Find().SetSort(bson.M{"createdAt": -1}).SetLimit(limit)
	cursor, err := db.Collection("emails").Find(context.Background(), bson.M{}, opts)
	if err != nil {
		return nil, err
	}
	emails := []EmailLog{}
	err = cursor.All(context.Background(), &emails)
	return emails, err
}
