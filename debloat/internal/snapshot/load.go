package snapshot

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
)

// Load relit un instantané écrit par Write.
func Load(path string) (Snapshot, error) {
	var s Snapshot
	data, err := os.ReadFile(path)
	if err != nil {
		return s, err
	}
	if err := json.Unmarshal(data, &s); err != nil {
		return s, fmt.Errorf("%s : %w", path, err)
	}
	if s.FormatVersion != FormatVersion {
		return s, fmt.Errorf("%s : format %d non pris en charge (attendu %d)", path, s.FormatVersion, FormatVersion)
	}
	return s, nil
}

// Oldest renvoie le chemin du plus ancien instantané d'un appareil : l'état d'origine,
// avant tout passage de l'outil. Les noms de fichiers sont horodatés, donc triables.
func Oldest(root, serial string) (string, error) {
	paths, err := filepath.Glob(filepath.Join(DeviceDir(root, serial), "backup-*.json"))
	if err != nil {
		return "", err
	}
	if len(paths) == 0 {
		return "", fmt.Errorf("aucun instantané pour %s dans %s : lance d'abord « backup »", serial, root)
	}
	sort.Strings(paths)
	return paths[0], nil
}
