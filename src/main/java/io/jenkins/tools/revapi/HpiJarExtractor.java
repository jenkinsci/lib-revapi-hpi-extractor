package io.jenkins.tools.revapi;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang3.StringUtils;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.java.spi.JarExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * If the provided archive is a Jenkins Plugin Archive (HPI file) then only those entries that are considered for API
 * analysis that are part of the [plugin-artifactId].jar file in the WEB-INF/lib folder.
 *
 * <p><b>Extension ID:</b> {@code hpi}
 *
 * @author Ullrich Hafner
 */
public class HpiJarExtractor implements JarExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(HpiJarExtractor.class);
    private static final String LIB_FOLDER = "WEB-INF/lib/";

    private static final Pattern HPI_PATTERN = Pattern.compile("(?<groupId>.*):(?<artifactId>.*):hpi:(?<version>.*)");

    @Override
    public Optional<InputStream> extract(final Archive archive) {
        Matcher matcher = HPI_PATTERN.matcher(archive.getName());
        if (matcher.matches()) {
            System.out.println("--- Inspecting " + archive.getName());

            return extractJarFile(archive, matcher.group("artifactId"));
        }
        else {
            return Optional.empty();
        }
    }

    /**
     * Tries to locate the jar file with the actual classes inside of the WEB-INF/lib folder.
     *
     * @param archive
     *         the HPI archive
     * @param artifactId
     *         the ID of the artifact to extract
     *
     * @return a stream to the extracted archive
     */
    private Optional<InputStream> extractJarFile(final Archive archive, final String artifactId) {
        try (ZipInputStream hpiPluginArchive = new ZipInputStream(archive.openStream())) {
            ZipEntry hpiEntry = hpiPluginArchive.getNextEntry();
            int prefixLen = LIB_FOLDER.length();
            while (hpiEntry != null) {
                if (StringUtils.startsWith(hpiEntry.getName(), LIB_FOLDER + artifactId)) {
                    return extractPluginJar(hpiPluginArchive, hpiEntry.getName());
                }

                hpiEntry = hpiPluginArchive.getNextEntry();
            }

        }
        catch (IOException ignore) {
            // log
        }
        return Optional.empty();
    }

    private Optional<InputStream> extractPluginJar(final ZipInputStream hpiPluginArchive, final String name) {
        try {
            System.out.println("--- Extracting " + name);

            byte[] buffer = new byte[2048];

            Path path = Files.createTempFile("revapi-java-jarextract-hpi", null);
            try (FileOutputStream fos = new FileOutputStream(path.toFile());
                 BufferedOutputStream bos = new BufferedOutputStream(fos, buffer.length)) {

                int len;
                while ((len = hpiPluginArchive.read(buffer)) > 0) {
                    bos.write(buffer, 0, len);
                }
            }

            return Optional.of(new FileInputStream(path.toFile()) {
                @Override
                public void close() throws IOException {
                    super.close();

                    Files.delete(path);
                }
            });
        }
        catch (IOException exception) {
            LOG.error("Can't extract " + name, exception);
        }
        return Optional.empty();
    }

    @Override
    public String getExtensionId() {
        return "hpi";
    }

    @Override
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("/META-INF/hpiJarExtract-config-schema.json"),
                Charset.forName("UTF-8"));
    }

    @Override
    public void initialize(final AnalysisContext analysisContext) {
        // no configuration required
    }
}