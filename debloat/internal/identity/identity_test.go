package identity

import "testing"

func TestCheck(t *testing.T) {
	root := t.TempDir()
	if err := Check(root, "192.168.1.57:5555", "STICK1"); err != nil {
		t.Fatalf("première fois : %v", err)
	}
	if err := Check(root, "192.168.1.57:5555", "STICK1"); err != nil {
		t.Fatalf("même appareil : %v", err)
	}
	if err := Check(root, "192.168.1.57:5555", "AUTRE"); err == nil {
		t.Fatal("un autre appareil à la même adresse aurait dû être refusé")
	}
	if err := Check(root, "192.168.1.58:5555", "AUTRE"); err != nil {
		t.Fatalf("autre adresse : %v", err)
	}
	if err := Check(root, "", "N_IMPORTE"); err != nil {
		t.Fatalf("sans adresse : %v", err)
	}
}
