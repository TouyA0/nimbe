// Package listes fournit les listes de paquets intégrées à l'outil.
package listes

import (
	"bufio"
	_ "embed"
	"fmt"
	"io"
	"regexp"
	"strings"
)

var (
	//go:embed protegee.txt
	protegee string
	//go:embed recommandes.txt
	recommandes string
	//go:embed optionnels.txt
	optionnels string
)

// Entry est un paquet d'une liste, avec son commentaire éventuel.
type Entry struct {
	Name    string
	Comment string
}

func Protected() []Entry   { return mustParse("protegee.txt", protegee) }
func Recommended() []Entry { return mustParse("recommandes.txt", recommandes) }
func Optional() []Entry    { return mustParse("optionnels.txt", optionnels) }

var validName = regexp.MustCompile(`^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)*$`)

// Parse lit une liste : un nom de paquet par ligne, « # » pour les commentaires.
// Un nom invalide est refusé, pour qu'une faute de frappe ne passe pas inaperçue.
func Parse(r io.Reader) ([]Entry, error) {
	var entries []Entry
	sc := bufio.NewScanner(r)
	for n := 1; sc.Scan(); n++ {
		line, comment, _ := strings.Cut(sc.Text(), "#")
		name := strings.TrimSpace(line)
		if name == "" {
			continue
		}
		if !validName.MatchString(name) {
			return nil, fmt.Errorf("ligne %d : nom de paquet invalide %q", n, name)
		}
		entries = append(entries, Entry{Name: name, Comment: strings.TrimSpace(comment)})
	}
	return entries, sc.Err()
}

func mustParse(file, content string) []Entry {
	entries, err := Parse(strings.NewReader(content))
	if err != nil {
		panic(fmt.Sprintf("liste intégrée %s : %v", file, err))
	}
	return entries
}
