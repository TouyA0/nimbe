package snapshot

import (
	"fmt"
	"os"
	"path/filepath"
	"time"
)

// Journal ajoute une ligne horodatée à sauvegardes/<série>/journal.log pour chaque modification.
func Journal(root, serial, line string) error {
	dir := DeviceDir(root, serial)
	if err := os.MkdirAll(dir, 0o750); err != nil {
		return err
	}
	f, err := os.OpenFile(filepath.Join(dir, "journal.log"), os.O_WRONLY|os.O_CREATE|os.O_APPEND, 0o640)
	if err != nil {
		return err
	}
	if _, err := fmt.Fprintf(f, "%s %s\n", time.Now().Format(time.RFC3339), line); err != nil {
		f.Close()
		return err
	}
	return f.Close()
}
