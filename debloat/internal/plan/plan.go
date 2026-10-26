// Package plan décide quoi faire sur l'appareil, sans rien exécuter : ce qui se teste sans appareil.
package plan

import (
	"fmt"

	"github.com/TouyA0/nimbe/debloat/internal/device"
	"github.com/TouyA0/nimbe/debloat/listes"
)

type Op int

const (
	OpDisable Op = iota
	OpEnable
	OpInstallExisting
)

func (o Op) String() string {
	switch o {
	case OpDisable:
		return "désactiver"
	case OpEnable:
		return "réactiver"
	case OpInstallExisting:
		return "remettre"
	}
	return fmt.Sprintf("Op(%d)", int(o))
}

// Action est une modification prévue. Ask vaut vrai pour un paquet de la liste optionnelle.
type Action struct {
	Package string
	Op      Op
	Comment string
	Ask     bool
}

// Skip est un paquet listé qu'on ne touchera pas, avec la raison.
type Skip struct {
	Package string
	Reason  string
}

const (
	ReasonProtected = "protégé"
	ReasonAbsent    = "absent de l'appareil"
	ReasonDisabled  = "déjà désactivé"
)

// Debloat prévoit la désactivation des paquets listés présents et actifs.
// Un paquet protégé n'est jamais désactivé, quelle que soit la liste où il apparaît.
func Debloat(current []device.Package, recommended, optional []listes.Entry, protected map[string]bool) ([]Action, []Skip) {
	byName := index(current)
	var actions []Action
	var skipped []Skip
	seen := map[string]bool{}

	consider := func(e listes.Entry, ask bool) {
		if seen[e.Name] {
			return
		}
		seen[e.Name] = true
		p, ok := byName[e.Name]
		switch {
		case protected[e.Name]:
			skipped = append(skipped, Skip{e.Name, ReasonProtected})
		case !ok || !p.Installed:
			skipped = append(skipped, Skip{e.Name, ReasonAbsent})
		case !p.Enabled:
			skipped = append(skipped, Skip{e.Name, ReasonDisabled})
		default:
			actions = append(actions, Action{Package: e.Name, Op: OpDisable, Comment: e.Comment, Ask: ask})
		}
	}
	for _, e := range recommended {
		consider(e, false)
	}
	for _, e := range optional {
		consider(e, true)
	}
	return actions, skipped
}

// Restore prévoit de remettre chaque paquet actif dans l'instantané d'origine et qui ne l'est plus.
// Il ne désactive jamais rien.
func Restore(origin, current []device.Package) []Action {
	byName := index(current)
	var actions []Action
	for _, o := range origin {
		if !o.Installed || !o.Enabled {
			continue
		}
		c, ok := byName[o.Name]
		switch {
		case !ok:
			// Désinstallé complètement (mise à jour système ?) : rien à faire sans l'APK.
		case !c.Installed:
			actions = append(actions, Action{Package: o.Name, Op: OpInstallExisting})
		case !c.Enabled:
			actions = append(actions, Action{Package: o.Name, Op: OpEnable})
		}
	}
	return actions
}

// Check signale les paquets protégés désactivés ou retirés (problèmes), et les paquets recommandés
// de nouveau actifs, par exemple après une mise à jour du fabricant (remarques).
func Check(current []device.Package, protected map[string]bool, recommended []listes.Entry) (problems, notes []string) {
	byName := index(current)
	for _, p := range current {
		if !protected[p.Name] {
			continue
		}
		if !p.Installed {
			problems = append(problems, fmt.Sprintf("%s est protégé mais retiré", p.Name))
		} else if !p.Enabled {
			problems = append(problems, fmt.Sprintf("%s est protégé mais désactivé", p.Name))
		}
	}
	for _, e := range recommended {
		if p, ok := byName[e.Name]; ok && p.Installed && p.Enabled && !protected[e.Name] {
			notes = append(notes, fmt.Sprintf("%s est de nouveau actif", e.Name))
		}
	}
	return problems, notes
}

func index(pkgs []device.Package) map[string]device.Package {
	m := make(map[string]device.Package, len(pkgs))
	for _, p := range pkgs {
		m[p.Name] = p
	}
	return m
}
