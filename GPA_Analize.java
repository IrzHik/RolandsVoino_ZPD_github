import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;

public class GPA_Analize extends JFrame {
    private int confidence, brightnessThreshold = 75, boundaryThreshold = 120;
    private static final Color THEME_COLOR = new Color(55, 120, 160);
    private JComboBox<String> resultSelector, crosswalkSelector;
    private String currentResultDir;
    private JPanel analysisBox, pixelAnalysisBlock;
    private static final int PANEL_WIDTH = 320, PANEL_HEIGHT = 200;
    private static final int ANALYSIS_PANEL_WIDTH = 400, ANALYSIS_PANEL_HEIGHT = 160;
    private BufferedImage currentAnalysisImage;
    private int[][] reducedMatrix;  // Add this field
    private JLabel pixelAnalysisImageLabel; // Add this field
    private JLabel[] pixelAnalysisInfoLabels; // Add this field
    private int originalPixelCount = 0;
    private int reducedPixelCount = 0;
    private boolean useReducedView = true;
    private boolean use4PixelRule = true;
    private JLabel originalCountLabel, reducedCountLabel;
    private JButton pixelRuleToggle;
    private JLabel moduleAnalysisImageLabel;
    private JLabel[] moduleAnalysisInfoLabels;
    private double moduleValue1 = 0.0;
    private double moduleValue2 = 0.0;
    private double moduleValue3 = 0.0;
    private double moduleValue4 = 0.0;
    private int moduleThreshold = 69;
    private double moduleSliderValue = 0.0;
    private JPanel moduleAnalysisBlock;
    private double moduleWhitePixels = 0;
    private double moduleBlackPixels = 0;
    private int[][] moduleReducedMatrix;
    private int groupCount = 1;
    private JLabel groupCountLabel;
    private JTable groupAnalysisTable;
    private JLabel groupAnalysisImageLabel;
    private final String[] columnNames = {"Krāsa", "Grupas nr.", "Slieksnis", "Pikseļu skaits", "Sadalījums"};
    private Object[][] tableData = new Object[17][5];
    private JLabel peakCountLabel; // Add this field
    private JLabel peakThresholdLabel;
    private JSlider peakThresholdSlider;
    private int peakThreshold = 0;
    private JPanel fifthAnalysisBlock;
    private JLabel fifthAnalysisImageLabel;
    private JPanel blackPixelPanel, whitePixelPanel;
    private JLabel blackValueLabel, whiteValueLabel;
    private JLabel contrastLabel, relativeContrastLabel;
    private JPanel whiteClusterAnalysisBlock;
    private JLabel whiteClusterImageLabel;
    private JLabel[] whiteClusterInfoLabels;
    private double whiteClusterValue1 = 0.0;
    private double whiteClusterValue2 = 0.0;
    private double whiteClusterValue3 = 0.0;
    private double whiteClusterValue4 = 0.0;
    private int whiteClusterThreshold = 180;
    private double whiteClusterSliderValue = 0.0;
    private JLabel clusterPercentageLabel;  // Add this field
    private JComboBox<String> calibrationSelector;
    private List<CalibrationValues> calibrationsList = new ArrayList<>();
    private int currentCalibrationIndex = 0;
    private JButton saveButton; // Add this field
    private int contrastThreshold = 75;  // Add this line
    private boolean isCustomSettings = false; // Add this line
    private boolean isCustomCalibration = false; // Add this field

    public GPA_Analize() {
        setupWindow();
        updateSelectors();
    }

    private void updateSelectors() {
        File resultsDir = new File("Results");
        resultSelector.removeAllItems();
        
        if (resultsDir.exists() && resultsDir.isDirectory()) {
            File[] files = resultsDir.listFiles(file -> 
                file.isDirectory() && file.getName().startsWith("Results_"));
            if (files != null && files.length > 0) {
                java.util.Arrays.sort(files);
                for (File f : files) {
                    // Extract the part after "Results_"
                    String displayName = f.getName().substring("Results_".length());
                    resultSelector.addItem(displayName);
                }
                resultSelector.setSelectedIndex(0);
                updateCrosswalkSelector();
            }
        }
    }

    private void updateCrosswalkSelector() {
        crosswalkSelector.removeAllItems();
        
        String selectedResult = (String) resultSelector.getSelectedItem();
        if (selectedResult != null) {
            // Add "Results_" prefix back for directory lookup
            currentResultDir = "Results_" + selectedResult;
            File resultDir = new File("Results", currentResultDir);
            java.util.List<String> items = searchRecursively(resultDir, ".txt").stream()
                .map(path -> path.substring(path.lastIndexOf(File.separator) + 1))
                .map(filename -> filename.replace(".txt", ""))
                .sorted((a, b) -> Integer.compare(
                    Integer.parseInt(a),
                    Integer.parseInt(b)))
                .collect(java.util.stream.Collectors.toList());
            
            if (!items.isEmpty()) {
                items.forEach(crosswalkSelector::addItem);
                crosswalkSelector.setSelectedIndex(0);
                loadSelectedData();
            }
        }
    }

    private java.util.List<String> searchRecursively(File dir, String extension) {
        java.util.List<String> results = new java.util.ArrayList<>();
        if (dir.exists() && dir.isDirectory()) {
            for (File file : dir.listFiles()) {
                if (file.isFile() && file.getName().endsWith(extension)) {
                    results.add(file.getPath());
                } else if (file.isDirectory()) {
                    results.addAll(searchRecursively(file, extension));
                }
            }
        }
        return results;
    }

    private void loadSelectedData() {
        analysisBox.removeAll();
        String selectedNum = (String) crosswalkSelector.getSelectedItem();
        if (selectedNum != null && currentResultDir != null) {
            String baseFolder = String.format("Results/%s", currentResultDir);
            updateAnalysisBox(baseFolder, selectedNum);
            loadConfidence(baseFolder + "/" + selectedNum + "." + "/" + selectedNum + ".txt");
            updatePixelAnalysisBox(baseFolder, selectedNum);
            // Add this line to trigger initial analysis
            if (pixelAnalysisImageLabel != null && pixelAnalysisInfoLabels != null) {
                updatePixelAnalysisDisplay(currentAnalysisImage.getWidth(), currentAnalysisImage.getHeight());
            }
            updateModuleAnalysisBox(baseFolder, selectedNum);
            updateWhiteClusterAnalysisBox(baseFolder, selectedNum);  // Add this line
            
            // Trigger module analysis update
            if (moduleReducedMatrix != null) {
                int width = moduleReducedMatrix[0].length;
                int height = moduleReducedMatrix.length;
                updateModuleAnalysisDisplay(width, height);
                updateModuleValues();
            }
            updateGroupAnalysisBox(baseFolder, selectedNum);
            updateFifthAnalysisBox(baseFolder, selectedNum);  // Add this line
        }
        analysisBox.revalidate();
        analysisBox.repaint();
    }

    private void loadConfidence(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            confidence = Integer.parseInt(reader.readLine().split(" ")[0]);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Kļūda: " + e.getMessage());
        }
    }

    private void setupWindow() {
        setTitle("Gājēju pārejas apzīmējumu analīze");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        JPanel titleBar = new JPanel();
        titleBar.setBackground(THEME_COLOR);
        titleBar.setLayout(new FlowLayout(FlowLayout.LEFT, 20, 10));
        
        resultSelector = new JComboBox<>();
        resultSelector.setPreferredSize(new Dimension(200, 25));
        resultSelector.addActionListener(e -> {
            if (e.getActionCommand().equals("comboBoxChanged")) {
                updateCrosswalkSelector();
            }
        });
        
        crosswalkSelector = new JComboBox<>();
        crosswalkSelector.setPreferredSize(new Dimension(100, 25));
        crosswalkSelector.addActionListener(e -> {
            if (e.getActionCommand().equals("comboBoxChanged")) {
                loadSelectedData();
            }
        });
        
        titleBar.add(new JLabel("Attēls:"){{
            setForeground(Color.WHITE);
            setFont(new Font("Segoe UI", Font.BOLD, 14));
        }});
        titleBar.add(resultSelector);
        
        titleBar.add(new JLabel("GPA:"){{
            setForeground(Color.WHITE);
            setFont(new Font("Segoe UI", Font.BOLD, 14));
        }});
        titleBar.add(crosswalkSelector);
        addCalibrationSelector(titleBar); // Add this line
        
        // Add save button
        saveButton = new JButton("Saglabāt datus");
        saveButton.setPreferredSize(new Dimension(200, 25));
        saveButton.addActionListener(e -> saveAnalysis());
        titleBar.add(saveButton);
        
        analysisBox = new JPanel();
        analysisBox.setLayout(new BoxLayout(analysisBox, BoxLayout.Y_AXIS));
        analysisBox.setBackground(Color.WHITE);
        
        JPanel contentWrapper = new JPanel();
        contentWrapper.setLayout(new BoxLayout(contentWrapper, BoxLayout.Y_AXIS));
        contentWrapper.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        contentWrapper.add(analysisBox);
        
        JScrollPane scrollPane = new JScrollPane(contentWrapper);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);  // Make scrolling faster
        add(scrollPane, BorderLayout.CENTER);
        
        add(titleBar, BorderLayout.NORTH);
        setLocationRelativeTo(null);
    }

    private Image scaleImage(Image img, int targetWidth, int targetHeight) {
        if (img == null) return null;
        
        int originalWidth = img.getWidth(null);
        int originalHeight = img.getHeight(null);
        
        double scaleW = (double)targetWidth / originalWidth;
        double scaleH = (double)targetHeight / originalHeight;
        double scale = Math.min(scaleW, scaleH);
        
        int newWidth = (int)(originalWidth * scale);
        int newHeight = (int)(originalHeight * scale);
        
        return img.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
    }

    private Dimension getScaledDimension(int originalWidth, int originalHeight, int targetWidth, int targetHeight) {
        double aspectRatio = (double) originalWidth / originalHeight;
        
        int scaledWidth = targetWidth;
        int scaledHeight = (int) (targetWidth / aspectRatio);
        
        if (scaledHeight > targetHeight) {
            scaledHeight = targetHeight;
            scaledWidth = (int) (targetHeight * aspectRatio);
        }
        
        return new Dimension(scaledWidth, scaledHeight);
    }

    private void updateAnalysisBox(String baseFolder, String crosswalkNum) {
        analysisBox.removeAll();

        JPanel mainBlock = new JPanel(new GridLayout(1, 3, 20, 0));
        mainBlock.setBackground(new Color(245, 245, 245));
        mainBlock.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY, 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10) // Reduced padding from 15
        ));

        // Original image panel with custom scaling
        JPanel originalPanel = new JPanel();
        originalPanel.setOpaque(false);
        originalPanel.setLayout(new BoxLayout(originalPanel, BoxLayout.Y_AXIS));
        JLabel originalLabel = new JLabel();
        originalLabel.setHorizontalAlignment(JLabel.CENTER);
        try {
            ImageIcon originalImg = new ImageIcon(baseFolder + "/segmented_" + currentResultDir.substring(8) + ".png");
            Image img = originalImg.getImage();
            Dimension scaledDim = getScaledDimension(
                img.getWidth(null), 
                img.getHeight(null), 
                PANEL_WIDTH, 
                PANEL_HEIGHT
            );
            Image scaled = img.getScaledInstance(
                scaledDim.width, 
                scaledDim.height, 
                Image.SCALE_SMOOTH
            );
            originalLabel.setIcon(new ImageIcon(scaled));
        } catch (Exception e) {
            originalLabel.setText("Nav attēla");
        }
        originalPanel.add(Box.createVerticalGlue());
        originalPanel.add(originalLabel);
        originalPanel.add(Box.createVerticalGlue());
        mainBlock.add(originalPanel);

        // Confidence section - transparent background
        JPanel confidencePanel = new JPanel();
        confidencePanel.setOpaque(false);
        confidencePanel.setLayout(new BoxLayout(confidencePanel, BoxLayout.Y_AXIS));
        
        // First row - Confidence in one line
        JPanel confidenceRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        confidenceRow.setOpaque(false);
        JLabel confidenceLabel = new JLabel("Uzticība: " + confidence + "%");
        confidenceLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        confidenceLabel.setForeground(THEME_COLOR);
        confidenceRow.add(confidenceLabel);
        
        // Second row - Pixel counts
        JPanel pixelCountRow = new JPanel();
        pixelCountRow.setLayout(new BoxLayout(pixelCountRow, BoxLayout.Y_AXIS));
        pixelCountRow.setOpaque(false);
        
        originalCountLabel = new JLabel("Oriģināls attēls: 0 px");
        reducedCountLabel = new JLabel("Reducēts attēls: 0 px");
        originalCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        reducedCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        originalCountLabel.setForeground(THEME_COLOR);
        reducedCountLabel.setForeground(THEME_COLOR);
        originalCountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        reducedCountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        pixelCountRow.add(originalCountLabel);
        pixelCountRow.add(Box.createVerticalStrut(5));
        pixelCountRow.add(reducedCountLabel);
        
        confidencePanel.add(Box.createVerticalGlue());
        confidencePanel.add(confidenceRow);
        confidencePanel.add(Box.createVerticalStrut(10));
        confidencePanel.add(pixelCountRow);
        confidencePanel.add(Box.createVerticalGlue());
        mainBlock.add(confidencePanel);

        // Modify crosswalk image section
        JPanel crosswalkPanel = new JPanel();
        crosswalkPanel.setLayout(new BoxLayout(crosswalkPanel, BoxLayout.Y_AXIS));
        crosswalkPanel.setOpaque(false);

        JLabel crosswalkLabel = new JLabel();
        crosswalkLabel.setHorizontalAlignment(JLabel.CENTER);
        crosswalkLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Add threshold value label
        JLabel thresholdLabel = new JLabel("Redukcijas slieksnis: " + boundaryThreshold);
        thresholdLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        thresholdLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        // Add slider for boundary detection with shorter width
        JSlider boundarySlider = new JSlider(0, 255, boundaryThreshold);
        boundarySlider.setPreferredSize(new Dimension(PANEL_WIDTH - 140, 20)); // Made shorter to accommodate buttons
        boundarySlider.setMaximumSize(new Dimension(PANEL_WIDTH - 140, 20));
        boundarySlider.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JPanel sliderPanel = createSliderWithButtons(boundarySlider, thresholdLabel, "Redukcijas slieksnis");

        try {
            String imagePath = baseFolder + "/" + crosswalkNum + "." + "/" + crosswalkNum + ".png";
            BufferedImage originalImage = ImageIO.read(new File(imagePath));
            
            int width = originalImage.getWidth();
            int height = originalImage.getHeight();
            int[][] imageData = new int[height][width];
            
            // Convert image to array once
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = originalImage.getRGB(x, y);
                    int alpha = (pixel >> 24) & 0xff;
                    if (alpha < 128) {
                        imageData[y][x] = -1;
                    } else {
                        Color color = new Color(pixel, true);
                        imageData[y][x] = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                    }
                }
            }

            // Count original non-transparent pixels
            originalPixelCount = countNonTransparentPixels(imageData);
            
            // Function to update image with new boundary
            Runnable updateBoundary = () -> {
                try {
                    BufferedImage imageWithBoundary = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2d = imageWithBoundary.createGraphics();
                    g2d.drawImage(originalImage, 0, 0, null);
                    
                    BoundaryPoints bounds = findBoundaryPoints(imageData, boundarySlider.getValue());
                    if (bounds.top != null && bounds.right != null && 
                        bounds.bottom != null && bounds.left != null) {
                        g2d.setColor(Color.RED);
                        g2d.setStroke(new BasicStroke(1));
                        g2d.drawLine(bounds.top.x, bounds.top.y, bounds.right.x, bounds.right.y);
                        g2d.drawLine(bounds.right.x, bounds.right.y, bounds.bottom.x, bounds.bottom.y);
                        g2d.drawLine(bounds.bottom.x, bounds.bottom.y, bounds.left.x, bounds.left.y);
                        g2d.drawLine(bounds.left.x, bounds.left.y, bounds.top.x, bounds.top.y);
                        
                        // Update all analysis blocks when boundary changes
                        updateReducedMatrix(imageData, bounds);
                        updatePixelAnalysisDisplay(width, height);
                        updateModuleAnalysisDisplay(width, height);
                        updateWhiteClusterAnalysis();
                        updateGroupAnalysis();
                        updateFifthAnalysisDisplay(width, height); // Add this line
                    }
                    g2d.dispose();
                    
                    Image scaled = scaleImage(imageWithBoundary, PANEL_WIDTH, PANEL_HEIGHT);
                    crosswalkLabel.setIcon(new ImageIcon(scaled));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };

            // Initial boundary drawing
            updateBoundary.run();

            // Add slider change listener
            boundarySlider.addChangeListener(e -> {
                boundaryThreshold = boundarySlider.getValue();
                thresholdLabel.setText("Redukcijas slieksnis: " + boundaryThreshold);
                updateBoundary.run();
                setCustomCalibration(); // Add this line
            });

            // After updating reducedMatrix
            reducedPixelCount = countNonTransparentPixels(reducedMatrix);

            // In updateAnalysisBox method, update the toggle button section:
            pixelRuleToggle = new JButton("Redukcija: " + (use4PixelRule ? "Ieslēgts" : "Izslēgts"));
            pixelRuleToggle.setAlignmentX(Component.CENTER_ALIGNMENT);
            pixelRuleToggle.addActionListener(e -> {
                use4PixelRule = !use4PixelRule;
                pixelRuleToggle.setText("Redukcija: " + (use4PixelRule ? "Ieslēgts" : "Izslēgts"));
                
                // Re-run boundary detection with current settings
                BoundaryPoints bounds = findBoundaryPoints(imageData, boundarySlider.getValue());
                updateReducedMatrix(imageData, bounds);
                
                // Update all analysis blocks
                updatePixelAnalysisDisplay(width, height);
                updateModuleAnalysisDisplay(width, height);
                updateWhiteClusterAnalysis();
                updateGroupAnalysis();
                updateFifthAnalysisDisplay(width, height); // Add this line
                updateBoundary.run();
                setCustomCalibration(); // Add this line
            });

        } catch (Exception e) {
            crosswalkLabel.setText("Nav attēla");
        }

        crosswalkPanel.add(crosswalkLabel);
        crosswalkPanel.add(Box.createVerticalStrut(5));
        crosswalkPanel.add(thresholdLabel);
        crosswalkPanel.add(sliderPanel); // Add the panel instead of just the slider

        crosswalkPanel.add(Box.createVerticalStrut(5));
        crosswalkPanel.add(pixelRuleToggle);

        mainBlock.add(crosswalkPanel);

        analysisBox.add(mainBlock);
    }

    private class PixelCounts {
        int white, black;
        PixelCounts(int w, int b) { white = w; black = b; }
    }

    private PixelCounts countPixels(BufferedImage image) {
        int white = 0, black = 0;
        int width = image.getWidth();
        int height = image.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xff;
                if (alpha < 128) continue; // Skip transparent pixels
                
                Color color = new Color(pixel, true);
                int brightness = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                if (brightness > brightnessThreshold) {
                    white++;
                } else {
                    black++;
                }
            }
        }
        return new PixelCounts(white, black);
    }

    private BufferedImage createBinaryImage(int[][] data, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (data[y][x] == -1) {
                    // Transparent pixel
                    image.setRGB(x, y, new Color(0, 0, 0, 0).getRGB());
                } else {
                    // Binary black or white based on threshold
                    int value = data[y][x] > brightnessThreshold ? 255 : 0;
                    image.setRGB(x, y, new Color(value, value, value).getRGB());
                }
            }
        }
        return image;
    }

    private class BoundaryPoints {
        Point top, right, bottom, left;
        BoundaryPoints(Point t, Point r, Point b, Point l) {
            top = t; right = r; bottom = b; left = l;
        }
    }

    private BoundaryPoints findBoundaryPoints(int[][] imageData) {
        int width = imageData[0].length;
        int height = imageData.length;
        Point top = null, right = null, bottom = null, left = null;

        // Find top point
        topLoop: for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (imageData[y][x] != -1 && imageData[y][x] > 120) {
                    top = new Point(x, y);
                    break topLoop;
                }
            }
        }

        // Find right point
        rightLoop: for (int x = width - 1; x >= 0; x--) {
            for (int y = 0; y < height; y++) {
                if (imageData[y][x] != -1 && imageData[y][x] > 120) {
                    right = new Point(x, y);
                    break rightLoop;
                }
            }
        }

        // Find bottom point
        bottomLoop: for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                if (imageData[y][x] != -1 && imageData[y][x] > 120) {
                    bottom = new Point(x, y);
                    break bottomLoop;
                }
            }
        }

        // Find left point
        leftLoop: for (int x = 0; x < width; x++) {
            for (int y = height - 1; y >= 0; y--) {
                if (imageData[y][x] != -1 && imageData[y][x] > 120) {
                    left = new Point(x, y);
                    break leftLoop;
                }
            }
        }

        return new BoundaryPoints(top, right, bottom, left);
    }

    private BoundaryPoints findBoundaryPoints(int[][] imageData, int threshold) {
        int width = imageData[0].length;
        int height = imageData.length;
        Point top = null, right = null, bottom = null, left = null;

        // Find top point
        topLoop: for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (imageData[y][x] != -1 && imageData[y][x] > threshold) {
                    top = new Point(x, y);
                    break topLoop;
                }
            }
        }

        // Find right point
        rightLoop: for (int x = width - 1; x >= 0; x--) {
            for (int y = 0; y < height; y++) {
                if (imageData[y][x] != -1 && imageData[y][x] > threshold) {
                    right = new Point(x, y);
                    break rightLoop;
                }
            }
        }

        // Find bottom point
        bottomLoop: for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                if (imageData[y][x] != -1 && imageData[y][x] > threshold) {
                    bottom = new Point(x, y);
                    break bottomLoop;
                }
            }
        }

        // Find left point
        leftLoop: for (int x = 0; x < width; x++) {
            for (int y = height - 1; y >= 0; y--) {
                if (imageData[y][x] != -1 && imageData[y][x] > threshold) {
                    left = new Point(x, y);
                    break leftLoop;
                }
            }
        }

        // After finding all boundary points, update the reduced matrix
        if (top != null && right != null && bottom != null && left != null) {
            updateReducedMatrix(imageData, new BoundaryPoints(top, right, bottom, left));
        }

        return new BoundaryPoints(top, right, bottom, left);
    }

    private BufferedImage createBoundaryImage(int[][] imageData, BoundaryPoints bounds) {
        int width = imageData[0].length;
        int height = imageData.length;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Draw original image
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (imageData[y][x] == -1) {
                    image.setRGB(x, y, new Color(0, 0, 0, 0).getRGB());
                } else {
                    int value = imageData[y][x];
                    image.setRGB(x, y, new Color(value, value, value).getRGB());
                }
            }
        }

        // Draw boundary lines in red
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.RED);
        g2d.setStroke(new BasicStroke(1));

        if (bounds.top != null && bounds.right != null && 
            bounds.bottom != null && bounds.left != null) {
            // Draw lines
            g2d.drawLine(bounds.top.x, bounds.top.y, bounds.right.x, bounds.right.y);
            g2d.drawLine(bounds.right.x, bounds.right.y, bounds.bottom.x, bounds.bottom.y);
            g2d.drawLine(bounds.bottom.x, bounds.bottom.y, bounds.left.x, bounds.left.y);
            g2d.drawLine(bounds.left.x, bounds.left.y, bounds.top.x, bounds.top.y);

            // Draw points
            int pointSize = 3;
            g2d.fillOval(bounds.top.x - pointSize/2, bounds.top.y - pointSize/2, pointSize, pointSize);
            g2d.fillOval(bounds.right.x - pointSize/2, bounds.right.y - pointSize/2, pointSize, pointSize);
            g2d.fillOval(bounds.bottom.x - pointSize/2, bounds.bottom.y - pointSize/2, pointSize, pointSize);
            g2d.fillOval(bounds.left.x - pointSize/2, bounds.left.y - pointSize/2, pointSize, pointSize);
        }

        g2d.dispose();
        return image;
    }

    private void updatePixelAnalysisBox(String baseFolder, String crosswalkNum) {
        pixelAnalysisBlock = new JPanel();
        pixelAnalysisBlock.setLayout(new BoxLayout(pixelAnalysisBlock, BoxLayout.Y_AXIS));

        JPanel analysisBlock = new JPanel(new GridLayout(1, 2, 10, 0));
        analysisBlock.setBackground(new Color(245, 245, 245));
        analysisBlock.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY, 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        // Left side - Generated image panel with title
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);

        // Image panel with padding and background
        JPanel imageWrapper = new JPanel(new BorderLayout());
        imageWrapper.setOpaque(false);
        
        // Background panel for the image
        JPanel imageBackground = new JPanel(new BorderLayout());
        imageBackground.setBackground(THEME_COLOR);
        imageBackground.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        
        JLabel imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageBackground.add(imageLabel, BorderLayout.CENTER);
        imageWrapper.add(imageBackground, BorderLayout.CENTER);

        leftPanel.add(Box.createVerticalStrut(5));
        leftPanel.add(imageWrapper);
        leftPanel.add(Box.createVerticalStrut(5));

        // Right side - Stats and controls
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setOpaque(false);

        try {
            String imagePath = baseFolder + "/" + crosswalkNum + "." + "/" + crosswalkNum + ".png";
            currentAnalysisImage = ImageIO.read(new File(imagePath));
            
            // Convert image to array data with transparency support
            int width = currentAnalysisImage.getWidth();
            int height = currentAnalysisImage.getHeight();
            int[][] imageData = new int[height][width];
            
            // First populate imageData
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = currentAnalysisImage.getRGB(x, y);
                    int alpha = (pixel >> 24) & 0xff;
                    if (alpha < 128) {
                        imageData[y][x] = -1;
                    } else {
                        Color color = new Color(pixel, true);
                        imageData[y][x] = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                    }
                }
            }

            // Find boundary points and update reducedMatrix
            BoundaryPoints bounds = findBoundaryPoints(imageData);
            updateReducedMatrix(imageData, bounds);

            // Create binary image instead of boundary image for initial display
            BufferedImage initialBinaryImage = createBinaryImage(reducedMatrix, width, height);
            Dimension scaledDim = getScaledDimension(width, height, ANALYSIS_PANEL_WIDTH / 3, ANALYSIS_PANEL_HEIGHT);
            Image scaled = initialBinaryImage.getScaledInstance(scaledDim.width, scaledDim.height, Image.SCALE_SMOOTH);
            imageLabel.setIcon(new ImageIcon(scaled));

            // Stats panel with consistent styling
            JPanel statsPanel = new JPanel();
            statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
            statsPanel.setOpaque(false);
            statsPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

            PixelCounts counts = countMatrixPixels(reducedMatrix);
            
            // Create labels with consistent style
            JLabel[] infoLabels = {
                new JLabel("Baltie pikseļi: " + counts.white),
                new JLabel("Melnie pikseļi: " + counts.black),
                new JLabel("Balts-Melns slieksnis: " + brightnessThreshold)
            };

            Font labelFont = new Font("Segoe UI", Font.PLAIN, 14);
            for (JLabel label : infoLabels) {
                label.setFont(labelFont);
                label.setAlignmentX(Component.CENTER_ALIGNMENT);
                statsPanel.add(label);
                statsPanel.add(Box.createVerticalStrut(5));
            }

            // Slider panel
            JPanel sliderPanel = new JPanel();
            sliderPanel.setOpaque(false);
            sliderPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
            
            JSlider thresholdSlider = new JSlider(0, 255, brightnessThreshold);
            thresholdSlider.setPreferredSize(new Dimension(160, 20)); // Made shorter to accommodate buttons
            thresholdSlider.setBackground(new Color(245, 245, 245));
            
            JPanel sliderWithButtons = createSliderWithButtons(thresholdSlider, infoLabels[2], "Balts-Melns slieksnis");
            sliderPanel.add(sliderWithButtons);

            thresholdSlider.addChangeListener(e -> {
                brightnessThreshold = thresholdSlider.getValue();
                infoLabels[2].setText("Balts-Melns slieksnis: " + brightnessThreshold);
                BufferedImage newImage = createBinaryImage(reducedMatrix, width, height);
                Image newScaled = newImage.getScaledInstance(scaledDim.width, scaledDim.height, Image.SCALE_SMOOTH);
                imageLabel.setIcon(new ImageIcon(newScaled));
                
                PixelCounts newCounts = countMatrixPixels(reducedMatrix);
                infoLabels[0].setText("Balti:Melni pikseļi: " + newCounts.white + " : " + newCounts.black);
                double percentage = Math.ceil((double) newCounts.white / (newCounts.white + newCounts.black) * 100 * 100) / 100.0;
                infoLabels[1].setText("Baltu pikseļu rel. daudzums: " + percentage + "%");
                setCustomCalibration(); // Add this line
            });

            // Add title before stats
            JLabel titleLabel = new JLabel("GPA strīpu garuma kļūdas");
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
            titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            titleLabel.setForeground(THEME_COLOR);
            
            rightPanel.add(Box.createVerticalGlue());
            rightPanel.add(titleLabel);
            rightPanel.add(Box.createVerticalStrut(10));
            rightPanel.add(statsPanel);
            rightPanel.add(Box.createVerticalStrut(10));
            rightPanel.add(sliderPanel);
            rightPanel.add(Box.createVerticalGlue());

            // Store the label reference
            pixelAnalysisImageLabel = imageLabel;
            pixelAnalysisInfoLabels = infoLabels;

            // Add this to trigger initial analysis
            updatePixelAnalysisDisplay(width, height);

        } catch (Exception e) {
            imageLabel.setText("Nav attēla");
        }

        analysisBlock.add(leftPanel);
        analysisBlock.add(rightPanel);

        pixelAnalysisBlock.add(analysisBlock);
        analysisBox.add(Box.createVerticalStrut(20));
        analysisBox.add(pixelAnalysisBlock);
    }

    private int[][] createBoundedArray(int[][] originalData, BoundaryPoints bounds) {
        int width = originalData[0].length;
        int height = originalData.length;
        int[][] boundedData = new int[height][width];
        
        // Initialize all values to -1
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boundedData[y][x] = -1;
            }
        }
        
        // Only proceed if we have valid boundary points
        if (bounds.top == null || bounds.right == null || 
            bounds.bottom == null || bounds.left == null) {
            return boundedData;
        }

        // Create line equations for each boundary
        // For each pixel, check if it's inside all four lines
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isInsideBoundary(x, y, bounds)) {
                    boundedData[y][x] = originalData[y][x];
                }
            }
        }
        
        return boundedData;
    }

    private boolean isInsideBoundary(int x, int y, BoundaryPoints bounds) {
        // Check if point is on the correct side of all four lines
        return isOnCorrectSide(x, y, bounds.top, bounds.right, true) &&
               isOnCorrectSide(x, y, bounds.right, bounds.bottom, true) &&
               isOnCorrectSide(x, y, bounds.bottom, bounds.left, true) &&
               isOnCorrectSide(x, y, bounds.left, bounds.top, true);
    }

    private boolean isOnCorrectSide(int x, int y, Point p1, Point p2, boolean positive) {
        // Calculate which side of the line the point is on using cross product
        int crossProduct = (p2.x - p1.x) * (y - p1.y) - (p2.y - p1.y) * (x - p1.x);
        return positive ? crossProduct >= 0 : crossProduct <= 0;
    }

    private JPanel createSliderWithButtons(JSlider slider, JLabel label, String prefix) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Create buttons with fixed size
        JButton minusBtn = new JButton("-");
        JButton plusBtn = new JButton("+");
        Dimension btnSize = new Dimension(25, 25);
        minusBtn.setPreferredSize(btnSize);
        plusBtn.setPreferredSize(btnSize);
        minusBtn.setMinimumSize(btnSize);
        plusBtn.setMinimumSize(btnSize);
        minusBtn.setMaximumSize(btnSize);
        plusBtn.setMaximumSize(btnSize);
        
        // Style the buttons
        Font buttonFont = new Font("Arial", Font.BOLD, 14);
        minusBtn.setFont(buttonFont);
        plusBtn.setFont(buttonFont);
        minusBtn.setMargin(new Insets(0, 0, 0, 0));
        plusBtn.setMargin(new Insets(0, 0, 0, 0));

        // Add button actions
        minusBtn.addActionListener(e -> {
            int val = slider.getValue();
            if (val > slider.getMinimum()) {
                if (prefix.contains("filtrācijas slieksnis") || prefix.equals("Izmērs")) {
                    // For percentage sliders, decrease by 0.1%
                    double currentVal = val / 10.0;
                    currentVal = Math.round((currentVal - 0.1) * 10) / 10.0; // Round to 1 decimal
                    slider.setValue((int)(currentVal * 10));
                    label.setText(prefix + ": " + String.format("%.1f%%", currentVal));
                } else if (prefix.contains("grupu skaita slieksnis")) {
                    slider.setValue(val - 1);
                    label.setText(prefix + ": " + (val - 1) + "%");
                } else {
                    slider.setValue(val - 1);
                    label.setText(prefix + ": " + (val - 1));
                }
            }
            setCustomCalibration(); // Add this line
        });
        
        plusBtn.addActionListener(e -> {
            int val = slider.getValue();
            if (val < slider.getMaximum()) {
                if (prefix.contains("filtrācijas slieksnis") || prefix.equals("Izmērs")) {
                    // For percentage sliders, increase by 0.1%
                    double currentVal = val / 10.0;
                    currentVal = Math.round((currentVal + 0.1) * 10) / 10.0; // Round to 1 decimal
                    slider.setValue((int)(currentVal * 10));
                    label.setText(prefix + ": " + String.format("%.1f%%", currentVal));
                } else if (prefix.contains("grupu skaita slieksnis")) {
                    slider.setValue(val + 1);
                    label.setText(prefix + ": " + (val + 1) + "%");
                } else {
                    slider.setValue(val + 1);
                    label.setText(prefix + ": " + (val + 1));
                }
            }
            setCustomCalibration(); // Add this line
        });

        panel.add(minusBtn);
        panel.add(Box.createHorizontalStrut(5));
        panel.add(slider);
        panel.add(Box.createHorizontalStrut(5));
        panel.add(plusBtn);

        slider.addChangeListener(e -> {
            setCustomCalibration(); // Add this line
        });

        return panel;
    }

    private void updateReducedMatrix(int[][] imageData, BoundaryPoints bounds) {
        int height = imageData.length;
        int width = imageData[0].length;
        reducedMatrix = new int[height][width];

        if (use4PixelRule) {
            // Use 4-point boundary algorithm
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (isInsideBoundary(x, y, bounds)) {
                        reducedMatrix[y][x] = imageData[y][x];
                    } else {
                        reducedMatrix[y][x] = -1;
                    }
                }
            }
        } else {
            // Just copy the original matrix without reduction
            for (int y = 0; y < height; y++) {
                System.arraycopy(imageData[y], 0, reducedMatrix[y], 0, width);
            }
        }

        // Update pixel counts
        originalPixelCount = countNonTransparentPixels(imageData);
        reducedPixelCount = countNonTransparentPixels(reducedMatrix);
        
        // Update labels
        if (originalCountLabel != null && reducedCountLabel != null) {
            originalCountLabel.setText("Oriģināls attēls: " + originalPixelCount + " px");
            reducedCountLabel.setText("Reducēts attēls: " + reducedPixelCount + " px");
        }
    }

    private PixelCounts countMatrixPixels(int[][] matrix) {
        int white = 0, black = 0;
        for (int y = 0; y < matrix.length; y++) {
            for (int x = 0; x < matrix[0].length; x++) {
                if (matrix[y][x] == -1) continue; // Skip transparent pixels
                
                if (matrix[y][x] > brightnessThreshold) {
                    white++;
                } else {
                    black++;
                }
            }
        }
        return new PixelCounts(white, black);
    }

    private void updatePixelAnalysisDisplay(int width, int height) {
        if (pixelAnalysisImageLabel != null && pixelAnalysisInfoLabels != null) {
            // Update image
            BufferedImage newImage = createBinaryImage(reducedMatrix, width, height);
            Dimension scaledDim = getScaledDimension(width, height, ANALYSIS_PANEL_WIDTH / 3, ANALYSIS_PANEL_HEIGHT);
            Image newScaled = newImage.getScaledInstance(scaledDim.width, scaledDim.height, Image.SCALE_SMOOTH);
            pixelAnalysisImageLabel.setIcon(new ImageIcon(newScaled));

            // Update pixel counts
            PixelCounts newCounts = countMatrixPixels(reducedMatrix);
            pixelAnalysisInfoLabels[0].setText("Balti : Melni pikseļi : " + newCounts.white + " : " + newCounts.black);
            double percentage = Math.ceil((double) newCounts.white / (newCounts.white + newCounts.black) * 100 * 100) / 100.0;
            pixelAnalysisInfoLabels[1].setText("Baltu piks. rel. daudzums: " + percentage + "%");
        }
    }

    private int countNonTransparentPixels(int[][] matrix) {
        int count = 0;
        for (int y = 0; y < matrix.length; y++) {
            for (int x = 0; x < matrix[0].length; x++) {
                if (matrix[y][x] != -1) count++;
            }
        }
        return count;
    }

    private void updateModuleAnalysisBox(String baseFolder, String crosswalkNum) {
        moduleAnalysisBlock = new JPanel();
        moduleAnalysisBlock.setLayout(new BoxLayout(moduleAnalysisBlock, BoxLayout.Y_AXIS));

        JPanel analysisBlock = new JPanel(new GridLayout(1, 2, 10, 0));
        analysisBlock.setBackground(new Color(245, 245, 245));
        analysisBlock.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY, 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        // Left side - Same as second block
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);

        JPanel imageWrapper = new JPanel(new BorderLayout());
        imageWrapper.setOpaque(false);
        
        JPanel imageBackground = new JPanel(new BorderLayout());
        imageBackground.setBackground(THEME_COLOR);
        imageBackground.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        
        moduleAnalysisImageLabel = new JLabel();
        moduleAnalysisImageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageBackground.add(moduleAnalysisImageLabel, BorderLayout.CENTER);
        imageWrapper.add(imageBackground, BorderLayout.CENTER);

        leftPanel.add(imageWrapper);

        // Right side - Controls and values
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setOpaque(false);

        // Values panel
        JPanel valuesPanel = new JPanel();
        valuesPanel.setLayout(new BoxLayout(valuesPanel, BoxLayout.Y_AXIS));
        valuesPanel.setOpaque(false);
        valuesPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        moduleAnalysisInfoLabels = new JLabel[] {
            new JLabel("Value 1: " + moduleValue1),
            new JLabel("Value 2: " + moduleValue2),
            new JLabel("Value 3: " + moduleValue3),
            new JLabel("Value 4: " + moduleValue4),
            new JLabel("Threshold: " + moduleThreshold),
            new JLabel("Lielo caurumu filtrācijas slieksnis: " + String.format("%.1f", moduleSliderValue) + "%")
        };

        // Add sliders and reorganize panels
        JSlider thresholdSlider = new JSlider(0, 255, moduleThreshold);
        JLabel thresholdValueLabel = moduleAnalysisInfoLabels[4];
        JPanel thresholdSliderPanel = createSliderWithButtons(thresholdSlider, thresholdValueLabel, "Threshold");

        // Add values panel first
        Font labelFont = new Font("Segoe UI", Font.PLAIN, 14);
        for (int i = 0; i < 4; i++) { // Add only the first 4 value labels
            moduleAnalysisInfoLabels[i].setFont(labelFont);
            moduleAnalysisInfoLabels[i].setAlignmentX(Component.CENTER_ALIGNMENT);
            valuesPanel.add(moduleAnalysisInfoLabels[i]);
            valuesPanel.add(Box.createVerticalStrut(5));
        }

        // Add threshold slider
        thresholdSlider.addChangeListener(e -> {
            moduleThreshold = thresholdSlider.getValue();
            thresholdValueLabel.setText("Threshold: " + moduleThreshold);
            // TODO: Add your processing logic here
            setCustomCalibration(); // Add this line
        });

        // Add fine control slider between the values and second slider
        JSlider fineControlSlider = new JSlider(0, 100, 0); // 0 = 0.0%, 100 = 10.0%
        fineControlSlider.setPreferredSize(new Dimension(160, 20));
        fineControlSlider.setMaximumSize(new Dimension(160, 20));
        
        JLabel fineControlLabel = moduleAnalysisInfoLabels[5];
        fineControlLabel.setFont(labelFont);
        fineControlLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel fineControlPanel = createSliderWithButtons(fineControlSlider, fineControlLabel, "Lielo caurumu filtrācijas slieksnis");

        fineControlSlider.addChangeListener(e -> {
            moduleSliderValue = fineControlSlider.getValue() / 10.0; // Convert to percentage (0.0% - 10.0%)
            fineControlLabel.setText("Lielo caurumu filtrācijas slieksnis: " + String.format("%.1f", moduleSliderValue) + "%");
            
            // Update analysis when slider changes
            if (moduleReducedMatrix != null) {
                int width = moduleReducedMatrix[0].length;
                int height = moduleReducedMatrix.length;
                updateModuleAnalysisDisplay(width, height);
            }
            setCustomCalibration(); // Add this line
        });

        // Modify slider size
        thresholdSlider.setPreferredSize(new Dimension(160, 20));
        thresholdSlider.setMaximumSize(new Dimension(160, 20));
        fineControlSlider.setPreferredSize(new Dimension(160, 20));
        fineControlSlider.setMaximumSize(new Dimension(160, 20));

        // Add title before values
        JLabel titleLabel = new JLabel("Caurumu skaits un platība");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setForeground(THEME_COLOR);
        
        rightPanel.add(Box.createVerticalGlue());
        rightPanel.add(titleLabel);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(valuesPanel);
        rightPanel.add(Box.createVerticalStrut(10));
        
        // Add current threshold value right after Value 4
        JLabel currentThresholdLabel = new JLabel("Balts-Melns slieksnis: " + moduleThreshold);
        currentThresholdLabel.setFont(labelFont);
        currentThresholdLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(currentThresholdLabel);
        rightPanel.add(Box.createVerticalStrut(10));
        
        rightPanel.add(thresholdSliderPanel);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(fineControlLabel);
        rightPanel.add(Box.createVerticalStrut(5));
        rightPanel.add(fineControlPanel);
        rightPanel.add(Box.createVerticalGlue());

        // Update threshold display when slider changes
        thresholdSlider.addChangeListener(e -> {
            moduleThreshold = thresholdSlider.getValue();
            thresholdValueLabel.setText("Threshold: " + moduleThreshold);
            currentThresholdLabel.setText("Balts-Melns slieksnis: " + moduleThreshold);
            
            // Update display when threshold changes
            if (moduleReducedMatrix != null) {
                int width = moduleReducedMatrix[0].length;
                int height = moduleReducedMatrix.length;
                updateModuleAnalysisDisplay(width, height);
                updateModuleValues(); // Add this call
            }
            setCustomCalibration(); // Add this line
        });

        // Initially show the same image as second block and analyze
        if (reducedMatrix != null) {
            int width = reducedMatrix[0].length;
            int height = reducedMatrix.length;
            
            // Create a copy of reducedMatrix for module analysis
            moduleReducedMatrix = new int[height][width];
            for (int y = 0; y < height; y++) {
                System.arraycopy(reducedMatrix[y], 0, moduleReducedMatrix[y], 0, width);
            }

            // Trigger initial analysis
            updateModuleAnalysisDisplay(width, height);
            updateModuleValues();
        }

        analysisBlock.add(leftPanel);
        analysisBlock.add(rightPanel);
        moduleAnalysisBlock.add(analysisBlock);
        analysisBox.add(Box.createVerticalStrut(20));
        analysisBox.add(moduleAnalysisBlock);
    }

    private void updateModuleValues() {
        if (moduleReducedMatrix != null) {
            // Create matrix copy and convert to binary
            int height = moduleReducedMatrix.length;
            int width = moduleReducedMatrix[0].length;
            int[][] matrixCopy = new int[height][width];
            for (int y = 0; y < height; y++) {
                System.arraycopy(moduleReducedMatrix[y], 0, matrixCopy[y], 0, width);
            }
            
            // Convert to binary using moduleThreshold
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (matrixCopy[y][x] != -1) {
                        matrixCopy[y][x] = (matrixCopy[y][x] > moduleThreshold) ? 255 : 0;
                    }
                }
            }

            boolean[][] visited = new boolean[height][width];
            int totalHoleCount = 0;
            int totalHoleArea = 0;
            int validHoleCount = 0;
            int validHoleArea = 0;

            // Calculate minimum hole size as percentage of total pixels
            double minHolePercentage = moduleSliderValue; // Now directly represents percentage
            int minHoleSize = (int)((minHolePercentage / 100.0) * reducedPixelCount);

            // Find and measure holes
            java.util.List<Integer> holeSizes = new java.util.ArrayList<>();
            
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (matrixCopy[y][x] == 0 && !visited[y][x]) {
                        int holeSize = floodFill(matrixCopy, visited, x, y);
                        if (holeSize > 0) {
                            holeSizes.add(holeSize);
                            totalHoleCount++;
                            totalHoleArea += holeSize;
                            
                            // Only count holes that meet the minimum size requirement
                            if (holeSize >= minHoleSize) {
                                validHoleCount++;
                                validHoleArea += holeSize;
                            }
                        }
                    }
                }
            }

            // Calculate percentages and round to 2 decimal places
            double totalHolePercentage = Math.round((totalHoleArea * 100.0 / reducedPixelCount) * 100.0) / 100.0;
            double validHolePercentage = Math.round((validHoleArea * 100.0 / reducedPixelCount) * 100.0) / 100.0;

            moduleValue1 = totalHoleCount;
            moduleValue2 = totalHolePercentage;
            moduleValue3 = validHoleCount;
            moduleValue4 = validHolePercentage;

            // Update labels
            if (moduleAnalysisInfoLabels != null) {
                moduleAnalysisInfoLabels[0].setText("Caurumu skaits: " + (int)moduleValue1);
                moduleAnalysisInfoLabels[1].setText("Caurumu kopējā platība: " + String.format("%.2f%%", moduleValue2));
                moduleAnalysisInfoLabels[2].setText("Lielo caurumu skaits: " + (int)moduleValue3);
                moduleAnalysisInfoLabels[3].setText("Lielo caurumu platība: " + String.format("%.2f%%", moduleValue4));
            }
        }
    }

private int floodFill(int[][] matrix, boolean[][] visited, int startX, int startY) {
    int height = matrix.length;
    int width = matrix[0].length;
    int holeSize = 0;
    boolean isValidHole = true;

    Queue<int[]> queue = new LinkedList<>();
    queue.add(new int[]{startX, startY});
    visited[startY][startX] = true;

    // Check 8 directions including diagonals
    int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
    int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};

    while (!queue.isEmpty() && isValidHole) {
        int[] current = queue.poll();
        int x = current[0];
        int y = current[1];
        holeSize++;

        // Check all 8 neighboring pixels
        for (int i = 0; i < 8; i++) {
            int nx = x + dx[i];
            int ny = y + dy[i];

            // Check if neighbor is outside matrix bounds
            if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                isValidHole = false;
                break;
            }

            // Check if neighbor is transparent (-1)
            if (matrix[ny][nx] == -1) {
                isValidHole = false;
                break;
            }

            // If neighbor is unvisited black pixel, add to queue
            if (matrix[ny][nx] == 0 && !visited[ny][nx]) {
                queue.add(new int[]{nx, ny});
                visited[ny][nx] = true;
            }
        }
    }

    // Clear remaining queue and mark remaining pixels as visited
    while (!queue.isEmpty()) {
        int[] current = queue.poll();
        visited[current[1]][current[0]] = true;
    }

    return isValidHole ? holeSize : 0;
}

    // Modify the updateModuleAnalysisDisplay method
    private void updateModuleAnalysisDisplay(int width, int height) {
        if (moduleAnalysisImageLabel != null && moduleReducedMatrix != null) {
            // Copy current reducedMatrix to moduleReducedMatrix
            for (int y = 0; y < height; y++) {
                System.arraycopy(reducedMatrix[y], 0, moduleReducedMatrix[y], 0, width);
            }
            
            // Update module values first to calculate holes
            updateModuleValues();
            
            // Create new image
            BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            
            // Create visited array for flood fill
            boolean[][] visited = new boolean[height][width];
            
            // Calculate minimum hole size threshold
            double minHolePercentage = moduleSliderValue;
            int minHoleSize = (int)((minHolePercentage / 100.0) * reducedPixelCount);
            
            // First, draw the base binary image
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (moduleReducedMatrix[y][x] == -1) {
                        newImage.setRGB(x, y, new Color(0, 0, 0, 0).getRGB());
                    } else {
                        int value = moduleReducedMatrix[y][x] > moduleThreshold ? 255 : 0;
                        newImage.setRGB(x, y, new Color(value, value, value).getRGB());
                    }
                }
            }
            
            // Then find and color the holes
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (!visited[y][x] && moduleReducedMatrix[y][x] != -1 && 
                        moduleReducedMatrix[y][x] <= moduleThreshold) {
                        // Collect pixels in this hole
                        java.util.List<Point> holePixels = new java.util.ArrayList<>();
                        int holeSize = floodFillWithPixels(moduleReducedMatrix, visited, x, y, holePixels);
                        
                        if (holeSize > 0) {
                            // Color the hole based on its size
                            Color holeColor = holeSize >= minHoleSize ? Color.GREEN : Color.RED;
                            for (Point p : holePixels) {
                                newImage.setRGB(p.x, p.y, holeColor.getRGB());
                            }
                        }
                    }
                }
            }
            
            Dimension scaledDim = getScaledDimension(width, height, ANALYSIS_PANEL_WIDTH / 3, ANALYSIS_PANEL_HEIGHT);
            Image newScaled = newImage.getScaledInstance(scaledDim.width, scaledDim.height, Image.SCALE_SMOOTH);
            moduleAnalysisImageLabel.setIcon(new ImageIcon(newScaled));
        }
    }

    // Add new method for flood fill that collects pixels
    private int floodFillWithPixels(int[][] matrix, boolean[][] visited, int startX, int startY, 
                                   java.util.List<Point> pixels) {
        int height = matrix.length;
        int width = matrix[0].length;
        int holeSize = 0;
        boolean isValidHole = true;

        Queue<Point> queue = new LinkedList<>();
        queue.add(new Point(startX, startY));
        visited[startY][startX] = true;

        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};

        while (!queue.isEmpty() && isValidHole) {
            Point current = queue.poll();
            pixels.add(current);
            holeSize++;

            for (int i = 0; i < 8; i++) {
                int nx = current.x + dx[i];
                int ny = current.y + dy[i];

                // Check bounds first
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                    isValidHole = false;
                    break;
                }

                // Check for transparency
                if (matrix[ny][nx] == -1) {
                    isValidHole = false;
                    break;
                }

                // If unvisited and black pixel, add to queue
                if (!visited[ny][nx] && matrix[ny][nx] <= moduleThreshold) {
                    queue.add(new Point(nx, ny));
                    visited[ny][nx] = true;
                }
            }
        }

        if (!isValidHole) {
            pixels.clear();
            while (!queue.isEmpty()) {
                Point p = queue.poll();
                visited[p.y][p.x] = true;
            }
            return 0;
        }

        return holeSize;
    }

    private void updateGroupAnalysisBox(String baseFolder, String crosswalkNum) {
        JPanel groupAnalysisBlock = new JPanel();
        groupAnalysisBlock.setLayout(new BoxLayout(groupAnalysisBlock, BoxLayout.Y_AXIS));

        JPanel analysisBlock = new JPanel(new GridLayout(1, 2, 10, 0));
        analysisBlock.setBackground(new Color(245, 245, 245));
        analysisBlock.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY, 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        // Right side now becomes left side - Controls and Table
        JPanel leftPanel = createGroupControlPanel();
        
        // Left side now becomes right side - Image
        JPanel rightPanel = createGroupImagePanel();

        analysisBlock.add(rightPanel);
        analysisBlock.add(leftPanel);
        
        groupAnalysisBlock.add(analysisBlock);
        analysisBox.add(Box.createVerticalStrut(20));
        analysisBox.add(groupAnalysisBlock);

        updateGroupAnalysis();
    }

    private void updateGroupAnalysis() {
        if (reducedMatrix == null) return;

        int width = reducedMatrix[0].length;
        int height = reducedMatrix.length;
        
        BufferedImage binaryImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        
        // Calculate group boundaries
        double rangeSize = 256.0 / groupCount;
        int[][] pixelGroups = new int[groupCount][2];
        int[] groupPixelCounts = new int[groupCount];
        
        // Initialize group boundaries
        for (int i = 0; i < groupCount; i++) {
            pixelGroups[i][0] = (int)(i * rangeSize);
            pixelGroups[i][1] = (int)((i + 1) * rangeSize - 1);
            if (i == groupCount - 1) pixelGroups[i][1] = 255;
        }

        // Count pixels and assign colors
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (reducedMatrix[y][x] != -1) {
                    int pixelValue = reducedMatrix[y][x];
                    int groupIndex = (int)(pixelValue / rangeSize);
                    if (groupIndex >= groupCount) groupIndex = groupCount - 1;
                    
                    groupPixelCounts[groupIndex]++;
                    
                    // Calculate color based on group's middle value
                    int middleValue = (pixelGroups[groupIndex][0] + pixelGroups[groupIndex][1]) / 2;
                    Color groupColor = new Color(middleValue, middleValue, middleValue);
                    binaryImage.setRGB(x, y, groupColor.getRGB());
                } else {
                    binaryImage.setRGB(x, y, new Color(0, 0, 0, 0).getRGB());
                }
            }
        }

        // Update table data
        for (int i = 0; i < tableData.length; i++) {
            for (int j = 0; j < tableData[i].length; j++) {
                tableData[i][j] = "";
            }
        }

        // Create enhanced color panel renderer with centered square
        class ColorRenderer extends DefaultTableCellRenderer {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, 
                boolean isSelected, boolean hasFocus, int row, int column) {
                if (value instanceof Color) {
                    JPanel wrapper = new JPanel(new GridBagLayout());
                    wrapper.setBackground(Color.WHITE);
                    
                    JPanel colorSquare = new JPanel();
                    colorSquare.setBackground((Color)value);
                    colorSquare.setPreferredSize(new Dimension(20, 20));
                    colorSquare.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
                    
                    wrapper.add(colorSquare);
                    return wrapper;
                }
                return super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            }
        }

        // Set custom renderer for first column
        groupAnalysisTable.getColumnModel().getColumn(0).setCellRenderer(new ColorRenderer());

        // Calculate total pixel count for percentage calculations
        int totalPixelsInGroups = 0;
        for (int i = 0; i < groupCount; i++) {
            totalPixelsInGroups += groupPixelCounts[i];
        }

        // Update table with new data including accumulative percentages
        for (int i = 0; i < groupCount; i++) {
            int middleValue = (pixelGroups[i][0] + pixelGroups[i][1]) / 2;
            tableData[i][0] = new Color(middleValue, middleValue, middleValue);
            tableData[i][1] = (i + 1);
            tableData[i][2] = pixelGroups[i][0] + "-" + pixelGroups[i][1];
            tableData[i][3] = groupPixelCounts[i];
            
            // Calculate accumulative percentage for current and next row
            if (i < groupCount - 1) {
                int accumulatedPixels = groupPixelCounts[i] + groupPixelCounts[i + 1];
                double percentage = (accumulatedPixels * 100.0) / totalPixelsInGroups;
                tableData[i][4] = String.format("%.2f%%", percentage);
            } else {
                tableData[i][4] = "---";
            }
        }

        // Update image display
        Dimension scaledDim = getScaledDimension(width, height, ANALYSIS_PANEL_WIDTH / 3, ANALYSIS_PANEL_HEIGHT);
        Image scaledImage = binaryImage.getScaledInstance(scaledDim.width, scaledDim.height, Image.SCALE_SMOOTH);
        groupAnalysisImageLabel.setIcon(new ImageIcon(scaledImage));

        // Refresh table
        groupAnalysisTable.repaint();
    }

    private boolean isPartOfExistingGroup(int[][] groups, int x, int y) {
        return groups[y][x] != 0;
    }

    private void floodFillGroup(int[][] matrix, int[][] groups, int startX, int startY, int groupNum, java.util.List<Point> pixels) {
        int height = matrix.length;
        int width = matrix[0].length;
        
        Queue<Point> queue = new LinkedList<>();
        queue.add(new Point(startX, startY));
        groups[startY][startX] = groupNum;
        pixels.add(new Point(startX, startY));

        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};

        while (!queue.isEmpty()) {
            Point current = queue.poll();
            
            for (int i = 0; i < 8; i++) {
                int nx = current.x + dx[i];
                int ny = current.y + dy[i];

                if (nx >= 0 && ny >= 0 && nx < width && ny < height &&
                    groups[ny][nx] == 0 && matrix[ny][nx] != -1 && 
                    matrix[ny][nx] <= moduleThreshold) {
                    queue.add(new Point(nx, ny));
                    groups[ny][nx] = groupNum;
                    pixels.add(new Point(nx, ny));
                }
            }
        }
    }

    private JPanel createGroupControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        // Add title before controls
        JLabel titleLabel = new JLabel("Krāsas vienveidība");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setForeground(THEME_COLOR);

        groupCountLabel = new JLabel("Grupu skaits: " + groupCount);
        groupCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        groupCountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Modify slider to have range 1-51
        JSlider groupSlider = new JSlider(1, 51, groupCount);
        groupSlider.setPreferredSize(new Dimension(160, 20));
        groupSlider.setMaximumSize(new Dimension(160, 20));
        JPanel sliderPanel = createSliderWithButtons(groupSlider, groupCountLabel, "Grupu skaits");

        // Update table data array size
        tableData = new Object[51][5]; // Changed from 17 to 51

        // Table with reduced height
        groupAnalysisTable = new JTable(tableData, columnNames);
        groupAnalysisTable.setEnabled(false);
        JScrollPane tableScrollPane = new JScrollPane(groupAnalysisTable);
        tableScrollPane.setPreferredSize(new Dimension(400, 150));

        // Add peak count label
        peakCountLabel = new JLabel("Krāsu grupu skaits: 0");
        peakCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        peakCountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Larger graph panel with full width
        JPanel graphPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawGraph(g);
            }
        };
        graphPanel.setPreferredSize(new Dimension(400, 250)); // Increased height
        graphPanel.setBorder(BorderFactory.createTitledBorder(""));

        // Add peak threshold controls after peak count label
        peakThresholdLabel = new JLabel("Krāsu grupu skaita slieksnis: " + peakThreshold + "%");
        peakThresholdLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        peakThresholdLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        peakThresholdSlider = new JSlider(0, 100, peakThreshold); // Initialize with current peakThreshold value
        peakThresholdSlider.setPreferredSize(new Dimension(160, 20));
        peakThresholdSlider.setMaximumSize(new Dimension(160, 20));
        JPanel peakSliderPanel = createSliderWithButtons(peakThresholdSlider, peakThresholdLabel, "Krāsu grupu skaita slieksnis");

        // Add components to panel
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(groupCountLabel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(sliderPanel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(tableScrollPane);
        panel.add(Box.createVerticalStrut(5));
        panel.add(peakCountLabel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(peakThresholdLabel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(peakSliderPanel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(graphPanel);

        // Add slider listener
        groupSlider.addChangeListener(e -> {
            groupCount = groupSlider.getValue();
            groupCountLabel.setText("Grupu skaits: " + groupCount);
            updateGroupAnalysis();
            graphPanel.repaint();
            setCustomCalibration(); // Add this line
        });

        // Add peak threshold slider listener
        peakThresholdSlider.addChangeListener(e -> {
            peakThreshold = peakThresholdSlider.getValue();
            peakThresholdLabel.setText("Krāsu grupu skaita slieksnis: " + peakThreshold + "%");
            graphPanel.repaint();
            setCustomCalibration(); // Add this line
        });

        return panel;
    }

    private JPanel createGroupImagePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        JPanel imageWrapper = new JPanel(new BorderLayout());
        imageWrapper.setOpaque(false);
        
        JPanel imageBackground = new JPanel(new BorderLayout());
        imageBackground.setBackground(THEME_COLOR);
        imageBackground.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        
        groupAnalysisImageLabel = new JLabel();
        groupAnalysisImageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageBackground.add(groupAnalysisImageLabel, BorderLayout.CENTER);
        imageWrapper.add(imageBackground, BorderLayout.CENTER);
        panel.add(imageWrapper);

        return panel;
    }

    private void drawGraph(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = 380;
        int height = 230; // Increased height
        int padding = 45; // Increased padding for labels
        int bottom = height - padding;
        
        // Draw axes with arrows
        g2.setColor(Color.BLACK);
        // Y axis
        g2.drawLine(padding, height - padding, padding, padding);
        int[] yArrowX = {padding, padding - 5, padding + 5};
        int[] yArrowY = {padding, padding + 10, padding + 10};
        g2.fillPolygon(yArrowX, yArrowY, 3);
        
        // X axis
        g2.drawLine(padding, bottom, width - padding, bottom);
        int[] xArrowX = {width - padding, width - padding - 10, width - padding - 10};
        int[] xArrowY = {bottom, bottom - 5, bottom + 5};
        g2.fillPolygon(xArrowX, xArrowY, 3);

        // Axis labels
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g2.drawString("Saladījums", padding - 35, padding - 10);
        g2.drawString("Grupu pāri", width - padding - 30, bottom + 25);

        // Plot data points
        if (groupCount > 1) {
            // Calculate step size based on available width
            double availableWidth = width - (padding * 2);
            double xStep = availableWidth / (groupCount - 1);
            int[] points = new int[groupCount - 1];
            double maxValue = 0;

            // Get percentage values and find max
            for (int i = 0; i < groupCount - 1; i++) {
                String value = (String) tableData[i][4];
                if (value != null && !value.equals("---")) {
                    double percentage = Double.parseDouble(value.replace("%", ""));
                    points[i] = (int) percentage;
                    maxValue = Math.max(maxValue, percentage);
                }
            }

            maxValue = Math.ceil((maxValue + 5) / 10.0) * 10;

            // Draw points and lines
            g2.setColor(THEME_COLOR);
            double prevX = padding;
            int prevY = bottom;
            int peakCount = 0;

            for (int i = 0; i < points.length; i++) {
                double x = padding + (i + 1) * xStep;
                int y = bottom - (int)((points[i] / maxValue) * (height - padding * 2));
                
                // Draw line
                g2.drawLine((int)prevX, prevY, (int)x, y);
                
                // Draw point
                g2.fillOval((int)x - 3, y - 3, 6, 6);
                
                // Check for peaks with updated conditions
                if (i == 0) {
                    // First point is a peak if it's above threshold and higher than next point
                    if (points[i] >= peakThreshold && points[i] > points[i+1]) {
                        peakCount++;
                        g2.setColor(Color.RED);
                        g2.drawString(points[i] + "%", (int)x - 10, y - 10);
                        g2.setColor(THEME_COLOR);
                    }
                } else if (i < points.length - 1) {
                    // Middle points
                    if (points[i] > points[i-1] && points[i] > points[i+1]) {
                        if (points[i] >= peakThreshold) {
                            peakCount++;
                            g2.setColor(Color.RED);
                            g2.drawString(points[i] + "%", (int)x - 10, y - 10);
                            g2.setColor(THEME_COLOR);
                        }
                    }
                } else {
                    // Last point
                    if (points[i] > points[i-1] && points[i] >= peakThreshold) {
                        peakCount++;
                        g2.setColor(Color.RED);
                        g2.drawString(points[i] + "%", (int)x - 10, y - 10);
                        g2.setColor(THEME_COLOR);
                    }
                }

                // Draw x-axis labels with adaptive font size
                String label = (i+1) + "-" + (i+2);
                int fontSize = Math.min(12, (int)(availableWidth / (groupCount * 2)));
                g2.setFont(new Font("Segoe UI", Font.PLAIN, Math.max(6, fontSize)));
                
                // Calculate text width for centering
                FontMetrics fm = g2.getFontMetrics();
                int textWidth = fm.stringWidth(label);
                g2.drawString(label, (int)(x - textWidth/2), bottom + 15);
                
                prevX = x;
                prevY = y;
            }

            // Update peak count label to show threshold info
            peakCountLabel.setText(String.format("Krāsu grupu skaits: %d", peakCount));

            // Reset font for Y axis scale
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            g2.setColor(Color.BLACK);
            for (int i = 0; i <= maxValue; i += 20) {
                int y = bottom - (int)((i / maxValue) * (height - padding * 2));
                g2.drawString(String.valueOf(i), padding - 25, y + 5);
                g2.drawLine(padding - 2, y, padding + 2, y);
            }
        }
    }

    private void updateFifthAnalysisBox(String baseFolder, String crosswalkNum) {
        fifthAnalysisBlock = new JPanel();
        fifthAnalysisBlock.setLayout(new BoxLayout(fifthAnalysisBlock, BoxLayout.Y_AXIS));

        JPanel analysisBlock = new JPanel(new GridLayout(1, 2, 10, 0));
        analysisBlock.setBackground(new Color(245, 245, 245));
        analysisBlock.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY, 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        // Left side with image and pixel blocks
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);

        // Image panel
        JPanel imageWrapper = new JPanel(new BorderLayout());
        imageWrapper.setOpaque(false);
        
        JPanel imageBackground = new JPanel(new BorderLayout());
        imageBackground.setBackground(THEME_COLOR);
        imageBackground.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        
        fifthAnalysisImageLabel = new JLabel();
        fifthAnalysisImageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageBackground.add(fifthAnalysisImageLabel, BorderLayout.CENTER);
        imageWrapper.add(imageBackground, BorderLayout.CENTER);

        // Add pixel color blocks panel
        JPanel pixelBlocksPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        pixelBlocksPanel.setOpaque(false);
        pixelBlocksPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        // Black pixel block
        JPanel blackBlock = new JPanel(new BorderLayout());
        blackBlock.setOpaque(false);
        blackPixelPanel = new JPanel();
        blackPixelPanel.setPreferredSize(new Dimension(50, 50));
        blackPixelPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        blackValueLabel = new JLabel("0", SwingConstants.CENTER);
        blackValueLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JLabel blackTitle = new JLabel("Melnu pikseļu vid. vērtība", SwingConstants.CENTER);
        blackTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        blackBlock.add(blackTitle, BorderLayout.NORTH);
        blackBlock.add(blackPixelPanel, BorderLayout.CENTER);
        blackBlock.add(blackValueLabel, BorderLayout.SOUTH);

        // White pixel block
        JPanel whiteBlock = new JPanel(new BorderLayout());
        whiteBlock.setOpaque(false);
        whitePixelPanel = new JPanel();
        whitePixelPanel.setPreferredSize(new Dimension(50, 50));
        whitePixelPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        whiteValueLabel = new JLabel("255", SwingConstants.CENTER);
        whiteValueLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JLabel whiteTitle = new JLabel("Baltu pikseļu vid. vērtība", SwingConstants.CENTER);
        whiteTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        whiteBlock.add(whiteTitle, BorderLayout.NORTH);
        whiteBlock.add(whitePixelPanel, BorderLayout.CENTER);
        whiteBlock.add(whiteValueLabel, BorderLayout.SOUTH);

        pixelBlocksPanel.add(blackBlock);
        pixelBlocksPanel.add(whiteBlock);

        leftPanel.add(imageWrapper);
        leftPanel.add(pixelBlocksPanel);

        // Right side controls
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setOpaque(false);

        // Add title
        JLabel titleLabel = new JLabel("Kontrasts");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setForeground(THEME_COLOR);

        // Add threshold slider
        JLabel thresholdLabel = new JLabel("Balts-Melns slieksnis: " + contrastThreshold);  // Changed from moduleThreshold
        thresholdLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        thresholdLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JSlider thresholdSlider = new JSlider(0, 255, contrastThreshold);  // Changed from moduleThreshold
        thresholdSlider.setPreferredSize(new Dimension(160, 20));
        thresholdSlider.setMaximumSize(new Dimension(160, 20));
        JPanel sliderPanel = createSliderWithButtons(thresholdSlider, thresholdLabel, "Balts-melns slieksnis");

        // Add contrast labels
        contrastLabel = new JLabel("Kontrasts: 0", SwingConstants.CENTER);
        contrastLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        contrastLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        relativeContrastLabel = new JLabel("Relatīvais kontrasts: 0%", SwingConstants.CENTER);
        relativeContrastLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        relativeContrastLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Add components to right panel
        rightPanel.add(Box.createVerticalGlue());
        rightPanel.add(titleLabel);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(thresholdLabel);
        rightPanel.add(Box.createVerticalStrut(5));
        rightPanel.add(sliderPanel);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(contrastLabel);
        rightPanel.add(Box.createVerticalStrut(5));
        rightPanel.add(relativeContrastLabel);
        rightPanel.add(Box.createVerticalGlue());

        // Add slider listener
        thresholdSlider.addChangeListener(e -> {
            contrastThreshold = thresholdSlider.getValue();  // Changed from moduleThreshold
            thresholdLabel.setText("Balts-Melns slieksnis: " + contrastThreshold);
            
            if (reducedMatrix != null) {
                int width = reducedMatrix[0].length;
                int height = reducedMatrix.length;
                updateFifthAnalysisDisplay(width, height);
            }
            setCustomCalibration(); // Add this line
        });

        // Initially show the image and calculate values
        if (reducedMatrix != null) {
            int width = reducedMatrix[0].length;
            int height = reducedMatrix.length;
            updateFifthAnalysisDisplay(width, height);
        }

        analysisBlock.add(leftPanel);
        analysisBlock.add(rightPanel);
        fifthAnalysisBlock.add(analysisBlock);
        analysisBox.add(Box.createVerticalStrut(20));
        analysisBox.add(fifthAnalysisBlock);
    }

    private void updateFifthAnalysisDisplay(int width, int height) {
        if (fifthAnalysisImageLabel != null && reducedMatrix != null) {
            BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            
            int blackPixelCount = 0;
            int whitePixelCount = 0;
            double blackSum = 0;
            double whiteSum = 0;
            
            // First pass: calculate averages using original values
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (reducedMatrix[y][x] != -1) {
                        int pixelValue = reducedMatrix[y][x];
                        if (pixelValue > contrastThreshold) {  // Changed from moduleThreshold
                            whiteSum += pixelValue;
                            whitePixelCount++;
                        } else {
                            blackSum += pixelValue;
                            blackPixelCount++;
                        }
                    }
                }
            }
            
            // Calculate averages and handle edge cases
            int blackAvg = blackPixelCount > 0 ? (int)(blackSum / blackPixelCount) : contrastThreshold;  // Changed from moduleThreshold
            int whiteAvg = whitePixelCount > 0 ? (int)(whiteSum / whitePixelCount) : contrastThreshold;  // Changed from moduleThreshold
            
            // Second pass: create binary display image
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (reducedMatrix[y][x] == -1) {
                        newImage.setRGB(x, y, new Color(0, 0, 0, 0).getRGB());
                    } else {
                        int value = reducedMatrix[y][x] > contrastThreshold ? 255 : 0;  // Changed from moduleThreshold
                        newImage.setRGB(x, y, new Color(value, value, value).getRGB());
                    }
                }
            }
            
            // Update color blocks with actual average values
            blackPixelPanel.setBackground(new Color(blackAvg, blackAvg, blackAvg));
            whitePixelPanel.setBackground(new Color(whiteAvg, whiteAvg, whiteAvg));
            blackValueLabel.setText(String.valueOf(blackAvg));
            whiteValueLabel.setText(String.valueOf(whiteAvg));
            
            // Calculate contrast values with safeguards
            int contrast = 0;
            double relativeContrast = 0.0;
            
            if (blackPixelCount > 0 && whitePixelCount > 0) {
                contrast = whiteAvg - blackAvg;
                relativeContrast = (contrast * 100.0) / 255.0;
                contrastLabel.setText("Kontrasts: " + contrast);
                relativeContrastLabel.setText(String.format("Relatīvais kontrasts: %.1f%%", relativeContrast));
            } else if (whitePixelCount == 0) {
                contrastLabel.setText("Kontrasts: 0");
                relativeContrastLabel.setText("Relatīvais kontrasts: 0%");
            } else if (blackPixelCount == 0) {
                contrastLabel.setText("Kontrasts: 0");
                relativeContrastLabel.setText("Relatīvais kontrasts: 0%");
            }
            
            // Update image
            Dimension scaledDim = getScaledDimension(width, height, ANALYSIS_PANEL_WIDTH / 3, ANALYSIS_PANEL_HEIGHT);
            Image newScaled = newImage.getScaledInstance(scaledDim.width, scaledDim.height, Image.SCALE_SMOOTH);
            fifthAnalysisImageLabel.setIcon(new ImageIcon(newScaled));
        }
    }

    private void updateWhiteClusterAnalysisBox(String baseFolder, String crosswalkNum) {
        whiteClusterAnalysisBlock = new JPanel();
        whiteClusterAnalysisBlock.setLayout(new BoxLayout(whiteClusterAnalysisBlock, BoxLayout.Y_AXIS));

        JPanel analysisBlock = new JPanel(new GridLayout(1, 2, 10, 0));
        analysisBlock.setBackground(new Color(245, 245, 245));
        analysisBlock.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY, 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        // Left side - Image panel with title
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);

        JPanel imageWrapper = new JPanel(new BorderLayout());
        imageWrapper.setOpaque(false);
        
        JPanel imageBackground = new JPanel(new BorderLayout());
        imageBackground.setBackground(THEME_COLOR);
        imageBackground.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        
        whiteClusterImageLabel = new JLabel();
        whiteClusterImageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageBackground.add(whiteClusterImageLabel, BorderLayout.CENTER);
        imageWrapper.add(imageBackground, BorderLayout.CENTER);
        leftPanel.add(imageWrapper);

        // Right side - Stats and controls
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Baltu strīpu salīdzinājums");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setForeground(THEME_COLOR);

        JPanel statsPanel = new JPanel();
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
        statsPanel.setOpaque(false);
        statsPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        whiteClusterInfoLabels = new JLabel[] {
            new JLabel("Baltu strīpu skaits: " + (int)whiteClusterValue1),
            new JLabel("Baltu strīpu kopējā platība: " + String.format("%.2f%%", whiteClusterValue2)),
            new JLabel("Pilnīgo baltu strīpu skaits: " + (int)whiteClusterValue3),
            new JLabel("Pilnīgo baltu strīpu platība: " + String.format("%.2f%%", whiteClusterValue4)),
            new JLabel("Balts-Melns slieksnis: " + whiteClusterThreshold),
            new JLabel("Pilnīgo baltu strīpu filtrācijas slieksnis: " + String.format("%.1f", whiteClusterSliderValue) + "%")
        };

        Font labelFont = new Font("Segoe UI", Font.PLAIN, 14);

        // Add first 4 labels to stats panel
        for (int i = 0; i < 4; i++) {
            whiteClusterInfoLabels[i].setFont(labelFont);
            whiteClusterInfoLabels[i].setAlignmentX(Component.CENTER_ALIGNMENT);
            statsPanel.add(whiteClusterInfoLabels[i]);
            statsPanel.add(Box.createVerticalStrut(5));
        }

        // Add the threshold label and slider
        whiteClusterInfoLabels[4].setFont(labelFont);
        whiteClusterInfoLabels[4].setAlignmentX(Component.CENTER_ALIGNMENT);
        statsPanel.add(whiteClusterInfoLabels[4]);
        statsPanel.add(Box.createVerticalStrut(5));

        // Create and add module threshold slider right after its label
        JSlider thresholdSlider = new JSlider(0, 255, whiteClusterThreshold);
        thresholdSlider.setPreferredSize(new Dimension(160, 20));
        JPanel thresholdSliderPanel = createSliderWithButtons(thresholdSlider, whiteClusterInfoLabels[4], "Balts-Melns slieksnis");
        statsPanel.add(thresholdSliderPanel);
        statsPanel.add(Box.createVerticalStrut(10));

        // Add size threshold label and slider
        whiteClusterInfoLabels[5].setFont(labelFont);
        whiteClusterInfoLabels[5].setAlignmentX(Component.CENTER_ALIGNMENT);
        statsPanel.add(whiteClusterInfoLabels[5]);
        statsPanel.add(Box.createVerticalStrut(5));

        JSlider sizeSlider = new JSlider(0, 100, 0);
        sizeSlider.setPreferredSize(new Dimension(160, 20));
        JPanel sizeSliderPanel = createSliderWithButtons(sizeSlider, whiteClusterInfoLabels[5], "Pilnīgo baltu strīpu filtrācijas slieksnis");

        thresholdSlider.addChangeListener(e -> {
            whiteClusterThreshold = thresholdSlider.getValue();
            whiteClusterInfoLabels[4].setText("Balts-Melns slieksnis: " + whiteClusterThreshold);
            updateWhiteClusterAnalysis();
            setCustomCalibration(); // Add this line
        });

        sizeSlider.addChangeListener(e -> {
            whiteClusterSliderValue = sizeSlider.getValue() / 10.0;
            whiteClusterInfoLabels[5].setText("Pilnīgo baltu strīpu filtrācijas slieksnis: " + String.format("%.1f", whiteClusterSliderValue) + "%");
            updateWhiteClusterAnalysis();
            setCustomCalibration(); // Add this line
        });

        rightPanel.add(Box.createVerticalGlue());
        rightPanel.add(titleLabel);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(statsPanel);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(sizeSliderPanel);
        
        // Add cluster percentage label
        clusterPercentageLabel = new JLabel("Pilnīgo baltu strīpu īpatsvars: 0%");
        clusterPercentageLabel.setFont(labelFont);
        clusterPercentageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(clusterPercentageLabel);
        rightPanel.add(Box.createVerticalGlue());

        analysisBlock.add(leftPanel);
        analysisBlock.add(rightPanel);

        whiteClusterAnalysisBlock.add(analysisBlock);
        analysisBox.add(Box.createVerticalStrut(20));
        analysisBox.add(whiteClusterAnalysisBlock);

        updateWhiteClusterAnalysis();
    }

    private void updateWhiteClusterAnalysis() {
        if (reducedMatrix != null) {
            int width = reducedMatrix[0].length;
            int height = reducedMatrix.length;
            
            BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            
            // First draw all non-transparent pixels in their original binary form
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (reducedMatrix[y][x] != -1) {
                        // Show black pixels as black and white pixels as white
                        if (reducedMatrix[y][x] > whiteClusterThreshold) {
                            newImage.setRGB(x, y, Color.WHITE.getRGB());
                        } else {
                            newImage.setRGB(x, y, Color.BLACK.getRGB());
                        }
                    } else {
                        newImage.setRGB(x, y, new Color(0, 0, 0, 0).getRGB());
                    }
                }
            }

            boolean[][] visited = new boolean[height][width];
            // Update how we store and use the slider value
            whiteClusterSliderValue = Math.round(whiteClusterSliderValue * 10) / 10.0; // Round to 1 decimal
            double minClusterPercentage = whiteClusterSliderValue;
            int minClusterSize = (int)((minClusterPercentage / 100.0) * reducedPixelCount);
            
            java.util.List<Integer> clusterSizes = new java.util.ArrayList<>();
            int totalClusters = 0;
            int totalArea = 0;
            int validClusters = 0;
            int validArea = 0;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (!visited[y][x] && isValidWhitePixel(x, y)) {
                        java.util.List<Point> clusterPixels = new java.util.ArrayList<>();
                        int clusterSize = floodFillWhiteCluster(x, y, visited, clusterPixels);
                        
                        if (clusterSize > 0) {
                            Color clusterColor = clusterSize >= minClusterSize ? Color.GREEN : Color.RED;
                            for (Point p : clusterPixels) {
                                newImage.setRGB(p.x, p.y, clusterColor.getRGB());
                            }
                            
                            clusterSizes.add(clusterSize);
                            totalClusters++;
                            totalArea += clusterSize;
                            
                            if (clusterSize >= minClusterSize) {
                                validClusters++;
                                validArea += clusterSize;
                            }
                        }
                    }
                }
            }

            // Fill transparent areas
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (newImage.getRGB(x, y) == 0) {
                        newImage.setRGB(x, y, new Color(0, 0, 0, 0).getRGB());
                    }
                }
            }

            // Update statistics
            whiteClusterValue1 = totalClusters;
            whiteClusterValue2 = totalArea * 100.0 / reducedPixelCount;
            whiteClusterValue3 = validClusters;
            whiteClusterValue4 = validArea * 100.0 / reducedPixelCount;

            // Update labels
            whiteClusterInfoLabels[0].setText("Baltu strīpu skaits: " + (int)whiteClusterValue1);
            whiteClusterInfoLabels[1].setText("Baltu strīpu kopējā platība: " + String.format("%.2f%%", whiteClusterValue2));
            whiteClusterInfoLabels[2].setText("Pilnīgo baltu strīpu skaits: " + (int)whiteClusterValue3);
            whiteClusterInfoLabels[3].setText("Pilnīgo baltu strīpu platība: " + String.format("%.2f%%", whiteClusterValue4));

            // Update cluster percentage
            double clusterPercentage = totalClusters > 0 ? 
                (validClusters * 100.0 / totalClusters) : 0.0;
            clusterPercentageLabel.setText(String.format(
                "Pilnīgo baltu strīpu īpatsvars: %.1f%%", clusterPercentage));

            // Update labels with correct formatting
            whiteClusterInfoLabels[5].setText("Pilnīgo baltu strīpu filtrācijas slieksnis: " + 
                String.format("%.1f%%", whiteClusterSliderValue));

            // Update image
            Dimension scaledDim = getScaledDimension(width, height, ANALYSIS_PANEL_WIDTH / 3, ANALYSIS_PANEL_HEIGHT);
            Image newScaled = newImage.getScaledInstance(scaledDim.width, scaledDim.height, Image.SCALE_SMOOTH);
            whiteClusterImageLabel.setIcon(new ImageIcon(newScaled));
        }
    }

    private boolean isValidWhitePixel(int x, int y) {
        if (reducedMatrix[y][x] == -1) return false;
        return reducedMatrix[y][x] > whiteClusterThreshold;
    }

    private int floodFillWhiteCluster(int startX, int startY, boolean[][] visited, java.util.List<Point> pixels) {
        int height = reducedMatrix.length;
        int width = reducedMatrix[0].length;
        int clusterSize = 0;
        boolean isValidCluster = true;

        Queue<Point> queue = new LinkedList<>();
        queue.add(new Point(startX, startY));
        visited[startY][startX] = true;

        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};

        while (!queue.isEmpty() && isValidCluster) {
            Point current = queue.poll();
            pixels.add(current);
            clusterSize++;

            for (int i = 0; i < 8; i++) {
                int nx = current.x + dx[i];
                int ny = current.y + dy[i];

                if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                    continue;
                }

                if (!visited[ny][nx] && isValidWhitePixel(nx, ny)) {
                    queue.add(new Point(nx, ny));
                    visited[ny][nx] = true;
                }
            }
        }

        if (!isValidCluster) {
            pixels.clear();
            return 0;
        }

        return clusterSize;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GPA_Analize().setVisible(true));
    }

    // Add new inner class to store calibration values
    private static class CalibrationValues {
        int boundaryThreshold;
        int brightnessThreshold;
        int moduleThreshold;
        double moduleSliderValue;
        int whiteClusterThreshold;
        double whiteClusterSliderValue;
        boolean use4PixelRule;
        String name;
        int groupCount;          // Add new fields
        int peakThreshold;
        int contrastThreshold;
        
        CalibrationValues(String name, int boundary, int brightness, int module, 
                         double moduleSlider, int whiteCluster, double whiteClusterSlider, 
                         boolean usePixelRule, int groupCount, int peakThreshold, int contrastThreshold) {
            this.name = name;
            this.boundaryThreshold = boundary;
            this.brightnessThreshold = brightness;
            this.moduleThreshold = module;
            this.moduleSliderValue = moduleSlider;
            this.whiteClusterThreshold = whiteCluster;
            this.whiteClusterSliderValue = whiteClusterSlider;
            this.use4PixelRule = usePixelRule;
            this.groupCount = groupCount;
            this.peakThreshold = peakThreshold;
            this.contrastThreshold = contrastThreshold;
        }
    }

    // Add this to the setupWindow method after creating crosswalkSelector
    private void addCalibrationSelector(JPanel titleBar) {
        JLabel calibrationLabel = new JLabel("Kalibrācijas šablons:");
        calibrationLabel.setForeground(Color.WHITE);
        calibrationLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleBar.add(calibrationLabel);

        calibrationSelector = new JComboBox<>();
        calibrationSelector.setPreferredSize(new Dimension(200, 25));
        titleBar.add(calibrationSelector);
        
        calibrationSelector.addActionListener(e -> {
            if (e.getActionCommand().equals("comboBoxChanged")) {
                currentCalibrationIndex = calibrationSelector.getSelectedIndex();
                applyCalibrationValues(currentCalibrationIndex);
                isCustomCalibration = false; // Reset custom flag when a predefined calibration is selected
            }
        });
        
        loadCalibrationValues();
    }

    // Replace Excel loading with text file loading
    private void loadCalibrationValues() {
        // Add default calibration first with new values
        calibrationsList.add(new CalibrationValues(
            "Tests", 11, 22, 33, 4.4, 55, 6.6, true, 1, 0, 75));
        calibrationSelector.addItem("Tests");
        calibrationSelector.addItem("Patstāvīgi pielāgots"); // Add "Custom" option

        try {
            File file = new File("kalibracija.txt");
            if (!file.exists()) {
                System.out.println("Using default calibration - Text file not found");
                return;
            }

            // Clear default values if file loads successfully
            calibrationsList.clear();
            calibrationSelector.removeAllItems();

            // Read text file
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 11) { // Updated length to include new values
                        try {
                            CalibrationValues cal = new CalibrationValues(
                                parts[0].trim(),                    // name
                                Integer.parseInt(parts[2].trim()),  // boundaryThreshold
                                Integer.parseInt(parts[3].trim()),  // brightnessThreshold
                                Integer.parseInt(parts[4].trim()),  // moduleThreshold
                                Double.parseDouble(parts[5].trim()),// moduleSliderValue
                                Integer.parseInt(parts[6].trim()),  // whiteClusterThreshold
                                Double.parseDouble(parts[7].trim()),// whiteClusterSliderValue
                                Boolean.parseBoolean(parts[1].trim()),// use4PixelRule
                                Integer.parseInt(parts[8].trim()),  // groupCount
                                Integer.parseInt(parts[9].trim()),  // peakThreshold
                                Integer.parseInt(parts[10].trim())  // contrastThreshold
                            );
                            
                            calibrationsList.add(cal);
                            calibrationSelector.addItem(cal.name);
                        } catch (Exception e) {
                            System.out.println("Error parsing line: " + line);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error loading calibration file: " + e.getMessage());
        }

        // Select first calibration
        if (calibrationSelector.getItemCount() > 0) {
            calibrationSelector.setSelectedIndex(0);
            applyCalibrationValues(0);
        }
    }

    // Add method to apply calibration values
    private void applyCalibrationValues(int index) {
        if (index < 0 || index >= calibrationsList.size()) return;
        
        CalibrationValues cal = calibrationsList.get(index);
        
        boundaryThreshold = cal.boundaryThreshold;
        brightnessThreshold = cal.brightnessThreshold;
        moduleThreshold = cal.moduleThreshold;
        moduleSliderValue = cal.moduleSliderValue;
        whiteClusterThreshold = cal.whiteClusterThreshold;
        whiteClusterSliderValue = cal.whiteClusterSliderValue;
        groupCount = cal.groupCount;
        peakThreshold = cal.peakThreshold;
        contrastThreshold = cal.contrastThreshold;
        
        // Update the peak threshold slider and label if they exist
        if (peakThresholdSlider != null && peakThresholdLabel != null) {
            peakThresholdSlider.setValue(peakThreshold);
            peakThresholdLabel.setText("Krāsu grupu skaita slieksnis: " + peakThreshold + "%");
        }
        
        // Update all displays
        if (currentAnalysisImage != null) {
            String baseFolder = String.format("Results/%s", currentResultDir);
            String selectedNum = (String)crosswalkSelector.getSelectedItem();
            loadSelectedData();
        }
    }

    private void setCustomCalibration() {
        if (!isCustomCalibration) {
            calibrationSelector.setSelectedItem("Patstāvīgi pielāgots");
            isCustomCalibration = true;
        }
    }

    // Replace the saveAnalysis method
    private void saveAnalysis() {
        try {
            String selectedResult = (String)resultSelector.getSelectedItem();
            String selectedNum = (String)crosswalkSelector.getSelectedItem();
            if (selectedNum == null || selectedResult == null) return;
            
            // Create analysis history directory if it doesn't exist
            File analysisDir = new File("analizes_vesture");
            if (!analysisDir.exists()) {
                analysisDir.mkdir();
            }
            
            // Get current date and time
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
            String timestamp = now.format(dateFormatter);
            DateTimeFormatter readableFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String readableTime = now.format(readableFormatter);
            
            // Create filename: analize_<foldername>_<number>_<timestamp>.txt
            String filename = String.format("analize_%s_%s_%s.txt", 
                selectedResult, selectedNum, timestamp);
            
            File outputFile = new File(analysisDir, filename);
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                writer.println("=== Analīzes rezultāti ===");
                writer.println("Izveidots: " + readableTime);
                
                writer.println("\nInfo:");
                writer.println("Attēls: " + selectedResult);
                writer.println("GPA: " + selectedNum);
                writer.println("Kalibrācijas šablons: " + (isCustomCalibration ? "Patstāvīgi pielāgots" : calibrationSelector.getSelectedItem()));
                writer.println("Uzticība: " + confidence + "%");
                writer.println("Oriģināls attēls: " + originalPixelCount + " px");
                writer.println("Reducēts attēls: " + reducedPixelCount + " px");
                
                writer.println("\nNeatkarīgie lielumi:");
                writer.println((use4PixelRule ? boundaryThreshold : "izslēgts") + " - Redukcijas slieksnis.");
                writer.println(brightnessThreshold + " - Balts-Melns slieksnis (GPA strīpu garuma kļūda).");
                writer.println(moduleThreshold + " - Balts-Melns slieksnis (Caurumu skaits un platība).");
                writer.println(String.format("%.1f%%", moduleSliderValue) + " - Lielo caurumu filtrācijas slieksnis (Caurumu skaits un platība).");
                writer.println(whiteClusterThreshold + " - Balts-Melns slieksnis (Baltu strīpu salīdzinājums).");
                writer.println(String.format("%.1f%%", whiteClusterSliderValue) + " - Pilnīgo baltu strīpu filtrācijas slieksnis (Baltu strīpu salīdzinājums).");
                writer.println(groupCount + " - Grupu skaits.");
                writer.println(peakThreshold + "% - Krāsu grupu skaita slieksnis.");  // No change needed as % is already included
                writer.println(contrastThreshold + " - Balts-Melns slieksnis (Kontrasts).");
                
                writer.println("\nAtkarīgie lielumi:");
                // White/Black pixel counts from first module
                PixelCounts pixelCounts = countMatrixPixels(reducedMatrix);
                writer.println(pixelCounts.white + " - Balti pikseļi.");
                writer.println(pixelCounts.black + " - Melni pikseļi.");
                
                // Values from module "GPA strīpu garuma kļūdas"
                double percentage = Math.ceil((double) pixelCounts.white / (pixelCounts.white + pixelCounts.black) * 100 * 100) / 100.0;
                writer.println(String.format("%.2f%%", percentage) + " - Baltu pikseļu relatīvais daudzums.");
                
                // Values from module "Caurumu skaits un platība"
                writer.println((int)moduleValue1 + " - Caurumu skaits.");
                writer.println(String.format("%.2f%%", moduleValue2) + " - Caurumu kopējā platība.");
                writer.println((int)moduleValue3 + " - Lielo caurumu skaits.");
                writer.println(String.format("%.2f%%", moduleValue4) + " - Lielo caurumu platība.");
                
                // Values from module "Baltu strīpu salīdzinājums"
                writer.println((int)whiteClusterValue1 + " - Baltu strīpu skaits.");
                writer.println(String.format("%.2f%%", whiteClusterValue2) + " - Baltu strīpu kopējā platība.");
                writer.println((int)whiteClusterValue3 + " - Pilnīgo baltu strīpu skaits.");
                writer.println(String.format("%.2f%%", whiteClusterValue4) + " - Pilnīgo baltu strīpu platība.");
                double clusterPercentage = whiteClusterValue1 > 0 ? 
                    (whiteClusterValue3 * 100.0 / whiteClusterValue1) : 0.0;
                writer.println(String.format("%.1f%%", clusterPercentage) + " - Pilnīgo baltu strīpu īpatsvars.");
                
                // Add color group count with percentage
                String colorGroupCount = peakCountLabel.getText();
                writer.println(colorGroupCount);  // Already contains: "Krāsu grupu skaits: X"
                
                // Values from module "Kontrasts"
                int whiteAvg = getAverageWhiteValue();
                int blackAvg = getAverageBlackValue();
                int contrast = whiteAvg - blackAvg;
                double relativeContrast = (contrast * 100.0) / 255.0;
                writer.println("Kontrasts: " + contrast);
                writer.println("Relatīvais kontrasts: " + String.format("%.1f%%", relativeContrast));
                
                JOptionPane.showMessageDialog(this, 
                    "Analīzes rezultāti saglabāti failā:\n" + filename);
                
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, 
                    "Kļūda saglabājot analīzi: " + e.getMessage());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Kļūda: " + e.getMessage());
        }
    }

private int getAverageWhiteValue() {
    if (reducedMatrix == null) return 0;
    
    int sum = 0;
    int count = 0;
    for (int[] row : reducedMatrix) {
        for (int pixel : row) {
            if (pixel != -1 && pixel > contrastThreshold) {  // Changed from moduleThreshold
                sum += pixel;
                count++;
            }
        }
    }
    return count > 0 ? sum / count : 0;
}

private int getAverageBlackValue() {
    if (reducedMatrix == null) return 0;
    
    int sum = 0;
    int count = 0;
    for (int[] row : reducedMatrix) {
        for (int pixel : row) {
            if (pixel != -1 && pixel <= contrastThreshold) {  // Changed from moduleThreshold
                sum += pixel;
                count++;
            }
        }
    }
    return count > 0 ? sum / count : 0;
}
}