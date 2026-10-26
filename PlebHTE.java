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

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;


public class PlebHTE {

    private JFrame window;
    private JTextField nameField;
	private JTextPane notesArea;
    private JCheckBox darkThemeCheck;
    private Path currentFile = null;
    private final Path notesFolder =
        Path.of("src").toAbsolutePath().normalize();
        
    private boolean isModified = false;
	private boolean isLoading = false;
    
    private Timer highlightTimer;

	private int dirtyStart = Integer.MAX_VALUE;
	private int dirtyEnd = -1;

	private static final Pattern LINK_PATTERN =
    Pattern.compile("\\[\\[([^\\[\\]\\r\\n]+)\\]\\]");

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
		window.setDefaultCloseOperation(
			JFrame.DO_NOTHING_ON_CLOSE
		);
        window.setLocationRelativeTo(null);
        
        window.addWindowListener(new WindowAdapter() {
		@Override
		public void windowClosing(WindowEvent e) {

				if (confirmSaveChanges()) {
					saveSettings();
					window.dispose();
				}
			}
		});

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
        
// Таймер для записи [[]], чтобы не обновлять постоянно

		highlightTimer = new Timer(100, e -> {
			if (dirtyStart != Integer.MAX_VALUE) {
				highlightChangedLines(dirtyStart, dirtyEnd);
			}

			dirtyStart = Integer.MAX_VALUE;
			dirtyEnd = -1;
		});

		highlightTimer.setRepeats(false);

		notesArea.getDocument().addDocumentListener(
			new DocumentListener() {

				private void scheduleUpdate(int start, int end) {
					dirtyStart = Math.min(dirtyStart, start);
					dirtyEnd = Math.max(dirtyEnd, end);

// Перезапускаем таймер

					highlightTimer.restart();
				}

			@Override
			public void insertUpdate(DocumentEvent e) {
				scheduleUpdate(
					e.getOffset(),
					e.getOffset() + e.getLength()
				);

				markModified();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				scheduleUpdate(
					e.getOffset(),
					e.getOffset()
				);

				markModified();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				// Изменение оформления не считаем
				// изменением содержимого документа
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
        
// Проверяем директории
        
        try {
			Files.createDirectories(notesFolder);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(
				window,
				"Не удалось создать папку заметок:\n"
					+ e.getMessage(),
				"Ошибка",
				JOptionPane.ERROR_MESSAGE
			);
			return;
		}

// Загружаем настройки

        loadSettings();
		updateTitle();
        window.setVisible(true);
    }
    
    private void setDocumentText(String text) {

		isLoading = true;

		try {
			notesArea.setText(text);
		} finally {
			isLoading = false;
		}

		isModified = false;
		updateTitle();

// Синхронизируем подсветку после загрузки

		if (highlightTimer != null) {
			highlightTimer.stop();
		}

		dirtyStart = Integer.MAX_VALUE;
		dirtyEnd = -1;

		highlightLinks();
	}
    
	private boolean saveToFile() {

		Path file;

		String name = nameField.getText()
			.trim()
			.toLowerCase(Locale.ROOT);

		String newFileName = name.endsWith(".txt")
			? name
			: name + ".txt";

		if (currentFile != null &&
				currentFile.getFileName().toString()
					.equals(newFileName)) {
			file = currentFile;
		} else {
			
// Если файл ещё не открыт или имя поменялось, используем новое имя


			if (name.isEmpty() || name.contains("/") ||
					name.contains("\\") ||
					name.equals(".") || name.equals("..")) {

				JOptionPane.showMessageDialog(
					window, "Введите корректное имя файла!"
				);
				return false;
			}


			if (currentFile != null) {

// Сохраняем в папку текущего документа
				
				file = currentFile.resolveSibling(newFileName);

			} else {

// Первый файл — в рабочую папку
				
				file = notesFolder.resolve(newFileName);
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
					return false;
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
			isModified = false;
			updateTitle();
			return true;
						

		} catch (IOException e) {
			JOptionPane.showMessageDialog(
				window,
				"Ошибка сохранения: " + e.getMessage(),
				"Ошибка",
				JOptionPane.ERROR_MESSAGE
			);
			return false;
		}
	}

	private boolean saveAsFile() {

		JFileChooser chooser =
			new JFileChooser(notesFolder.toFile());

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
					return false;
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
				isModified = false;
				updateTitle();
				saveSettings();
				


				return true;

			} catch (IOException e) {
				JOptionPane.showMessageDialog(
					window,
					"Ошибка сохранения: " + e.getMessage(),
					"Ошибка",
					JOptionPane.ERROR_MESSAGE
				);
				return false;
			}
		}
		
		return false;
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

					setDocumentText(text);
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
	
	private boolean createLinkedDocument(Path file) {

		int choice = JOptionPane.showConfirmDialog(
			window,
			"Документ не найден:\n" +
			file.getFileName() +
			"\n\nСоздать новый документ?",
			"Новая заметка",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.QUESTION_MESSAGE
		);

		if (choice != JOptionPane.YES_OPTION) {
			return false;
		}

		try {
			// Создаём пустой файл, не перезаписывая существующий
			Files.createFile(file);
			return true;

		} catch (java.nio.file.FileAlreadyExistsException e) {
			// Файл появился после проверки — используем его
			return Files.isRegularFile(file);

		} catch (IOException e) {
			JOptionPane.showMessageDialog(
				window,
				"Ошибка создания файла:\n" + e.getMessage(),
				"Ошибка",
				JOptionPane.ERROR_MESSAGE
			);
			return false;
		}
	}
	
	private void openFile() {

		JFileChooser chooser = new JFileChooser(notesFolder.toFile());

		int result = chooser.showOpenDialog(window);

		if (result == JFileChooser.APPROVE_OPTION) {

			Path file = chooser.getSelectedFile().toPath();
			
			if (!confirmSaveChanges()) {
				return;
			}

			try {
				
				
				String text = Files.readString(file);

				setDocumentText(text);
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
				saveSettings();
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

		String title = "PlebHTE — ";

		if (currentFile == null) {
			title += "Новый документ";
		} else {
			title += currentFile.getFileName();
		}

		if (isModified) {
			title += " *";
		}

		window.setTitle(title);
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
		
		Path target = notesFolder.resolve(fileName);


		if (!Files.isRegularFile(target)) {

			// Если путь уже занят папкой или другим объектом
			if (Files.exists(target)) {
				JOptionPane.showMessageDialog(
					window,
					"По этому пути находится не обычный файл!"
				);
				return;
			}

			// Предлагаем создать документ
			if (!createLinkedDocument(target)) {
				return;
			}
		}

		try {
			String content = Files.readString(target);

			if (!confirmSaveChanges()) {
				return;
			}

			currentFile = target;
			setDocumentText(content);

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
			saveSettings();

		} catch (IOException e) {
			JOptionPane.showMessageDialog(
				window,
				"Ошибка открытия: " + e.getMessage(),
				"Ошибка",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}
	
	private boolean confirmSaveChanges() {

		// Если ничего не изменялось,
		// можно продолжать без вопросов
		if (!isModified) {
			return true;
		}

		String fileName = currentFile == null
				? "Новый документ"
				: currentFile.getFileName().toString();

		Object[] options = {
			"Сохранить",
			"Не сохранять",
			"Отмена"
		};

		int choice = JOptionPane.showOptionDialog(
			window,
			"В документе \"" + fileName +
			"\" есть несохранённые изменения.\n" +
			"Сохранить перед продолжением?",
			"Несохранённые изменения",
			JOptionPane.DEFAULT_OPTION,
			JOptionPane.WARNING_MESSAGE,
			null,
			options,
			options[0]
		);

		if (choice == 0) {
			// Сохранить и продолжить,
			// только если запись успешна
			return saveToFile();
		}

		if (choice == 1) {
			// Продолжить без сохранения
			return true;
		}

		// Отмена или закрытие диалога
		return false;
	}
	
	
	private void highlightChangedLines(int start, int end) {

		StyledDocument doc = notesArea.getStyledDocument();
		int length = doc.getLength();

		if (length == 0) {
			return;
		}

		// Не позволяем выйти за границы документа
		start = Math.max(0, Math.min(start, length));
		end = Math.max(start, Math.min(end, length));

		// Находим логические строки изменённого участка
		Element root = doc.getDefaultRootElement();

		int firstLine = root.getElementIndex(start);
		int lastLine = root.getElementIndex(end);

		int from = root.getElement(firstLine).getStartOffset();
		int to = Math.min(
			root.getElement(lastLine).getEndOffset(),
			length
		);

		// Читаем только нужный фрагмент
		String text;

		try {
			text = doc.getText(from, to - from);
		} catch (BadLocationException e) {
			return;
		}

		// Стиль обычного текста
		SimpleAttributeSet normal = new SimpleAttributeSet();

		StyleConstants.setForeground(
			normal,
			darkThemeCheck.isSelected()
				? Color.WHITE : Color.BLACK
		);

		StyleConstants.setUnderline(normal, false);

		// Стиль ссылки
		SimpleAttributeSet link = new SimpleAttributeSet();

		StyleConstants.setForeground(
			link, new Color(65, 145, 245)
		);

		StyleConstants.setUnderline(link, true);

		// Сбрасываем подсветку только нужных строк
		doc.setCharacterAttributes(
			from, to - from, normal, false
		);

		Matcher matcher = LINK_PATTERN.matcher(text);

		while (matcher.find()) {
			doc.setCharacterAttributes(
				from + matcher.start(),
				matcher.end() - matcher.start(),
				link,
				false
			);
		}
	}


	private void highlightLinks() {
		highlightChangedLines(
			0,
			notesArea.getDocument().getLength()
		);
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
        highlightLinks();
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

