/**
 * Copyright 2006-2007 DFKI GmbH.
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
package marytts.features;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import marytts.modules.phonemiser.AllophoneSet;

public class FeatureProcessorManager 
{
    protected Map<String,MaryFeatureProcessor> processors;

    protected Map<String, String[]> phonefeatures2values;
    
    public FeatureProcessorManager()
    {
        processors = new TreeMap<String,MaryFeatureProcessor>();

        MaryGenericFeatureProcessors.TargetElementNavigator segment = new MaryGenericFeatureProcessors.SegmentNavigator();
        MaryGenericFeatureProcessors.TargetElementNavigator prevSegment = new MaryGenericFeatureProcessors.PrevSegmentNavigator();
        MaryGenericFeatureProcessors.TargetElementNavigator nextSegment = new MaryGenericFeatureProcessors.NextSegmentNavigator();
        MaryGenericFeatureProcessors.TargetElementNavigator syllable = new MaryGenericFeatureProcessors.SyllableNavigator();
        MaryGenericFeatureProcessors.TargetElementNavigator prevSyllable = new MaryGenericFeatureProcessors.PrevSyllableNavigator();
        MaryGenericFeatureProcessors.TargetElementNavigator nextSyllable = new MaryGenericFeatureProcessors.NextSyllableNavigator();
        MaryGenericFeatureProcessors.TargetElementNavigator nextNextSyllable = new MaryGenericFeatureProcessors.NextNextSyllableNavigator();
        MaryGenericFeatureProcessors.TargetElementNavigator lastWord = new MaryGenericFeatureProcessors.LastWordInSentenceNavigator();

        addFeatureProcessor(new MaryGenericFeatureProcessors.Edge());
        addFeatureProcessor(new MaryGenericFeatureProcessors.HalfPhoneLeftRight());
        addFeatureProcessor(new MaryGenericFeatureProcessors.Accented("accented", syllable));
        addFeatureProcessor(new MaryGenericFeatureProcessors.Stressed("stressed", syllable));
        addFeatureProcessor(new MaryGenericFeatureProcessors.Stressed("prev_stressed", prevSyllable));
        addFeatureProcessor(new MaryGenericFeatureProcessors.Stressed("next_stressed", nextSyllable));
        addFeatureProcessor(new MaryGenericFeatureProcessors.WordNumSyls());
        addFeatureProcessor(new MaryGenericFeatureProcessors.PosInSyl());
        addFeatureProcessor(new MaryGenericFeatureProcessors.SylBreak("syl_break", syllable));
        addFeatureProcessor(new MaryGenericFeatureProcessors.SylBreak("prev_syl_break", prevSyllable));
        addFeatureProcessor(new MaryGenericFeatureProcessors.PositionType());
        addFeatureProcessor(new MaryGenericFeatureProcessors.BreakIndex());
        addFeatureProcessor(new MaryGenericFeatureProcessors.IsPause("prev_is_pause", prevSegment));
        addFeatureProcessor(new MaryGenericFeatureProcessors.IsPause("next_is_pause", nextSegment));
        addFeatureProcessor(new MaryGenericFeatureProcessors.TobiAccent("tobi_accent", syllable));
        addFeatureProcessor(new MaryGenericFeatureProcessors.TobiAccent("next_tobi_accent", nextSyllable));
        addFeatureProcessor(new MaryGenericFeatureProcessors.TobiAccent("nextnext_tobi_accent", nextNextSyllable));
        addFeatureProcessor(new MaryGenericFeatureProcessors.TobiEndtone("tobi_endtone", syllable));
        addFeatureProcessor(new MaryGenericFeatureProcessors.TobiEndtone("next_tobi_endtone", nextSyllable));
        addFeatureProcessor(new MaryGenericFeatureProcessors.TobiEndtone("nextnext_tobi_endtone", nextNextSyllable));
        addFeatureProcessor(new MaryGenericFeatureProcessors.WordPunc("sentence_punc", lastWord));
        addFeatureProcessor(new MaryGenericFeatureProcessors.SylsFromPhraseStart());
        addFeatureProcessor(new MaryGenericFeatureProcessors.SylsFromPhraseEnd());
        addFeatureProcessor(new MaryGenericFeatureProcessors.StressedSylsFromPhraseStart());
        addFeatureProcessor(new MaryGenericFeatureProcessors.StressedSylsFromPhraseEnd());
        addFeatureProcessor(new MaryGenericFeatureProcessors.AccentedSylsFromPhraseStart());
        addFeatureProcessor(new MaryGenericFeatureProcessors.AccentedSylsFromPhraseEnd());
        addFeatureProcessor(new MaryGenericFeatureProcessors.SylsFromPrevStressed());
        addFeatureProcessor(new MaryGenericFeatureProcessors.SylsToNextStressed());
        addFeatureProcessor(new MaryGenericFeatureProcessors.SylsFromPrevAccent());
        addFeatureProcessor(new MaryGenericFeatureProcessors.SylsToNextAccent());
        addFeatureProcessor(new MaryGenericFeatureProcessors.WordNumSegs());
        addFeatureProcessor(new MaryGenericFeatureProcessors.SegsFromSylStart());
        addFeatureProcessor(new MaryGenericFeatureProcessors.SegsFromSylEnd());
        addFeatureProcessor(new MaryGenericFeatureProcessors.SylNumSegs());
        addFeatureProcessor(new MaryGenericFeatureProcessors.SentenceNumPhrases());
        addFeatureProcessor(new MaryGenericFeatureProcessors.SentenceNumWords());
        addFeatureProcessor(new MaryGenericFeatureProcessors.PhraseNumWords());
        addFeatureProcessor(new MaryGenericFeatureProcessors.PhraseNumSyls());
        addFeatureProcessor(new MaryGenericFeatureProcessors.SegsFromWordStart());
        addFeatureProcessor(new MaryGenericFeatureProcessors.SegsFromWordEnd());
        addFeatureProcessor(new MaryGenericFeatureProcessors.SylsFromWordStart());
        addFeatureProcessor(new MaryGenericFeatureProcessors.SylsFromWordEnd());
        addFeatureProcessor(new MaryGenericFeatureProcessors.WordsFromPhraseStart());
        addFeatureProcessor(new MaryGenericFeatureProcessors.WordsFromPhraseEnd());
        addFeatureProcessor(new MaryGenericFeatureProcessors.WordsFromSentenceStart());
        addFeatureProcessor(new MaryGenericFeatureProcessors.WordsFromSentenceEnd());
        addFeatureProcessor(new MaryGenericFeatureProcessors.PhrasesFromSentenceStart());
        addFeatureProcessor(new MaryGenericFeatureProcessors.PhrasesFromSentenceEnd());
        addFeatureProcessor(new MaryGenericFeatureProcessors.NextAccent());
        addFeatureProcessor(new MaryGenericFeatureProcessors.PrevAccent());
        addFeatureProcessor(new MaryGenericFeatureProcessors.PhraseEndtone());
        addFeatureProcessor(new MaryGenericFeatureProcessors.PrevPhraseEndtone());
        addFeatureProcessor(new MaryGenericFeatureProcessors.PrevPunctuation());
        addFeatureProcessor(new MaryGenericFeatureProcessors.NextPunctuation());
        addFeatureProcessor(new MaryGenericFeatureProcessors.WordsFromPrevPunctuation());
        addFeatureProcessor(new MaryGenericFeatureProcessors.WordsToNextPunctuation());
        addFeatureProcessor(new MaryGenericFeatureProcessors.Selection_Prosody(syllable));
        addFeatureProcessor(new MaryGenericFeatureProcessors.SentenceStyle());
        addFeatureProcessor(new MaryGenericFeatureProcessors.UnitDuration());
        addFeatureProcessor(new MaryGenericFeatureProcessors.UnitLogF0());

        // Set up default values for phone features:
        phonefeatures2values = new HashMap<String, String[]>();
        // cplace: 0-n/a l-labial a-alveolar p-palatal b-labio_dental d-dental v-velar g-?
        phonefeatures2values.put("cplace", new String[] { "0", "l", "a", "p", "b", "d", "v", "g"});
        // ctype: 0-n/a s-stop f-fricative a-affricative n-nasal l-liquid r-r
        phonefeatures2values.put("ctype", new String[] {"0", "s", "f", "a", "n", "l", "r"});
        // cvox: 0=n/a +=on -=off
        phonefeatures2values.put("cvox", new String[] {"0", "+", "-"});
        // vc: 0=n/a +=vowel -=consonant
        phonefeatures2values.put("vc", new String[] {"0", "+", "-"});
        // vfront: 0-n/a 1-front  2-mid 3-back
        phonefeatures2values.put("vfront", new String[] {"0", "1", "2", "3"});
        // vheight: 0-n/a 1-high 2-mid 3-low
        phonefeatures2values.put("vheight", new String[] {"0", "1", "2", "3"});
        // vlng: 0-n/a s-short l-long d-dipthong a-schwa
        phonefeatures2values.put("vlng", new String[] {"0", "s", "l", "d", "a"});
        // vrnd: 0=n/a +=on -=off
        phonefeatures2values.put("vrnd", new String[] {"0", "+", "-"});
    }
    
    /**
     * Get the locale for this feature processor manager.
     * Locale-specific subclasses must override this method to return
     * the respective locale. This base class returns null to indicate
     * that the feature processor manager can be used as a fallback for
     * any locale.
     * @return
     */
    public Locale getLocale()
    {
        return null;
    }
    
    /**
     * Provide a space-separated list of the feature names for all the 
     * feature processors known to this feature processor manager.
     * The list is unsorted except that byte-valued feature processors come first,
     * followed by short-valued feature processors, followed by continuous feature
     * processors.
     * @return
     */
    public String listFeatureProcessorNames()
    {
        String bytes = listByteValuedFeatureProcessorNames();
        String shorts = listShortValuedFeatureProcessorNames();
        String conts = listContinuousFeatureProcessorNames();
        StringBuilder sb = new StringBuilder(bytes.length()+shorts.length()+conts.length()+2);
        sb.append(bytes);
        if (bytes.length() > 0 && shorts.length() > 0) {
            sb.append(" ");
        }
        sb.append(shorts);
        if (conts.length() > 0) {
            sb.append(" ");
        }
        sb.append(conts);
        return sb.toString();
    }
    
    public String listByteValuedFeatureProcessorNames()
    {
        StringBuilder sb = new StringBuilder();
        for (String name : processors.keySet()) {
            MaryFeatureProcessor fp = processors.get(name);
            if (fp instanceof ByteValuedFeatureProcessor) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(name);
            }
        }
        return sb.toString();
    }

    public String listShortValuedFeatureProcessorNames()
    {
        StringBuilder sb = new StringBuilder();
        for (String name : processors.keySet()) {
            MaryFeatureProcessor fp = processors.get(name);
            if (fp instanceof ShortValuedFeatureProcessor) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(name);
            }
        }
        return sb.toString();
    }

    public String listContinuousFeatureProcessorNames()
    {
        StringBuilder sb = new StringBuilder();
        for (String name : processors.keySet()) {
            MaryFeatureProcessor fp = processors.get(name);
            if (fp instanceof ContinuousFeatureProcessor) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(name);
            }
        }
        return sb.toString();
    }

    
    protected void addFeatureProcessor(MaryFeatureProcessor fp)
    {
        processors.put(fp.getName(), fp);
    }
    
    public MaryFeatureProcessor getFeatureProcessor(String name)
    {
        return processors.get(name);
    }
    
    protected void setupPhonemeFeatureProcessors(AllophoneSet phoneset, String[] phonemeValues, String pauseSymbol)
    {
        MaryGenericFeatureProcessors.TargetElementNavigator segment = new MaryGenericFeatureProcessors.SegmentNavigator();

        addFeatureProcessor(new MaryLanguageFeatureProcessors.Phoneme("phoneme", phonemeValues, pauseSymbol, segment));
        addFeatureProcessor(new MaryLanguageFeatureProcessors.HalfPhoneUnitName(phonemeValues, pauseSymbol));
        addFeatureProcessor(new MaryLanguageFeatureProcessors.SegOnsetCoda(phoneset));
        // Phone features:
        for (String feature : phonefeatures2values.keySet()) {
            addFeatureProcessor(new MaryLanguageFeatureProcessors.PhoneFeature
                    (phoneset, "ph_"+feature, feature, phonefeatures2values.get(feature), pauseSymbol, segment));
        }
      
        Map<String,MaryGenericFeatureProcessors.TargetElementNavigator> segments =
            new HashMap<String, MaryGenericFeatureProcessors.TargetElementNavigator>();
        
        segments.put("prev", new MaryGenericFeatureProcessors.PrevSegmentNavigator());
        segments.put("prev_prev", new MaryGenericFeatureProcessors.PrevPrevSegmentNavigator());
        segments.put("next", new MaryGenericFeatureProcessors.NextSegmentNavigator());
        segments.put("next_next", new MaryGenericFeatureProcessors.NextNextSegmentNavigator());

        for (String position : segments.keySet()) {
            MaryGenericFeatureProcessors.TargetElementNavigator navi = segments.get(position);
            addFeatureProcessor(new MaryLanguageFeatureProcessors.Phoneme(position+"_phoneme", phonemeValues, pauseSymbol, navi));
            // Phone features:
            for (String feature : phonefeatures2values.keySet()) {
                addFeatureProcessor(new MaryLanguageFeatureProcessors.PhoneFeature
                        (phoneset, position+"_"+feature, feature, phonefeatures2values.get(feature), pauseSymbol, navi));
            }
            
        }

    }
    

}

