// debloat allège un appareil Android TV via ADB, de façon réversible.
// Pour l'instant : diag et backup, qui ne modifient rien sur l'appareil.
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/TouyA0/nimbe/debloat/internal/adb"
	"github.com/TouyA0/nimbe/debloat/internal/device"
	"github.com/TouyA0/nimbe/debloat/internal/snapshot"
)

const usage = `Usage : debloat <commande> [options]

Commandes :
  diag     Relève modèle, Android, mémoire, stockage, réseau, accueil et paquets (ne modifie rien)
  backup   Enregistre l'état de tous les paquets pour pouvoir le restaurer (ne modifie rien)

Options :
  -s <série|ip:port>   Appareil à utiliser si plusieurs sont connectés
  -adb <chemin>        Binaire adb (par défaut : PATH, puis ~/Android/Sdk/platform-tools)
  -out <dossier>       Dossier des sauvegardes (par défaut : sauvegardes)
  -capture=false       (diag) Ne pas faire de capture d'écran
`

func main() {
	if err := run(os.Args[1:], os.Stdout); err != nil {
		fmt.Fprintln(os.Stderr, "erreur :", err)
		os.Exit(1)
	}
}

func run(args []string, w io.Writer) error {
	if len(args) == 0 || args[0] == "-h" || args[0] == "help" {
		fmt.Fprint(w, usage)
		return nil
	}
	cmd := args[0]

	fs := flag.NewFlagSet(cmd, flag.ContinueOnError)
	fs.Usage = func() { fmt.Fprint(fs.Output(), usage) }
	serial := fs.String("s", "", "")
	adbBin := fs.String("adb", "", "")
	out := fs.String("out", "sauvegardes", "")
	capture := fs.Bool("capture", true, "")
	if err := fs.Parse(args[1:]); err != nil {
		return err
	}

	if *adbBin == "" {
		bin, err := adb.FindBin()
		if err != nil {
			return err
		}
		*adbBin = bin
	}
	r := adb.Exec{Bin: *adbBin, Serial: *serial}

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()

	switch cmd {
	case "diag":
		return diag(ctx, r, w, *out, *capture)
	case "backup":
		return backup(ctx, r, w, *out)
	default:
		return fmt.Errorf("commande inconnue %q\n\n%s", cmd, usage)
	}
}

func collect(ctx context.Context, r adb.Runner) (device.Info, []device.Package, error) {
	if err := adb.CheckConnected(ctx, r); err != nil {
		return device.Info{}, nil, err
	}
	info, err := device.Collect(ctx, r)
	if err != nil {
		return info, nil, err
	}
	pkgs, err := device.ListPackages(ctx, r)
	return info, pkgs, err
}

func diag(ctx context.Context, r adb.Runner, w io.Writer, root string, capture bool) error {
	info, pkgs, err := collect(ctx, r)
	if err != nil {
		return err
	}
	now := time.Now()
	printReport(w, info, device.Summarize(pkgs))

	dir := filepath.Join(snapshot.DeviceDir(root, info.Serial), "diag-"+snapshot.Stamp(now))
	if err := os.MkdirAll(dir, 0o750); err != nil {
		return err
	}
	if err := snapshot.WriteJSON(filepath.Join(dir, "diag.json"), info); err != nil {
		return err
	}
	if err := writePackageList(filepath.Join(dir, "paquets.txt"), pkgs); err != nil {
		return err
	}
	if capture {
		// exec-out transmet le PNG brut, sans conversion des fins de ligne.
		png, err := r.Run(ctx, "exec-out", "screencap", "-p")
		if err != nil || len(png) == 0 {
			fmt.Fprintf(w, "\nCapture d'écran impossible : %v\n", err)
		} else if err := os.WriteFile(filepath.Join(dir, "capture.png"), png, 0o640); err != nil {
			return err
		}
	}
	fmt.Fprintf(w, "\nRésultats enregistrés dans %s\n", dir)
	return nil
}

func backup(ctx context.Context, r adb.Runner, w io.Writer, root string) error {
	info, pkgs, err := collect(ctx, r)
	if err != nil {
		return err
	}
	path, err := snapshot.Write(root, snapshot.Snapshot{
		FormatVersion: snapshot.FormatVersion,
		CreatedAt:     time.Now(),
		Device:        info,
		Packages:      pkgs,
	})
	if err != nil {
		return err
	}
	s := device.Summarize(pkgs)
	fmt.Fprintf(w, "Instantané de %s %s (%s) : %d paquets, dont %d désactivés.\n",
		info.Manufacturer, info.Model, info.Serial, s.Total, s.Disabled)
	fmt.Fprintf(w, "Enregistré dans %s\n", path)
	return nil
}

func printReport(w io.Writer, info device.Info, s device.Summary) {
	row := func(label, value string) {
		if value == "" {
			value = "?"
		}
		fmt.Fprintf(w, "  %-18s %s\n", label, value)
	}
	fmt.Fprintln(w, "Appareil")
	row("Modèle", strings.TrimSpace(info.Manufacturer+" "+info.Model))
	row("Nom de code", info.Device)
	row("Numéro de série", info.Serial)
	row("Android", fmt.Sprintf("%s (API %d)", info.AndroidVersion, info.SDK))
	row("Processeur", fmt.Sprintf("%s, %d cœurs, %s", info.Platform, info.CPUCores, info.ABI))
	row("Mémoire", fmt.Sprintf("%s libres sur %s", formatKB(info.MemAvailableKB), formatKB(info.MemTotalKB)))
	row("Stockage /data", fmt.Sprintf("%s libres sur %s", formatKB(info.DataFreeKB), formatKB(info.DataTotalKB)))
	row("Réseau", strings.TrimSpace(info.IP+" ("+info.Interface+")"))
	row("Accueil actuel", info.HomeActivity)

	fmt.Fprintln(w, "\nPaquets")
	row("Total", fmt.Sprint(s.Total))
	row("Système", fmt.Sprint(s.System))
	row("Installés par toi", fmt.Sprint(s.ThirdParty))
	row("Désactivés", fmt.Sprint(s.Disabled))
	row("Retirés", fmt.Sprint(s.Removed))

	if len(info.Warnings) > 0 {
		fmt.Fprintln(w, "\nNon relevé")
		for _, msg := range info.Warnings {
			fmt.Fprintln(w, "  -", msg)
		}
	}
}

func writePackageList(path string, pkgs []device.Package) error {
	var b strings.Builder
	for _, p := range pkgs {
		var tags []string
		if p.System {
			tags = append(tags, "système")
		}
		if !p.Enabled {
			tags = append(tags, "désactivé")
		}
		if !p.Installed {
			tags = append(tags, "retiré")
		}
		fmt.Fprintf(&b, "%s\t%s\n", p.Name, strings.Join(tags, ","))
	}
	f, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0o640)
	if err != nil {
		return err
	}
	_, err = f.WriteString(b.String())
	return errors.Join(err, f.Close())
}

func formatKB(kb int64) string {
	if kb <= 0 {
		return "?"
	}
	if kb >= 1024*1024 {
		return strings.Replace(fmt.Sprintf("%.1f Go", float64(kb)/(1024*1024)), ".", ",", 1)
	}
	return fmt.Sprintf("%d Mo", kb/1024)
}
