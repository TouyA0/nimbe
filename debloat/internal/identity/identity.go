// Package identity mémorise quel appareil répond à quelle adresse adb, pour ne jamais modifier
// un autre appareil que prévu (par exemple si la box a donné l'IP du stick à une autre TV).
package identity

import (
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
)

const file = "appareils.json"

// Check compare le numéro de série trouvé à celui mémorisé pour cette adresse.
// Une adresse inconnue est mémorisée. Sans adresse (appareil unique, USB), il n'y a rien à vérifier.
func Check(root, target, serial string) error {
	if target == "" {
		return nil
	}
	path := filepath.Join(root, file)
	known := map[string]string{}
	data, err := os.ReadFile(path)
	switch {
	case errors.Is(err, fs.ErrNotExist):
	case err != nil:
		return err
	default:
		if err := json.Unmarshal(data, &known); err != nil {
			return fmt.Errorf("%s illisible : %w", path, err)
		}
	}

	if prev, ok := known[target]; ok {
		if prev != serial {
			return fmt.Errorf("l'appareil à l'adresse %s a changé : attendu %s, trouvé %s. "+
				"Rien n'a été modifié. Si c'est voulu, retire cette adresse de %s", target, prev, serial, path)
		}
		return nil
	}

	known[target] = serial
	if err := os.MkdirAll(root, 0o750); err != nil {
		return err
	}
	data, err = json.MarshalIndent(known, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(path, append(data, '\n'), 0o640)
}
