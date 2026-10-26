package snapshot

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/TouyA0/nimbe/debloat/internal/device"
)

func TestDeviceDirSanitizesSerial(t *testing.T) {
	got := DeviceDir("sauvegardes", "192.168.1.57:5555/../x")
	if want := filepath.Join("sauvegardes", "192.168.1.57_5555_.._x"); got != want {
		t.Errorf("DeviceDir = %q, veut %q", got, want)
	}
}

func TestWriteRoundTripAndNeverOverwrites(t *testing.T) {
	root := t.TempDir()
	s := Snapshot{
		FormatVersion: FormatVersion,
		CreatedAt:     time.Date(2026, 10, 26, 16, 5, 1, 0, time.UTC),
		Device:        device.Info{Serial: "ABC123"},
		Packages:      []device.Package{{Name: "com.a", System: true, Enabled: true, Installed: true}},
	}

	path, err := Write(root, s)
	if err != nil {
		t.Fatal(err)
	}
	if want := filepath.Join(root, "ABC123", "backup-20261026-160501.json"); path != want {
		t.Errorf("chemin = %q, veut %q", path, want)
	}

	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	var back Snapshot
	if err := json.Unmarshal(data, &back); err != nil {
		t.Fatal(err)
	}
	if back.Device.Serial != "ABC123" || len(back.Packages) != 1 || !back.CreatedAt.Equal(s.CreatedAt) {
		t.Errorf("relu : %+v", back)
	}

	if _, err := Write(root, s); err == nil {
		t.Error("une deuxième écriture au même instant aurait dû échouer au lieu d'écraser")
	}
}
