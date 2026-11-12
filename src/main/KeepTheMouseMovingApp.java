import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class KeepTheMouseMovingApp {
    private static final int DEFAULT_DELAY_MINUTES = 3;
    private static final String PROPERTY_NAME = "delay.minutes";

    private final JFrame frame = new JFrame("Keep The Mouse Moving");
    private final JButton startButton = new JButton("Start");
    private final JButton stopButton = new JButton("Stop");
    private final JButton settingsButton = new JButton();
    private final JLabel countdownLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel stateLabel = new JLabel("Zastaveno", SwingConstants.CENTER);
    

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mouse-delay-worker");
        t.setDaemon(true);
        return t;
    });

    private final Robot robot;
    private volatile boolean running;
    private volatile int delayMinutes;
    private volatile int remainingSeconds;
    private volatile Point lastMousePosition;

    private KeepTheMouseMovingApp() throws AWTException {
        robot = new Robot();
        delayMinutes = loadDelayMinutes();
        remainingSeconds = delayMinutes * 60;
        lastMousePosition = currentMousePosition();
        createUi();
        executor.scheduleAtFixedRate(this::tickSafely, 0, 1, TimeUnit.SECONDS);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new KeepTheMouseMovingApp().show();
            } catch (AWTException ex) {
                JOptionPane.showMessageDialog(null,
                        "Nelze inicializovat ovládání myši: " + ex.getMessage(),
                        "Chyba", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void createUi() {
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setMinimumSize(new Dimension(430, 210));
        frame.setSize(500, 240);
        frame.setLocationRelativeTo(null);
        
        var appIconUrl =
                KeepTheMouseMovingApp.class.getResource("/KeepTheMouseMoving.png");

        if (appIconUrl != null) {
            frame.setIconImage(
                    new ImageIcon(appIconUrl).getImage()
            );
        }

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        startButton.setPreferredSize(new Dimension(120, 38));
        startButton.setToolTipText("Zahájit odpočet");
        stopButton.setPreferredSize(new Dimension(120, 38));
        stopButton.setToolTipText("Zastavit odpočet");
        
        var iconUrl = KeepTheMouseMovingApp.class.getResource("/settings.png");

        if (iconUrl != null) {
            ImageIcon originalIcon = new ImageIcon(iconUrl);

            Image scaledImage = originalIcon.getImage().getScaledInstance(
                    22,
                    22,
                    Image.SCALE_SMOOTH
            );

            settingsButton.setIcon(new ImageIcon(scaledImage));
        }
        else {
        	System.out.println("Icon not found!");
        }
        
        settingsButton.setToolTipText("Nastavit čas odpočtu");
        settingsButton.setPreferredSize(new Dimension(52, 38));
        
        top.add(startButton);
        top.add(settingsButton);
        top.add(stopButton);

        countdownLabel.setFont(countdownLabel.getFont().deriveFont(Font.BOLD, 34f));
        JPanel countdownPanel = new JPanel(new BorderLayout(10, 0));
        countdownPanel.add(countdownLabel, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.add(countdownPanel, BorderLayout.CENTER);
        bottom.add(stateLabel, BorderLayout.SOUTH);

        root.add(top, BorderLayout.NORTH);
        root.add(bottom, BorderLayout.CENTER);
        frame.setContentPane(root);

        startButton.addActionListener(e -> startMonitoring());
        stopButton.addActionListener(e -> stopMonitoring());
        settingsButton.addActionListener(e -> showSettingsDialog());
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                executor.shutdownNow();
                frame.dispose();
            }
        });

        refreshUi();
        updateButtonState();
    }

    private void show() {
        frame.setVisible(true);
        startMonitoring();
    }

    private void startMonitoring() {
        running = true;
        resetCycle();
        updateButtonState();
        refreshUi();
    }

    private void stopMonitoring() {
        running = false;
        remainingSeconds = delayMinutes * 60;
        updateButtonState();
        refreshUi();
    }

    private void resetCycle() {
        remainingSeconds = delayMinutes * 60;
        lastMousePosition = currentMousePosition();
    }

    private void tickSafely() {
        try {
            tick();
        } catch (RuntimeException ex) {
            SwingUtilities.invokeLater(() -> stateLabel.setText("Chyba: " + ex.getMessage()));
        }
    }

    private void tick() {
        if (!running) {
            refreshUi();
            return;
        }

        Point current = currentMousePosition();
        if (!current.equals(lastMousePosition)) {
            lastMousePosition = current;
            remainingSeconds = delayMinutes * 60;
            refreshUi();
            return;
        }

        if (remainingSeconds > 0) {
            remainingSeconds--;
        }

        if (remainingSeconds <= 0) {
            moveMouseOnePixel(current);
            resetCycle();
        }
        refreshUi();
    }

    private void moveMouseOnePixel(Point current) {
        Rectangle bounds = virtualScreenBounds();
        int targetX = current.x + 1;
        if (targetX >= bounds.x + bounds.width) {
            targetX = current.x - 1;
        }
        if (targetX < bounds.x) {
            targetX = Math.min(current.x + 1, bounds.x + bounds.width - 1);
        }
        robot.mouseMove(targetX, current.y);
    }

    private static Rectangle virtualScreenBounds() {
        Rectangle result = new Rectangle();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            result = result.union(device.getDefaultConfiguration().getBounds());
        }
        return result;
    }

    private static Point currentMousePosition() {
        PointerInfo info = MouseInfo.getPointerInfo();
        return info == null ? new Point(0, 0) : info.getLocation();
    }

    private void showSettingsDialog() {
        stopMonitoring();
        JDialog dialog = new JDialog(frame, "Nastavení času", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(delayMinutes, 1, 10, 1));
        JButton saveButton = new JButton("Ulož");

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(new EmptyBorder(16, 16, 16, 16));
        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT));
        line.add(new JLabel("Čas odpočtu (minuty):"));
        line.add(spinner);
        content.add(line, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(saveButton);
        content.add(actions, BorderLayout.SOUTH);

        saveButton.addActionListener(e -> {
            int newDelay = (Integer) spinner.getValue();
            try {
                saveDelayMinutes(newDelay);
                delayMinutes = newDelay;
                remainingSeconds = delayMinutes * 60;
                lastMousePosition = currentMousePosition();
                refreshUi();
                dialog.dispose();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dialog,
                        "Soubor delay.properties nelze uložit:\n" + ex.getMessage(),
                        "Chyba při ukládání", JOptionPane.ERROR_MESSAGE);
            }
        });

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void refreshUi() {
        int value = Math.max(0, remainingSeconds);
        int minutes = value / 60;
        int seconds = value % 60;
        String state = running ? "Odpočet běží" : "Zastaveno";

        SwingUtilities.invokeLater(() -> {
            countdownLabel.setText(String.format("%02d:%02d", minutes, seconds));
            stateLabel.setText(state);
        });
    }

    private void updateButtonState() {
        SwingUtilities.invokeLater(() -> {
            startButton.setEnabled(!running);
            stopButton.setEnabled(running);
        });
    }

    private int loadDelayMinutes() {
        Path file = propertiesPath();
        if (!Files.isRegularFile(file)) {
            return DEFAULT_DELAY_MINUTES;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            int value = Integer.parseInt(properties.getProperty(PROPERTY_NAME,
                    Integer.toString(DEFAULT_DELAY_MINUTES)).trim());
            return Math.max(1, Math.min(10, value));
        } catch (IOException | NumberFormatException ex) {
            return DEFAULT_DELAY_MINUTES;
        }
    }

    private void saveDelayMinutes(int minutes) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(PROPERTY_NAME, Integer.toString(minutes));
        try (OutputStream output = Files.newOutputStream(propertiesPath())) {
            properties.store(output, "Keep The Mouse Moving settings");
        }
    }

    private static Path propertiesPath() {
        return applicationDirectory().resolve("delay.properties");
    }

    private static Path applicationDirectory() {
        String packagedAppDir = System.getProperty("app.dir");
        if (packagedAppDir != null && !packagedAppDir.isBlank()) {
            Path appDir = Paths.get(packagedAppDir).toAbsolutePath();
            Path launcherDir = appDir.getParent();
            return launcherDir != null ? launcherDir : appDir;
        }
        try {
            Path location = Paths.get(KeepTheMouseMovingApp.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath();
            return Files.isRegularFile(location) ? location.getParent() : location;
        } catch (Exception ex) {
            return Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        }
    }
}
