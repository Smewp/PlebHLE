import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.*;
import javax.swing.text.Highlighter;
import javax.swing.text.DefaultHighlighter;

import java.awt.*;
import java.awt.event.*;

import java.io.IOException;
import java.io.StringWriter;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class PlebHTE {
	
	// Our [[link]] pattern
    private static final Pattern LINK_PATTERN =
            Pattern.compile("\\[\\[([^\\[\\]\\r\\n]+)\\]\\]");
    private static final String EMPTY_HTML = "<html><body><p></p></body></html>";

    private final Path notesFolder = Path.of("notes").toAbsolutePath().normalize(); // Working with [notes] folder
    private final Preferences prefs = Preferences.userNodeForPackage(PlebHTE.class); // Local preferences saves

    private JFrame window;
    private JTextField nameField;
    private JTextPane notesArea;
    private JCheckBox darkThemeCheck;
    private Path currentFile;
    
    private final List<Object> wikiHighlights =
		new ArrayList<>();

	private final Highlighter.HighlightPainter wikiPainter =
		new DefaultHighlighter.DefaultHighlightPainter(
			new Color(215, 230, 255)
		);
    
    private boolean isModified;
    private boolean isLoading;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PlebHTE().createUI()); // Loading at the end
    }

    private void createUI() {
        window = new JFrame("PlebHTE");
        window.setSize(800, 600);
        window.setMinimumSize(new Dimension(550, 400));
        window.setLocationRelativeTo(null);
        window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { 
                if (confirmSaveChanges()) { 
                    saveSettings();
                    window.dispose(); // Asking to save document before closing if changed
                }
            }
        });

        JPanel panel = new JPanel(new BorderLayout(10, 10)); 
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
//Top panel
        JPanel top = new JPanel(new BorderLayout(10, 10)); 
        nameField = new JTextField();
        
        top.add(new JLabel("Note name:"), BorderLayout.WEST);
        top.add(nameField, BorderLayout.CENTER);
        JPanel header = new JPanel(new BorderLayout(0, 8));

		header.add(top, BorderLayout.NORTH);
		header.add(
			createFormattingToolbar(),
			BorderLayout.SOUTH
		);

		panel.add(header, BorderLayout.NORTH);

        notesArea = new JTextPane();
        notesArea.setContentType("text/html");
        setupFormattingShortcuts();
        
        Timer wikiTimer = new Timer(150, e -> highlightWikiLinks()); // Timer for updating link hilights
		wikiTimer.setRepeats(false);

        notesArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { markModified(); if (!isLoading) wikiTimer.restart(); }
            @Override public void removeUpdate(DocumentEvent e) { markModified(); if (!isLoading) wikiTimer.restart(); }
            @Override public void changedUpdate(DocumentEvent e) { markModified();}
        });
        notesArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                    int offset = notesArea.viewToModel2D(e.getPoint());
                    if (offset >= 0) openLinkAt(offset);
                }
            }
        });
        panel.add(new JScrollPane(notesArea), BorderLayout.CENTER);

        JButton saveButton = new JButton("Save");
        JButton loadButton = new JButton("Load");
        JButton saveAsButton = new JButton("Save as...");
        darkThemeCheck = new JCheckBox("Dark mode");

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        bottom.add(saveButton);
        bottom.add(loadButton);
        bottom.add(saveAsButton);
        bottom.add(darkThemeCheck);
        panel.add(bottom, BorderLayout.SOUTH);
        window.setContentPane(panel);

        saveButton.addActionListener(e -> saveToFile());
        loadButton.addActionListener(e -> openFile());
        saveAsButton.addActionListener(e -> saveAsFile());
        darkThemeCheck.addActionListener(e -> {
            applyTheme(darkThemeCheck.isSelected());
            prefs.putBoolean("darkTheme", darkThemeCheck.isSelected());
        });

        JRootPane root = window.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F5"), "quickSave");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control S"), "quickSave");
        root.getActionMap().put("quickSave", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { saveButton.doClick(); }
        });

        try {
            Files.createDirectories(notesFolder);
        } catch (IOException e) {
            showError("Cannot create notes folder", e);
            window.dispose();
            return;
        }

        loadSettings();
        updateTitle();
        window.setVisible(true);
    }
    
    private JToolBar createFormattingToolbar() {

		JToolBar toolbar = new JToolBar();
		toolbar.setFloatable(false);

		// Bold
		JButton bold = new JButton("B");
		bold.setFont(bold.getFont().deriveFont(Font.BOLD));
		bold.setToolTipText("Bold");

		// Italic
		JButton italic = new JButton("I");
		italic.setFont(italic.getFont().deriveFont(Font.ITALIC));
		italic.setToolTipText("Italic");

		// Underline
		JButton underline = new JButton("U");
		underline.setToolTipText("Underline");

		toolbar.add(bold);
		toolbar.add(italic);
		toolbar.add(underline);

		toolbar.addSeparator();

		// Text size
		JComboBox<String> fontSize = new JComboBox<>(
			new String[]{"8","10","12","14","18","24","32"}
		);

		fontSize.setSelectedItem("12");
		fontSize.setMaximumSize(new Dimension(65, 28));
		fontSize.setToolTipText("Size of HTML text");

		toolbar.add(new JLabel(" Size: "));
		toolbar.add(fontSize);

		toolbar.addSeparator();

		// Text color
		JButton colorButton = new JButton("Color");
		colorButton.setToolTipText("Color of HTML text");

		toolbar.add(colorButton);

		bold.addActionListener(e ->
			applyHtmlAction(new HTMLEditorKit.BoldAction())
		);

		italic.addActionListener(e ->
			applyHtmlAction(new HTMLEditorKit.ItalicAction())
		);

		underline.addActionListener(e ->
			applyHtmlAction(new HTMLEditorKit.UnderlineAction())
		);

		fontSize.addActionListener(e -> {

			int size = Integer.parseInt(
				(String) fontSize.getSelectedItem()
			);

			applyHtmlAction(
				new HTMLEditorKit.FontSizeAction(
					"Font size", size
				)
			);
		});

		colorButton.addActionListener(e -> {

			Color selected = JColorChooser.showDialog(
				window,
				"Chose text color",
				Color.BLACK
			);

			if (selected != null) {
				applyHtmlAction(
					new HTMLEditorKit.ForegroundAction(
						"Text color", selected
					)
				);
			}
		});
		return toolbar;
	}

    // Only way to change a text
	private void setDocumentText(String html) {
		isLoading = true;
		try {
			notesArea.setText(html);
			highlightWikiLinks();
			isModified = false;
		} finally {	isLoading = false; }

		updateTitle();
	}
	
	private void applyHtmlAction(Action action) {

		action.actionPerformed(
			new ActionEvent(
				notesArea,
				ActionEvent.ACTION_PERFORMED,
				"format"
			)
		);

		notesArea.requestFocusInWindow();
	}

	private void markModified() {

		if (isLoading) {
			return;
		}

		if (!isModified) {
			isModified = true;
			updateTitle();
		}
	}

    private void updateTitle() {
        String fileName = currentFile == null ? "New document" : currentFile.getFileName().toString();
        window.setTitle("PlebHTE — " + fileName + (isModified ? " *" : ""));
    }

    private boolean confirmSaveChanges() {
        if (!isModified) return true;
        Object[] options = {"Save", "Don't save", "Cancel"};
        int answer = JOptionPane.showOptionDialog(window,
                "There are unsaved changes. Save before continuing?",
                "Unsaved changes", JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        if (answer == 0) return saveToFile();
        return answer == 1;
    }

    private String normalizeHtmlName(String name) {
        name = name.trim().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) name = name.substring(0, name.length() - 5);
        else if (name.endsWith(".txt")) name = name.substring(0, name.length() - 4);
        if (name.isEmpty() || name.equals(".") || name.equals("..") ||
                name.contains("/") || name.contains("\\") || name.indexOf('\0') >= 0) {
            return null;
        }
        return name + ".html";
    }

    private String displayName(Path path) {
        String name = path.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) return name.substring(0, name.length() - 5);
        if (lower.endsWith(".txt")) return name.substring(0, name.length() - 4);
        return name;
    }

    private boolean confirmOverwrite(Path file) {
        if (!Files.exists(file)) return true;
        if (currentFile != null && file.toAbsolutePath().normalize()
                .equals(currentFile.toAbsolutePath().normalize())) return true;
        int answer = JOptionPane.showConfirmDialog(window,
                "Overwrite existing file?\n" + file,
                "Confirm overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return answer == JOptionPane.YES_OPTION;
    }

    private boolean saveToFile() {
        String fileName = normalizeHtmlName(nameField.getText());
        if (fileName == null) {
            JOptionPane.showMessageDialog(window, "Enter a valid note name.");
            return false;
        }
        Path file = currentFile == null
                ? notesFolder.resolve(fileName)
                : currentFile.resolveSibling(fileName);
        return saveDocument(file);
    }

    private boolean saveAsFile() {
        JFileChooser chooser = new JFileChooser(notesFolder.toFile());
        String suggested = normalizeHtmlName(nameField.getText());
        if (suggested == null) suggested = "notes.html";
        Path suggestedPath = currentFile == null
                ? notesFolder.resolve(suggested) : currentFile.resolveSibling(suggested);
        chooser.setSelectedFile(suggestedPath.toFile());
        if (chooser.showSaveDialog(window) != JFileChooser.APPROVE_OPTION) return false;

        Path chosen = chooser.getSelectedFile().toPath();
        String name = normalizeHtmlName(chosen.getFileName().toString());
        if (name == null) {
            JOptionPane.showMessageDialog(window, "Invalid file name.");
            return false;
        }
        return saveDocument(chosen.resolveSibling(name));
    }

    private boolean saveDocument(Path file) {
        if (!confirmOverwrite(file)) return false;
        try {
            HTMLEditorKit kit = (HTMLEditorKit) notesArea.getEditorKit();
            HTMLDocument doc = (HTMLDocument) notesArea.getDocument();
            StringWriter out = new StringWriter();
            kit.write(out, doc, 0, doc.getLength());
            // Write to a temporary file first, then replace the destination.
            // This prevents a failed write from truncating an existing note.
            Path temp = Files.createTempFile(file.toAbsolutePath().getParent(),
                    ".plebhte-", ".tmp");
            try {
                Files.writeString(temp, out.toString(), StandardCharsets.UTF_8);
                try {
                    Files.move(temp, file,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException | BadLocationException e) {
            showError("Cannot save HTML", e);
            return false;
        }
        currentFile = file;
        nameField.setText(displayName(file));
        isModified = false;
        saveSettings();
        updateTitle();
        return true;
    }

    private String readDocument(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return content;
// Take old notes as a text
        String escaped = content.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;");
        return "<html><body><pre>" + escaped + "</pre></body></html>";
    }

    private void openDocument(Path file, boolean checkUnsaved) {
        String html;
        try {
            html = readDocument(file);
        } catch (IOException e) {
            showError("Cannot open document", e);
            return;
        }
        if (checkUnsaved && !confirmSaveChanges()) return;
        currentFile = file;
        setDocumentText(html);
        nameField.setText(displayName(file));
        saveSettings();
        updateTitle();
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser(notesFolder.toFile());
        if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
            openDocument(chooser.getSelectedFile().toPath(), true);
        }
    }

    private void openLinkAt(int position) {
        try {
            String text = notesArea.getDocument().getText(0, notesArea.getDocument().getLength());
            Matcher matcher = LINK_PATTERN.matcher(text);
            while (matcher.find()) {
                if (position >= matcher.start() && position < matcher.end()) {
                    openLinkedDocument(matcher.group(1).trim());
                    return;
                }
            }
        } catch (BadLocationException e) {
            showError("Cannot read link", e);
        }
    }

    private void openLinkedDocument(String link) {
        String fileName = normalizeHtmlName(link);
        if (fileName == null) {
            JOptionPane.showMessageDialog(window, "Invalid link name.");
            return;
        }
        Path htmlFile = notesFolder.resolve(fileName);
        Path target = htmlFile;
        if (!Files.exists(htmlFile)) {
            String base = fileName.substring(0, fileName.length() - 5);
            Path txt = notesFolder.resolve(base + ".txt");
            if (Files.isRegularFile(txt)) target = txt;
        }
        if (Files.exists(target) && !Files.isRegularFile(target)) {
            JOptionPane.showMessageDialog(window, "This path is not a regular file.");
            return;
        }
        if (Files.isRegularFile(target)) {
            openDocument(target, true);
            return;
        }
        if (!confirmSaveChanges()) return;
        int answer = JOptionPane.showConfirmDialog(window,
                "Create document " + htmlFile.getFileName() + "?",
                "New note", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            Files.createFile(htmlFile);
        } catch (FileAlreadyExistsException e) {
            if (Files.isRegularFile(htmlFile)) {
                openDocument(htmlFile, false);
            } else {
                showError("Cannot create document", e);
            }
            return;
        } catch (IOException e) {
            showError("Cannot create document", e);
            return;
        }
        currentFile = htmlFile;
        setDocumentText(EMPTY_HTML);
        nameField.setText(displayName(htmlFile));
        saveSettings();
    }

    private void saveSettings() {
        prefs.put("name", nameField.getText());
        prefs.putBoolean("darkTheme", darkThemeCheck.isSelected());
        if (currentFile != null) {
            prefs.put("lastFile", currentFile.toAbsolutePath().toString());
        }
    }

    private void loadSettings() {
        nameField.setText(prefs.get("name", ""));
        boolean dark = prefs.getBoolean("darkTheme", false);
        darkThemeCheck.setSelected(dark);
        applyTheme(dark);
        String last = prefs.get("lastFile", "");
        if (!last.isEmpty()) {
            try {
                Path file = Path.of(last);
                if (Files.isRegularFile(file)) openDocument(file, false);
            } catch (InvalidPathException e) {
                showError("Invalid last document path", e);
            }
        }
    }

    private void applyTheme(boolean dark) {
        Color bg = dark ? new Color(45, 45, 45) : new Color(245, 245, 245);
        Color fg = dark ? Color.WHITE : Color.BLACK;
        Color inputBg = dark ? new Color(60, 60, 60) : Color.WHITE;
        applyColors(window.getContentPane(), bg, fg);
        nameField.setBackground(inputBg);
//        notesArea.setBackground(inputBg);
        nameField.setForeground(fg);
//        notesArea.setForeground(fg);
//        notesArea.setCaretColor(fg);

        window.repaint();
    }

    private void applyColors(Component c, Color bg, Color fg) {
        if (c instanceof JPanel) c.setBackground(bg);
        if (c instanceof JLabel || c instanceof JCheckBox) c.setForeground(fg);
        if (c instanceof JCheckBox) c.setBackground(bg);
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) applyColors(child, bg, fg);
        }
    }
    
    private void setupFormattingShortcuts() {

		InputMap input = notesArea.getInputMap();
		ActionMap actions = notesArea.getActionMap();

		input.put(
			KeyStroke.getKeyStroke("control B"),
			"font-bold"
		);

		input.put(
			KeyStroke.getKeyStroke("control I"),
			"font-italic"
		);

		input.put(
			KeyStroke.getKeyStroke("control U"),
			"font-underline"
		);

		actions.put(
			"font-bold",
			new HTMLEditorKit.BoldAction()
		);

		actions.put(
			"font-italic",
			new HTMLEditorKit.ItalicAction()
		);

		actions.put(
			"font-underline",
			new HTMLEditorKit.UnderlineAction()
		);
	}
    
    

private void highlightWikiLinks() {

    Highlighter highlighter = notesArea.getHighlighter();

    // Remove only old link hilights
    for (Object tag : wikiHighlights) {
        highlighter.removeHighlight(tag);
    }

    wikiHighlights.clear();

    try {
        String text = notesArea.getDocument().getText(
            0,
            notesArea.getDocument().getLength()
        );

        Matcher matcher = LINK_PATTERN.matcher(text);

        while (matcher.find()) {

            Object tag = highlighter.addHighlight(
                matcher.start(),
                matcher.end(),
                wikiPainter
            );

            wikiHighlights.add(tag);
        }

    } catch (BadLocationException e) {
        // Highlights shouldn't mess the editing
    }
}



    private void showError(String message, Exception e) {
        JOptionPane.showMessageDialog(window, message + ":\n" + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}
