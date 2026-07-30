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

    // [1] Scans data folder, starts UI, returns chosen CSV file name.
    public String selectDataset() { // ---> [1]
        List<String> datasets = scanDatasets(); // ---> [2] List will be returned for scanDatasets which will have name
                                                // of all csv

        if (datasets.isEmpty()) {
            System.out.println("No CSV files found in '" + DATA_FOLDER + "' folder.");
            return null;
        }

        try {
            initializeTerminal(); // ---> [3]
            return runSelectionLoop(datasets); // ---> [4]
        } catch (IOException e) {
            throw new RuntimeException("Failed to run terminal UI.", e);
        } finally {
            closeTerminal();
        }
    }

    // [2] Returns sorted list of .csv file names from data/ folder.
    private List<String> scanDatasets() { // ---> [2]
        File folder = new File(DATA_FOLDER);
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(CSV_EXTENSION));

        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                names.add(file.getName());
            }
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    // [3] Opens terminal with JNA support, switches to raw mode.
    private void initializeTerminal() throws IOException { // ---> [3]
        terminal = TerminalBuilder.builder()
                .system(true) // use the real system terminal (stdin/stdout)
                .jna(true) // enable JNA for native Windows support
                .build(); // create the Terminal object
        terminal.enterRawMode(); // switch to raw input mode
    }

    private void closeTerminal() {
        if (terminal != null) {
            try {
                terminal.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    // [4] Main loop: renders list, reads keys, returns selected file or null on
    // quit.
    private String runSelectionLoop(List<String> datasets) throws IOException { // ---> [4]
        while (true) {
            render(datasets); // ---> [6]
            int firstByte = terminal.reader().read();

            if (firstByte == '\r' || firstByte == '\n') {
                return datasets.get(selectedIndex);
            }

            if (firstByte == 'q' || firstByte == 'Q') {
                return null;
            }

            if (firstByte == 27) {
                int nextByte = terminal.reader().read(200);
                if (nextByte == -1)
                    return null;

                if (nextByte == '[') {
                    int arrowByte = terminal.reader().read(200);
                    switch (arrowByte) {
                        case 'A':
                            moveSelection(-1, datasets.size());
                            break; // ---> [5]
                        case 'B':
                            moveSelection(1, datasets.size());
                            break; // ---> [5]
                        default:
                            break;
                    }
                } else if (nextByte == 'O') {
                    int arrowByte = terminal.reader().read(200);
                    switch (arrowByte) {
                        case 'A':
                            moveSelection(-1, datasets.size());
                            break; // ---> [5]
                        case 'B':
                            moveSelection(1, datasets.size());
                            break; // ---> [5]
                        default:
                            break;
                    }
                }
            }
        }
    }

    // [5] Moves highlighted index up/down with wraparound.
    private void moveSelection(int delta, int size) { // ---> [5]
        selectedIndex = ((selectedIndex + delta) % size + size) % size;
    }

    // [6] Clears screen and redraws the full interface.
    private void render(List<String> datasets) { // ---> [6]
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
                                .background(AttributedStyle.CYAN));
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