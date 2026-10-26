package plan

import (
	"fmt"
	"testing"

	"github.com/TouyA0/nimbe/debloat/internal/device"
	"github.com/TouyA0/nimbe/debloat/listes"
)

func pkg(name string, enabled, installed bool) device.Package {
	return device.Package{Name: name, System: true, Enabled: enabled, Installed: installed}
}

func TestDebloat(t *testing.T) {
	current := []device.Package{
		pkg("com.ads", true, true),
		pkg("com.launcher", true, true),
		pkg("com.off", false, true),
		pkg("com.removed", true, false),
		pkg("com.cast", true, true),
	}
	recommended := []listes.Entry{
		{Name: "com.ads", Comment: "pubs"},
		{Name: "com.launcher"},
		{Name: "com.off"},
		{Name: "com.removed"},
		{Name: "com.ghost"},
	}
	optional := []listes.Entry{{Name: "com.cast"}, {Name: "com.ads"}}
	protected := map[string]bool{"com.launcher": true}

	actions, skipped := Debloat(current, recommended, optional, protected)

	wantActions := []Action{
		{Package: "com.ads", Op: OpDisable, Comment: "pubs"},
		{Package: "com.cast", Op: OpDisable, Ask: true},
	}
	if fmt.Sprint(actions) != fmt.Sprint(wantActions) {
		t.Errorf("actions =\n%v\nveut\n%v", actions, wantActions)
	}
	wantSkipped := []Skip{
		{"com.launcher", ReasonProtected},
		{"com.off", ReasonDisabled},
		{"com.removed", ReasonAbsent},
		{"com.ghost", ReasonAbsent},
	}
	if fmt.Sprint(skipped) != fmt.Sprint(wantSkipped) {
		t.Errorf("ignorés =\n%v\nveut\n%v", skipped, wantSkipped)
	}
}

func TestDebloatNeverTouchesProtectedEvenIfOptional(t *testing.T) {
	actions, _ := Debloat(
		[]device.Package{pkg("com.android.vending", true, true)},
		nil,
		[]listes.Entry{{Name: "com.android.vending"}},
		map[string]bool{"com.android.vending": true},
	)
	if len(actions) != 0 {
		t.Errorf("un paquet protégé a été prévu : %v", actions)
	}
}

func TestRestore(t *testing.T) {
	origin := []device.Package{
		pkg("com.disabled.by.tool", true, true),
		pkg("com.removed.later", true, true),
		pkg("com.was.off", false, true),
		pkg("com.untouched", true, true),
		pkg("com.gone", true, true),
	}
	current := []device.Package{
		pkg("com.disabled.by.tool", false, true),
		pkg("com.removed.later", true, false),
		pkg("com.was.off", false, true),
		pkg("com.untouched", true, true),
		pkg("com.new", false, true),
	}
	got := Restore(origin, current)
	want := []Action{
		{Package: "com.disabled.by.tool", Op: OpEnable},
		{Package: "com.removed.later", Op: OpInstallExisting},
	}
	if fmt.Sprint(got) != fmt.Sprint(want) {
		t.Errorf("Restore =\n%v\nveut\n%v", got, want)
	}
}

func TestCheck(t *testing.T) {
	current := []device.Package{
		pkg("com.android.providers.tv", false, true),
		pkg("com.android.vending", true, false),
		pkg("com.google.android.gms", true, true),
		pkg("com.ads", true, true),
		pkg("com.ads.off", false, true),
	}
	protected := map[string]bool{
		"com.android.providers.tv": true, "com.android.vending": true, "com.google.android.gms": true,
	}
	problems, notes := Check(current, protected, []listes.Entry{{Name: "com.ads"}, {Name: "com.ads.off"}})
	if len(problems) != 2 {
		t.Errorf("problèmes = %v", problems)
	}
	if len(notes) != 1 {
		t.Errorf("remarques = %v", notes)
	}
}

func TestEmbeddedListsParse(t *testing.T) {
	if len(listes.Protected()) == 0 {
		t.Fatal("liste protégée vide")
	}
	protected := map[string]bool{}
	for _, e := range listes.Protected() {
		protected[e.Name] = true
	}
	for _, must := range []string{"com.android.providers.tv", "com.android.vending", "com.google.android.gms"} {
		if !protected[must] {
			t.Errorf("%s doit être dans la liste protégée", must)
		}
	}
	_ = listes.Recommended()
	_ = listes.Optional()
}
