package device

import (
	"bufio"
	"context"
	"sort"
	"strings"

	"github.com/TouyA0/nimbe/debloat/internal/adb"
)

// Package décrit l'état d'un paquet pour l'utilisateur 0.
// Installed est faux pour un paquet système retiré de l'utilisateur (récupérable par install-existing).
type Package struct {
	Name      string `json:"name"`
	System    bool   `json:"system"`
	Enabled   bool   `json:"enabled"`
	Installed bool   `json:"installed"`
}

// ListPackages croise les listes de pm pour obtenir l'état de chaque paquet, trié par nom.
func ListPackages(ctx context.Context, r adb.Runner) ([]Package, error) {
	lists := map[string]map[string]bool{}
	for _, flag := range []string{"-u", "", "-s", "-d"} {
		args := []string{"pm", "list", "packages"}
		if flag != "" {
			args = append(args, flag)
		}
		out, err := adb.Shell(ctx, r, args...)
		if err != nil {
			return nil, err
		}
		lists[flag] = parsePackageList(out)
	}

	pkgs := make([]Package, 0, len(lists["-u"]))
	for name := range lists["-u"] {
		pkgs = append(pkgs, Package{
			Name:      name,
			System:    lists["-s"][name],
			Enabled:   !lists["-d"][name],
			Installed: lists[""][name],
		})
	}
	sort.Slice(pkgs, func(i, j int) bool { return pkgs[i].Name < pkgs[j].Name })
	return pkgs, nil
}

// parsePackageList lit les lignes « package:nom ».
func parsePackageList(out string) map[string]bool {
	names := make(map[string]bool)
	sc := bufio.NewScanner(strings.NewReader(out))
	for sc.Scan() {
		if name, ok := strings.CutPrefix(strings.TrimSpace(sc.Text()), "package:"); ok && name != "" {
			names[name] = true
		}
	}
	return names
}

// Summary compte les paquets par catégorie pour l'affichage.
type Summary struct {
	Total, System, ThirdParty, Disabled, Removed int
}

func Summarize(pkgs []Package) Summary {
	var s Summary
	for _, p := range pkgs {
		s.Total++
		if p.System {
			s.System++
		} else {
			s.ThirdParty++
		}
		if !p.Enabled {
			s.Disabled++
		}
		if !p.Installed {
			s.Removed++
		}
	}
	return s
}
