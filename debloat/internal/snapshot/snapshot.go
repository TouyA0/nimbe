// Package snapshot enregistre l'état d'un appareil dans sauvegardes/<numéro de série>/.
package snapshot

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"time"

	"github.com/TouyA0/nimbe/debloat/internal/device"
)

// FormatVersion change si la structure du fichier change, pour que restore sache le relire.
const FormatVersion = 1

// Snapshot est l'instantané utilisé plus tard par restore pour tout remettre comme avant.
type Snapshot struct {
	FormatVersion int              `json:"format_version"`
	CreatedAt     time.Time        `json:"created_at"`
	Device        device.Info      `json:"device"`
	Packages      []device.Package `json:"packages"`
}

// DeviceDir renvoie le dossier propre à un appareil, en neutralisant les caractères
// du numéro de série qui poseraient problème dans un chemin.
func DeviceDir(root, serial string) string {
	safe := regexp.MustCompile(`[^A-Za-z0-9._-]`).ReplaceAllString(serial, "_")
	return filepath.Join(root, safe)
}

// Stamp formate une date pour un nom de fichier triable.
func Stamp(t time.Time) string {
	return t.Format("20060102-150405")
}

// Write enregistre l'instantané sans jamais écraser un fichier existant, et renvoie son chemin.
func Write(root string, s Snapshot) (string, error) {
	dir := DeviceDir(root, s.Device.Serial)
	if err := os.MkdirAll(dir, 0o750); err != nil {
		return "", err
	}
	path := filepath.Join(dir, fmt.Sprintf("backup-%s.json", Stamp(s.CreatedAt)))
	return path, WriteJSON(path, s)
}

// WriteJSON écrit v en JSON indenté ; échoue si le fichier existe déjà.
func WriteJSON(path string, v any) error {
	data, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return err
	}
	f, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0o640)
	if err != nil {
		return err
	}
	if _, err := f.Write(append(data, '\n')); err != nil {
		f.Close()
		return err
	}
	return f.Close()
}
