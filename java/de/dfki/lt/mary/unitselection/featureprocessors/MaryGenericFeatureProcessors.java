package de.dfki.lt.mary.unitselection.featureprocessors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

import com.sun.speech.freetts.Item;
import com.sun.speech.freetts.ProcessException;
import com.sun.speech.freetts.Relation;
import com.sun.speech.freetts.en.us.USEnglish;
import com.sun.speech.freetts.util.Utilities;

import de.dfki.lt.mary.unitselection.Target;
import de.dfki.lt.mary.unitselection.cart.PathExtractor;
import de.dfki.lt.mary.unitselection.cart.PathExtractorImpl;

/**
 * A collection of feature processors that operate on Target objects.
 * Their names are all prefixed with "mary_" to make sure no confusion with the old FreeTTS feature processors occurs. 
 * @author schroed
 *
 */
public class MaryGenericFeatureProcessors
{
    protected static Item getSegment(Target target)
    {
        Item segment = target.getItem();
        return segment;
    }

    protected static Item getSyllable(Target target)
    {
        Item segment = target.getItem();
        if (segment == null) return null;
        segment = segment.getItemAs(Relation.SYLLABLE_STRUCTURE);
        if (segment == null) return null;
        Item syllable = segment.getParent();
        return syllable;
    }

    protected static Item getWord(Target target)
    {
        Item segment = target.getItem();
        if (segment == null) return null;
        segment = segment.getItemAs(Relation.SYLLABLE_STRUCTURE);
        if (segment == null) return null;
        Item syllable = segment.getParent();
        if (syllable == null) return null;
        Item word = syllable.getParent();
        return word;
    }

    protected final static PathExtractor FIRST_SYLLABLE_PATH = new PathExtractorImpl(
            "R:SylStructure.parent.R:Phrase.parent.daughter.R:SylStructure.daughter",
            false);

    protected final static PathExtractor LAST_SYLLABLE_PATH = new PathExtractorImpl(
            "R:SylStructure.parent.R:Phrase.parent.daughtern.R:SylStructure.daughter",
            false);

    protected final static PathExtractor LAST_LAST_SYLLABLE_PATH = new PathExtractorImpl(
            "R:SylStructure.parent.R:Phrase.parent.daughtern.R:SylStructure.daughtern",
            false);

    protected final static PathExtractor SUB_PHRASE_PATH = new PathExtractorImpl(
            "R:SylStructure.parent.R:Phrase.parent.p", false);

    protected final static Pattern DOUBLE_PATTERN = Pattern
            .compile(USEnglish.RX_DOUBLE);

    protected final static Pattern DIGITS_PATTERN = Pattern
            .compile(USEnglish.RX_DIGITS);

    // no instances
    protected MaryGenericFeatureProcessors()
    {
    }

    /**
     * Classifies the type of word break
     * 
     * @param item
     *            the item to process
     * 
     * @return "4" for a big break, "3" for a break; otherwise "1"
     * 
     * @throws ProcessException
     *             if an exception occurred during the processing
     */
    public static String wordBreak(Item item) throws ProcessException
    {
        Item ww = item.getItemAs(Relation.PHRASE);
        if (ww == null || ww.getNext() != null) {
            return "1";
        } else {
            String pname = ww.getParent().toString();
            if (pname.equals("BB")) {
                return "4";
            } else if (pname.equals("B")) {
                return "3";
            } else {
                return "1";
            }
        }
    }

    /**
     * Gets the punctuation associated with the word
     * 
     * @param item
     *            the word to process
     * 
     * @return the punctuation associated with the word
     * 
     * @throws ProcessException
     *             if an exception occurred during the processing
     */
    public static String wordPunc(Item item) throws ProcessException
    {
        Item ww = item.getItemAs(Relation.TOKEN);
        if (ww != null && ww.getNext() != null) {
            return "";
        } else {
            if (ww != null && ww.getParent() != null) {
                return ww.getParent().getFeatures().getString("punc");
            } else {
                return "";
            }
        }
    }

    /**
     * Determines if the given item is accented
     * 
     * @param item
     *            the item of interest
     * 
     * @return <code>true</code> if the item is accented, otherwise
     *         <code>false</code>
     */
    private static boolean isAccented(Item item)
    {
        return item.getFeatures().isPresent("accent");
    }

    /**
     * Rails an int. flite never returns an int more than 19 from a feature
     * processor, we duplicate that behavior here so that our tests will match.
     * 
     * @param val
     *            the value to rail
     * 
     * @return val clipped to be betweein 0 and 19
     */
    private static int rail(int val)
    {
        return val > 19 ? 19 : val;
    }

    
    /**
     * Returns as an Integer the number of syllables in the given word. This is
     * a feature processor. A feature processor takes an item, performs some
     * sort of processing on the item and returns an object.
     */
    public static class WordNumSyls implements ByteValuedFeatureProcessor
    {
        public String getName() { return "mary_word_numsyls"; }
        public String[] getValues() {
            return new String[] {"0", "1", "2", "3", "4", "5", "6", "7",
                    "8", "9", "10", "11", "12", "13", "14", "15", "16",
                    "17", "18", "19"};
        }
        /**
         * Performs some processing on the given item.
         * 
         * @param item
         *            the item to process
         * 
         * @return the number of syllables in the given word
         */
        public byte process(Target target)
        {
            Item word = getWord(target);
            if (word == null) return (byte)0;
            word = word.getItemAs(Relation.SYLLABLE_STRUCTURE);
            if (word == null) return (byte)0;
            int count = 0;
            Item daughter = word.getDaughter();
            while (daughter != null) {
                count++;
                daughter = daughter.getNext();
            }
            return (byte) rail(count);
        }
    }

    /**
     * Counts the number of accented syllables since the last major break. This
     * is a feature processor. A feature processor takes an item, performs some
     * sort of processing on the item and returns an object.
     */
    public static class AccentedSylIn implements ByteValuedFeatureProcessor
    {
        public String getName() { return "mary_asyl_in"; }
        public String[] getValues() {
            return new String[] {"0", "1", "2", "3", "4", "5", "6", "7",
                    "8", "9", "10", "11", "12", "13", "14", "15", "16",
                    "17", "18", "19"};
        }

        /**
         * Performs some processing on the given item.
         * 
         * @param item
         *            the item to process
         * 
         * @return the number of accented syllables since the last major break
         * 
         * @throws ProcessException
         *             if an exception occurred during the processing
         */
        public byte process(Target target)
        {
            int count = 0;
            Item ss = getSyllable(target);
            if (ss == null) return (byte)0;
            ss = ss.getItemAs(Relation.SYLLABLE);
            if (ss == null) return (byte)0;
            Item firstSyllable = (Item) FIRST_SYLLABLE_PATH.findTarget(ss);

            for (Item p = ss; p != null; p = p.getPrevious()) {
                if (isAccented(p)) {
                    count++;
                }
                if (p.equalsShared(firstSyllable)) {
                    break;
                }
            }
            return (byte)rail(count);
        }
    }

    /**
     * Counts the number of stressed syllables since the last major break. This
     * is a feature processor. A feature processor takes an item, performs some
     * sort of processing on the item and returns an object.
     */
    public static class StressedSylIn implements FeatureProcessor
    {
        public String getName() { return "ssyl_in"; }

        /**
         * Performs some processing on the given item.
         * 
         * @param item
         *            the item to process
         * 
         * @return the number of stresses syllables since the last major break
         * 
         * @throws ProcessException
         *             if an exception occurred during the processing
         */
        public String process(Item item) throws ProcessException
        {
            int count = 0;
            Item ss = item.getItemAs(Relation.SYLLABLE);
            Item firstSyllable = (Item) FIRST_SYLLABLE_PATH.findTarget(item);

            // this should include the first syllable, but
            // flite 1.1 and festival don't.

            for (Item p = ss.getPrevious(); p != null
                    && !p.equalsShared(firstSyllable); p = p.getPrevious()) {
                if ("1".equals(p.getFeatures().getString("stress"))) {
                    count++;
                }
            }
            return Integer.toString(rail(count));
        }
    }

    /**
     * Counts the number of stressed syllables since the last major break. This
     * is a feature processor. A feature processor takes an item, performs some
     * sort of processing on the item and returns an object.
     */
    public static class SylIn implements FeatureProcessor
    {
        public String getName() { return "syl_in"; }

        /**
         * Performs some processing on the given item.
         * 
         * @param item
         *            the item to process
         * 
         * @return the number of stressed syllables since the last major break
         * 
         * @throws ProcessException
         *             if an exception occurred during the processing
         */
        public String process(Item item) throws ProcessException
        {
            int count = 0;
            Item ss = item.getItemAs(Relation.SYLLABLE);
            Item firstSyllable = (Item) FIRST_SYLLABLE_PATH.findTarget(item);

            for (Item p = ss; p != null; p = p.getPrevious(), count++) {
                if (p.equalsShared(firstSyllable)) {
                    break;
                }
            }
            return Integer.toString(rail(count));
        }
    }

    /**
     * Counts the number of stressed syllables since the last major break. This
     * is a feature processor. A feature processor takes an item, performs some
     * sort of processing on the item and returns an object.
     */
    public static class SylOut implements FeatureProcessor
    {
        public String getName() { return "syl_out"; }

        /**
         * Performs some processing on the given item.
         * 
         * @param item
         *            the item to process
         * 
         * @return the number of stressed syllables since the last major break
         * 
         * @throws ProcessException
         *             if an exception occurred during the processing
         */
        public String process(Item item) throws ProcessException
        {
            int count = 0;
            Item ss = item.getItemAs(Relation.SYLLABLE);
            Item firstSyllable = (Item) LAST_LAST_SYLLABLE_PATH
                    .findTarget(item);

            for (Item p = ss; p != null; p = p.getNext()) {
                if (p.equalsShared(firstSyllable)) {
                    break;
                }
                count++;
            }
            return Integer.toString(rail(count));
        }
    }

    /**
     * Counts the number of stressed syllables until the next major break. This
     * is a feature processor. A feature processor takes an item, performs some
     * sort of processing on the item and returns an object.
     */
    public static class StressedSylOut implements FeatureProcessor
    {
        public String getName() { return "ssyl_out"; }

        /**
         * Performs some processing on the given item.
         * 
         * @param item
         *            the item to process
         * 
         * @return the number of stressed syllables until the next major break
         * 
         * @throws ProcessException
         *             if an exception occurred during the processing
         */
        public String process(Item item) throws ProcessException
        {
            int count = 0;
            Item ss = item.getItemAs(Relation.SYLLABLE);
            Item lastSyllable = (Item) LAST_SYLLABLE_PATH.findTarget(item);

            for (Item p = ss.getNext(); p != null; p = p.getNext()) {
                if ("1".equals(p.getFeatures().getString("stress"))) {
                    count++;
                }
                if (p.equalsShared(lastSyllable)) {
                    break;
                }
            }
            return Integer.toString(rail(count));
        }
    }

    /**
     * Returns the length of the string. (generally this is a digit string) This
     * is a feature processor. A feature processor takes an item, performs some
     * sort of processing on the item and returns an object.
     */
    public static class NumDigits implements FeatureProcessor
    {
        public String getName() { return "num_digits"; }

        /**
         * Performs some processing on the given item.
         * 
         * @param item
         *            the item to process
         * 
         * @return the length of the string
         * 
         * @throws ProcessException
         *             if an exception occurred during the processing
         */
        public String process(Item item) throws ProcessException
        {
            String name = item.getFeatures().getString("name");
            return Integer.toString(rail(name.length()));
        }
    }

    /**
     * Returns true ("1") if the given item is a number between 0 and 32
     * exclusive, otherwise, returns "0". string) This is a feature processor. A
     * feature processor takes an item, performs some sort of processing on the
     * item and returns an object.
     */
    public static class MonthRange implements FeatureProcessor
    {
        public String getName() { return "month_range"; }

        /**
         * Performs some processing on the given item.
         * 
         * @param item
         *            the item to process
         * 
         * @return returns "1" if the given item is a number between 0 and 32
         *         (exclusive) otherwise returns "0"
         * 
         * @throws ProcessException
         *             if an exception occurred during the processing
         */
        public String process(Item item) throws ProcessException
        {
            int v = Integer.parseInt(item.getFeatures().getString("name"));
            if ((v > 0) && (v < 32)) {
                return "1";
            } else {
                return "0";
            }
        }
    }

    /**
     * Checks to see if the given syllable is accented. This is a feature
     * processor. A feature processor takes an item, performs some sort of
     * processing on the item and returns an object.
     */
    public static class Accented implements ByteValuedFeatureProcessor
    {
        public String getName() { return "mary_accented"; }
        public String[] getValues() {
            return new String[] {"0", "1"};
        }

        /**
         * Performs some processing on the given item.
         * 
         * @param item
         *            the item to process
         * 
         * @return "1" if the syllable is accented; otherwise "0"
         * 
         * @throws ProcessException
         *             if an exception occurred during the processing
         */
        public byte process(Target target)
        {
            Item syllable = getSyllable(target);
            if (syllable != null && isAccented(syllable)) {
                return (byte)1;
            } else {
                return (byte)0;
            }
        }
    }

    /**
     * Find the last accented syllable This is a feature processor. A feature
     * processor takes an item, performs some sort of processing on the item and
     * returns an object.
     */
    public static class LastAccent implements FeatureProcessor
    {
        public String getName() { return "last_accent"; }

        /**
         * Performs some processing on the given item.
         * 
         * @param item
         *            the item to process
         * 
         * @return the count of the last accented syllable
         * 
         * @throws ProcessException
         *             if an exception occurred during the processing
         */
        public String process(Item item) throws ProcessException
        {
            int count = 0;

            for (Item p = item.getItemAs(Relation.SYLLABLE); p != null; p = p
                    .getPrevious(), count++) {
                if (isAccented(p)) {
                    break;
                }
            }
            return Integer.toString(rail(count));
        }
    }

    /**
     * Finds the position of the phoneme in the syllable This is a feature
     * processor. A feature processor takes an item, performs some sort of
     * processing on the item and returns an object.
     */
    public static class PosInSyl implements FeatureProcessor
    {
        public String getName() { return "pos_in_syl"; }

        /**
         * Performs some processing on the given item.
         * 
         * @param item
         *            the item to process
         * 
         * @return the position of the phoneme in the syllable
         * 
         * @throws ProcessException
         *             if an exception occurred during the processing
         */
        public String process(Item item) throws ProcessException
        {
            int count = -1;

            for (Item p = item.getItemAs(Relation.SYLLABLE_STRUCTURE); p != null; p = p
                    .getPrevious()) {
                count++;
            }
            return Integer.toString(rail(count));
        }
    }

    /**
     * Classifies the the syllable as single, initial, mid or final. This is a
     * feature processor. A feature processor takes an item, performs some sort
     * of processing on the item and returns an object.
     */
    public static class PositionType implements FeatureProcessor
    {
        public String getName() { return "position_type"; }

        /**
         * Performs some processing on the given item.
         * 
         * @param item
         *            the item to process
         * 
         * @return classifies the syllable as "single", "final", "initial" or
         *         "mid"
         * 
         * @throws ProcessException
         *             if an exception occurred during the processing
         */
        public String process(Item item) throws ProcessException
        {
            String type;

            Item s = item.getItemAs(Relation.SYLLABLE_STRUCTURE);
            if (s == null) {
                type = "single";
            } else if (s.getNext() == null) {
                if (s.getPrevious() == null) {
                    type = "single";
                } else {
                    type = "final";
                }
            } else if (s.getPrevious() == null) {
                type = "initial";
            } else {
                type = "mid";
            }
            return type;
        }
    }

    /**
     * Determines the break level after this syllable This is a feature
     * processor. A feature processor takes an item, performs some sort of
     * processing on the item and returns an object.
     */
    public static class SylBreak implements FeatureProcessor
    {
        public String getName() { return "syl_break"; }

        /**
         * Performs some processing on the given item.
         * 
         * @param syl
         *            the item to process
         * 
         * @return the break level after this syllable
         * 
         * @throws ProcessException
         *             if an exception occurred during the processing
         */
        public String process(Item syl) throws ProcessException
        {
            Utilities.debug("SylBreak: Got item '" + syl + "' in the '"
                    + syl.getOwnerRelation().getName() + "' relation.");
            Item ss = syl.getItemAs(Relation.SYLLABLE_STRUCTURE);
            if (ss == null) {
                Utilities
                        .debug("SylBreak: Cannot get this as SYLLABLE_STRUCTURE item");
                return "1";
            } else if (ss.getNext() != null) {
                Utilities
                        .debug("SylBreak: this is not the last syllable in this word");
                return "0";
            } else if (ss.getParent() == null) {
                return "1";
            } else {
                Utilities
                        .debug("SylBreak: this is word-final, calculate the wordBreak.");
                return wordBreak(ss.getParent());
            }
        }
    }

    /**
     * Determines the word break. This is a feature processor. A feature
     * processor takes an item, performs some sort of processing on the item and
     * returns an object.
     */
    public static class WordBreak implements FeatureProcessor
    {
        public String getName() { return "word_break"; }

        /**
         * Performs some processing on the given item.
         * 
         * @param word
         *            the item to process
         * 
         * @return the break level for this word
         * 
         * @throws ProcessException
         *             if an exception occurred during the processing
         */
        public String process(Item word) throws ProcessException
        {
            return wordBreak(word);
        }
    }

    /**
     * Determines the word punctuation. This is a feature processor. A feature
     * processor takes an item, performs some sort of processing on the item and
     * returns an object.
     */
    public static class WordPunc implements FeatureProcessor
    {
        public String getName() { return "word_punc"; }

        /**
         * Performs some processing on the given item.
         * 
         * @param word
         *            the item to process
         * 
         * @return the punctuation for this word
         * 
         * @throws ProcessException
         *             if an exception occurred during the processing
         */
        public String process(Item word) throws ProcessException
        {
            return wordPunc(word);
        }
    }

    /**
     * Counts the number of phrases before this one. This is a feature
     * processor. A feature processor takes an item, performs some sort of
     * processing on the item and returns an object.
     */
    public static class SubPhrases implements FeatureProcessor
    {
        public String getName() { return "sub_phrases"; }

        /**
         * Performs some processing on the given item.
         * 
         * @param item
         *            the item to process
         * 
         * @return the number of phrases before this one
         * 
         * @throws ProcessException
         *             if an exception occurred during the processing
         */
        public String process(Item item) throws ProcessException
        {
            int count = 0;
            Item inPhrase = (Item) SUB_PHRASE_PATH.findTarget(item);

            for (Item p = inPhrase; p != null; p = p.getPrevious()) {
                count++;
            }
            return Integer.toString(rail(count));
        }
    }

    /**
     * Returns the duration of the given segment This is a feature processor. A
     * feature processor takes an item, performs some sort of processing on the
     * item and returns an object.
     */
    public static class SegmentDuration implements FeatureProcessor
    {
        public String getName() { return "segment_duration"; }

        /**
         * Performs some processing on the given item.
         * 
         * @param seg
         *            the item to process
         * 
         * @return the duration of the segment as a string.
         * 
         * @throws ProcessException
         *             if an exception occurred during the processing
         */
        public String process(Item seg) throws ProcessException
        {
            if (seg == null) {
                return "0";
            } else if (seg.getPrevious() == null) {
                return seg.getFeatures().getObject("end").toString();
            } else {
                return Float.toString(seg.getFeatures().getFloat("end")
                        - seg.getPrevious().getFeatures().getFloat("end"));
            }
        }
    }

    /**
     * Checks if segment is sylfinal This is a feature processor. A feature
     * processor takes an item, performs some sort of processing on the item and
     * returns an object.
     */
    public static class SylFinal implements FeatureProcessor
    {
        public String getName() { return "syl_final"; }

        public String process(Item seg) throws ProcessException
        {
            Item sylItem = seg.getItemAs(Relation.SYLLABLE_STRUCTURE);
            if (sylItem == null || sylItem.getNext() != null) {
                return "0";
            } else {
                return "1";
            }
        }
    }

    /**
     * Checks if segment is a pause This is a feature processor. A feature
     * processor takes an item, performs some sort of processing on the item and
     * returns an object.
     */
    public static class LispIsPau implements FeatureProcessor
    {
        public String getName() { return "lisp_is_pau"; }

        /**
         * Check if segment is a pause
         * 
         * @param seg
         *            the segment
         * @return 0 if false, 1 if true
         * @throws ProcessException
         */
        public String process(Item seg) throws ProcessException
        {
            Item segItem = seg.getItemAs(Relation.SEGMENT);
            if (segItem == null || !(segItem.toString().equals("pau"))) {
                return "0";
            } else {
                return "1";
            }
        }
    }

    /**
     * Calculates the pitch of a segment This processor should be used by target
     * items only
     */
    public static class Seg_Pitch implements FeatureProcessor
    {
        public String getName() { return "seg_pitch"; }

        public String process(Item seg) throws ProcessException
        {
            // System.out.println("Looking for pitch...");
            // get mid position of segment
            float mid;
            float end = seg.getFeatures().getFloat("end");
            Item prev = seg.getPrevious();
            if (prev == null) {
                mid = end / 2;
            } else {
                float prev_end = prev.getFeatures().getFloat("end");
                mid = prev_end + (end - prev_end) / 2;
            }
            Relation targetRelation = seg.getUtterance().getRelation("Target");
            // if segment has no target relation, you can not calculate
            // the segment pitch
            if (targetRelation == null) {
                return "0.0";
            }
            // get F0 and position of previous and next target
            Item nextTargetItem = targetRelation.getHead();
            while (nextTargetItem != null
                    && nextTargetItem.getFeatures().getFloat("pos") < mid) {
                nextTargetItem = nextTargetItem.getNext();
            }
            if (nextTargetItem == null)
                return "0.0";
            Item lastTargetItem = nextTargetItem.getPrevious();
            if (lastTargetItem == null)
                return "0.0";
            float lastF0 = lastTargetItem.getFeatures().getFloat("f0");
            float lastPos = lastTargetItem.getFeatures().getFloat("pos");
            float nextF0 = nextTargetItem.getFeatures().getFloat("f0");
            float nextPos = nextTargetItem.getFeatures().getFloat("pos");
            assert lastPos <= mid && mid <= nextPos;
            // build a linear function (f(x) = slope*x+intersectionYAxis)
            float slope = (nextF0 - lastF0) / (nextPos - lastPos);
            // calculate the pitch
            float pitch = lastF0 + slope * (mid - lastPos);
            if (!(lastF0 <= pitch && pitch <= nextF0 || nextF0 <= pitch
                    && pitch <= lastF0)) {
                throw new NullPointerException();
            }

            if (Float.isNaN(pitch)) {
                pitch = (float) 0.0;
            }
            return Float.toString(pitch);
        }
    }
    
    
    /**
     * The ToBI accent of the current syllable. This is a feature processor. A feature
     * processor takes an item, performs some sort of processing on the item and
     * returns an object.
     */
    public static class TobiAccent implements FeatureProcessor
    {
        public String getName() { return "tobi_accent"; }

        /**
         * For the given syllable item, return its tobi accent, 
         * or NONE if there is none.
         */
        public String process(Item syllable) throws ProcessException
        {
            String accent = syllable.getFeatures().getString("accent");
            if (accent == null) {
                return "NONE";
            }
            return accent;
        }
    }

    /**
     * The ToBI accent of the current syllable. This is a feature processor. A feature
     * processor takes an item, performs some sort of processing on the item and
     * returns an object.
     */
    public static class TobiEndtone implements FeatureProcessor
    {
        public String getName() { return "tobi_endtone"; }
        /**
         * For the given syllable item, return its tobi end tone, 
         * or NONE if there is none.
         */
        public String process(Item syllable) throws ProcessException
        {
            String endtone = syllable.getFeatures().getString("endtone");
            if (endtone == null) {
                return "NONE";
            }
            return endtone;
        }
    }

}
