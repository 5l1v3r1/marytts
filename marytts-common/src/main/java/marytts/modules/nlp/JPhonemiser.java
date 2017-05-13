/**
 * Copyright 2002-2008 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * This file is part of MARY TTS.
 *
 * MARY TTS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package marytts.modules.nlp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import marytts.datatypes.MaryData;
import marytts.datatypes.MaryXML;
import marytts.exceptions.MaryConfigurationException;
import marytts.fst.FSTLookup;
import marytts.modules.nlp.phonemiser.AllophoneSet;
import marytts.modules.nlp.phonemiser.TrainedLTS;
import marytts.modules.InternalModule;
import marytts.server.MaryProperties;
import marytts.util.MaryRuntimeUtils;
import marytts.util.MaryUtils;
import marytts.util.dom.MaryDomUtils;

import marytts.data.Utterance;
import marytts.data.Sequence;
import marytts.data.Relation;
import marytts.data.SupportedSequenceType;
import marytts.data.utils.IntegerPair;
import marytts.data.item.linguistic.Word;
import marytts.data.item.phonology.Phoneme;
import marytts.data.item.phonology.Syllable;
import marytts.data.item.phonology.Accent;

import com.google.common.base.Splitter;

/**
 * The phonemiser module -- java implementation.
 *
 * @author Marc Schr&ouml;der, Sathish
 * @author ingmar
 */

public class JPhonemiser extends InternalModule {
	protected final String SYL_SEP = "-";
	protected final String FIRST_STRESS = "'";
	protected final String SECOND_STRESS = ",";

	protected Map<String, List<String>> userdict;
	protected FSTLookup lexicon;
	protected TrainedLTS lts;
	protected boolean removeTrailingOneFromPhones = true;

	protected AllophoneSet allophoneSet;

	protected Pattern punctuationPosRegex;
	protected Pattern unpronounceablePosRegex;

	public JPhonemiser(String propertyPrefix) throws IOException, MaryConfigurationException {
		this("JPhonemiser", propertyPrefix + "allophoneset", propertyPrefix + "userdict", propertyPrefix + "lexicon",
				propertyPrefix + "lettertosound", propertyPrefix + "removeTrailingOneFromPhones");
	}

	/**
	 * Constructor providing the individual filenames of files that are
	 * required.
	 *
	 * @param componentName
	 *            componentName
	 * @param allophonesProperty
	 *            allophonesProperty
	 * @param userdictProperty
	 *            userdictProperty
	 * @param lexiconProperty
	 *            lexiconProperty
	 * @param ltsProperty
	 *            ltsProperty
	 * @throws IOException
	 *             IOException
	 * @throws MaryConfigurationException
	 *             MaryConfigurationException
	 */
	public JPhonemiser(String componentName, String allophonesProperty, String userdictProperty, String lexiconProperty,
			String ltsProperty) throws IOException, MaryConfigurationException {
		this(componentName, allophonesProperty, userdictProperty, lexiconProperty, ltsProperty, null);
	}

	/**
	 * Constructor providing the individual filenames of files that are
	 * required.
	 *
	 * @param componentName
	 *            componentName
	 * @param allophonesProperty
	 *            allophonesProperty
	 * @param userdictProperty
	 *            userdictProperty
	 * @param lexiconProperty
	 *            lexiconProperty
	 * @param ltsProperty
	 *            ltsProperty
	 * @param removetrailingonefromphonesProperty
	 *            removetrailingonefromphonesProperty
	 * @throws IOException
	 *             IOException
	 * @throws MaryConfigurationException
	 *             MaryConfigurationException
	 */
	public JPhonemiser(String componentName, String allophonesProperty, String userdictProperty, String lexiconProperty,
			String ltsProperty, String removetrailingonefromphonesProperty)
			throws IOException, MaryConfigurationException {
		super(componentName, MaryRuntimeUtils.needAllophoneSet(allophonesProperty).getLocale());
		allophoneSet = MaryRuntimeUtils.needAllophoneSet(allophonesProperty);
		// userdict is optional
		String userdictFilename = MaryProperties.getFilename(userdictProperty); // may
																				// be
																				// null
		if (userdictFilename != null) {
			if (new File(userdictFilename).exists()) {
				userdict = readLexicon(userdictFilename);
			} else {
				logger.info("User dictionary '" + userdictFilename + "' for locale '" + getLocale()
						+ "' does not exist. Ignoring.");
			}
		}
		InputStream lexiconStream = MaryProperties.needStream(lexiconProperty);
		logger.debug("Loading lexicon " + MaryProperties.getProperty(lexiconProperty, "<null>") + "for locale "
				+ getLocale() + " from the property " + lexiconProperty);
		lexicon = new FSTLookup(lexiconStream, lexiconProperty);
		InputStream ltsStream = MaryProperties.needStream(ltsProperty);
		if (removetrailingonefromphonesProperty != null) {
			this.removeTrailingOneFromPhones = MaryProperties.getBoolean(removetrailingonefromphonesProperty, true);
		}
		lts = new TrainedLTS(allophoneSet, ltsStream, this.removeTrailingOneFromPhones);
	}

	public void startup() throws Exception {
		super.startup();
		setPunctuationPosRegex();
		setUnpronounceablePosRegex();
	}

	public MaryData process(MaryData d) throws Exception {
		Utterance utt = d.getData();

		Sequence<Word> words = (Sequence<Word>) utt.getSequence(SupportedSequenceType.WORD);
		Sequence<Syllable> syllables = new Sequence<Syllable>();
		ArrayList<IntegerPair> alignment_word_phone = new ArrayList<IntegerPair>();

		Sequence<Phoneme> phones = new Sequence<Phoneme>();
		ArrayList<IntegerPair> alignment_syllable_phone = new ArrayList<IntegerPair>();

		Relation rel_words_sent = utt.getRelation(SupportedSequenceType.SENTENCE, SupportedSequenceType.WORD)
				.getReverse();
		HashSet<IntegerPair> alignment_word_phrase = new HashSet<IntegerPair>();

		for (int i_word = 0; i_word < words.size(); i_word++) {
			Word w = words.get(i_word);

			String text;

			if (w.soundsLike() != null)
				text = w.soundsLike();
			else
				text = w.getText();

			// Get POS
			String pos = w.getPOS();

			// Ok adapt phonemes now
			ArrayList<String> phonetisation_string = new ArrayList<String>();
			if (maybePronounceable(text, pos)) {
				// If text consists of several parts (e.g., because that was
				// inserted into the sounds_like attribute), each part
				// is transcribed separately.
				StringBuilder ph = new StringBuilder();
				String g2p_method = null;
				StringTokenizer st = new StringTokenizer(text, " -");
				while (st.hasMoreTokens()) {
					String graph = st.nextToken();
					StringBuilder helper = new StringBuilder();
					String phon = phonemise(graph, pos, helper);

					// FIXME: what does it mean : null result should not be
					// processed
					if (phon == null)
						continue;

					if (ph.length() == 0)
						g2p_method = helper.toString();

					phonetisation_string.add(phon);
				}

				if (phonetisation_string.size() > 0) {
					createSubStructure(w, phonetisation_string, allophoneSet, syllables, phones,
							alignment_syllable_phone, i_word, alignment_word_phone);

					// Adapt G2P method
					w.setG2PMethod(g2p_method);
				}
			} else {
				alignment_word_phone.add(new IntegerPair(i_word, phones.size()));
				phones.add(new Phoneme("_"));
			}
		}

		// Relation word/syllable
		utt.addSequence(SupportedSequenceType.PHONE, phones);
		Relation rel_word_phone = new Relation(words, phones, alignment_word_phone);
		utt.setRelation(SupportedSequenceType.WORD, SupportedSequenceType.PHONE, rel_word_phone);

		utt.addSequence(SupportedSequenceType.SYLLABLE, syllables);
		Relation rel_syllable_phone = new Relation(syllables, phones, alignment_syllable_phone);
		utt.setRelation(SupportedSequenceType.SYLLABLE, SupportedSequenceType.PHONE, rel_syllable_phone);

		MaryData result = new MaryData(d.getLocale(), utt);
		return result;
	}

	protected void createSubStructure(Word w, ArrayList<String> phonetisation_string, AllophoneSet allophoneSet,
			Sequence<Syllable> syllables, Sequence<Phoneme> phones, ArrayList<IntegerPair> alignment_syllable_phone,
			int word_index, ArrayList<IntegerPair> alignment_word_phone) throws Exception {

		int stress = 0;
		int phone_offset = phones.size();
		Accent accent = null;
		Phoneme tone = null;

		for (String syl_string : phonetisation_string) {
			if (syl_string.trim().isEmpty()) {
				continue;
			}

			logger.debug("Dealing with \"" + syl_string + "\"");
			Splitter syl_string_plitter = Splitter.on(' ').omitEmptyStrings().trimResults();

			Iterable<String> syl_tokens = syl_string_plitter.split(syl_string);
			for (String token : syl_tokens) {

				// Syllable separator
				if (token.equals(SYL_SEP)) {
					// Create the syllable
					syllables.add(new Syllable(tone, stress, accent)); // FIXME:
																		// ho to
																		// get
																		// the
																		// tone
																		// ?

					// Update the phone/syllable relation
					for (; phone_offset < phones.size(); phone_offset++) {
						alignment_syllable_phone.add(new IntegerPair(syllables.size() - 1, phone_offset));
						alignment_word_phone.add(new IntegerPair(word_index, phone_offset));
					}

					// Reinit for the next part
					tone = null;
					stress = 0;
					accent = null;
				}
				// First stress
				else if (token.equals(FIRST_STRESS)) {
					stress = 1;
					accent = w.getAccent();
				}
				// Second stress
				else if (token.equals(SECOND_STRESS)) {
					stress = 2;
				} else {
					Phoneme cur_ph = new Phoneme(token);
					phones.add(cur_ph);
				}
			}

			// Create the syllable
			syllables.add(new Syllable(tone, stress, accent)); // FIXME: ho to
																// get the tone
																// ?

			// Update the phone/syllable relation
			for (; phone_offset < phones.size(); phone_offset++) {
				alignment_syllable_phone.add(new IntegerPair(syllables.size() - 1, phone_offset));
				alignment_word_phone.add(new IntegerPair(word_index, phone_offset));
			}
		}
	}

	/**
	 * Phonemise the word text. This starts with a simple lexicon lookup,
	 * followed by some heuristics, and finally applies letter-to-sound rules if
	 * nothing else was successful.
	 *
	 * @param text
	 *            the textual (graphemic) form of a word.
	 * @param pos
	 *            the part-of-speech of the word
	 * @param g2pMethod
	 *            This is an awkward way to return a second String parameter via
	 *            a StringBuilder. If a phonemisation of the text is found, this
	 *            parameter will be filled with the method of phonemisation
	 *            ("lexicon", ... "rules").
	 * @return a phonemisation of the text if one can be generated, or null if
	 *         no phonemisation method was successful.
	 */
	public String phonemise(String text, String pos, StringBuilder g2pMethod) {
		// First, try a simple userdict and lexicon lookup:

		String result = userdictLookup(text, pos);
		if (result != null) {
			g2pMethod.append("userdict");
			return result;
		}

		result = lexiconLookup(text, pos);
		if (result != null) {
			g2pMethod.append("lexicon");
			return result;
		}

		// Lookup attempts failed. Try normalising exotic letters
		// (diacritics on vowels, etc.), look up again:
		String normalised = MaryUtils.normaliseUnicodeLetters(text, getLocale());
		if (!normalised.equals(text)) {
			result = userdictLookup(normalised, pos);
			if (result != null) {
				g2pMethod.append("userdict");
				return result;
			}
			result = lexiconLookup(normalised, pos);
			if (result != null) {
				g2pMethod.append("lexicon");
				return result;
			}
		}

		// Cannot find it in the lexicon -- apply letter-to-sound rules
		// to the normalised form

		String phones = lts.predictPronunciation(text);
		try {
			result = lts.syllabify(phones);
		} catch (IllegalArgumentException e) {
			logger.error(String.format("Problem with token <%s> [%s]: %s", text, phones, e.getMessage()));
		}
		if (result != null) {
			g2pMethod.append("rules");
			return result;
		}

		return null;
	}

	/**
	 * Look a given text up in the (standard) lexicon. part-of-speech is used in
	 * case of ambiguity.
	 *
	 * @param text
	 *            text
	 * @param pos
	 *            pos
	 * @return null if text == null or text.length is 0, null if entries.length
	 *         is 0, entries[0] otherwise
	 */
	public String lexiconLookup(String text, String pos) {
		if (text == null || text.length() == 0)
			return null;
		String[] entries;
		entries = lexiconLookupPrimitive(text, pos);
		// If entry is not found directly, try the following changes:
		// - lowercase the word
		// - all lowercase but first uppercase
		if (entries.length == 0) {
			text = text.toLowerCase(getLocale());
			entries = lexiconLookupPrimitive(text, pos);
		}
		if (entries.length == 0) {
			text = text.substring(0, 1).toUpperCase(getLocale()) + text.substring(1);
			entries = lexiconLookupPrimitive(text, pos);
		}

		if (entries.length == 0)
			return null;
		return entries[0];
	}

	private String[] lexiconLookupPrimitive(String text, String pos) {
		String[] entries;
		if (pos != null) { // look for pos-specific version first
			entries = lexicon.lookup(text + pos);
			if (entries.length == 0) { // not found -- lookup without pos
				entries = lexicon.lookup(text);
			}
		} else {
			entries = lexicon.lookup(text);
		}
		return entries;
	}

	/**
	 * look a given text up in the userdict. part-of-speech is used in case of
	 * ambiguity.
	 *
	 * @param text
	 *            text
	 * @param pos
	 *            pos
	 * @return null if userdict is null or text is null or text.length is 0,
	 *         null if entries is null, transcr otherwise
	 */
	public String userdictLookup(String text, String pos) {
		if (userdict == null || text == null || text.length() == 0)
			return null;
		List<String> entries = userdict.get(text);
		// If entry is not found directly, try the following changes:
		// - lowercase the word
		// - all lowercase but first uppercase
		if (entries == null) {
			text = text.toLowerCase(getLocale());
			entries = userdict.get(text);
		}
		if (entries == null) {
			text = text.substring(0, 1).toUpperCase(getLocale()) + text.substring(1);
			entries = userdict.get(text);
		}

		if (entries == null)
			return null;

		String transcr = null;
		for (String entry : entries) {
			String[] parts = entry.split("\\|");
			transcr = parts[0];
			if (parts.length > 1 && pos != null) {
				StringTokenizer tokenizer = new StringTokenizer(entry);
				while (tokenizer.hasMoreTokens()) {
					String onePos = tokenizer.nextToken();
					if (pos.equals(onePos))
						return transcr; // found
				}
			}
		}
		// no match of POS: return last entry
		return transcr;
	}

	/**
	 * Access the allophone set underlying this phonemiser.
	 *
	 * @return allophoneSet
	 */
	public AllophoneSet getAllophoneSet() {
		return allophoneSet;
	}

	/**
	 * Read a lexicon. Lines must have the format
	 *
	 * graphemestring | phonestring | optional-parts-of-speech
	 *
	 * The pos-item is optional. Different pos's belonging to one grapheme chain
	 * may be separated by whitespace
	 *
	 *
	 * @param lexiconFilename
	 *            lexiconFilename
	 * @throws IOException
	 *             IOException
	 * @return fLexicon
	 */
	protected Map<String, List<String>> readLexicon(String lexiconFilename) throws IOException {
		logger.debug(String.format("Reading lexicon from '%s'", lexiconFilename));
		String line;
		Map<String, List<String>> fLexicon = new HashMap<String, List<String>>();

		BufferedReader lexiconFile = new BufferedReader(
				new InputStreamReader(new FileInputStream(lexiconFilename), "UTF-8"));
		while ((line = lexiconFile.readLine()) != null) {
			// Ignore empty lines and comments:
			if (line.trim().equals("") || line.startsWith("#"))
				continue;

			String[] lineParts = line.split("\\s*\\|\\s*");
			String graphStr = lineParts[0];
			String phonStr = null;
			try {
				phonStr = lineParts[1];
			} catch (ArrayIndexOutOfBoundsException e) {
				logger.warn(String.format("Lexicon '%s': missing transcription for '%s'", lexiconFilename, graphStr));
				continue;
			}
			try {
				allophoneSet.splitIntoAllophones(phonStr);
			} catch (IllegalArgumentException e) {
				logger.warn(String.format("Lexicon '%s': invalid entry for '%s': %s", lexiconFilename, graphStr,
						e.getMessage()));
				continue;
			}
			String phonPosStr = phonStr;
			if (lineParts.length > 2) {
				String pos = lineParts[2];
				if (!pos.trim().equals(""))
					phonPosStr += "|" + pos;
			}

			List<String> transcriptions = fLexicon.get(graphStr);
			if (null == transcriptions) {
				transcriptions = new ArrayList<String>();
				fLexicon.put(graphStr, transcriptions);
			}
			transcriptions.add(phonPosStr);
		}
		lexiconFile.close();
		return fLexicon;
	}

	/**
	 * Compile a regex pattern used to determine whether tokens are processed as
	 * punctuation or not, based on whether their <code>pos</code> attribute
	 * matches the pattern.
	 *
	 */
	protected void setPunctuationPosRegex() {
		String language = getLocale().getLanguage();
		String propertyName = language + ".pos.punct.regex";
		String defaultRegex = "\\$PUNCT";
		String regex = MaryProperties.getProperty(propertyName);
		if (regex == null) {
			logger.debug(String.format("Property %s not set, using default", propertyName));
			regex = defaultRegex;
		} else {
			logger.debug(String.format("Using property %s", propertyName));
		}
		try {
			punctuationPosRegex = Pattern.compile(regex);
		} catch (PatternSyntaxException e) {
			logger.error(String.format("Could not compile regex pattern /%s/, using default instead", regex));
			punctuationPosRegex = Pattern.compile(defaultRegex);
		}
		logger.debug(String.format("Punctuation regex pattern set to /%s/", punctuationPosRegex));
	}

	/**
	 * Compile a regex pattern used to determine whether tokens are processed as
	 * unprounounceable or not, based on whether their <code>pos</code>
	 * attribute matches the pattern.
	 *
	 */
	protected void setUnpronounceablePosRegex() {
		String language = getLocale().getLanguage();
		String propertyName = language + ".pos.unprounounceable.regex";
		String defaultRegex = "^[^a-zA-Z]+$";
		String regex = MaryProperties.getProperty(propertyName);
		if (regex == null) {
			logger.debug(String.format("Property %s not set, using default", propertyName));
			regex = defaultRegex;
		} else {
			logger.debug(String.format("Using property %s", propertyName));
		}
		try {
			unpronounceablePosRegex = Pattern.compile(regex);
		} catch (PatternSyntaxException e) {
			logger.error(String.format("Could not compile regex pattern /%s/, using default instead", regex));
			unpronounceablePosRegex = Pattern.compile(defaultRegex);
		}
		logger.debug(String.format("Punctuation regex pattern set to /%s/", unpronounceablePosRegex));
	}

	/**
	 * Based on the regex compiled in {@link #setPunctuationPosRegex()},
	 * determine whether a given POS string is classified as punctuation
	 *
	 * @param pos
	 *            the POS tag
	 * @return <b>true</b> if the POS tag matches the regex pattern;
	 *         <b>false</b> otherwise
	 * @throws NullPointerException
	 *             if the regex pattern is null (because it hasn't been set
	 *             during module startup)
	 *
	 */
	public boolean isPosPunctuation(String pos) {
		if (pos != null && punctuationPosRegex.matcher(pos).matches()) {
			return true;
		}
		return false;
	}

	public boolean isUnpronounceable(String pos) {
		if (pos != null && unpronounceablePosRegex.matcher(pos).matches()) {
			return true;
		}
		return false;
	}

	/**
	 * Determine whether token should be pronounceable, based on text and POS
	 * tag.
	 *
	 * @param text
	 *            the text of the token
	 * @param pos
	 *            the POS tag of the token
	 * @return <b>false</b> if the text is empty, or if it contains no word
	 *         characters <em>and</em> the POS tag indicates punctuation;
	 *         <b>true</b> otherwise
	 */
	public boolean maybePronounceable(String text, String pos) {
		// does text contain anything at all?
		if (text == null || text.isEmpty()) {
			return false;
		}

		// does text contain at least one word character?
		if (text.matches(".*\\w.*")) {
			return true;
		}

		// does POS tag indicate punctuation?
		if (isPosPunctuation(pos)) {
			return false;
		}

		// does POS tag indicate punctuation?
		if (isUnpronounceable(pos)) {
			return false;
		}

		// by default, just try to pronounce anyway
		return true;
	}
}
