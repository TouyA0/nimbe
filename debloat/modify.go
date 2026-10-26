package main

import (
	"bufio"
	"context"
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/TouyA0/nimbe/debloat/internal/adb"
	"github.com/TouyA0/nimbe/debloat/internal/device"
	"github.com/TouyA0/nimbe/debloat/internal/identity"
	"github.com/TouyA0/nimbe/debloat/internal/plan"
	"github.com/TouyA0/nimbe/debloat/internal/snapshot"
	"github.com/TouyA0/nimbe/debloat/listes"
)

// nimbePackage est notre launcher : il ne doit jamais se désactiver lui-même.
const nimbePackage = "io.github.touya0.nimbe"

// modifier regroupe ce dont ont besoin debloat, restore et check.
type modifier struct {
	r      adb.Runner
	w      io.Writer
	in     *prompter
	root   string
	target string
	dryRun bool
}

// prepare vérifie l'appareil et son identité, puis relève ses paquets.
func (m *modifier) prepare(ctx context.Context) (device.Info, []device.Package, error) {
	info, pkgs, err := collect(ctx, m.r)
	if err != nil {
		return info, nil, err
	}
	if err := identity.Check(m.root, m.target, info.Serial); err != nil {
		return info, nil, err
	}
	fmt.Fprintf(m.w, "Appareil : %s %s (%s), Android %s\n\n",
		info.Manufacturer, info.Model, info.Serial, info.AndroidVersion)
	return info, pkgs, nil
}

// protectedSet réunit la liste protégée et ce qui est détecté sur l'appareil.
// Si la détection échoue, on s'arrête : mieux vaut ne rien faire que désactiver l'accueil de secours.
func (m *modifier) protectedSet(ctx context.Context) (map[string]bool, error) {
	set := map[string]bool{nimbePackage: true}
	for _, e := range listes.Protected() {
		set[e.Name] = true
	}
	homes, err := device.HomePackages(ctx, m.r)
	if err != nil {
		return nil, fmt.Errorf("impossible de détecter les applis d'accueil : %w", err)
	}
	for _, p := range homes {
		set[p] = true
	}
	ime, err := device.InputMethodPackage(ctx, m.r)
	if err != nil {
		return nil, fmt.Errorf("impossible de détecter le clavier : %w", err)
	}
	if ime != "" {
		set[ime] = true
	}
	return set, nil
}

func (m *modifier) recommended(extra string) ([]listes.Entry, error) {
	entries := listes.Recommended()
	if extra == "" {
		return entries, nil
	}
	f, err := os.Open(extra)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	more, err := listes.Parse(f)
	if err != nil {
		return nil, fmt.Errorf("%s : %w", extra, err)
	}
	return append(entries, more...), nil
}

func (m *modifier) debloat(ctx context.Context, extraList string) error {
	info, pkgs, err := m.prepare(ctx)
	if err != nil {
		return err
	}
	protected, err := m.protectedSet(ctx)
	if err != nil {
		return err
	}
	recommended, err := m.recommended(extraList)
	if err != nil {
		return err
	}
	actions, skipped := plan.Debloat(pkgs, recommended, listes.Optional(), protected)

	for _, s := range skipped {
		if s.Reason != plan.ReasonAbsent {
			fmt.Fprintf(m.w, "  ignoré   %-45s %s\n", s.Package, s.Reason)
		}
	}
	if len(actions) == 0 {
		fmt.Fprintln(m.w, "Rien à désactiver. Les listes sont dans listes/recommandes.txt et listes/optionnels.txt.")
		return nil
	}

	printActions(m.w, actions)
	if m.dryRun {
		fmt.Fprintln(m.w, "\nSimulation : rien n'a été modifié.")
		return nil
	}

	// Toutes les questions avant de toucher à quoi que ce soit.
	var chosen []plan.Action
	var mandatory int
	for _, a := range actions {
		if !a.Ask {
			mandatory++
		}
	}
	if mandatory > 0 && !m.in.confirm(fmt.Sprintf("\nDésactiver %s ?", count(mandatory, "paquet recommandé", "paquets recommandés"))) {
		return fmt.Errorf("annulé : rien n'a été modifié")
	}
	for _, a := range actions {
		if !a.Ask || m.in.confirm(fmt.Sprintf("Désactiver aussi %s (optionnel) ?", a.Package)) {
			chosen = append(chosen, a)
		}
	}
	if len(chosen) == 0 {
		fmt.Fprintln(m.w, "Rien de choisi : rien n'a été modifié.")
		return nil
	}

	fmt.Fprintln(m.w)
	if _, err := writeBackup(m.w, m.root, info, pkgs); err != nil {
		return fmt.Errorf("instantané impossible, rien n'a été modifié : %w", err)
	}
	return m.apply(ctx, info.Serial, chosen)
}

func (m *modifier) restore(ctx context.Context, from string) error {
	info, pkgs, err := m.prepare(ctx)
	if err != nil {
		return err
	}
	if from == "" {
		if from, err = snapshot.Oldest(m.root, info.Serial); err != nil {
			return err
		}
	}
	origin, err := snapshot.Load(from)
	if err != nil {
		return err
	}
	if origin.Device.Serial != info.Serial {
		return fmt.Errorf("%s vient de l'appareil %s, pas de %s : rien n'a été modifié",
			from, origin.Device.Serial, info.Serial)
	}
	fmt.Fprintf(m.w, "Instantané de référence : %s\n", from)

	actions := plan.Restore(origin.Packages, pkgs)
	if len(actions) == 0 {
		fmt.Fprintln(m.w, "L'appareil est déjà dans l'état de cet instantané.")
		return nil
	}
	printActions(m.w, actions)
	if m.dryRun {
		fmt.Fprintln(m.w, "\nSimulation : rien n'a été modifié.")
		return nil
	}
	if !m.in.confirm(fmt.Sprintf("\nRemettre %s ?", count(len(actions), "paquet", "paquets"))) {
		return fmt.Errorf("annulé : rien n'a été modifié")
	}
	return m.apply(ctx, info.Serial, actions)
}

func (m *modifier) check(ctx context.Context, extraList string) error {
	_, pkgs, err := m.prepare(ctx)
	if err != nil {
		return err
	}
	protected, err := m.protectedSet(ctx)
	if err != nil {
		return err
	}
	recommended, err := m.recommended(extraList)
	if err != nil {
		return err
	}
	problems, notes := plan.Check(pkgs, protected, recommended)
	for _, n := range notes {
		fmt.Fprintln(m.w, "  à refaire  ", n, "(relance debloat)")
	}
	for _, p := range problems {
		fmt.Fprintln(m.w, "  PROBLÈME   ", p, "(relance restore)")
	}
	if len(problems) > 0 {
		return fmt.Errorf("%d paquet(s) indispensable(s) inactif(s)", len(problems))
	}
	fmt.Fprintln(m.w, "Tous les paquets protégés présents sont actifs.")
	return nil
}

// apply exécute les actions une à une, note chacune dans le journal, et continue après un échec.
func (m *modifier) apply(ctx context.Context, serial string, actions []plan.Action) error {
	failed := 0
	for _, a := range actions {
		var err error
		switch a.Op {
		case plan.OpDisable:
			err = device.Disable(ctx, m.r, a.Package)
		case plan.OpEnable:
			err = device.Enable(ctx, m.r, a.Package)
		case plan.OpInstallExisting:
			err = device.InstallExisting(ctx, m.r, a.Package)
		}
		status := "ok"
		if err != nil {
			failed++
			status = "échec : " + err.Error()
			fmt.Fprintf(m.w, "  ✗ %s %s : %v\n", a.Op, a.Package, err)
		} else {
			fmt.Fprintf(m.w, "  ✓ %s %s\n", a.Op, a.Package)
		}
		if jerr := snapshot.Journal(m.root, serial, fmt.Sprintf("%s %s %s", a.Op, a.Package, status)); jerr != nil {
			return fmt.Errorf("journal : %w", jerr)
		}
	}
	if failed > 0 {
		return fmt.Errorf("%d action(s) sur %d en échec, voir le journal", failed, len(actions))
	}
	fmt.Fprintf(m.w, "\nTerminé : %d action(s). Journal dans %s/journal.log\n", len(actions), snapshot.DeviceDir(m.root, serial))
	return nil
}

// count accorde un nom avec son nombre : « 1 paquet », « 3 paquets ».
func count(n int, singular, plural string) string {
	if n <= 1 {
		return fmt.Sprintf("%d %s", n, singular)
	}
	return fmt.Sprintf("%d %s", n, plural)
}

func printActions(w io.Writer, actions []plan.Action) {
	fmt.Fprintln(w, "Prévu :")
	for _, a := range actions {
		line := fmt.Sprintf("  %-10s %s", a.Op, a.Package)
		if a.Ask {
			line += " (optionnel)"
		}
		if a.Comment != "" {
			line += "  # " + a.Comment
		}
		fmt.Fprintln(w, line)
	}
}

// prompter pose les questions oui/non, ou répond oui à tout avec -yes.
type prompter struct {
	r   *bufio.Reader
	w   io.Writer
	yes bool
}

func newPrompter(in io.Reader, w io.Writer, yes bool) *prompter {
	return &prompter{r: bufio.NewReader(in), w: w, yes: yes}
}

// confirm renvoie vrai seulement pour une réponse explicite « o » ou « oui » ; non par défaut.
func (p *prompter) confirm(question string) bool {
	if p.yes {
		fmt.Fprintln(p.w, question, "oui (-yes)")
		return true
	}
	fmt.Fprint(p.w, question, " [o/N] ")
	line, _ := p.r.ReadString('\n')
	switch strings.ToLower(strings.TrimSpace(line)) {
	case "o", "oui", "y", "yes":
		return true
	}
	return false
}
