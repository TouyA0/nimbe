package listes

import (
	"strings"
	"testing"
)

func TestParse(t *testing.T) {
	in := "# titre\n\ncom.a   # commentaire\n  com.b_c.d\n"
	got, err := Parse(strings.NewReader(in))
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 2 || got[0] != (Entry{"com.a", "commentaire"}) || got[1].Name != "com.b_c.d" {
		t.Errorf("Parse = %+v", got)
	}
}

func TestParseRejectsInvalidNames(t *testing.T) {
	for _, in := range []string{"com.a b\n", "com.a;rm -rf /\n", "com..a\n", "com.a/.Main\n"} {
		if _, err := Parse(strings.NewReader(in)); err == nil {
			t.Errorf("Parse(%q) aurait dû échouer", in)
		}
	}
}
