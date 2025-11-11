package Tech_Nagendra.Certificates_genration.Service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Service
public class DynamicFontService {

    @Value("${custom.fonts.dir:src/main/resources/fonts}")
    private String customFontsDir;

    private final Map<String, Font> loadedFonts = new ConcurrentHashMap<>();
    private final Set<String> availableFontFamilies = ConcurrentHashMap.newKeySet();
    private boolean fontsInitialized = false;

    @PostConstruct
    public void autoLoadFonts() {
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                loadFontsDynamically();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    public synchronized void loadFontsDynamically() {
        if (fontsInitialized) {
            System.out.println("Fonts already initialized");
            return;
        }

        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            int count = loadFontsFromClasspath("classpath:fonts/**/*.ttf");
            count += loadFontsFromClasspath("classpath:fonts/**/*.otf");
            count += loadFontsFromClasspath("classpath:fonts/**/*.jar");


            count += loadFontsFromPath(customFontsDir);

            registerFontsWithSystem(ge);
            collectAvailableFontFamilies(ge);

            fontsInitialized = true;
        } catch (Exception e) {
            System.err.println(" Error in dynamic font loading: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int loadFontsFromClasspath(String pattern) {
        int count = 0;
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(pattern);

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) continue;

                if (filename.toLowerCase().endsWith(".jar")) {
                    count += loadFontsFromJar(resource);
                } else {
                    count += loadIndividualFontFile(resource, filename);
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading fonts from classpath: " + e.getMessage());
        }
        return count;
    }

    private int loadIndividualFontFile(Resource resource, String filename) {
        try (InputStream is = resource.getInputStream()) {
            Font font = Font.createFont(Font.TRUETYPE_FONT, is);
            loadedFonts.putIfAbsent(filename, font);
            System.out.println("Loaded classpath font: " + filename);
            return 1;
        } catch (Exception e) {
            System.err.println("Error loading font " + filename + ": " + e.getMessage());
        }
        return 0;
    }

    private int loadFontsFromJar(Resource jarResource) {
        int count = 0;
        try {
            File tempJar = File.createTempFile("font_", ".jar");
            try (InputStream is = jarResource.getInputStream();
                 FileOutputStream fos = new FileOutputStream(tempJar)) {
                is.transferTo(fos);
            }

            try (JarFile jarFile = new JarFile(tempJar)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.isDirectory() && (entry.getName().endsWith(".ttf") || entry.getName().endsWith(".otf"))) {
                        try (InputStream is = jarFile.getInputStream(entry)) {
                            Font font = Font.createFont(Font.TRUETYPE_FONT, is);
                            loadedFonts.putIfAbsent(entry.getName(), font);
                            count++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading fonts from JAR " + jarResource.getFilename() + ": " + e.getMessage());
        }
        return count;
    }

    private int loadFontsFromPath(String directoryPath) {
        int count = 0;
        try {
            File dir = new File(directoryPath);
            if (!dir.exists() || !dir.isDirectory()) {
                return 0;
            }

            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".ttf")
                    || name.toLowerCase().endsWith(".otf")
                    || name.toLowerCase().endsWith(".jar"));

            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".jar")) {
                        count += loadFontsFromExternalJar(file);
                    } else {
                        count += loadIndividualExternalFont(file);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error loading fonts from " + directoryPath + ": " + e.getMessage());
        }
        return count;
    }

    private int loadFontsFromExternalJar(File jarFile) {
        int count = 0;
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && (entry.getName().endsWith(".ttf") || entry.getName().endsWith(".otf"))) {
                    try (InputStream is = jar.getInputStream(entry)) {
                        Font font = Font.createFont(Font.TRUETYPE_FONT, is);
                        loadedFonts.putIfAbsent(entry.getName(), font);
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading external JAR: " + e.getMessage());
        }
        return count;
    }

    private int loadIndividualExternalFont(File file) {
        try {
            Font font = Font.createFont(Font.TRUETYPE_FONT, file);
            loadedFonts.putIfAbsent(file.getName(), font);
            System.out.println("Loaded external font: " + file.getName());
            return 1;
        } catch (Exception e) {
            System.err.println("Error loading external font " + file.getName() + ": " + e.getMessage());
        }
        return 0;
    }

    private void registerFontsWithSystem(GraphicsEnvironment ge) {
        int count = 0;
        for (Font font : loadedFonts.values()) {
            try {
                ge.registerFont(font);
                count++;
            } catch (Exception ignored) {
            }
        }
    }
    private void collectAvailableFontFamilies(GraphicsEnvironment ge) {
        availableFontFamilies.clear();
        availableFontFamilies.addAll(Arrays.asList(ge.getAvailableFontFamilyNames()));
    }

    public Font getFont(String fontName, int style, float size) {
        for (Map.Entry<String, Font> entry : loadedFonts.entrySet()) {
            if (entry.getKey().toLowerCase().contains(fontName.toLowerCase())) {
                return entry.getValue().deriveFont(style, size);
            }
        }
        return new Font(fontName, style, (int) size);
    }
    public Map<String, Object> getFontInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("loadedFontFiles", loadedFonts.size());
        info.put("availableFontFamilies", availableFontFamilies.size());
        info.put("fontsInitialized", fontsInitialized);
        info.put("fontDirectory", customFontsDir);
        info.put("timestamp", new Date());
        return info;
    }

    public void reloadFonts() {
        fontsInitialized = false;
        loadedFonts.clear();
        availableFontFamilies.clear();
        loadFontsDynamically();
    }

    public Set<String> getAvailableFontFamilies() {
        if (availableFontFamilies.isEmpty()) {
            collectAvailableFontFamilies(GraphicsEnvironment.getLocalGraphicsEnvironment());
        }
        return new HashSet<>(availableFontFamilies);
    }

    public boolean isFontFamilyAvailable(String fontName) {
        return availableFontFamilies.stream()
                .anyMatch(f -> f.equalsIgnoreCase(fontName));
    }
}
