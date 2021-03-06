/*
 * Open Source Software published under the Apache Licence, Version 2.0.
 */

package io.github.vocabhunter.analysis.file;

import io.github.vocabhunter.analysis.core.VocabHunterException;
import io.github.vocabhunter.analysis.model.Analyser;
import io.github.vocabhunter.analysis.model.AnalysisResult;
import io.github.vocabhunter.analysis.session.EnrichedSessionState;
import io.github.vocabhunter.analysis.session.FileNameTool;
import io.github.vocabhunter.analysis.session.SessionSerialiser;
import io.github.vocabhunter.analysis.session.SessionState;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.text.BreakIterator;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

import static io.github.vocabhunter.analysis.core.CoreConstants.LOCALE;

@Singleton
public class FileStreamer {
    private static final Logger LOG = LoggerFactory.getLogger(FileStreamer.class);

    private final Analyser analyser;

    @Inject
    public FileStreamer(final Analyser analyser) {
        this.analyser = analyser;
    }

    public List<String> lines(final Path file) {
        Metadata metadata = new Metadata();

        try (InputStream in = TikaInputStream.get(file, metadata))  {
            Tika tika = new Tika();
            String fullText = tika.parseToString(in, metadata, -1);

            if (StringUtils.isBlank(fullText)) {
                throw new VocabHunterException(String.format("No text in file '%s'", file));
            } else {
                return splitToList(fullText);
            }
        } catch (IOException | TikaException e) {
            throw new VocabHunterException(String.format("Unable to read file '%s'", file), e);
        }
    }

    private List<String> splitToList(final String text) {
        List<String> list = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(LOCALE);
        iterator.setText(text);
        int start = iterator.first();
        int end = iterator.next();

        while (end != BreakIterator.DONE) {
            String line = text.substring(start, end);

            if (StringUtils.isNoneBlank(line)) {
                list.add(line.replaceAll("\\s+", " ").trim());
            }
            start = end;
            end = iterator.next();
        }

        return list;
    }

    public AnalysisResult analyse(final Path file) {
        Instant start = Instant.now();
        List<String> stream = lines(file);
        String filename = FileNameTool.filename(file);
        AnalysisResult result = analyser.analyse(stream, filename);
        int count = result.getOrderedUses().size();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        LOG.info("Analysed text and found {} words in {}ms ({})", count, duration.toMillis(), filename);

        return result;
    }

    public EnrichedSessionState createNewSession(final Path file) {
        AnalysisResult model = analyse(file);

        return new EnrichedSessionState(new SessionState(model));
    }

    public EnrichedSessionState createOrOpenSession(final Path file) {
        try {
            return SessionSerialiser.read(file);
        } catch (final VocabHunterException e) {
            LOG.debug("{} is not a session file", file, e);
            return createNewSession(file);
        }
    }
}
