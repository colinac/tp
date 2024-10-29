package seedu.address.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test class for BackupManager.
 */
public class BackupManagerTest {

    @TempDir
    public Path temporaryFolder;
    private BackupManager backupManager;
    private Path backupDirectory;
    private Path sourceFile;

    @BeforeEach
    public void setUp() throws IOException {
        // Set up the backup directory and source file in the temporary folder
        backupDirectory = temporaryFolder.resolve("backups");
        Files.createDirectories(backupDirectory);

        sourceFile = temporaryFolder.resolve("addressBook.json");
        Files.writeString(sourceFile, "Sample AddressBook Data");

        backupManager = new BackupManager(backupDirectory);
    }

    @Test
    public void createIndexedBackup_validParameters_backupCreated() throws IOException {
        String actionDescription = "testAction";
        int index = backupManager.createIndexedBackup(sourceFile, actionDescription);

        // Verify that the backup file exists
        assertTrue(index >= 0 && index < 10, "Backup index should be between 0 and 9.");

        // Check that a backup file with the correct index and description exists
        boolean backupFileExists = Files.list(backupDirectory)
                .anyMatch(path -> path
                        .getFileName()
                        .toString()
                        .matches(index + "_" + actionDescription + "_.*\\.json"));

        assertTrue(backupFileExists, "Backup file with specified index and description should exist.");
    }

    @Test
    public void createIndexedBackup_nullActionDescription_backupCreated() throws IOException {
        int index = backupManager.createIndexedBackup(sourceFile, null);

        // Verify that the backup file exists
        assertTrue(index >= 0 && index < 10, "Backup index should be between 0 and 9.");

        // Check that a backup file with the correct index exists
        boolean backupFileExists = Files.list(backupDirectory)
                .anyMatch(path -> path.getFileName().toString().matches(index + "_null_.*\\.json"));

        assertTrue(backupFileExists, "Backup file with specified index should exist.");
    }

    @Test
    public void createIndexedBackup_multipleBackups_indicesRotate() throws IOException {
        // Create multiple backups to test index rotation
        for (int i = 0; i < 15; i++) { // Exceeds MAX_BACKUPS to test rotation
            int index = backupManager.createIndexedBackup(sourceFile, "action" + i);

            // Verify that the index cycles between 0 and 9
            assertEquals(i % 10, index, "Backup index should cycle between 0 and 9.");
        }
    }

    @Test
    public void restoreBackupByIndex_validIndex_backupRestored() throws IOException {
        // Create a backup at index 0
        int index = backupManager.createIndexedBackup(sourceFile, "restoreTest");

        // Attempt to restore the backup
        Path restoredPath = backupManager.restoreBackupByIndex(index);

        // Verify that the restored path exists and matches the backup file
        assertNotNull(restoredPath, "Restored path should not be null.");
        assertTrue(Files.exists(restoredPath), "Restored backup file should exist.");
    }

    @Test
    public void restoreBackupByIndex_invalidIndex_throwsIoException() {
        int invalidIndex = 9; // Assuming index 9 has no backup

        // Expect IOException when restoring a non-existent backup
        IOException exception = assertThrows(IOException.class, () -> backupManager.restoreBackupByIndex(invalidIndex));
        assertEquals("Backup with index " + invalidIndex + " not found.", exception.getMessage());
    }

    @Test
    public void listBackups_noBackups_returnsEmptyList() throws IOException {
        List<Path> backups = backupManager.listBackups();
        assertTrue(backups.isEmpty(), "Backup list should be empty when no backups exist.");
    }

    @Test
    public void listBackups_withBackups_returnsBackupList() throws IOException {
        // Create some backups
        backupManager.createIndexedBackup(sourceFile, "listTest1");
        backupManager.createIndexedBackup(sourceFile, "listTest2");

        List<Path> backups = backupManager.listBackups();
        assertEquals(2, backups.size(), "Backup list should contain 2 backups.");
    }

    @Test
    public void backupManager_initializationWithNullPath_throwsIoException() {
        assertThrows(IOException.class, () -> new BackupManager(null));
    }
}
