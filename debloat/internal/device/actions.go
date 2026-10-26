package device

import (
	"bufio"
	"context"
	"fmt"
	"strings"

	"github.com/TouyA0/nimbe/debloat/internal/adb"
)

// Les seules commandes qui modifient l'appareil. Toutes sont réversibles.

// Disable désactive un paquet pour l'utilisateur 0, sans le désinstaller.
func Disable(ctx context.Context, r adb.Runner, pkg string) error {
	return pmExpect(ctx, r, "new state: disabled-user", "pm", "disable-user", "--user", "0", pkg)
}

// Enable réactive un paquet.
func Enable(ctx context.Context, r adb.Runner, pkg string) error {
	return pmExpect(ctx, r, "new state: enabled", "pm", "enable", pkg)
}

// InstallExisting remet un paquet système qui avait été retiré pour l'utilisateur 0.
func InstallExisting(ctx context.Context, r adb.Runner, pkg string) error {
	return pmExpect(ctx, r, "installed for user", "cmd", "package", "install-existing", pkg)
}

// pmExpect lance la commande et vérifie que pm confirme le changement :
// certaines versions d'Android répondent avec un code de sortie 0 même en cas d'échec.
func pmExpect(ctx context.Context, r adb.Runner, want string, cmd ...string) error {
	out, err := adb.Shell(ctx, r, cmd...)
	if err != nil {
		return err
	}
	if !strings.Contains(out, want) {
		return fmt.Errorf("réponse inattendue de pm : %s", strings.TrimSpace(out))
	}
	return nil
}

// HomePackages liste les paquets de toutes les applis d'accueil installées.
func HomePackages(ctx context.Context, r adb.Runner) ([]string, error) {
	out, err := adb.Shell(ctx, r, "cmd", "package", "query-activities", "--brief",
		"-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
	if err != nil {
		return nil, err
	}
	var pkgs []string
	seen := map[string]bool{}
	sc := bufio.NewScanner(strings.NewReader(out))
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if strings.Contains(line, " ") {
			continue
		}
		if pkg, _, ok := strings.Cut(line, "/"); ok && pkg != "" && !seen[pkg] {
			seen[pkg] = true
			pkgs = append(pkgs, pkg)
		}
	}
	return pkgs, nil
}

// InputMethodPackage renvoie le paquet du clavier utilisé actuellement.
func InputMethodPackage(ctx context.Context, r adb.Runner) (string, error) {
	out, err := adb.Shell(ctx, r, "settings", "get", "secure", "default_input_method")
	if err != nil {
		return "", err
	}
	pkg, _, _ := strings.Cut(strings.TrimSpace(out), "/")
	if pkg == "null" {
		return "", nil
	}
	return pkg, nil
}
