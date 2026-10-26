// Package device relève l'identité, le matériel et les paquets d'un appareil Android, en lecture seule.
package device

import (
	"bufio"
	"context"
	"fmt"
	"regexp"
	"strconv"
	"strings"

	"github.com/TouyA0/nimbe/debloat/internal/adb"
)

// Info décrit l'appareil. Les champs vides ou nuls n'ont pas pu être relevés (voir Warnings).
type Info struct {
	Serial         string   `json:"serial"`
	Manufacturer   string   `json:"manufacturer"`
	Model          string   `json:"model"`
	Device         string   `json:"device"`
	AndroidVersion string   `json:"android_version"`
	SDK            int      `json:"sdk"`
	Fingerprint    string   `json:"fingerprint"`
	ABI            string   `json:"abi"`
	Platform       string   `json:"platform"`
	CPUCores       int      `json:"cpu_cores"`
	MemTotalKB     int64    `json:"mem_total_kb"`
	MemAvailableKB int64    `json:"mem_available_kb"`
	DataTotalKB    int64    `json:"data_total_kb"`
	DataFreeKB     int64    `json:"data_free_kb"`
	Interface      string   `json:"interface"`
	IP             string   `json:"ip"`
	HomeActivity   string   `json:"home_activity"`
	Warnings       []string `json:"warnings,omitempty"`
}

// Collect relève les informations de l'appareil. Seul getprop est indispensable :
// les autres relevés en échec sont notés dans Warnings sans tout arrêter.
func Collect(ctx context.Context, r adb.Runner) (Info, error) {
	var info Info

	out, err := adb.Shell(ctx, r, "getprop")
	if err != nil {
		return info, err
	}
	props := parseGetprop(out)
	info.Serial = props["ro.serialno"]
	info.Manufacturer = props["ro.product.manufacturer"]
	info.Model = props["ro.product.model"]
	info.Device = props["ro.product.device"]
	info.AndroidVersion = props["ro.build.version.release"]
	info.SDK, _ = strconv.Atoi(props["ro.build.version.sdk"])
	info.Fingerprint = props["ro.build.fingerprint"]
	info.ABI = props["ro.product.cpu.abi"]
	info.Platform = firstNonEmpty(props["ro.board.platform"], props["ro.hardware"])

	if info.Serial == "" {
		if out, err := r.Run(ctx, "get-serialno"); err == nil {
			info.Serial = strings.TrimSpace(string(out))
		}
	}
	if info.Serial == "" || info.Serial == "unknown" {
		return info, fmt.Errorf("numéro de série introuvable : impossible d'identifier l'appareil")
	}

	warn := func(what string, err error) {
		info.Warnings = append(info.Warnings, fmt.Sprintf("%s : %v", what, err))
	}

	if out, err := adb.Shell(ctx, r, "cat", "/proc/meminfo"); err != nil {
		warn("mémoire", err)
	} else {
		info.MemTotalKB, info.MemAvailableKB = parseMeminfo(out)
	}

	if out, err := adb.Shell(ctx, r, "cat", "/sys/devices/system/cpu/present"); err != nil {
		warn("processeur", err)
	} else {
		info.CPUCores = parseCPURange(out)
	}

	if out, err := adb.Shell(ctx, r, "df", "-k", "/data"); err != nil {
		warn("stockage", err)
	} else if info.DataTotalKB, info.DataFreeKB, err = parseDf(out); err != nil {
		warn("stockage", err)
	}

	if out, err := adb.Shell(ctx, r, "ip", "-o", "-4", "addr", "show"); err != nil {
		warn("réseau", err)
	} else {
		info.Interface, info.IP = parseIP(out)
	}

	if out, err := adb.Shell(ctx, r, "cmd", "package", "resolve-activity", "--brief",
		"-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"); err != nil {
		warn("accueil par défaut", err)
	} else {
		info.HomeActivity = parseResolveActivity(out)
	}

	return info, nil
}

// parseGetprop lit les lignes « [clé]: [valeur] ».
func parseGetprop(out string) map[string]string {
	props := make(map[string]string)
	re := regexp.MustCompile(`^\[([^\]]+)\]: \[(.*)\]$`)
	sc := bufio.NewScanner(strings.NewReader(out))
	for sc.Scan() {
		if m := re.FindStringSubmatch(strings.TrimSpace(sc.Text())); m != nil {
			props[m[1]] = m[2]
		}
	}
	return props
}

func parseMeminfo(out string) (total, available int64) {
	sc := bufio.NewScanner(strings.NewReader(out))
	for sc.Scan() {
		fields := strings.Fields(sc.Text())
		if len(fields) < 2 {
			continue
		}
		v, err := strconv.ParseInt(fields[1], 10, 64)
		if err != nil {
			continue
		}
		switch fields[0] {
		case "MemTotal:":
			total = v
		case "MemAvailable:":
			available = v
		}
	}
	return total, available
}

// parseCPURange compte les cœurs à partir d'une liste comme « 0-3 » ou « 0-3,6 ».
func parseCPURange(out string) int {
	count := 0
	for _, part := range strings.Split(strings.TrimSpace(out), ",") {
		lo, hi, isRange := strings.Cut(part, "-")
		a, err := strconv.Atoi(lo)
		if err != nil {
			continue
		}
		if !isRange {
			count++
			continue
		}
		if b, err := strconv.Atoi(hi); err == nil && b >= a {
			count += b - a + 1
		}
	}
	return count
}

// parseDf lit la taille et l'espace libre (en Ko) sur la deuxième ligne de « df -k ».
func parseDf(out string) (total, free int64, err error) {
	lines := strings.Split(strings.TrimSpace(out), "\n")
	if len(lines) < 2 {
		return 0, 0, fmt.Errorf("sortie de df inattendue")
	}
	fields := strings.Fields(lines[len(lines)-1])
	if len(fields) < 4 {
		return 0, 0, fmt.Errorf("sortie de df inattendue")
	}
	if total, err = strconv.ParseInt(fields[1], 10, 64); err != nil {
		return 0, 0, err
	}
	if free, err = strconv.ParseInt(fields[3], 10, 64); err != nil {
		return 0, 0, err
	}
	return total, free, nil
}

// parseIP renvoie la première adresse IPv4 hors boucle locale, en préférant le Wi-Fi et l'Ethernet.
func parseIP(out string) (iface, ip string) {
	re := regexp.MustCompile(`^\d+:\s+(\S+)\s+inet\s+([\d.]+)/`)
	var fallbackIface, fallbackIP string
	sc := bufio.NewScanner(strings.NewReader(out))
	for sc.Scan() {
		m := re.FindStringSubmatch(sc.Text())
		if m == nil || m[1] == "lo" {
			continue
		}
		if strings.HasPrefix(m[1], "wlan") || strings.HasPrefix(m[1], "eth") {
			return m[1], m[2]
		}
		if fallbackIP == "" {
			fallbackIface, fallbackIP = m[1], m[2]
		}
	}
	return fallbackIface, fallbackIP
}

// parseResolveActivity garde la dernière ligne qui désigne une activité (« paquet/.Activité »).
func parseResolveActivity(out string) string {
	lines := strings.Split(strings.TrimSpace(out), "\n")
	for i := len(lines) - 1; i >= 0; i-- {
		if l := strings.TrimSpace(lines[i]); strings.Contains(l, "/") && !strings.Contains(l, " ") {
			return l
		}
	}
	return ""
}

func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if v != "" {
			return v
		}
	}
	return ""
}
