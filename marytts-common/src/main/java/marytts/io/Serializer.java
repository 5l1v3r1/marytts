package marytts.io;

import java.io.File;
import marytts.data.Utterance;
import marytts.io.MaryIOException;

/**
 *
 *
 * @author <a href="mailto:slemaguer@coli.uni-saarland.de">Sébastien Le
 *         Maguer</a>
 */
public interface Serializer {
	public Utterance load(File file) throws MaryIOException;

	public void save(File file, Utterance utt) throws MaryIOException;

	public String toString(Utterance utt) throws MaryIOException;

	public Utterance fromString(String content) throws MaryIOException;

}
