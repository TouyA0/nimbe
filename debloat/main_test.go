package main

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"testing"
)

// fakeTV simule un appareil : les commandes pm modifient vraiment son état.
type fakeTV struct {
	serial   string
	disabled map[string]bool
	packages []string
	calls    []string
}

func newFakeTV() *fakeTV {
	return &fakeTV{
		serial:   "STICK1",
		disabled: map[string]bool{},
		packages: []string{"com.ads", "com.android.vending", "com.google.android.tvlauncher", "com.cast", "com.ime"},
	}
}

func (f *fakeTV) Run(_ context.Context, args ...string) ([]byte, error) {
	cmd := strings.Join(args, " ")
	f.calls = append(f.calls, cmd)
	list := func(keep func(string) bool) []byte {
		var b strings.Builder
		names := append([]string(nil), f.packages...)
		sort.Strings(names)
		for _, p := range names {
			if keep(p) {
				fmt.Fprintf(&b, "package:%s\n", p)
			}
		}
		return []byte(b.String())
	}
	switch {
	case cmd == "get-state":
		return []byte("device\n"), nil
	case cmd == "shell getprop":
		return []byte(fmt.Sprintf("[ro.serialno]: [%s]\n[ro.product.model]: [TV]\n", f.serial)), nil
	case cmd == "shell pm list packages -u", cmd == "shell pm list packages", cmd == "shell pm list packages -s":
		return list(func(string) bool { return true }), nil
	case cmd == "shell pm list packages -d":
		return list(func(p string) bool { return f.disabled[p] }), nil
	case strings.HasPrefix(cmd, "shell cmd package query-activities"):
		return []byte("com.google.android.tvlauncher/.MainActivity\n"), nil
	case cmd == "shell settings get secure default_input_method":
		return []byte("com.ime/.Ime\n"), nil
	case strings.HasPrefix(cmd, "shell pm disable-user --user 0 "):
		p := strings.TrimPrefix(cmd, "shell pm disable-user --user 0 ")
		f.disabled[p] = true
		return []byte("Package " + p + " new state: disabled-user\n"), nil
	case strings.HasPrefix(cmd, "shell pm enable "):
		p := strings.TrimPrefix(cmd, "shell pm enable ")
		delete(f.disabled, p)
		return []byte("Package " + p + " new state: enabled\n"), nil
	case strings.HasPrefix(cmd, "shell "):
		return nil, fmt.Errorf("non simulé : %s", cmd)
	}
	return nil, fmt.Errorf("commande inattendue : %s", cmd)
}

func newModifier(tv *fakeTV, root, answers string, dryRun bool) (*modifier, *bytes.Buffer) {
	var out bytes.Buffer
	return &modifier{
		r: tv, w: &out, in: newPrompter(strings.NewReader(answers), &out, false),
		root: root, target: "192.168.1.57:5555", dryRun: dryRun,
	}, &out
}

func writeList(t *testing.T, dir, content string) string {
	t.Helper()
	path := filepath.Join(dir, "liste.txt")
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}

func TestDebloatThenRestore(t *testing.T) {
	tv := newFakeTV()
	root := t.TempDir()
	// Une liste qui contient, par erreur, des paquets protégés : ils doivent être ignorés.
	list := writeList(t, t.TempDir(), "com.ads\ncom.android.vending\ncom.google.android.tvlauncher\ncom.ime\n")

	m, out := newModifier(tv, root, "o\n", false)
	if err := m.debloat(context.Background(), list); err != nil {
		t.Fatalf("debloat : %v\n%s", err, out)
	}
	if !tv.disabled["com.ads"] {
		t.Error("com.ads aurait dû être désactivé")
	}
	for _, p := range []string{"com.android.vending", "com.google.android.tvlauncher", "com.ime"} {
		if tv.disabled[p] {
			t.Errorf("paquet protégé désactivé : %s", p)
		}
	}
	if backups, _ := filepath.Glob(filepath.Join(root, "STICK1", "backup-*.json")); len(backups) != 1 {
		t.Errorf("attendu un instantané avant modification, trouvé %v", backups)
	}
	if journal, err := os.ReadFile(filepath.Join(root, "STICK1", "journal.log")); err != nil ||
		!strings.Contains(string(journal), "désactiver com.ads ok") {
		t.Errorf("journal : %q, %v", journal, err)
	}

	m, out = newModifier(tv, root, "o\n", false)
	if err := m.restore(context.Background(), ""); err != nil {
		t.Fatalf("restore : %v\n%s", err, out)
	}
	if len(tv.disabled) != 0 {
		t.Errorf("après restore, encore désactivés : %v", tv.disabled)
	}
}

func TestDebloatDryRunAndRefusalChangeNothing(t *testing.T) {
	list := writeList(t, t.TempDir(), "com.ads\n")
	for name, tc := range map[string]struct {
		answers string
		dryRun  bool
	}{
		"simulation": {"", true},
		"refus":      {"n\n", false},
		"entrée":     {"\n", false},
	} {
		t.Run(name, func(t *testing.T) {
			tv := newFakeTV()
			root := t.TempDir()
			m, out := newModifier(tv, root, tc.answers, tc.dryRun)
			_ = m.debloat(context.Background(), list)
			for _, c := range tv.calls {
				if strings.Contains(c, "disable-user") {
					t.Errorf("modification malgré %s : %s\n%s", name, c, out)
				}
			}
			if backups, _ := filepath.Glob(filepath.Join(root, "STICK1", "backup-*.json")); len(backups) != 0 {
				t.Errorf("instantané écrit malgré %s", name)
			}
		})
	}
}

func TestIdentityLockBlocksOtherDevice(t *testing.T) {
	root := t.TempDir()
	list := writeList(t, t.TempDir(), "com.ads\n")

	m, _ := newModifier(newFakeTV(), root, "", true)
	if err := m.debloat(context.Background(), list); err != nil {
		t.Fatal(err)
	}

	other := newFakeTV()
	other.serial = "AUTRE_TV"
	m, _ = newModifier(other, root, "o\n", false)
	if err := m.debloat(context.Background(), list); err == nil {
		t.Fatal("un autre appareil à la même adresse aurait dû être refusé")
	}
	if len(other.disabled) != 0 {
		t.Errorf("l'autre appareil a été modifié : %v", other.disabled)
	}
}

func TestCheckReportsDisabledProtected(t *testing.T) {
	tv := newFakeTV()
	tv.disabled["com.android.vending"] = true
	m, _ := newModifier(tv, t.TempDir(), "", false)
	if err := m.check(context.Background(), ""); err == nil {
		t.Fatal("check aurait dû signaler le Play Store désactivé")
	}
}
