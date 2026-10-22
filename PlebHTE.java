
import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import javax.swing.text.*;
import javax.swing.event.*;
import java.util.regex.*;
import java.util.Locale;



public class PlebHTE {

    private JFrame window;
    private JTextField nameField;
	private JTextPane notesArea;
    private JCheckBox darkThemeCheck;
    private Path currentFile = null;

// Постоянное хранилище настроек

    private final Preferences prefs =
            Preferences.userNodeForPackage(PlebHTE.class);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PlebHTE().createUI());
    }

    private void createUI() {

// Данные окна и приложения

        window = new JFrame("Мои заметки");
        window.setSize(800, 600);
        window.setMinimumSize(new Dimension(800, 600));
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(
                15, 15, 15, 15));

// Верхняя панель

        JPanel topPanel = new JPanel(new BorderLayout(10, 10));

        nameField = new JTextField();
        topPanel.add(new JLabel("Note name:"), BorderLayout.WEST);
        topPanel.add(nameField, BorderLayout.CENTER);

        panel.add(topPanel, BorderLayout.NORTH);

// Основное поле заметок

        notesArea = new JTextPane();
        
        
		notesArea.getDocument().addDocumentListener(
			new DocumentListener() {

				private void update() {
					SwingUtilities.invokeLater(
						() -> highlightLinks()
					);
				}

				@Override
				public void insertUpdate(DocumentEvent e) {
					update();
				}

				@Override
				public void removeUpdate(DocumentEvent e) {
					update();
				}

				@Override
				public void changedUpdate(DocumentEvent e) {

// Изменилось оформление, не сам текст

				}
			}
		);

        
		notesArea.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {

				if (e.getButton() == MouseEvent.BUTTON1 &&
					e.getClickCount() == 1) {

					int position = notesArea.viewToModel2D(
						e.getPoint()
					);

					if (position >= 0) {
						openLinkAt(position);
					}
				}
			}
		});


        JScrollPane scrollPane = new JScrollPane(notesArea);
        panel.add(scrollPane, BorderLayout.CENTER);

// Нижняя панель
        
        JPanel bottomPanel = new JPanel(
                new FlowLayout(FlowLayout.CENTER, 10, 5));

        JButton saveButton = new JButton("Сохранить");
        JButton loadButton = new JButton("Загрузка");
        JButton clearButton = new JButton("Очистить");
		JButton saveAsButton = new JButton("Сохранить как...");
        darkThemeCheck = new JCheckBox("Тёмная тема");

        bottomPanel.add(saveButton);
		bottomPanel.add(loadButton);
        // bottomPanel.add(clearButton);
		bottomPanel.add(saveAsButton);
        bottomPanel.add(darkThemeCheck);

        panel.add(bottomPanel, BorderLayout.SOUTH);

// Обработчики кнопок

        saveButton.addActionListener(e -> saveToFile());
        saveAsButton.addActionListener(e -> saveAsFile());
        
        saveButton.getInputMap(
			JComponent.WHEN_IN_FOCUSED_WINDOW
			).put(
				KeyStroke.getKeyStroke("F5"),
				"quickSave"
			);
			
		saveButton.getInputMap(
			JComponent.WHEN_IN_FOCUSED_WINDOW
				).put(
				KeyStroke.getKeyStroke("control S"),
			"quickSave"
		);

		saveButton.getActionMap().put(
			"quickSave",
			new AbstractAction() {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent e) {
					saveButton.doClick();
				}
			}
		);
		


        clearButton.addActionListener(e -> {
            notesArea.setText("");
        });

	loadButton.addActionListener(e -> openFile());	


        darkThemeCheck.addActionListener(e -> {
            applyTheme(darkThemeCheck.isSelected());
            prefs.putBoolean(
                    "darkTheme", darkThemeCheck.isSelected());
        });

        window.setContentPane(panel);

// Загружаем настройки

        loadSettings();
		updateTitle();
        window.setVisible(true);
    }
    
	private void saveToFile() {

		Path file;

		if (currentFile != null &&
			currentFile.getFileName().toString()
					.equals(nameField.getText() + ".txt"))  {
						
// Если файл уже открыт и имя совпадает, сохраняем в него
			
			file = currentFile;
						
		} else {
			
// Если файл ещё не открыт или имя поменялось, используем новое имя

			String name = nameField.getText()
					.trim()
					.toLowerCase(Locale.ROOT);

			if (name.isEmpty() || name.contains("/") ||
					name.contains("\\") ||
					name.equals(".") || name.equals("..")) {

				JOptionPane.showMessageDialog(
					window, "Введите корректное имя файла!"
				);
				return;
			}

			String newFileName = name + ".txt";

			if (currentFile != null) {

// Сохраняем в папку текущего документа
				
				file = currentFile.resolveSibling(newFileName);

			} else {

// Первый файл — в рабочую папку
				
				file = Path.of(newFileName);
			}

// Если это другой файл, проверяем перезапись
			
			if (!file.toAbsolutePath().normalize().equals(
					currentFile == null
						? null
						: currentFile.toAbsolutePath().normalize()
				) && Files.exists(file)) {

				int confirm = JOptionPane.showConfirmDialog(
					window,
					"Файл уже существует. Перезаписать?",
					"Подтверждение",
					JOptionPane.YES_NO_OPTION
				);

				if (confirm != JOptionPane.YES_OPTION) {
					return;
				}
			}

		}

		try {
			Files.writeString(file, notesArea.getText());

			currentFile = file;

			JOptionPane.showMessageDialog(
				window,
				"Файл сохранён:\n" + file.toAbsolutePath()
			);
			
			saveSettings();
			currentFile = file;
			updateTitle();

		} catch (IOException e) {
			JOptionPane.showMessageDialog(
				window,
				"Ошибка сохранения: " + e.getMessage(),
				"Ошибка",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private void saveAsFile() {

		JFileChooser chooser = new JFileChooser();

// Предлагаем имя текущего файла
		
		if (currentFile != null) {
			chooser.setSelectedFile(currentFile.toFile());
		} else {
			String name = nameField.getText().trim();

			if (name.isEmpty()) {
				name = "notes";
			}

			chooser.setSelectedFile(
				new java.io.File(name + ".txt")
			);
		}

		int result = chooser.showSaveDialog(window);

		if (result == JFileChooser.APPROVE_OPTION) {

			Path file = chooser.getSelectedFile().toPath();
			
			file = file.resolveSibling(
				file.getFileName().toString()
					.toLowerCase(Locale.ROOT)
			);

// Добавляем расширение, если его нет
			
			if (!file.getFileName().toString()
					 .toLowerCase(java.util.Locale.ROOT)
					 .endsWith(".txt")) {
				file = Path.of(file.toString() + ".txt");
			}

// Предупреждаем о перезаписи
			
			if (Files.exists(file)) {

				int confirm = JOptionPane.showConfirmDialog(
					window,
					"Файл уже существует. Перезаписать?",
					"Подтверждение",
					JOptionPane.YES_NO_OPTION
				);

				if (confirm != JOptionPane.YES_OPTION) {
					return;
				}
			}

			try {
				Files.writeString(file, notesArea.getText());

				currentFile = file;

				JOptionPane.showMessageDialog(
					window,
					"Файл успешно сохранён!"
				);
				currentFile = file;
				updateTitle();
				saveSettings();

			} catch (IOException e) {
				JOptionPane.showMessageDialog(
					window,
					"Ошибка сохранения: " + e.getMessage(),
					"Ошибка",
					JOptionPane.ERROR_MESSAGE
				);
			}
		}
	}


	private void saveSettings() {

		prefs.put("name", nameField.getText());

		prefs.putBoolean(
			"darkTheme",
			darkThemeCheck.isSelected()
		);

		if (currentFile != null) {
			prefs.put(
				"lastFile",
				currentFile.toAbsolutePath().toString()
			);
		}
	}


	private void loadSettings() {

		nameField.setText(prefs.get("name", ""));

		boolean dark = prefs.getBoolean("darkTheme", false);
		darkThemeCheck.setSelected(dark);
		applyTheme(dark);

		String lastFile = prefs.get("lastFile", "");

		if (!lastFile.isEmpty()) {

			try {
				Path file = Path.of(lastFile);

				if (Files.isRegularFile(file)) {
					String text = Files.readString(file);

					notesArea.setText(text);
					currentFile = file;

					String fileName = file.getFileName().toString();

					if (fileName.toLowerCase(Locale.ROOT)
							.endsWith(".txt")) {
						fileName = fileName.substring(
							0, fileName.length() - 4
						);
					}

					nameField.setText(fileName);
					updateTitle();
				}

			} catch (IOException | java.nio.file.InvalidPathException e) {
				JOptionPane.showMessageDialog(
					window,
					"Не удалось открыть предыдущий документ:\n"
						+ e.getMessage()
				);
			}
		}
	}
	
	private void openFile() {

		JFileChooser chooser = new JFileChooser();

		int result = chooser.showOpenDialog(window);

		if (result == JFileChooser.APPROVE_OPTION) {

			Path file = chooser.getSelectedFile().toPath();

			try {
				String text = Files.readString(file);

				notesArea.setText(text);
				currentFile = file;

				JOptionPane.showMessageDialog(
					window,
					"Файл успешно открыт!"
				);
				
				String fileName = file.getFileName().toString();

				if (fileName.toLowerCase(java.util.Locale.ROOT)
						.endsWith(".txt")) {

					fileName = fileName.substring(
						0, fileName.length() - 4
					);
				}

				nameField.setText(fileName);

				updateTitle();
				
			} catch (IOException e) {
				JOptionPane.showMessageDialog(
					window,
					"Ошибка открытия: " + e.getMessage(),
					"Ошибка",
					JOptionPane.ERROR_MESSAGE
				);
			}
		}
	}

	private void updateTitle() {

		if (currentFile == null) {
			window.setTitle("Мои заметки — Новый документ");
		} else {
			window.setTitle(
				"Мои заметки — " + currentFile.getFileName()
			);
		}
	}
	
	private void openLinkAt(int position) {

		String text = notesArea.getText();

// Ищем конструкции вида [[Название]]
		
		Pattern pattern = Pattern.compile("\\[\\[([^\\[\\]\\r\\n]+)\\]\\]");
		Matcher matcher = pattern.matcher(text);

		while (matcher.find()) {

// Проверяем, попал ли клик внутрь ссылки
			
			if (position >= matcher.start() &&
				position < matcher.end()) {

				String linkName = matcher.group(1).trim();

				if (linkName.isEmpty()) {
					return;
				}

				openLinkedDocument(linkName);
				return;
			}
		}
	}
	
		
	private void openLinkedDocument(String name) {

// Не разрешаем использовать ссылки как пути
		
		if (name.equals(".") || name.equals("..") ||
			name.contains("/") || name.contains("\\") ||
			name.indexOf('\0') >= 0) {

			JOptionPane.showMessageDialog(
				window, "Некорректное название ссылки!"
			);
			return;
		}

		String normalizedName = name.trim()
				.toLowerCase(Locale.ROOT);

		String fileName = normalizedName.endsWith(".txt")
				? normalizedName
				: normalizedName + ".txt";
				
		

// Ищем ссылку рядом с текущим документом
		
		Path folder = currentFile != null
				? currentFile.toAbsolutePath().getParent()
				: Path.of("").toAbsolutePath();

		Path target = folder.resolve(fileName);

		if (!Files.isRegularFile(target)) {
			JOptionPane.showMessageDialog(
				window,
				"Документ не найден:\n" + target
			);
			return;
		}

// Пока спрашиваем перед заменой текущего текста.
// Позже заменим это проверкой изменений.
		
		int confirm = JOptionPane.showConfirmDialog(
			window,
			"Открыть " + fileName +
			"?\nУбедись, что текущие изменения сохранены.",
			"Переход по ссылке",
			JOptionPane.OK_CANCEL_OPTION
		);

		if (confirm != JOptionPane.OK_OPTION) {
			return;
		}

		try {
			String content = Files.readString(target);

			notesArea.setText(content);
			currentFile = target;

// Отображаем имя без расширения
			
			String displayName =
					target.getFileName().toString();

			if (displayName.toLowerCase(
					java.util.Locale.ROOT).endsWith(".txt")) {
				displayName = displayName.substring(
					0, displayName.length() - 4
				);
			}

			nameField.setText(displayName);
			updateTitle();

		} catch (IOException e) {
			JOptionPane.showMessageDialog(
				window,
				"Ошибка открытия: " + e.getMessage(),
				"Ошибка",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}
	
	
	private void highlightLinks() {

		StyledDocument doc = notesArea.getStyledDocument();
		String text = notesArea.getText();

// Обычный стиль
		
		SimpleAttributeSet normal = new SimpleAttributeSet();
		StyleConstants.setForeground(normal,
			darkThemeCheck.isSelected()
				? Color.WHITE : Color.BLACK);
		StyleConstants.setUnderline(normal, false);

// Стиль гиперссылки

		SimpleAttributeSet link = new SimpleAttributeSet();
		StyleConstants.setForeground(link, new Color(65, 145, 245));
		StyleConstants.setUnderline(link, true);

// Сначала убираем старую подсветку

		doc.setCharacterAttributes(
			0, doc.getLength(), normal, true
		);

// Находим конструкции [[Название]]
		
		Pattern pattern = Pattern.compile(
			"\\[\\[([^\\[\\]\\r\\n]+)\\]\\]"
		);

		Matcher matcher = pattern.matcher(text);

		while (matcher.find()) {
			doc.setCharacterAttributes(
				matcher.start(),
				matcher.end() - matcher.start(),
				link,
				true
			);
		}
	}




    private void applyTheme(boolean dark) {
        Color background = dark
                ? new Color(45, 45, 45) : new Color(245, 245, 245);

        Color foreground = dark
                ? Color.WHITE : Color.BLACK;

        Color inputBackground = dark
                ? new Color(60, 60, 60) : Color.WHITE;

        Container root = window.getContentPane();

        applyColors(root, background, foreground);

        nameField.setBackground(inputBackground);
        notesArea.setBackground(inputBackground);

        nameField.setForeground(foreground);
        notesArea.setForeground(foreground);
        notesArea.setCaretColor(foreground);

        root.repaint();
    }

    private void applyColors(
            Component component, Color bg, Color fg) {

        if (component instanceof JPanel) {
            component.setBackground(bg);
        }

        if (component instanceof JLabel
                || component instanceof JCheckBox) {
            component.setForeground(fg);
        }

        if (component instanceof JCheckBox) {
            component.setBackground(bg);
        }

        if (component instanceof Container) {
            for (Component child :
                    ((Container) component).getComponents()) {
                applyColors(child, bg, fg);
            }
        }
    }
}

