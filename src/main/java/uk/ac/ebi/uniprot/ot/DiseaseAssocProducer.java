package uk.ac.ebi.uniprot.ot;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.kraken.interfaces.uniprot.UniProtEntry;
import uk.ac.ebi.uniprot.ot.converter.UniProtDiseaseAssocCollator;
import uk.ac.ebi.uniprot.ot.input.UniProtEvSource;
import uk.ac.ebi.uniprot.ot.model.base.Base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Directs the evidence string generation, given the appropriate input source(s).
 *
 * @author Edd
 */
public class DiseaseAssocProducer {
  // logger
  private static final Logger LOGGER = LoggerFactory.getLogger(DiseaseAssocProducer.class);

  private ObjectMapper objectMapper;

  private Iterator<UniProtEntry> entryIterator;
  private Path outputFile;
  private UniProtDiseaseAssocCollator evStringCollator;
  private ObjectMapper jsonCreator;
  private int max;
  private AtomicInteger writeCount;
  private boolean verbose;

  @Inject
  public DiseaseAssocProducer(
      Iterator<UniProtEntry> entryIterator,
      @Named("outputFile") File outputFile,
      UniProtDiseaseAssocCollator evStringCollator)
      throws IOException {

    this.outputFile = outputFile.toPath();
    this.entryIterator = entryIterator;
    this.evStringCollator = evStringCollator;
    this.jsonCreator = new ObjectMapper();
    this.max = -1;
    this.writeCount = new AtomicInteger(0);
    this.objectMapper = new ObjectMapper();
  }

  /** Coordinates the transformation of a given input source to a file of evidence strings. */
  public void produce() {
    if (this.entryIterator != null) {
      // create output file
      try (BufferedWriter writer = Files.newBufferedWriter(outputFile, Charset.defaultCharset())) {

        LOGGER.debug("Generating data dump");
        while (entryIterator.hasNext() && (max == -1 || writeCount.get() < max)) {
          UniProtEntry uniProtEntry = entryIterator.next();
          UniProtEvSource evSource = createEvSource(uniProtEntry);
          convertSourceAndWrite(writer, evSource);
        }

      } catch (IOException exception) {
        LOGGER.error("Error writing to output file, {}", outputFile.getFileName());
        LOGGER.error("Exception Info: {}", exception);
      }

      LOGGER.info(
          "# successful conversions {}",
          evStringCollator.getConversionReport().getTotalItemsSucceeded());
      LOGGER.info(
          "# failed conversions {}", evStringCollator.getConversionReport().getTotalItemsFailed());
      LOGGER.debug("Finished Open Targets evidence string generation.");
    } else {
      LOGGER.error("EntryIterator was null. Cannot produce evidence strings.");
    }
  }

  static UniProtEvSource createEvSource(UniProtEntry uniProtEntry) {
    UniProtEvSource eSource = new UniProtEvSource();
    eSource.setUniProtEntry(uniProtEntry);
    return eSource;
  }

  private void convertSourceAndWrite(BufferedWriter writer, UniProtEvSource evSource)
      throws IOException {
    // create evidence strings
    Collection<Base> bases = this.evStringCollator.convert(evSource);

    // .. and write them
    for (Base base : bases) {
      if (this.verbose) {
        LOGGER.debug(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(base));
      }

      if (writeCount.getAndIncrement() != 0) {
        writer.append("\n");
      }

      writer.append(jsonCreator.writeValueAsString(base));
    }
  }

  @Inject
  public void setMax(@Named("max") int max) {
    this.max = max;
  }

  @Inject
  public void setVerbose(@Named("verbose") boolean verbose) {
    this.verbose = verbose;
  }
}
