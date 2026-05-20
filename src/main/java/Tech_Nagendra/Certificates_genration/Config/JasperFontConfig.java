package Tech_Nagendra.Certificates_genration.Config;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.fonts.SimpleFontExtensionHelper;
import org.springframework.context.annotation.Configuration;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

@Configuration
public class JasperFontConfig {
    static {
        try {
            loadFontJarsFromResources();
        } catch (Exception e) {
            System.err.println(" Font loading failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public static void loadFontJarsFromResources() throws Exception {
     String fontJarDir = "src/main/resources/fonts";
//            String fontJarDir = "C:/inetpub/wwwroot/Genkart/Genkart_Bankend/fonts";
        File fontDir = new File(fontJarDir);

        if (!fontDir.exists() || !fontDir.isDirectory()) {
            System.err.println(" Font directory not found: " + fontDir.getAbsolutePath());
            return;
        }

        File[] jarFiles = fontDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            System.err.println(" No font jars found in " + fontJarDir);
            return;
        }

        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        URL[] jarUrls = new URL[jarFiles.length];
        for (int i = 0; i < jarFiles.length; i++) {
            jarUrls[i] = jarFiles[i].toURI().toURL();
            System.out.println(" Found font JAR: " + jarFiles[i].getName());
        }

        URLClassLoader newLoader = new URLClassLoader(jarUrls, parent);
        Thread.currentThread().setContextClassLoader(newLoader);
        System.out.println(" All font jars added to classpath successfully.");
        JasperReportsContext ctx = DefaultJasperReportsContext.getInstance();
        Object helper;
        try {
            Method m = SimpleFontExtensionHelper.class.getMethod("getInstance", JasperReportsContext.class);
            helper = m.invoke(null, ctx);
        } catch (NoSuchMethodException e) {
            Method m = SimpleFontExtensionHelper.class.getMethod("getInstance");
            helper = m.invoke(null);
        }
        try {
            Method reload = SimpleFontExtensionHelper.class.getMethod("loadFontFamilies");
            reload.invoke(helper);
            System.out.println(" Font families reloaded via reflection.");
        } catch (NoSuchMethodException e) {
            System.out.println(" loadFontFamilies() method not found; fonts should auto-register from extension jars.");
        }
        System.out.println(" Jasper font extension JARs are active.");
    }
}
