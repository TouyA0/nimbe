// Package adb lance les commandes adb vers un appareil Android.
package adb

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

// Runner exécute adb avec des arguments donnés. Il est remplacé par un faux dans les tests.
type Runner interface {
	Run(ctx context.Context, args ...string) ([]byte, error)
}

// Exec appelle le vrai binaire adb, éventuellement sur un appareil précis (-s).
type Exec struct {
	Bin    string
	Serial string
}

func (e Exec) Run(ctx context.Context, args ...string) ([]byte, error) {
	full := make([]string, 0, len(args)+2)
	if e.Serial != "" {
		full = append(full, "-s", e.Serial)
	}
	full = append(full, args...)

	cmd := exec.CommandContext(ctx, e.Bin, full...)
	var stderr bytes.Buffer
	cmd.Stderr = &stderr
	out, err := cmd.Output()
	if err != nil {
		msg := strings.TrimSpace(stderr.String())
		return out, fmt.Errorf("adb %s : %w : %s", strings.Join(args, " "), err, msg)
	}
	return out, nil
}

// Shell lance une commande dans le shell de l'appareil et renvoie sa sortie texte.
func Shell(ctx context.Context, r Runner, cmd ...string) (string, error) {
	out, err := r.Run(ctx, append([]string{"shell"}, cmd...)...)
	return string(out), err
}

// FindBin cherche adb dans le PATH, puis dans le SDK installé par Android Studio.
func FindBin() (string, error) {
	if p, err := exec.LookPath("adb"); err == nil {
		return p, nil
	}
	if home, err := os.UserHomeDir(); err == nil {
		p := filepath.Join(home, "Android", "Sdk", "platform-tools", "adb")
		if _, err := os.Stat(p); err == nil {
			return p, nil
		}
	}
	return "", fmt.Errorf("adb introuvable : installe android-tools ou indique son chemin avec -adb")
}

// CheckConnected vérifie qu'un appareil répond, pour donner un message clair sinon.
func CheckConnected(ctx context.Context, r Runner) error {
	out, err := r.Run(ctx, "get-state")
	if err != nil {
		return fmt.Errorf("aucun appareil prêt (adb connect <ip>:5555 ? plusieurs appareils → -s) : %w", err)
	}
	if state := strings.TrimSpace(string(out)); state != "device" {
		return fmt.Errorf("appareil dans l'état %q au lieu de \"device\" (débogage autorisé sur la TV ?)", state)
	}
	return nil
}
