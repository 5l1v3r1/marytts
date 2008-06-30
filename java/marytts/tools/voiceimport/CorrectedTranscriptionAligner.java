/**
 * Copyright 2008 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * Permission is hereby granted, free of charge, to use and distribute
 * this software and its documentation without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of this work, and to
 * permit persons to whom this work is furnished to do so, subject to
 * the following conditions:
 * 
 * 1. The code must retain the above copyright notice, this list of
 *    conditions and the following disclaimer.
 * 2. Any modifications must be clearly marked as such.
 * 3. Original authors' names are not deleted.
 * 4. The authors' names are not used to endorse or promote products
 *    derived from this software without specific prior written
 *    permission.
 *
 * DFKI GMBH AND THE CONTRIBUTORS TO THIS WORK DISCLAIM ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS, IN NO EVENT SHALL DFKI GMBH NOR THE
 * CONTRIBUTORS BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL
 * DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
 * PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS
 * ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
 * THIS SOFTWARE.
 */
package marytts.tools.voiceimport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import marytts.datatypes.MaryData;
import marytts.modules.TargetFeatureLister;
import marytts.modules.phonemiser.Phoneme;
import marytts.modules.phonemiser.PhonemeSet;
import marytts.modules.synthesis.FreeTTSVoices;
import marytts.modules.synthesis.Voice;
import marytts.signalproc.display.Histogram;
import marytts.unitselection.select.Target;

import org.apache.tools.ant.types.CommandlineJava.SysProperties;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.sun.speech.freetts.Item;
import com.sun.speech.freetts.Relation;
import com.sun.speech.freetts.Utterance;



public class CorrectedTranscriptionAligner extends VoiceImportComponent {
    
    private DatabaseLayout db;
    private String locale;
    // properties
    public final String ORIGTRANS = "CorrectedTranscriptionAligner.original";
    public final String CORRTRANS = "CorrectedTranscriptionAligner.corrected";
    public final String RESULTTRANS = "CorrectedTranscriptionAligner.results";
    //public final String SYMCOSTS = "CorrectedTranscriptionAligner.costfile";
    public final String PHONEMEXML = "CorrectedTranscriptionAligner.phonesetXML";
    private int progress;
    
    Map<String, Integer> aligncost;
    int defaultcost;
    int skipcost;
    PhonemeSet phonemeSet;
    
    // String for a baundary
    private String possibleBnd = "_";
    // String for pause boundary
    //private String pauseBnd = "_";
    
    public CorrectedTranscriptionAligner() {
        this.aligncost = new HashMap<String, Integer>();
        this.defaultcost = 1;
        this.skipcost = 1;
    }

    public String getName() {
        return "CorrectedTranscriptionAligner";
    }
    
    public void initialiseComp()
    {      
        
    }
    
    public SortedMap getDefaultProps(DatabaseLayout db) {
        this.db = db;
        String phonemeXml;
        locale = db.getProp(db.LOCALE);
        if (props == null){
            props = new TreeMap();
            
            // original transcriptions (?LABDIR / TEXTDIR)
            String origTrans = System.getProperty(ORIGTRANS);
            if ( origTrans == null ) {
                origTrans = db.getProp(db.ROOTDIR)
                +"intonisedXML"//phonemisedXML?
                +System.getProperty("file.separator");
            }
            props.put(ORIGTRANS,origTrans);
            
            // corrected transcriptions
            String corrTrans = System.getProperty(CORRTRANS);
            if ( corrTrans == null ) {
                corrTrans = db.getProp(db.ROOTDIR)
                +"lab"
                +System.getProperty("file.separator");
            }
            props.put(CORRTRANS,corrTrans);
            
            // aligned corrected transcriptions
            String resultTrans = System.getProperty(RESULTTRANS);
            if ( resultTrans == null ) {
                resultTrans = db.getProp(db.ROOTDIR)
                +"correctedIntonisedXML"//correctedPhonemisedXML?
                +System.getProperty("file.separator");
            }
            props.put(RESULTTRANS,resultTrans);
            
            // TODO: at the moment, for convenience, disrances are taken from 
            // phonset, so that the user does not have to specify alignment costs
            /*
            // alignment costs
            String symCosts = System.getProperty(SYMCOSTS);
            if ( symCosts == null ) {
                symCosts = db.getProp(db.ROOTDIR)
                +"temp"
                +System.getProperty("file.separator")
                +"alignmentcosts_de.txt";
                //"/project/mary/lib/modules/de/cap/alignmentcosts_de.txt";
            }
            props.put(SYMCOSTS,symCosts);*/
            
            // alignment costs
            if(locale.startsWith("de")){
                phonemeXml = db.getProp(db.MARYBASE)
                        +File.separator+"lib"+File.separator+"modules"
                        +File.separator+"de"+File.separator+"cap"+File.separator+"phoneme-list-de.xml";
            }
            else{
                phonemeXml = db.getProp(db.MARYBASE)
                        +File.separator+"lib"+File.separator+"modules"
                        +File.separator+"en"+File.separator+"cap"+File.separator+"phoneme-list-en.xml";
            }
            props.put(PHONEMEXML, phonemeXml);

        }
        return props;
    }
    
    protected void setupHelp(){
        props2Help = new TreeMap();
        props2Help.put(ORIGTRANS,"directory containing the files with text and automatic phonemization");
        props2Help.put(CORRTRANS,"directory containing manually corrected transcriptions");
        props2Help.put(RESULTTRANS,"directory for the texts with aligned corrected transcriptions");
       // props2Help.put(SYMCOSTS,"file with the distance that is to be used for alignment");
    }
    
    /**
     * This overides possibly defined costs for boundary symbols, so that
     * the following assumptions hold:
     * 
     * 1. Alignment from any boundary symbol to another is for free
     * 2. Alignment from any boundary symbol to a non-boundary symbol is 
     *    prohibitively expensive (100 x defaultCost)
     *    
     * Since there are only non-boundary symbols and pauses in the output 
     * transcriptions, all boundary symbol in the input string are therefor
     * aligned to a pause or deleted.
     * 
     */
    private void useDefaultBoundaryCosts(){


        int max = 20 * this.defaultcost;
        //int max = 20;
        
        for (String phName : this.phonemeSet.getPhonemeNames()){
            // dont align boundaries with anything else
            //this.aligncost.put( this.pauseBnd    + " " + phName         ,max);
            this.aligncost.put( this.possibleBnd + " " + phName         ,max);
            //this.aligncost.put( phName          + " " + this.pauseBnd   ,max);
            this.aligncost.put( phName          + " " + this.possibleBnd,max);
        }
        
        // distance between pauses is zero, with slight conservative bias
        //this.aligncost.put(this.pauseBnd + " " + this.pauseBnd,0);
        this.aligncost.put(this.possibleBnd + " " + this.possibleBnd,0);        
        //this.aligncost.put(this.pauseBnd + " " + this.possibleBnd,1);
        //this.aligncost.put(this.possibleBnd + " " + this.pauseBnd,1+this.skipcost);

    }
    
    public int getProgress() {
        return progress;
    }
    /**
     * align and change automatic transcriptions to manually 
     * corrected ones.
     * 
     * XML-Version: this changes mary xml-files (PHONEMISED)
     * @throws TransformerException 
     * @throws ParserConfigurationException 
     * @throws SAXException 
     * @throws XPathExpressionException 
     */
    public boolean compute() throws IOException, TransformerException, ParserConfigurationException, SAXException, XPathExpressionException{
        
        // set costs used for distance computation
        
        /* TODO: make use of cost file dependent on settings
        try{
            this.setDistance(new BufferedReader(new FileReader ((String) props.get(this.SYMCOSTS))));
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("File with symbol costs not set: " + (String) props.get(this.SYMCOSTS));
        }
        */
        
        
        // phoneme set is used for splitting the sampa strings and setting the costs
        this.setPhonemeSet(PhonemeSet.getPhonemeSet((String) props.get(this.PHONEMEXML)));

        this.setDistance();
        
        this.setDefaultCost( this.getMaxCost() );
        
        this.setSkipCost( this.getMaxCost() * 3 / 10 ); // 0.25 / 0.3 /0.33 seem all fine
                
        // use the default setting to align boundaries only to itself
        this.useDefaultBoundaryCosts();
        
        File xmlOutDir = new File((String) props.get(this.RESULTTRANS));
        if (!xmlOutDir.exists())
            xmlOutDir.mkdir();
            
        // for parsing xml files
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        
        // for writing xml files
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer = tFactory.newTransformer();

        // TODO: debugging
        System.out.println("traversing through " + bnl.getLength() + " files");
       
        for (int i=0;i<bnl.getLength();i++){
            progress = 100*i/bnl.getLength();
            File nextFile = new File(props.get(this.ORIGTRANS)
                    +System.getProperty("file.separator")
                    +bnl.getName(i)+".xml");
            // TODO: reinstall
            //System.out.println(bnl.getName(i));
            
            // get original xml file
            Document doc = db.parse(nextFile);

            // open destination xml file
            FileWriter docDest  = new FileWriter((String) props.get(this.RESULTTRANS) + nextFile.getName());
            
            // open file with manual transcription that is to be aligned
            
            //BufferedReader manTrans;
            
            String manTransString;
            try{

                String trfdir = (String) props.get(this.CORRTRANS);

                
                //if (!trfdir.endsWith(System.getProperty("File.separator")))
                //        trfdir += System.getProperty("File.separator");
                
                String trfname = trfdir + 
                nextFile.getName().substring(0, nextFile.getName().length() - 4) + ".lab";
                
                //System.out.println(trfname);
                
                manTransString = this.readLabelFile(trfname);
                
            } catch ( FileNotFoundException e ) {
                System.out.println("No manual transcription found, copy original ...");
                
                // transform the unchanged xml-structure to a file
                DOMSource source = new DOMSource( doc );
                StreamResult output = new StreamResult(docDest);
                transformer.transform(source, output);

                continue;
            }
            
            // align transcriptions
            this.alignXmlTranscriptions(doc, manTransString);
            
            // write results to output
            DOMSource source = new DOMSource( doc );
            StreamResult output = new StreamResult(docDest);
            transformer.transform(source, output);
        }
                
        return true;
    }
     
    
    public void setPhonemeSet(PhonemeSet aPhonemeSet) {
        this.phonemeSet = aPhonemeSet;
    }

    /**
     * This reads in a label file and returns a String of the phonetic symbols,
     * seperated by white spaces. Pause symbols ("_") are disregarded (skipped).
     * 
     * @throws IOException if something goes wrong with opening/reading the file
     * 
     */
    private String readLabelFile(String trfname) throws IOException {
        // reader for label file.
        BufferedReader lab = new BufferedReader(new FileReader(trfname));
                
        // string with phonemes, seperated by white spaces
        String result = "";
        
        String line;
        
        while ((line = lab.readLine()) != null){
            if ( line.startsWith("#") ) 
                continue;
            
            String[] lineLmnts = line.split("\\s+");
            
           if ( lineLmnts.length != 3 )
               throw new IllegalArgumentException("Expected three columns in label file, got " + lineLmnts.length);
           
           result += lineLmnts[2] + " ";
           
        }
        
        // TODO: check
        // if Label File does not start with pause symbol, insert it
        // as well as a pause duration of zero (...)
        if(! result.startsWith("_")){
            result = "_ " + result;
            //this.pauseLengths.add(0, 0);
        }
        
        return result;
    }

    /**
     * This sets the distance by using the phoneme set of the aligner object.
     * Phoneme set must already be specified.
     */
    private void setDistance(){
        
        if (null == this.phonemeSet )
            throw new IllegalStateException("Phoneme set must be specified before generic distance method can be executed.");
        
        for (String fromSym : this.phonemeSet.getPhonemeNames()){
            for (String toSym : this.phonemeSet.getPhonemeNames()){
                
                int diff = 0;
                
                Phoneme fromPh = this.phonemeSet.getPhoneme(fromSym);
                Phoneme toPh = this.phonemeSet.getPhoneme(toSym);
                
                // for each difference increase distance
                diff += (!fromSym.equals(toSym))? 2:0;
                diff += (fromPh.isFricative() != toPh.isFricative())? 2:0;
                diff += (fromPh.isGlide() != toPh.isGlide())?   2:0;
                diff += (fromPh.isLiquid() != toPh.isLiquid())? 2:0;
                diff += (fromPh.isNasal() != toPh.isNasal())?   2:0;
                diff += (fromPh.isPlosive() != toPh.isPlosive())? 1:0;
                diff += (fromPh.isSonorant() != toPh.isSonorant())? 2:0;
                diff += (fromPh.isSyllabic() != toPh.isSyllabic())? 1:0;
                diff += (fromPh.isVoiced() != toPh.isVoiced())? 1:0;
                diff += (fromPh.isVowel() != toPh.isVowel())? 2:0;
                diff += Math.abs(fromPh.sonority() - toPh.sonority());
                
                String key = fromSym + " " + toSym;
                
                //System.out.println(key + " " + diff);
                
                this.aligncost.put(key, diff);
            }
        }
    }
    
    /**
     * DEPRECATED / no longer used by compute()
     * 
     * unless you want to specify special costs, set the distance with the phonemeset instead
     * 
     * This sets the distance between two symbols from a feature/cost 
     * description, usually a file.
     * 
     * This file may contain two types of lines.
     * 1.) feature enumerations: 
     *     suppose we have symbols s1 and s2, and features f1, f2, f3.
     *     possible lines are:
     *     
     * s1 f1_of_s1 f2_of_s1 f3_of_s1
     * s2 f1_of_s2 f2_of_s2 f3_of_s2
     * 
     *     The distance between two symbols is then the number of the
     *     cases where they disagree in a feature.
     *     
     * 2.) Explicit cost statements. The override the feature difference costs.
     *     For example, to state that mapping s2 to s1 has costs 4 simply add
     *     the line:
     *     
     * >> s2 s1 4
     * 
     */
    private void setDistance(BufferedReader input) throws IOException{
        String l;
        
        String[] lineLmnts;
        List<String[]> symFeats =  new ArrayList<String[]>();
        

        
        // read data for costs
        while ((l = input.readLine()) != null) {
            l = l.trim();
            
            // comment line
            if ( l.equals("") || l.startsWith("#"))
                continue;
            
            lineLmnts = l.split("\\s+");
            
            if ( lineLmnts[0].equals(">>") ){
                // collect specified costs
                
                String key = lineLmnts[1] + " " + lineLmnts[2];
                
                try{
                    Integer value = Integer.valueOf(lineLmnts[3]);
                    this.aligncost.put(key, value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Expected number in alignment cost definition");
                }
                
            } else {
                // store features to compute costs if unspecified
                symFeats.add(lineLmnts);
            }
            
            // TODO: for efficiency reasons only compute "triangle matrix"
            // for the feature combinations of all symbols compute distances
            for (String[] feats1 : symFeats){
                for (String[] feats2 : symFeats){
                    
                    int distance = 0;
                    
                    // compare similarity (first element is not a feature but the symbol)
                    for (int i = 1; i < feats1.length; i++){
                        
                        if (!feats1[i].equals(feats2[i]))
                            distance++;
                    }
                    
                    String key = feats1[0] + " " + feats2[0];
                    
                    // if no value is set, set this distance
                    if (!this.aligncost.containsKey(key)){
                        this.aligncost.put(key, distance);
                    }                    
                }
            }    
        }
    }
    
    /**
     * 
     * This computes the alignment that has the lowest distance between two 
     * Strings.
     * 
     * There are three differences to the normal Levenshtein-distance:
     * 
     * 1. Only insertions and deletions are allowed, no replacements (i.e. no 
     *    "diagonal" transitions)
     * 2. insertion costs are dependent on a particular phone on the input side
     *    (the one they are aligned to)
     * 3. deletion is equivalent to a symbol on the input side that is not 
     *    aligned. There are costs associated with that.
     *    
     * The method returns the output string with alignment boundaries ('#') 
     * inserted.
     * 
     * @param in
     * @param out
     * @return
     */
    private String distanceAlign(String in, String out ) {
        String[] istr = in.split(" ");
        String[] ostr = out.split(" ");
        String delim = "#";


        
        // distances:
        // 1. previous distance (= previous column in matrix)
        int[] p_d = new int[ostr.length+1];
        // 2. current distance
        int[] d = new int[ostr.length+1];
        // 3. dummy array for swapping, when switching to new column
        int[] _d;
        
        // array indicating if a skip was performed (= if current character has not been aligned)
        // same arrays as for distances
        boolean[] p_sk = new boolean[ ostr.length + 1 ];
        boolean[] sk   = new boolean[ ostr.length + 1 ];
        boolean[] _sk;
        
        // arrays storing the alignments corresponding to distances
        String[] p_al = new String[ ostr.length + 1 ];
        String[] al   = new String[ ostr.length + 1 ];
        String[] _al;
        
        // initialize values
        p_d[0]  = 0;
        p_al[0] = "";
        p_sk[0] = true;

        
        // ... still initializing
        for (int j = 1; j < ostr.length + 1; j++){
            // only possibility first is to align the first letter 
            // of the input string to everything
            p_al[j] = p_al[j-1] + " " + ostr[j-1]; 
            p_d[j] = p_d[j-1] + symDist(istr[0],ostr[j-1]);
            p_sk[j] = false;        
        }
        
        // constant penalty for not aligning a character
        int skConst = this.skipcost;
        
        // align
        // can start at 1, since 0 has been treated in initialization
        for (int i=1; i < istr.length; i++) {
            
            // zero'st row stands for skipping from the beginning on
            d[0] = p_d[0] + skConst;
            al[0] = p_al[0] + " " + delim;
            sk[0] = true;
            
            for (int j = 1 ; j < ostr.length + 1; j++ ) {
                
                // translation cost between symbols ( j-1, because 0 row 
                // inserted for not aligning at beginning)
                int tr_cost = symDist(istr[i], ostr[j-1]);
                
                // skipping cost greater zero if not yet aligned 
                int sk_cost = p_sk[j]? skConst : 0;
                
                if ( sk_cost + p_d[j] < tr_cost + d[j-1]) {
                    // skipping cheaper
                    
                    
                    // cost is cost from previous input char + skipping
                    d[j]  = sk_cost + p_d[j];
                    // alignment is from prev. input + delimiter
                    al[j] = p_al[j] + " " + delim;
                    // yes, we skipped
                    sk[j] = true;
                    
                } else {
                    // aligning cheaper
                                
                    // cost is that from previously aligned output + distance
                    d[j]  = tr_cost + d[j-1];
                    // alignment continues from previously aligned
                    al[j] = al[j-1] + " " + ostr[j-1];
                    // nope, didn't skip
                    sk[j] = false;
                    
                }
            }
            
            // swapping
            _d  = p_d;
            p_d = d;
            d   = _d;

            _sk  = p_sk;
            p_sk = sk;
            sk   = _sk;
            
            _al  = p_al;
            p_al = al;
            al   = _al;
        }
        
        
        return p_al[ostr.length];
        
    }
    
    /**
     * 
     * This changes the transcription of a MaryData object into a corrected
     * transcription. The Mary data is changed. 
     * The symbols of the original transcription aligned aligned to corrected 
     * ones, with which they are replaced in turn.
     * 
     * @param d
     * @param oSymStr
     * @return
     * @throws ParserConfigurationException 
     * @throws IOException 
     * @throws SAXException 
     */
    public Document alignXmlTranscriptions(Document doc, String correct) throws SAXException, IOException, ParserConfigurationException, XPathExpressionException    {
        
        // use xpath to get all t and boundary elements
        XPath xpath = XPathFactory.newInstance().newXPath();
        // we rely on the assumption that the result of the evaluation is 
        // a list rather than a set and that it is retrieved in document order
        // TODO: check that assumption
        NodeList tokens = (NodeList) xpath.evaluate("//t | //boundary", doc, XPathConstants.NODESET);
                
        String orig = this.collectTranscription(tokens);
        
        // now we align the transcriptions and split it at the delimiters
        String al = this.distanceAlign(orig.trim(),correct.trim()) + " ";
        
        String[] alignments = al.split("#");
        
        // change the transcription in xml according to the aligned one
        doc = this.changeTranscriptions(doc, tokens, orig, alignments);
                
        return doc;
    }
    
    /**
     * 
     * This computes a string of phonetic symbols out of an intonised mary xml:
     * - standard phonemes are taken from "sampa" attribute
     * - after each token-element (except those followed by a "boundary"-element), 
     *   a "bnd" symbol is inserted (standing for a possible pause)
     * 
     * @param tokens
     * @return
     */
    private String collectTranscription(NodeList tokens) {

        // TODO: make delims argument
        // String Tokenizer devides transcriptions into syllables
        // syllable delimiters and stress symbols are retained
        String delims = "',-";

        // String storing the original transcription begins with a pause
        String orig = this.possibleBnd + " " ;
        
        // get original phoneme String
        for (int tNr = 0; tNr < tokens.getLength() ; tNr++ ){
            
            Element token = (Element) tokens.item(tNr);
            
            // only look at it if there is a sampa to change
            if ( token.hasAttribute("sampa") ){                   
                
                String sampa = token.getAttribute("sampa");
    
                List<String> sylsAndDelims = new ArrayList<String>();
                StringTokenizer sTok = new StringTokenizer(sampa, delims, true);
                
                while(sTok.hasMoreElements()){
                    String currTok = sTok.nextToken();
                    
                    if (delims.indexOf(currTok) == -1) {
                        // current Token is no delimiter
                        for ( Phoneme ph : phonemeSet.splitIntoPhonemes(currTok)){
                            orig += ph.name() + " ";
                        }// ... for each phoneme
                    }// ... if no delimiter
                }// ... while there are more tokens    
            }
                
            // TODO: simplify
            if ( token.getTagName().equals("t") ){
                                
                // if the following element is no boundary, insert a non-pause delimiter
                if (tNr == tokens.getLength()-1 || 
                    !((Element) tokens.item(tNr+1)).getTagName().equals("boundary") ){
                        orig += this.possibleBnd + " ";
                        
                    }
                                                       
            } else if ( token.getTagName().equals("boundary")){
                                
                    orig += this.possibleBnd + " ";

            } else {
                // should be "t" or "boundary" elements
                assert(false);
            }
                        
        }// ... for each t-Element
        
        return orig;
    }
    
    /**
     * 
     * This changes the transcription according to a given sequence of phonetic
     * symbols (including boundaries and pauses). The sequence is given as a
     * String containing the phonetic symbols seperated by whitespaces.
     * 
     * @param doc
     * @param tokens
     * @param orig
     * @param alignments
     * @return
     */
    private Document changeTranscriptions(Document doc, NodeList tokens, String orig, String[] alignments){
        
        // TODO: make argument
        // String Tokenizer devides transcriptions into syllables
        // syllable delimiters and stress symbols are retained
        String delims = "',-";
        
        // counter to keep track of the position in alignment array
        // starts with 1 since transcription begins with a pause
        int currAl = 1;
        
        // second looping: get original phoneme String
        for (int tNr = 0; tNr < tokens.getLength() ; tNr++ ){
            Element token = (Element) tokens.item(tNr);
   
            String sampa = token.getAttribute("sampa");
            
            // the transcription to which the old is aligned
            String newSampa = "";
            
                
            // only look at it if there is a sampa to change
            if ( ((Element) tokens.item(tNr)).hasAttribute("sampa") ){   
                List<String> sylsAndDelims = new ArrayList<String>();
                StringTokenizer sTok = new StringTokenizer(sampa, delims, true);
                
                while(sTok.hasMoreElements()){
                    String currTok = sTok.nextToken();
                    
                    if (delims.indexOf(currTok) == -1) {
                        // current Token is no delimiter
                        for ( Phoneme ph : phonemeSet.splitIntoPhonemes(currTok)){
                            orig += ph.name();
                           
                            // new transciption is the aligned ones without white spaces
                            newSampa += alignments[currAl].replaceAll(" ", "");
                            currAl +=1;
                        }
                        
                        
                    } else {
                        // all delimiters have to be copied
                        
                        
                        // exceptions treated below...
                        String previousChar;
                        if (newSampa.length() == 0)
                            previousChar = "";
                        else
                            previousChar = newSampa.substring(newSampa.length()-1);
                        
                        
                        // with a few exceptions:
                        // 1. a syllable is only indicated after a phoneme symbol
                        // 2. no two subsequent stress symbols are allowed
                        if ( ( previousChar.equals("") && currTok.equals("-") ) || 
                             ( previousChar.equals("-") && currTok.equals("-") )||
                             ( previousChar.equals("'") && currTok.equals("-") )||
                             ( previousChar.equals(",") && currTok.equals("-") )||
                             ( previousChar.equals("'") && currTok.equals("'") )||
                             ( previousChar.equals(",") && currTok.equals("'") )||
                             ( previousChar.equals("'") && currTok.equals(",") )||
                             ( previousChar.equals(",") && currTok.equals(",") )){
                            // continue

                        } else { //...
                            newSampa += currTok;

                        }
                        

                        
                    }
                }// ... while there are more tokens 
                
                // if new sampa ends with delimiters, delete them
                while (newSampa.length() > 0 &&
                        delims.indexOf( newSampa.substring(newSampa.length()-1) ) != -1 )
                {
                    newSampa = newSampa.substring(0,newSampa.length()-1);
                }
                
                // set new sampa
                token.setAttribute("sampa", newSampa);
                
            }// ... if there is transcription
            
            // treat boundaries
            // TODO: simplify
            if ( token.getTagName().equals("t") ){
                
                // if the following element is no boundary, a delimiter was inserted
                if (tNr == tokens.getLength()-1 || 
                    !((Element) tokens.item(tNr+1)).getTagName().equals("boundary") ){
                                            
                        currAl += 1;                        
                    }
                                   
            } else if ( token.getTagName().equals("boundary")){
                

                    currAl += 1;                     

            } else {
                // should be "t" or "boundary" elements
                assert(false);
            }

        }// ... for each t-Element


        return doc;
    }


    private int getMaxCost(){
        int maxMapping = Collections.max(this.aligncost.values());
        return (maxMapping > this.defaultcost) ? maxMapping : this.defaultcost;
    }
    
    private void setDefaultCost(int aCost){
        this.defaultcost = aCost;
    }
    
    private int getDefaultCost(){
        return this.defaultcost;
    }
    
    private void setSkipCost(int aCost){
        this.skipcost = aCost;
    }
    
    private int symDist(String aString1, String aString2) {
        
        String key = aString1 + " " + aString2;
        
        // if a value is stored, return it
        if (this.aligncost.containsKey(key)){
            return aligncost.get(key);
        } else {       
            // otherwise use 0 for equal symbols and defaultcost for different symbols
            return (aString1.equals(aString2))? 0:this.defaultcost;
        }
    }


}
