/**
 * Copyright 2008 DFKI GmbH.
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
package marytts.tools.voiceimport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

import marytts.datatypes.MaryXML;
import marytts.modules.phonemiser.Allophone;
import marytts.modules.phonemiser.AllophoneSet;
import marytts.util.dom.MaryDomUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.traversal.NodeIterator;
import org.w3c.dom.traversal.TreeWalker;
import org.xml.sax.SAXException;

public class TranscriptionAligner extends VoiceImportComponent {
    
    private DatabaseLayout db;
    private String locale;
    // properties
    public final String ORIGTRANS = "TranscriptionAligner.original";
    public final String CORRTRANS = "TranscriptionAligner.corrected";
    public final String RESULTTRANS = "TranscriptionAligner.results";
    public final String ALLOPHONEXML = "TranscriptionAligner.allophoneSetXML";
    private int progress;
    
    Map<String, Integer> aligncost;
    int defaultcost;
    int skipcost;
    AllophoneSet allophoneSet;
    
    // String for a boundary
    private String possibleBnd = "_";

    
    public TranscriptionAligner() {
        this.aligncost = new HashMap<String, Integer>();
        this.defaultcost = 1;
        this.skipcost = 1;
    }

    public String getName() {
        return "TranscriptionAligner";
    }
    
    public void initialiseComp()
    {      
        
    }
    
    public SortedMap<String,String> getDefaultProps(DatabaseLayout theDb) {
        this.db = theDb;
        String allophoneSetXml;
        locale = db.getProp(db.LOCALE);
        if (props == null){
            props = new TreeMap<String, String>();
            
            // original transcriptions (?LABDIR / TEXTDIR)
            String origTrans = System.getProperty(ORIGTRANS);
            if ( origTrans == null ) {
                origTrans = db.getProp(db.ROOTDIR)
                +"prompt_allophones"
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
                +"allophones"
                +System.getProperty("file.separator");
            }
            props.put(RESULTTRANS,resultTrans);
                        
            // alignment costs
            // generate file location of allophone definition file from locale as:
            // MARYBASE/lib/modules/en/us/lexicon/allophones.en_US.xml
            String[] localeParts = locale.split("_");
            allophoneSetXml = db.getProp(db.MARYBASE)+"/lib/modules/"
                + localeParts[0].toLowerCase()
                + ((localeParts.length > 1) ? "/"+localeParts[1].toLowerCase() : "")
                + "/lexicon/allophones."+locale+".xml";
            props.put(ALLOPHONEXML, allophoneSetXml);

        }
        return props;
    }
    
    protected void setupHelp(){
        props2Help = new TreeMap<String, String>();
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
        
        for (String phName : this.allophoneSet.getAllophoneNames()){
            // dont align boundaries with anything else
            this.aligncost.put( this.possibleBnd + " " + phName         ,max);
            this.aligncost.put( phName          + " " + this.possibleBnd,max);
        }
        
        // distance between pauses is zero, with slight conservative bias
        this.aligncost.put(this.possibleBnd + " " + this.possibleBnd,0);
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
        
        // phoneme set is used for splitting the sampa strings and setting the costs
        this.setAllophoneSet(AllophoneSet.getAllophoneSet((String) props.get(this.ALLOPHONEXML)));

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
        dbf.setNamespaceAware(true);
        DocumentBuilder docBuilder = dbf.newDocumentBuilder();
        
        // for writing xml files
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer = tFactory.newTransformer();

        System.out.println("traversing through " + bnl.getLength() + " files");
       
        for (int i=0;i<bnl.getLength();i++){
            progress = 100*i/bnl.getLength();
            File nextFile = new File(props.get(this.ORIGTRANS)
                    +System.getProperty("file.separator")
                    +bnl.getName(i)+".xml");

            System.out.println(bnl.getName(i));
            
            // get original xml file
            Document doc = docBuilder.parse(nextFile);

            // open destination xml file
            FileWriter docDest  = new FileWriter((String) props.get(this.RESULTTRANS) + nextFile.getName());
            
            // open file with manual transcription that is to be aligned
            
            //BufferedReader manTrans;
            
            String manTransString;
            try{

                String trfdir = (String) props.get(this.CORRTRANS);
                
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
     
    
    public void setAllophoneSet(AllophoneSet aSet) {
        this.allophoneSet = aSet;
    }

    /**
     * This reads in a label file and returns a String of the phonetic symbols,
     * separated by white spaces. Pause symbols ("_") are disregarded (skipped).
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
        
        // todo: check
        // if Label File does not start with pause symbol, insert it
        // as well as a pause duration of zero (...)
        if(! result.startsWith("_")){
            result = "_ " + result;
            //this.pauseLengths.add(0, 0);
        }
        lab.close();
        return result;
    }

    /**
     * This sets the distance by using the phoneme set of the aligner object.
     * Phoneme set must already be specified.
     */
    private void setDistance(){
        
        if (null == this.allophoneSet )
            throw new IllegalStateException("Phoneme set must be specified before generic distance method can be executed.");
        
        for (String fromSym : this.allophoneSet.getAllophoneNames()){
            for (String toSym : this.allophoneSet.getAllophoneNames()){
                
                int diff = 0;
                
                Allophone fromPh = this.allophoneSet.getAllophone(fromSym);
                Allophone toPh = this.allophoneSet.getAllophone(toSym);
                
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
                
                this.aligncost.put(key, diff);
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
     * The symbols of the original transcription are aligned to corrected 
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
        
        // get all t and boundary elements
        NodeIterator tokenIt = MaryDomUtils.createNodeIterator(doc, MaryXML.TOKEN, MaryXML.BOUNDARY);
        List<Element> tokens = new ArrayList<Element>();
        Element e;
        while ((e = (Element) tokenIt.nextNode()) != null) {
            tokens.add(e);
        }
        
        String orig = this.collectTranscription(tokens);
        
        // now we align the transcriptions and split it at the delimiters
        String al = this.distanceAlign(orig.trim(),correct.trim()) + " ";

        System.out.println("Alignments: "+al);
        String[] alignments = al.split("#");
        
        // change the transcription in xml according to the aligned one
        doc = this.changeTranscriptions(doc, tokens, alignments);
                
        return doc;
    }
    
    /**
     * 
     * This computes a string of phonetic symbols out of an allophones xml:
     * - standard phonemes are taken from "ph" attribute
     * - after each token-element (except those followed by a "boundary"-element), 
     *   a "bnd" symbol is inserted (standing for a possible pause)
     * 
     * @param tokens
     * @return
     */
    private String collectTranscription(List<Element> tokens)
    {
        // String Tokenizer devides transcriptions into syllables
        // syllable delimiters and stress symbols are retained
        String delims = "',-";

        // String storing the original transcription begins with a pause
        String orig = " " + this.possibleBnd + " " ;

        
        // get original phoneme String
        for (int tNr = 0; tNr < tokens.size() ; tNr++ ){
            
            Element token = (Element) tokens.get(tNr);
            
            // only look at it if there is a sampa to change
            if ( token.hasAttribute("ph") ) {                   
                
                String sampa = token.getAttribute("ph");
    
                List<String> sylsAndDelims = new ArrayList<String>();
                StringTokenizer sTok = new StringTokenizer(sampa, delims, true);
                
                while(sTok.hasMoreElements()){
                    String currTok = sTok.nextToken();
                    
                    if (delims.indexOf(currTok) == -1) {
                        // current Token is no delimiter
                        
                        
                        for (Allophone ph : allophoneSet.splitIntoAllophones(currTok)){
                            
                            if (!ph.isTone()) {
                                orig += ph.name() + " ";
                            }
                        }// ... for each phoneme
                        
                        
                    }// ... if no delimiter
                }// ... while there are more tokens    
            }
            
            if ( token.getTagName().equals(MaryXML.TOKEN) )
                orig += this.possibleBnd + " ";
                                        
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
    private Document changeTranscriptions(Document doc, List<Element> tokens, String[] alignments){
        
        // todo: make delims argument
        // String Tokenizer devides transcriptions into syllables
        // syllable delimiters and stress symbols are retained
        String delims = "',-";
        
        // counter to keep track of the position in alignment array
        // starts with 1 since transcription begins with a pause
        int currAl = 0;
        
        // second looping: get original phoneme String
        for (int tNr = 0; tNr < tokens.size() ; tNr++ ){
            Element token = tokens.get(tNr);
   
            // treat boundaries
            if ( token.getTagName().equals("t") ) {
                String sampa = token.getAttribute("ph");
                StringBuilder newSampa = new StringBuilder();
                // only look at it if there is a sampa to change
                if ( !sampa.equals("") ) {
                    List<String> sylsAndDelims = new ArrayList<String>();
                    StringTokenizer sTok = new StringTokenizer(sampa, delims, true);
                    
                    while(sTok.hasMoreElements()) {
                        String currTok = sTok.nextToken();
                        if (delims.indexOf(currTok) == -1) {
                            // current Token is no delimiter
                            for (Allophone ph : allophoneSet.splitIntoAllophones(currTok)) {
                                if (ph.isTone()) continue; // skip tone symbols, if any
                                
                                if (newSampa.length() > 0) { // insert a space between all symbols
                                    newSampa.append(" ");
                                }
                                // new transcription is the aligned ones without white spaces
                                newSampa.append(alignments[currAl].replaceAll(" ", ""));
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
                                if (newSampa.length() > 0) { // spaces between all symbols
                                    newSampa.append(" ");
                                }
                                newSampa.append(currTok);
                            }                      
                        }
                    }// ... while there are more syllables 
                    
                    // if new sampa ends with delimiters, delete them
                    while (newSampa.length() > 0 &&
                            delims.indexOf( newSampa.substring(newSampa.length()-1) ) != -1 ) {
                        newSampa.deleteCharAt(newSampa.length()-1);
                    }
                    
                    // set new sampa
                    token.setAttribute("ph", newSampa.toString());
                    reCreateSubStructure(token);
                }// ... if there is transcription

                // There is always an entry in alignment between words, usually an empty one, but
                // sometimes a pause symbol (_).
                // if the following element is no boundary, a delimiter was inserted
                if (tNr == tokens.size()-1 || 
                    !((Element) tokens.get(tNr+1)).getTagName().equals("boundary") ) {
                    
                    if (currAl < alignments.length && alignments[currAl].indexOf(possibleBnd) > -1) {
                        System.out.println("  inserted boundary in xml");
                        Element b = doc.createElement("boundary");
                        b.setAttribute("breakindex", "3");
                        token.getParentNode().insertBefore(b, token.getNextSibling());
                    }
                    currAl += 1; // Step forward in alignment only when no boundary is following
                    
                }
            } else if (token.getTagName().equals("boundary")) {
                
                if (currAl >= alignments.length 
                        || !alignments[currAl].trim().equals(possibleBnd)) {
                    System.out.println("  deleted boundary from xml");
                    token.getParentNode().removeChild(token);
                }

                    currAl += 1;   
                             
            } else {
                // should be "t" or "boundary" elements
                assert(false);
            }

        }// ... for each t-Element

        return doc;
    }

    private void reCreateSubStructure(Element token)
    {
        String phone = token.getAttribute("ph");
        if (phone.equals(""))
            return; // nothing to do

        // First delete all child elements
        Element child;
        while ((child = MaryDomUtils.getFirstChildElement(token)) != null) {
            token.removeChild(child);
        }
        // Now create them new:
        StringTokenizer tok = new StringTokenizer(phone, "-");
        Document document = token.getOwnerDocument();
        while (tok.hasMoreTokens()) {
            String sylString = tok.nextToken();
            Element syllable = MaryXML.createElement(document, MaryXML.SYLLABLE);
            token.appendChild(syllable);
            // Check for stress signs:
            String first = sylString.substring(0, 1);
            if (first.equals("'")) {
                syllable.setAttribute("stress", "1");
                // The primary stressed syllable of a word
                // inherits the accent:
                if (token.hasAttribute("accent")) {
                    syllable.setAttribute("accent", token.getAttribute("accent"));
                }
            } else if (first.equals(",")) {
                syllable.setAttribute("stress", "2");
            }
            // Remember transcription in ph attribute:
            syllable.setAttribute("ph", sylString);
            // Now identify the composing segments:
            Allophone[] allophones = allophoneSet.splitIntoAllophones(sylString);
            for (int i = 0; i < allophones.length; i++) {
                Element segment = MaryXML.createElement(document, MaryXML.PHONE);
                syllable.appendChild(segment);
                segment.setAttribute("p", allophones[i].name());
                // TODO: need to set loudness-specific voice quality attribute "vq" for de6 and de7
            }
        }
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

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception
    {
        VoiceImportComponent vic  =  new TranscriptionAligner();
        DatabaseLayout db = new DatabaseLayout(vic);
        vic.compute();
    }
}

