package device

import (
	"context"
	"fmt"
	"strings"
	"testing"
)

// fakeRunner renvoie une sortie préparée pour chaque commande adb.
type fakeRunner map[string]string

func (f fakeRunner) Run(_ context.Context, args ...string) ([]byte, error) {
	key := strings.Join(args, " ")
	out, ok := f[key]
	if !ok {
		return nil, fmt.Errorf("commande inattendue : %s", key)
	}
	return []byte(out), nil
}

// Sorties relevées sur l'émulateur Android TV API 30.
const (
	getpropOut = `[ro.build.version.release]: [11]
[ro.build.version.sdk]: [30]
[ro.product.manufacturer]: [Google]
[ro.product.model]: [AOSP TV on x86]
[ro.product.device]: [generic_x86_arm]
[ro.product.cpu.abi]: [x86]
[ro.hardware]: [ranchu]
[ro.serialno]: [EMULATOR37X2X12X0]
[ro.empty]: []
`
	meminfoOut = `MemTotal:        2028192 kB
MemFree:          105492 kB
MemAvailable:    1141284 kB
`
	dfOut = `Filesystem     1K-blocks   Used Available Use% Mounted on
/dev/block/vdc   6094400 242140   5710048   5% /storage/emulated/0/Android/obb
`
	ipOut = `1: lo    inet 127.0.0.1/8 scope host lo\       valid_lft forever preferred_lft forever
16: radio0    inet 10.0.2.15/24 brd 10.0.2.255 scope global radio0\       valid_lft forever preferred_lft forever
14: wlan0    inet 10.0.2.16/24 brd 10.0.2.255 scope global wlan0\       valid_lft forever preferred_lft forever
`
	homeOut = `priority=2 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
com.google.android.tvlauncher/.MainActivity
`
)

func TestCollect(t *testing.T) {
	r := fakeRunner{
		"shell getprop":                             getpropOut,
		"shell cat /proc/meminfo":                   meminfoOut,
		"shell cat /sys/devices/system/cpu/present": "0-3\n",
		"shell df -k /data":                         dfOut,
		"shell ip -o -4 addr show":                  ipOut,
		"shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME": homeOut,
	}
	info, err := Collect(context.Background(), r)
	if err != nil {
		t.Fatal(err)
	}
	want := Info{
		Serial: "EMULATOR37X2X12X0", Manufacturer: "Google", Model: "AOSP TV on x86",
		Device: "generic_x86_arm", AndroidVersion: "11", SDK: 30, ABI: "x86", Platform: "ranchu",
		CPUCores: 4, MemTotalKB: 2028192, MemAvailableKB: 1141284,
		DataTotalKB: 6094400, DataFreeKB: 5710048,
		Interface: "wlan0", IP: "10.0.2.16",
		HomeActivity: "com.google.android.tvlauncher/.MainActivity",
	}
	if fmt.Sprintf("%+v", info) != fmt.Sprintf("%+v", want) {
		t.Errorf("Collect =\n%+v\nveut\n%+v", info, want)
	}
}

func TestCollectKeepsGoingOnOptionalFailures(t *testing.T) {
	// Seul getprop répond : le relevé aboutit quand même, avec des avertissements.
	info, err := Collect(context.Background(), fakeRunner{"shell getprop": getpropOut})
	if err != nil {
		t.Fatal(err)
	}
	if len(info.Warnings) != 5 {
		t.Errorf("attendu 5 avertissements, obtenu %d : %v", len(info.Warnings), info.Warnings)
	}
}

func TestCollectRequiresSerial(t *testing.T) {
	r := fakeRunner{"shell getprop": "[ro.product.model]: [X]\n", "get-serialno": "unknown\n"}
	if _, err := Collect(context.Background(), r); err == nil {
		t.Fatal("attendu une erreur sans numéro de série")
	}
}

func TestParseCPURange(t *testing.T) {
	for in, want := range map[string]int{"0-3\n": 4, "0": 1, "0-3,6": 5, "0-1,4-7": 6, "": 0} {
		if got := parseCPURange(in); got != want {
			t.Errorf("parseCPURange(%q) = %d, veut %d", in, got, want)
		}
	}
}

func TestParseIPFallsBackToOtherInterface(t *testing.T) {
	iface, ip := parseIP("1: lo    inet 127.0.0.1/8 scope host lo\n5: rmnet0    inet 10.1.2.3/24 scope global rmnet0\n")
	if iface != "rmnet0" || ip != "10.1.2.3" {
		t.Errorf("parseIP = %s %s", iface, ip)
	}
}

func TestParseResolveActivityWithoutDefault(t *testing.T) {
	if got := parseResolveActivity("No activity found\n"); got != "" {
		t.Errorf("parseResolveActivity = %q, veut vide", got)
	}
}

func TestListPackages(t *testing.T) {
	r := fakeRunner{
		"shell pm list packages -u": "package:com.a\npackage:com.removed\npackage:com.user\n",
		"shell pm list packages":    "package:com.a\npackage:com.user\n",
		"shell pm list packages -s": "package:com.a\npackage:com.removed\n",
		"shell pm list packages -d": "package:com.a\n",
	}
	pkgs, err := ListPackages(context.Background(), r)
	if err != nil {
		t.Fatal(err)
	}
	want := []Package{
		{Name: "com.a", System: true, Enabled: false, Installed: true},
		{Name: "com.removed", System: true, Enabled: true, Installed: false},
		{Name: "com.user", System: false, Enabled: true, Installed: true},
	}
	if fmt.Sprint(pkgs) != fmt.Sprint(want) {
		t.Errorf("ListPackages =\n%v\nveut\n%v", pkgs, want)
	}
	s := Summarize(pkgs)
	if s != (Summary{Total: 3, System: 2, ThirdParty: 1, Disabled: 1, Removed: 1}) {
		t.Errorf("Summarize = %+v", s)
	}
}
