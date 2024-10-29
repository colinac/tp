package seedu.address.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import seedu.address.commons.core.LogsCenter;

/**
 * Handles the creation, cleanup, and restoration of backups for the AddressBook data files.
 */
public class BackupManager {

    private static final Logger logger = LogsCenter.getLogger(BackupManager.class);
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");
    private static final int MAX_BACKUPS = 10; // indexed from 0 to 9
    private final Path backupDirectory;
    private final Object backupLock = new Object(); // Lock object to synchronize backup operations
    private int currentIndex;


    /**
     * Constructs a {@code BackupManager} with the specified backup directory.
     *
     * @param backupDirectory The path to the backup directory. Must not be {@code null}.
     * @throws IOException If the backup directory path is {@code null} or if an error occurs
     *                     while creating the directory.
     */
    public BackupManager(Path backupDirectory) throws IOException {
        if (backupDirectory == null) {
            throw new IOException("Backup directory path cannot be null.");
        }

        this.backupDirectory = backupDirectory;
        if (!Files.exists(backupDirectory)) {
            Files.createDirectories(backupDirectory);
            logger.info("Created backup directory at: " + backupDirectory);
        }
        initializeCurrentIndex();
    }

    private void initializeCurrentIndex() throws IOException {
        // Initialize currentIndex based on existing backups
        // Find the highest index, and set currentIndex to (highest index + 1) % MAX_BACKUPS
        try (Stream<Path> backups = Files.list(backupDirectory)) {
            List<Integer> indices = backups
                    .filter(Files::isRegularFile)
                    .map(this::extractIndex)
                    .filter(index -> index >= 0)
                    .sorted()
                    .collect(Collectors.toList());
            if (indices.isEmpty()) {
                currentIndex = 0;
            } else {
                int highestIndex = indices.get(indices.size() - 1);
                currentIndex = (highestIndex + 1) % MAX_BACKUPS;
            }
        }
    }

    /**
     * Creates a backup with a fixed index, from 0 to 9, replacing the existing backup at that index.
     *
     * @param sourcePath        The path to the current address book file.
     * @param actionDescription A description for the backup file (e.g., "delete_John").
     * @return The index used for the backup.
     * @throws IOException If an error occurs during backup creation.
     */
    public int createIndexedBackup(Path sourcePath, String actionDescription) throws IOException {
        synchronized (backupLock) {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            String backupFileName = String.format("%d_%s_%s.json", currentIndex, actionDescription, timestamp);
            Path backupPath = backupDirectory.resolve(backupFileName);

            // Delete existing backup at currentIndex if it exists
            deleteBackupByIndex(currentIndex);

            // Copy source to the backup path
            Files.copy(sourcePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Backup created with index: " + currentIndex);

            int usedIndex = currentIndex;
            // Update currentIndex
            currentIndex = (currentIndex + 1) % MAX_BACKUPS;

            return usedIndex;
        }
    }

    private void deleteBackupByIndex(int index) throws IOException {
        try (Stream<Path> backups = Files.list(backupDirectory)) {
            backups.filter(path -> extractIndex(path) == index)
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                            logger.info("Deleted old backup at index " + index + ": " + path);
                        } catch (IOException e) {
                            logger.warning("Failed to delete backup: " + path + " - " + e.getMessage());
                        }
                    });
        }
    }

    // Changed from private to package-private (default access)
    int extractIndex(Path backupPath) {
        String filename = backupPath.getFileName().toString();
        try {
            int underscoreIndex = filename.indexOf('_');
            if (underscoreIndex == -1) {
                return -1;
            }
            String indexStr = filename.substring(0, underscoreIndex);
            return Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            logger.warning("Invalid backup file index format: " + filename);
            return -1;
        }
    }

    /**
     * Restores a backup by its index.
     *
     * @param index The index of the backup to restore.
     * @return The path to the backup file.
     * @throws IOException If the backup file is not found or cannot be accessed.
     */
    public Path restoreBackupByIndex(int index) throws IOException {
        try (Stream<Path> backups = Files.list(backupDirectory)) {
            Optional<Path> backupToRestore = backups
                    .filter(path -> extractIndex(path) == index)
                    .findFirst();

            if (backupToRestore.isPresent()) {
                return backupToRestore.get();
            } else {
                throw new IOException("Backup with index " + index + " not found.");
            }
        }
    }

    /**
     * Lists all available backups with their indices.
     *
     * @return A list of backup file paths.
     * @throws IOException If an error occurs while accessing the backup directory.
     */
    public List<Path> listBackups() throws IOException {
        try (Stream<Path> backups = Files.list(backupDirectory)) {
            return backups.filter(Files::isRegularFile)
                    .sorted(Comparator.comparingInt(this::extractIndex))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Cleans up old backups, keeping only the most recent `maxBackups`.
     *
     * @param maxBackups The number of backups to retain.
     * @throws IOException If an error occurs during cleanup.
     */
    public void cleanOldBackups(int maxBackups) throws IOException {
        if (maxBackups < 1) {
            throw new IllegalArgumentException("maxBackups must be at least 1.");
        }

        try (Stream<Path> backups = Files.list(backupDirectory)) {
            List<Path> backupFiles = backups.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(this::getFileCreationTime).reversed())
                    .collect(Collectors.toList());

            for (int i = maxBackups; i < backupFiles.size(); i++) {
                Files.deleteIfExists(backupFiles.get(i));
                logger.info("Deleted old backup: " + backupFiles.get(i));
            }
        }
    }

    private FileTime getFileCreationTime(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            logger.warning("Failed to get file creation time for " + path + ": " + e.getMessage());
            return FileTime.fromMillis(0);
        }
    }
}
