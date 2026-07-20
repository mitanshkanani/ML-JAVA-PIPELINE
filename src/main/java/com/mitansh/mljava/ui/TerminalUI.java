package com.mitansh.mljava.ui;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TerminalUI {

    private static final String DATA_FOLDER = "data";
    private static final String CSV_EXTENSION = ".csv";
    
    private Terminal terminal;
    private int selectedIndex = 0;

    public String selectDataset() {
        List<String> datasets = scanDatasets();

        if (datasets.isEmpty()) {
            System.out.println("No CSV files found in '" + DATA_FOLDER + "' folder.");
            return null;
        }

        try {
            initializeTerminal();
            return runSelectionLoop(datasets);
        } catch (IOException e) {
            throw new RuntimeException("Failed to run terminal UI.", e);
        } finally {
            closeTerminal();
        }
    }

    private List<String> scanDatasets() {
        File folder = new File(DATA_FOLDER);
        File[] files = folder.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(CSV_EXTENSION));

        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                names.add(file.getName());
            }
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private void initializeTerminal() throws IOException {
        terminal = TerminalBuilder.builder()
                .system(true)
                .jna(true)
                .build();
        terminal.enterRawMode();
    }

    private void closeTerminal() {
        if (terminal != null) {
            try {
                terminal.close();
            } catch (IOException e) {
                // ignore shutdown errors
            }
        }
    }

    private String runSelectionLoop(List<String> datasets) throws IOException {
        while (true) {
            render(datasets);
            int firstByte = terminal.reader().read();

            // Enter (CR or LF)
            if (firstByte == '\r' || firstByte == '\n') {
                return datasets.get(selectedIndex);
            }

            // Quit keys (q / Q / plain ESC)
            if (firstByte == 'q' || firstByte == 'Q') {
                return null;
            }

            // Handle escape sequences (arrow keys, Esc)
            if (firstByte == 27) {  // ESC
                int nextByte = terminal.reader().read(200); // 200ms for next char
                if (nextByte == -1) {
                    return null; // plain ESC -> exit
                }

                // Standard CSI sequence ( ESC [ ... )
                if (nextByte == '[') {
                    int arrowByte = terminal.reader().read(200);
                    switch (arrowByte) {
                        case 'A': moveSelection(-1, datasets.size()); break; // Up
                        case 'B': moveSelection(1, datasets.size());  break; // Down
                        case 'C': break; // Right (ignore)
                        case 'D': break; // Left (ignore)
                        default:  break; // unknown CSI – ignore
                    }
                }
                // Alternate SS3 sequence ( ESC O ... ) – used by some terminals
                else if (nextByte == 'O') {
                    int arrowByte = terminal.reader().read(200);
                    switch (arrowByte) {
                        case 'A': moveSelection(-1, datasets.size()); break;
                        case 'B': moveSelection(1, datasets.size());  break;
                        default:  break;
                    }
                }
                // Any other byte after ESC is ignored
            }
        }
    }

    private void moveSelection(int delta, int size) {
        selectedIndex = ((selectedIndex + delta) % size + size) % size;
    }

    private void render(List<String> datasets) {
        int width = terminal.getWidth();
        clearScreen();
        drawHeader(width);
        drawSectionTitle();
        drawDatasetList(datasets);
        drawFooter(width, datasets.size());
        terminal.flush();
    }

    private void clearScreen() {
        terminal.puts(InfoCmp.Capability.clear_screen);
    }

    private void drawHeader(int width) {
        String divider = repeat('=', width);
        printLine(divider);
        printLine(centered("ML-JAVA-PIPELINE", width));
        printLine(divider);
        printLine("");
    }

    private void drawSectionTitle() {
        printLine("  Select Dataset");
        printLine("");
    }

    private void drawDatasetList(List<String> datasets) {
        for (int i = 0; i < datasets.size(); i++) {
            boolean isSelected = (i == selectedIndex);
            String prefix = isSelected ? "❯ " : "  ";
            String line = "  " + prefix + datasets.get(i);
            
            if (isSelected) {
                AttributedString styled = new AttributedString(
                    line, 
                    AttributedStyle.DEFAULT
                        .foreground(AttributedStyle.BLACK)
                        .background(AttributedStyle.CYAN)
                );
                printStyledLine(styled);
            } else {
                printLine(line);
            }
        }
        printLine("");
    }

    private void drawFooter(int width, int itemCount) {
        String divider = repeat('─', Math.min(width, 60));
        printLine(divider);
        printLine("  ↑↓ Navigate    Enter Select    Esc/q Exit");
    }

    private void printLine(String text) {
        terminal.writer().println(text);
    }

    private void printStyledLine(AttributedString text) {
        terminal.writer().println(text.toAnsi());
    }

    private String repeat(char c, int count) {
        char[] chars = new char[Math.max(count, 0)];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private String centered(String text, int width) {
        int padding = Math.max((width - text.length()) / 2, 0);
        return repeat(' ', padding) + text;
    }
}